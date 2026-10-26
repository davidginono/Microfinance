package com.sacco.mvp.grouplending;

import com.sacco.mvp.accounting.dto.VoucherDtos.ReceiptContribution;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.AllocationPreview;
import com.sacco.mvp.grouplending.dto.GroupCollectionDtos.*;
import com.sacco.mvp.grouplending.dto.GroupSharedLoanDtos.Balance;
import com.sacco.mvp.grouplending.service.SharedContributionAllocator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SharedContributionAllocatorTest {
    final UUID loan=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();
    final Beneficiary parent=new Beneficiary(loan,UUID.randomUUID(),"G1","Shared borrowers","L1");
    BigDecimal n(String value){return new BigDecimal(value);}
    Balance balance(UUID id,String principal,String interest,String fees){return new Balance(id,id.toString(),"Synthetic member",n(principal),n(interest),n(fees),n("0.00"),n("0.00"),n("0.00"),n("0.00"),false);}
    Form form(String... amounts){var f=new Form();f.setRows(new ArrayList<>());for(var amount:amounts){var r=new Row();r.setLoanId(loan);r.setPayerId(first);r.setAmount(n(amount));r.setDescription("Verified cash");f.getRows().add(r);}return f;}
    Map<UUID,Payer> payers(){return Map.of(first,new Payer(first,"P1","Actual payer"));}
    BigDecimal sum(List<ReceiptContribution> rows,UUID id,java.util.function.Function<ReceiptContribution,BigDecimal> component){return rows.stream().filter(r->r.beneficiaryId().equals(id)).map(component).reduce(BigDecimal.ZERO,BigDecimal::add);}
    @Test void cashComponentsFollowSeparateProportionalRemainingShares(){
        var balances=List.of(balance(first,"10.00","1.00","2.00"),balance(second,"30.00","3.00","0.00"));var f=form("4.00","8.00");
        var rows=SharedContributionAllocator.allocate(f,parent,payers(),new AllocationPreview(n("6.00"),n("4.00"),n("34.00"),n("2.00")),balances);
        assertThat(sum(rows,first,ReceiptContribution::principal)).isEqualByComparingTo("1.50");assertThat(sum(rows,second,ReceiptContribution::principal)).isEqualByComparingTo("4.50");
        assertThat(sum(rows,first,ReceiptContribution::interest)).isEqualByComparingTo("1.00");assertThat(sum(rows,second,ReceiptContribution::interest)).isEqualByComparingTo("3.00");
        assertThat(sum(rows,first,ReceiptContribution::charges)).isEqualByComparingTo("2.00");assertThat(sum(rows,second,ReceiptContribution::charges)).isZero();
        assertThat(rows.stream().filter(r->r.sourceLineNo()==1).map(ReceiptContribution::amount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("4.00");
        assertThat(SharedContributionAllocator.allocate(f,parent,payers(),new AllocationPreview(n("6.00"),n("4.00"),n("34.00"),n("2.00")),balances)).isEqualTo(rows);
    }
    @Test void oneCentNeverOvercreditsAZeroOrExhaustedShare(){
        var rows=SharedContributionAllocator.allocate(form("0.01"),parent,payers(),new AllocationPreview(n("0.01"),n("0.00"),n("0.00"),n("0.00")),List.of(balance(first,"0.00","0.00","0.00"),balance(second,"0.01","0.00","0.00")));
        assertThat(rows).hasSize(1);assertThat(rows.getFirst().beneficiaryId()).isEqualTo(second);assertThat(rows.getFirst().amount()).isEqualByComparingTo("0.01");
    }
    @Test void maximumContributorAndBeneficiaryCountsStayWithinNinetyNineEdges(){
        var balances=new ArrayList<Balance>();for(int i=0;i<50;i++)balances.add(balance(UUID.randomUUID(),i==0?"1.99":i==49?"2.01":"2.00","0.00","0.00"));
        var amounts=new String[50];Arrays.fill(amounts,"2.00");var rows=SharedContributionAllocator.allocate(form(amounts),parent,payers(),new AllocationPreview(n("100.00"),n("0.00"),n("0.00"),n("0.00")),balances);
        assertThat(rows.size()).isBetween(50,99);assertThat(rows.stream().map(ReceiptContribution::amount).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("100.00");
        for(var b:balances)assertThat(sum(rows,b.client(),ReceiptContribution::principal)).isEqualByComparingTo(b.principal());
    }
    @Test void unbalancedContributorCommandsFailInsteadOfInventingCredit(){
        var allocation=new AllocationPreview(n("10.00"),n("0.00"),n("0.00"),n("0.00"));var balances=List.of(balance(first,"10.00","0.00","0.00"));
        assertThatThrownBy(()->SharedContributionAllocator.allocate(form("9.99"),parent,payers(),allocation,balances)).hasMessage("group.error.attribution");
        assertThatThrownBy(()->SharedContributionAllocator.allocate(form("10.01"),parent,payers(),allocation,balances)).hasMessage("group.error.attribution");
    }
}
