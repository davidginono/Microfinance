package com.sacco.mvp.accounting.reports;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.sacco.mvp.accounting.reports.CashFlowAllocation.*;

class CashFlowAllocationTest {
    private final UUID cash=UUID.randomUUID(),bank=UUID.randomUUID(),income=UUID.randomUUID(),funding=UUID.randomUUID(),asset=UUID.randomUUID();
    private SourceLine line(UUID account,String purpose,String amount){return new SourceLine(UUID.randomUUID(),account,"T","ASSET",purpose,new BigDecimal(amount));}
    private Source source(SourceLine...lines){return new Source(UUID.randomUUID(),UUID.randomUUID(),1,"Synthetic",List.of(lines));}
    private Split split(SourceLine line,UUID target,Activity activity,String amount){return new Split(line.id(),target,activity,new BigDecimal(amount));}
    @Test void exactCompoundCashReceiptRetainsTwoActivities(){var m=line(cash,"CASH","100.01");var s=source(m,line(income,"INTEREST_INCOME","-30.01"),line(funding,"FUNDING","-70.00"));assertThatCode(()->validate(s,List.of(split(m,income,Activity.OPERATING,"30.01"),split(m,funding,Activity.FINANCING,"70.00")),null)).doesNotThrowAnyException();}
    @Test void missingOrInflatedMoneyLineIsRejected(){var m=line(cash,"CASH","100.01");var s=source(m,line(income,"INCOME","-100.01"));assertThatThrownBy(()->validate(s,List.of(split(m,income,Activity.OPERATING,"100.00")),null)).hasMessage("financial.cash.error.lineTotal");}
    @Test void fractionalCentsAndWrongSignsRejected(){var m=line(cash,"CASH","1.01");var s=source(m,line(income,"INCOME","-1.01"));assertThatThrownBy(()->validate(s,List.of(split(m,income,Activity.OPERATING,"1.011")),null)).hasMessage("financial.cash.error.amount");assertThatThrownBy(()->validate(s,List.of(split(m,income,Activity.OPERATING,"-1.01")),null)).hasMessage("financial.cash.error.moneyLine");}
    @Test void internalTransferIsRetainedAndNetZero(){var c=line(cash,"CASH","-20.01");var b=line(bank,"BANK","20.01");assertThatCode(()->validate(source(c,b),List.of(split(c,bank,Activity.INTERNAL_TRANSFER,"-20.01"),split(b,cash,Activity.INTERNAL_TRANSFER,"20.01")),null)).doesNotThrowAnyException();}
    @Test void transferCannotBeRenamedOperatingRevenue(){var c=line(cash,"CASH","-20.01");var b=line(bank,"BANK","20.01");assertThatThrownBy(()->validate(source(c,b),List.of(split(c,bank,Activity.OPERATING,"-20.01"),split(b,cash,Activity.INTERNAL_TRANSFER,"20.01")),null)).hasMessage("financial.cash.error.transfer");}
    @Test void noncashResidualNeedsExplicitReviewedEvidence(){var m=line(cash,"CASH","100.00");var s=source(m,line(funding,"FUNDING","-150.00"),line(asset,"FIXED_ASSET","50.00"));var splits=List.of(split(m,funding,Activity.FINANCING,"100.00"));assertThatThrownBy(()->validate(s,splits,null)).hasMessage("financial.cash.error.noncashEvidence");assertThatCode(()->validate(s,splits,"Synthetic 50.00 asset financed without cash")).doesNotThrowAnyException();}
    @Test void allocationCannotOveruseCounterpart(){var m=line(cash,"CASH","100.00");var s=source(m,line(income,"INCOME","-30.00"),line(funding,"FUNDING","-70.00"));assertThatThrownBy(()->validate(s,List.of(split(m,income,Activity.OPERATING,"40.00"),split(m,funding,Activity.FINANCING,"60.00")),"Explanation")).hasMessage("financial.cash.error.counterpartTotal");}
    @Test void duplicateAndForeignLineIdsRejected(){var m=line(cash,"CASH","100.00");var s=source(m,line(income,"INCOME","-100.00"));var split=split(m,income,Activity.OPERATING,"50.00");assertThatThrownBy(()->validate(s,List.of(split,split),null)).hasMessage("financial.cash.error.duplicate");assertThatThrownBy(()->validate(s,List.of(new Split(UUID.randomUUID(),income,Activity.OPERATING,new BigDecimal("100.00"))),null)).hasMessage("financial.cash.error.moneyLine");}
    @Test void missingCoverageIsNeverComplete(){assertThat(new Coverage(List.of(),List.of(UUID.randomUUID())).complete()).isFalse();}
}
