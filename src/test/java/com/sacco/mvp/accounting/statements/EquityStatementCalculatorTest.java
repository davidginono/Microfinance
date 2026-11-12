package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static com.sacco.mvp.accounting.statements.StatementCalculatorTest.*;

class EquityStatementCalculatorTest {
    Balance capital=balance("EQUITY","CAPITAL","EQUITY","FINANCING","-100","0","20");
    Balance retained=balance("EQUITY","OTHER","EQUITY","FINANCING","-40","11","0");
    Balance reserve=balance("EQUITY","OTHER","EQUITY","FINANCING","-10","0","6");
    Balance income=balance("INCOME","INCOME","INCOME","OPERATING","0","0","30");
    Setup setup(){return new Setup(1,List.of(new Component("Owner capital","Mtaji",List.of(capital.id()),false),new Component("Retained earnings","Faida",List.of(retained.id()),true),new Component("Reserve","Akiba",List.of(reserve.id()),false)),List.of(),List.of());}
    Movement movement(Balance b,Category c,String debit,String credit){return new Movement(b.id(),c,false,n(debit),n(credit));}
    @Test void separatesPostedChangesAndReserveTransfersDoNotIncreaseTotalEquity(){
        var movements=List.of(movement(capital,Category.CONTRIBUTION,"0","20"),movement(retained,Category.DISTRIBUTION,"5","0"),movement(retained,Category.TRANSFER,"6","0"),movement(reserve,Category.TRANSFER,"0","6"));
        var result=EquityStatementCalculator.calculate(List.of(capital,retained,reserve,income),movements,setup());
        assertThat(result.current()).extracting(EquityRow::label).containsExactly("fs.opening","fs.surplus","fs.movement.CONTRIBUTION","fs.movement.DISTRIBUTION","fs.movement.TRANSFER","fs.closing");
        assertThat(result.current().getLast().values().get("component:1")).isEqualByComparingTo("59");
        assertThat(result.current().getLast().total()).isEqualByComparingTo("195");
        assertThat(result.current().get(4).total()).isEqualByComparingTo("0");assertThat(result.checks()).isEmpty();
    }
    @Test void componentChecksDetectErrorsEvenWhenOverallTotalMatches(){
        var result=EquityStatementCalculator.calculate(List.of(capital,retained,reserve,income),List.of(movement(capital,Category.CONTRIBUTION,"0","20"),movement(retained,Category.DISTRIBUTION,"5","0")),setup());
        assertThat(result.checks()).filteredOn(c->c.key().equals("equity")).hasSize(2);
    }
    @Test void unclassifiedChangesAreNeverInventedAsCapitalOrReserves(){
        var result=EquityStatementCalculator.calculate(List.of(capital),List.of(movement(capital,Category.UNCLASSIFIED,"0","20")),Setup.empty());
        assertThat(result.current()).extracting(EquityRow::label).contains("fs.movement.UNCLASSIFIED");assertThat(result.checks()).extracting(Check::key).contains("equityMovements");assertThat(result.current().getLast().total()).isEqualByComparingTo("120");
    }
    @Test void closingProfitDoesNotEraseIncomeOrCountProfitTwice(){
        var earnings=balance("EQUITY","OTHER","EQUITY","FINANCING","0","0","30");var closedIncome=balance("INCOME","INCOME","INCOME","OPERATING","0","30","30");
        var movements=List.of(movement(earnings,Category.EARNINGS_TRANSFER,"0","30"),movement(closedIncome,Category.EARNINGS_TRANSFER,"30","0"));
        var config=new Setup(1,List.of(new Component("Retained earnings","",List.of(earnings.id()),true)),List.of(),List.of());
        var result=EquityStatementCalculator.calculate(List.of(earnings,closedIncome),movements,config);
        assertThat(result.current().get(1).total()).isEqualByComparingTo("30");assertThat(result.current().getLast().total()).isEqualByComparingTo("30");assertThat(result.checks()).isEmpty();
        var adjusted=EquityStatementCalculator.incomeWithoutClosings(List.of(closedIncome),movements);
        assertThat(adjusted.getFirst().movement()).isEqualByComparingTo("-30");assertThat(adjusted.getFirst().closing()).isEqualByComparingTo("0");
    }
    @Test void comparativePeriodUsesItsOwnOpeningAndSignedChanges(){
        var prior=new Balance(capital.id(),"311001","Capital","EQUITY","CAPITAL",capital.mapping(),n("100").negate(),n("0"),n("20"),n("120").negate(),n("50").negate(),n("0"),n("10"),n("60").negate());
        var result=EquityStatementCalculator.calculate(List.of(prior),List.of(movement(prior,Category.CONTRIBUTION,"0","20"),new Movement(prior.id(),Category.CONTRIBUTION,true,n("0"),n("10"))),Setup.empty());
        assertThat(result.previous().getFirst().total()).isEqualByComparingTo("50");assertThat(result.previous().getLast().total()).isEqualByComparingTo("60");assertThat(result.checks()).isEmpty();
    }
    @Test void signedCorrectionsAndCentsRemainExact(){
        var b=balance("EQUITY","CAPITAL","EQUITY","FINANCING","-10.01","0.03","0");var result=EquityStatementCalculator.calculate(List.of(b),List.of(movement(b,Category.CONTRIBUTION,"0.03","0")),Setup.empty());
        assertThat(result.current().get(2).total()).isEqualByComparingTo("-0.03");assertThat(result.current().getLast().total()).isEqualByComparingTo("9.98");assertThat(result.checks()).isEmpty();
    }
}
