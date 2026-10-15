package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.RegisterSort;
import com.sacco.mvp.accounting.dto.VoucherDtos;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.accounting.repository.VoucherRepository;
import com.sacco.mvp.accounting.repository.LoanReceiptRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegisterColumnSortingTest {
    @Test void combinedReceiptRegisterAppliesEveryColumnSortAcrossAllReceiptSources() {
        var actor = mock(AppUserPrincipal.class);
        when(actor.getSaccoId()).thenReturn("institution"); when(actor.getStationId()).thenReturn("branch");
        for (String column : List.of("number", "date", "party", "reference", "transactions", "amount", "status")) {
            for (String direction : List.of("asc", "desc")) {
                var jdbc = mock(JdbcTemplate.class);
                var filter = new VoucherDtos.Filter("payer", null, null, "POSTED", column+"_"+direction, 2, 30);
                new LoanReceiptRepository(jdbc).list(actor,filter,true);
                var call = mockingDetails(jdbc).getInvocations().iterator().next();
                assertThat((String)call.getArgument(0)).contains("union all", "from accountant_loan_receipt", "from accountant_loan_post")
                    .endsWith(" "+direction+",v.id "+direction+" limit ? offset ?");
                assertThat(call.getArguments()).contains("institution", "branch", true, "payer", "POSTED", 31, 60);
            }
        }
    }
    @Test void everyVoucherColumnOrdersTheScopedResultBeforePagination() {
        for (String column : List.of("number", "date", "party", "reference", "transactions", "amount", "status")) {
            for (String direction : List.of("asc", "desc")) {
                var jdbc = mock(JdbcTemplate.class);
                var filter = new VoucherDtos.Filter("payer", null, null, "POSTED", column+"_"+direction, 2, 30);
                VoucherService.validateFilter(filter);
                new VoucherRepository(jdbc).list("institution", "branch", VoucherDtos.Type.RECEIPT, filter);
                var call = mockingDetails(jdbc).getInvocations().iterator().next();
                assertThat((String)call.getArgument(0)).contains("where v.sacco_id=? and v.station_id=? and v.type=?")
                    .contains("order by ").endsWith(" "+direction+",v.id "+direction+" limit ? offset ?");
                assertThat(call.getArguments()).contains("institution", "branch", "RECEIPT", "payer", "POSTED", 31, 60);
            }
        }
    }

    @Test void loanAndPostingColumnsKeepBranchProductDateFiltersAndBoundedPages() {
        var actor = mock(AppUserPrincipal.class);
        when(actor.getSaccoId()).thenReturn("institution"); when(actor.getStationId()).thenReturn("branch");
        var product = UUID.randomUUID(); var today = LocalDate.of(2026,10,14);
        for (boolean posting : List.of(false,true)) {
            for (String column : List.of("number", "product", "client", "date", "amount", "status", posting?"reference":"balance")) {
                for (String direction : List.of("asc", "desc")) {
                    var jdbc = mock(JdbcTemplate.class); var repository = new LoanRecordingRepository(jdbc);
                    var filter = new LoanRecordingDtos.Filter("client", today.minusDays(10), today, "", column+"_"+direction, 2, null, product);
                    if (posting) repository.posts(actor,"PAYMENT",filter,26,50);
                    else repository.loans(actor,filter,today,26,50);
                    var call = mockingDetails(jdbc).getInvocations().iterator().next();
                    assertThat((String)call.getArgument(0)).contains(posting?"where p.sacco_id=? and p.station_id=?":"where r.sacco_id=? and r.station_id=?")
                        .contains("a.loan_product_setting_id=?::uuid")
                        .endsWith(" "+direction+","+(posting?"p":"a")+".id "+direction+" limit ? offset ?");
                    assertThat(call.getArguments()).contains("institution", "branch", product, today, today.minusDays(10), 26, 50);
                }
            }
        }
    }

    @Test void unknownColumnsAndDirectionsCannotReachSql() {
        var columns = Map.of("date","v.effective_date","amount","v.total");
        for (String invalid : List.of("party_asc", "date_random", "date_desc;drop table accounting_voucher", "", "date_asc_nulls_first")) {
            assertThatThrownBy(()->RegisterSort.order(invalid,columns,"v.id")).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(RegisterSort.order("newest",columns,"v.id")).isEqualTo("v.effective_date desc,v.id desc");
        assertThat(RegisterSort.order("oldest",columns,"v.id")).isEqualTo("v.effective_date asc,v.id asc");
        assertThat(RegisterSort.order("amount",columns,"v.id")).isEqualTo("v.total desc,v.id desc");
    }
}
