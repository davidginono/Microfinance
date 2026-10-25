package com.sacco.mvp.grouplending;

import com.sacco.mvp.accounting.dto.VoucherDtos.ReceiptContribution;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.AllocationPreview;
import com.sacco.mvp.grouplending.dto.GroupCollectionDtos.*;
import com.sacco.mvp.grouplending.service.GroupContributionAllocator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GroupContributionAllocatorTest {
    @Test void fiftyUnequalContributionsConserveActualLoanComponentsToTheCent(){
        UUID loan=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();var random=new Random(9381);
        var borrowers=Map.of(loan,new Beneficiary(loan,first,"C1","Borrower","LN-1"));var payers=Map.of(first,new Payer(first,"C1","First payer"),second,new Payer(second,"C2","Second payer"));
        var components=Map.of(loan,new AllocationPreview(new BigDecimal("1000.01"),new BigDecimal("75.37"),BigDecimal.ZERO,new BigDecimal("10.89")));
        for(int scenario=0;scenario<100;scenario++){
            var form=new Form();form.setRows(new ArrayList<>());int remaining=108627;
            for(int row=0;row<50;row++){int cents=row==49?remaining:1+random.nextInt(remaining-(49-row));remaining-=cents;var item=new Row();item.setLoanId(loan);item.setPayerId(row%2==0?first:second);item.setAmount(BigDecimal.valueOf(cents,2));item.setDescription("Verified contribution "+row);form.getRows().add(item);}
            var attributed=GroupContributionAllocator.allocate(form,borrowers,payers,components);
            assertThat(attributed).hasSize(50).allSatisfy(r->assertThat(r.amount()).isEqualByComparingTo(r.principal().add(r.interest()).add(r.charges())));
            assertThat(attributed.stream().map(ReceiptContribution::principal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("1000.01");
            assertThat(attributed.stream().map(ReceiptContribution::interest).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("75.37");
            assertThat(attributed.stream().map(ReceiptContribution::charges).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("10.89");
            assertThat(GroupContributionAllocator.allocate(form,borrowers,payers,components)).isEqualTo(attributed);
        }
    }
}
