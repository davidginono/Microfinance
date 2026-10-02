package com.sacco.mvp.accounting.statements;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static com.sacco.mvp.accounting.statements.StatementDefinition.*;
import static org.assertj.core.api.Assertions.*;

class StatementDefinitionTest {
    @Test void statementAndSubmissionClaimsAreNeverDefaultGrants(){
        for(var position:com.sacco.mvp.domain.Position.values())for(boolean memberAccess:List.of(false,true))assertThat(com.sacco.mvp.domain.UserClaim.defaultClaims(List.of(position),memberAccess)).noneMatch(c->c.name().startsWith("STATEMENT_") || c.name().startsWith("REGULATORY_SUBMISSION_"));
    }
    final UUID cash=UUID.randomUUID(),capital=UUID.randomUUID();
    final List<Account> catalog=List.of(new Account(cash,"CASH","Cash","ASSET","CASH"),new Account(capital,"CAPITAL","Capital","EQUITY","CAPITAL"));
    Row group(String id,UUID account,Section section,Sign sign){return new Row(id,id,id,RowKind.ACCOUNT_GROUP,section,Unit.TZS,List.of(account),sign,null,null,null,true,false,false,null);}
    Row expression(String id,Operation op,String...args){return new Row(id,id,id,op==Operation.RATIO?RowKind.RATIO:RowKind.SUBTOTAL,Section.NOTES,op==Operation.RATIO?Unit.RATIO:Unit.TZS,List.of(),null,new Expression(op,List.of(args)),null,null,true,false,false,null);}
    StatementDefinition def(Row...rows){return new StatementDefinition(1,1,Kind.BALANCE_SHEET,"Statement","Taarifa",List.of(rows),List.of(),true);}
    @Test void exactValuesAndRatiosAreIndependentOfRowOrderAndLabels(){
        var a=group("A",cash,Section.ASSETS,Sign.DEBIT_POSITIVE);var e=group("E",capital,Section.EQUITY,Sign.CREDIT_POSITIVE);
        var d=def(expression("TOTAL",Operation.SUM,"A","E"),expression("RATIO",Operation.RATIO,"A","E"),e,a);d.validateMappings(catalog);
        var v=d.calculate(Map.of(cash,new BigDecimal("1000000000000000.01"),capital,new BigDecimal("-1000000000000000.01")));
        assertThat(v.get("TOTAL").amount()).isEqualByComparingTo("2000000000000000.02");assertThat(v.get("RATIO").amount()).isEqualByComparingTo("1.000000");
    }
    @Test void omittedForeignOverlapAndInvalidSignsAreRejected(){
        var a=group("A",cash,Section.ASSETS,Sign.DEBIT_POSITIVE);var e=group("E",capital,Section.EQUITY,Sign.CREDIT_POSITIVE);
        assertThatThrownBy(()->def(a).validateMappings(catalog)).hasMessage("statement.error.omitted");
        assertThatThrownBy(()->def(a,e,group("DUP",cash,Section.ASSETS,Sign.DEBIT_POSITIVE)).validateMappings(catalog)).hasMessage("statement.error.overlap");
        assertThatThrownBy(()->def(group("FOREIGN",UUID.randomUUID(),Section.ASSETS,Sign.DEBIT_POSITIVE),e).validateMappings(catalog)).hasMessage("statement.error.account");
        assertThatThrownBy(()->def(group("WRONG",cash,Section.ASSETS,Sign.CREDIT_POSITIVE),e).validateMappings(catalog)).hasMessage("statement.error.sign");
    }
    @Test void cycleUnitsDoubleCountingAndDeepExpressionsAreRejected(){
        var a=group("A",cash,Section.ASSETS,Sign.DEBIT_POSITIVE);var e=group("E",capital,Section.EQUITY,Sign.CREDIT_POSITIVE);
        assertThatThrownBy(()->def(a,e,expression("T",Operation.SUM,"A","U"),expression("U",Operation.SUM,"E","T")).validate()).hasMessage("statement.error.cycle");
        assertThatThrownBy(()->def(a,e,expression("T",Operation.SUM,"A","E"),expression("D",Operation.SUM,"T","A")).validate()).hasMessage("statement.error.doubleCount");
        assertThatThrownBy(()->def(a,e,expression("R",Operation.RATIO,"A","E"),expression("T",Operation.SUM,"A","R")).validate()).hasMessage("statement.error.units");
        var list=new ArrayList<Row>();list.add(a);list.add(e);for(int n=0;n<9;n++)list.add(expression("R"+n,Operation.RATIO,n==0?"A":"X"+n,"E"));
        // A chain of disjoint subtotal operands exceeds depth even when cached children were validated first.
        list=new ArrayList<>();for(int n=0;n<12;n++)list.add(group("A"+n,UUID.randomUUID(),Section.ASSETS,Sign.DEBIT_POSITIVE));
        list.add(expression("S0",Operation.SUM,"A0","A1"));for(int n=1;n<9;n++)list.add(expression("S"+n,Operation.SUM,"S"+(n-1),"A"+(n+1)));
        var tooDeep=new StatementDefinition(1,1,Kind.BALANCE_SHEET,"Statement","Taarifa",list,List.of(),false);assertThatThrownBy(tooDeep::validate).hasMessage("statement.error.depth");
    }
    @Test void zeroDenominatorAndUnknownValuesStayExplicit(){
        var d=def(group("A",cash,Section.ASSETS,Sign.DEBIT_POSITIVE),group("E",capital,Section.EQUITY,Sign.CREDIT_POSITIVE),expression("R",Operation.RATIO,"A","E"));
        assertThat(d.calculate(Map.of(cash,new BigDecimal("0.01"),capital,BigDecimal.ZERO)).get("R").status()).isEqualTo("DIVIDE_BY_ZERO");
        assertThat(d.calculate(Map.of(cash,new BigDecimal("0.01"))).get("R").status()).isEqualTo("UNAVAILABLE");
    }
    @Test void approvedMemoIsVisibleButCannotMultiplySubtotals(){
        var memo=new Row("MEMO","Cash disclosure","Maelezo ya fedha",RowKind.ACCOUNT_GROUP,Section.NOTES,Unit.TZS,List.of(cash),Sign.DEBIT_POSITIVE,null,null,null,true,false,true,"Accountant approved repeated disclosure");
        var a=group("A",cash,Section.ASSETS,Sign.DEBIT_POSITIVE);var e=group("E",capital,Section.EQUITY,Sign.CREDIT_POSITIVE);var d=def(a,e,memo,expression("TOTAL",Operation.SUM,"A","E"));d.validateMappings(catalog);
        assertThat(d.calculate(Map.of(cash,new BigDecimal("2.01"),capital,new BigDecimal("-2.01"))).get("TOTAL").amount()).isEqualByComparingTo("4.02");
        assertThatThrownBy(()->def(a,e,memo,expression("INVALID",Operation.SUM,"A","MEMO")).validate()).hasMessage("statement.error.units");
    }
    @Test void hostileTextAndUnsupportedVersionsAreRejected(){
        var d=new StatementDefinition(2,1,Kind.NOTES,"Statement","Taarifa",List.of(),List.of(),false);assertThatThrownBy(d::validate).hasMessage("statement.error.version");
        var hostile=new StatementDefinition(1,1,Kind.NOTES,"<script>","Taarifa",List.of(),List.of(),false);assertThatThrownBy(hostile::validate).hasMessage("statement.error.text");
    }
}
