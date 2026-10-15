package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.AccountingLibraryDtos.Activity;
import com.sacco.mvp.accounting.repository.AccountingCodeLibraryRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountingLibrarySortingTest {
    @Test void everyTransactionColumnCanSortAcrossPages() {
        for (var column : Map.of("code","t.code", "name","t.name", "activity","activity_code", "sourceEvent","t.source_event", "template","has_template", "status","t.active").entrySet()) {
            for (String direction : List.of("asc", "desc")) {
                var jdbc=mock(JdbcTemplate.class);
                new AccountingCodeLibraryRepository(jdbc).transactionRegister("institution",null,"","ACTIVE","",60,31,column.getKey(),direction);
                var call=mockingDetails(jdbc).getInvocations().iterator().next();
                assertThat((String)call.getArgument(0)).contains("where t.sacco_id=?")
                    .endsWith("order by "+column.getValue()+" "+direction+",t.id limit ? offset ?");
                assertThat(call.getArguments()).contains("institution","ACTIVE",31,60);
            }
        }
    }
    @Test void sortedPagesKeepTenantFiltersAndBoundedLookahead() {
        var repository=mock(AccountingCodeLibraryRepository.class);
        var ledger=mock(GeneralLedgerService.class);
        var actor=mock(AppUserPrincipal.class);when(actor.getSaccoId()).thenReturn("institution");
        var service=new AccountingCodeLibraryService(repository,ledger,null,null);
        var rows=IntStream.range(0,31).mapToObj(n->new Activity(UUID.randomUUID(),"A"+n,"Name "+n,"","",true)).toList();
        when(repository.activities("institution","office","ACTIVE",60,31,"name","desc")).thenReturn(rows);
        var page=service.activities(actor," Office ","ACTIVE",2,30,"name","desc");
        assertThat(page.rows()).hasSize(30);assertThat(page.hasNext()).isTrue();assertThat(page.page()).isEqualTo(2);
        verify(ledger).requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");
        verify(repository).activities("institution","office","ACTIVE",60,31,"name","desc");
    }

    @Test void invalidSortingAndUnauthorizedReadsNeverReachTheQuery() {
        var repository=mock(AccountingCodeLibraryRepository.class);var ledger=mock(GeneralLedgerService.class);
        var actor=mock(AppUserPrincipal.class);var service=new AccountingCodeLibraryService(repository,ledger,null,null);
        assertThatThrownBy(()->service.activities(actor,"","",0,30,"name;drop table gl_activity","asc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.transactionRegister(actor,null,"","",0,30,"code","random")).isInstanceOf(IllegalArgumentException.class);
        doThrow(new AccessDeniedException("Denied")).when(ledger).requireActor(actor,"ACCOUNTING_ACCOUNTS_VIEW");
        assertThatThrownBy(()->service.activities(actor,"","",0,30,"name","asc")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test void transactionOrderRunsBeforeLimitWithStableTieBreakerAndParentScope() {
        var jdbc=mock(JdbcTemplate.class);var repository=new AccountingCodeLibraryRepository(jdbc);var parent=UUID.randomUUID();
        repository.transactionRegister("institution",parent,"office","ACTIVE","",100,51,"status","desc");
        var sql=ArgumentCaptor.forClass(String.class);var args=ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).query(sql.capture(),any(RowMapper.class),args.capture());
        assertThat(sql.getValue()).contains("where t.sacco_id=? and t.activity_id=?").endsWith("order by t.active desc,t.id limit ? offset ?");
        assertThat(args.getValue()).containsExactly("institution",parent,"office","office","office","ACTIVE","ACTIVE","","",51,100);
        assertThatThrownBy(()->repository.activities("institution","","",0,31,"unknown","asc")).isInstanceOf(IllegalArgumentException.class);
    }
}
