package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.reports.CashFlowAllocation.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import static com.sacco.mvp.accounting.statements.StatementDefinition.*;
import static org.assertj.core.api.Assertions.*;

class StatementCashFlowTest {
    final UUID bank=UUID.randomUUID(),capital=UUID.randomUUID(),funding=UUID.randomUUID(),moneyLine=UUID.randomUUID();
    final OffsetDateTime cutoff=OffsetDateTime.parse("2026-11-01T00:00:00Z");
    Row group(String id,UUID account,Section activity,String exception){return new Row(id,id,id,RowKind.ACCOUNT_GROUP,activity,Unit.TZS,List.of(account),Sign.DEBIT_POSITIVE,null,null,null,false,true,false,exception);}
    StatementDefinition definition(Row...rows){return new StatementDefinition(1,2,Kind.CASH_FLOW,"Cash flow","Mtiririko wa fedha",List.of(rows),List.of(),false);}
    Coverage coverage(List<Split> splits){UUID journal=UUID.randomUUID();var lines=List.of(new SourceLine(moneyLine,bank,"BANK","ASSET","BANK",new BigDecimal("1000000000000000.01")),new SourceLine(UUID.randomUUID(),capital,"CAPITAL","EQUITY","CAPITAL",new BigDecimal("-1000000000000000.01")));var source=new Source(journal,UUID.randomUUID(),1,"Synthetic compound fixture",lines);return new Coverage(List.of(new Version(UUID.randomUUID(),journal,1,UUID.randomUUID(),cutoff.minusHours(2),"Synthetic source evidence",null,"a".repeat(64),"b".repeat(64),source,splits,UUID.randomUUID(),cutoff.minusHours(1),"Independent synthetic review")),List.of());}
    Map<String,Object> automatic(UUID account,String amount){return Map.of("account_id",account.toString(),"money_lines",1,"counterpart_movement",new BigDecimal(amount));}
    @Test void exactReviewedActivitiesAndAutomaticMovementsAreResolvedOnce(){
        var d=definition(group("FIN",capital,Section.FINANCING,null),group("INV",capital,Section.INVESTING,"Approved separate activity placement"));d.validateMappings(List.of(new Account(bank,"BANK","Bank","ASSET","BANK"),new Account(capital,"CAPITAL","Capital","EQUITY","CAPITAL")));
        var proof=coverage(List.of(new Split(moneyLine,capital,Activity.FINANCING,new BigDecimal("999999999999999.99")),new Split(moneyLine,capital,Activity.INVESTING,new BigDecimal("0.02"))));var result=StatementCashFlow.resolve(d,List.of(automatic(capital,"-1.01")),proof,cutoff);
        assertThat(result.total()).isEqualByComparingTo("1000000000000001.02");assertThat(d.calculateCashRows(result.rows()).get("INV").amount()).isEqualByComparingTo("0.02");assertThat(result.rows().get("FIN")).isEqualByComparingTo("1000000000000001.00");assertThat(result.versions()).containsExactlyElementsOf(proof.versions());
    }
    @Test void missingReviewAndWrongActivityNeverBecomeZero(){
        var d=definition(group("FIN",capital,Section.FINANCING,null));assertThatThrownBy(()->StatementCashFlow.resolve(d,List.of(),new Coverage(List.of(),List.of(UUID.randomUUID())),cutoff)).hasMessage("statement.error.cashMissing");
        assertThatThrownBy(()->StatementCashFlow.resolve(d,List.of(),coverage(List.of(new Split(moneyLine,capital,Activity.OPERATING,new BigDecimal("1000000000000000.01")))),cutoff)).hasMessage("statement.error.cashMapping");
    }
    @Test void noAutomaticActivityIsInferredForExceptionOnlyPlacements(){
        var d=definition(group("INV",capital,Section.INVESTING,"Reviewed allocations only"));assertThatThrownBy(()->StatementCashFlow.resolve(d,List.of(automatic(capital,"-1.01")),new Coverage(List.of(),List.of()),cutoff)).hasMessage("statement.error.cashMapping");
    }
    @Test void duplicateActivityAndUnreviewedSplitTreatmentAreRejected(){
        var catalog=List.of(new Account(capital,"CAPITAL","Capital","EQUITY","CAPITAL"));assertThatThrownBy(()->definition(group("A",capital,Section.FINANCING,null),group("B",capital,Section.FINANCING,"Exception")).validateMappings(catalog)).hasMessage("statement.error.overlap");assertThatThrownBy(()->definition(group("A",capital,Section.FINANCING,null),group("B",capital,Section.INVESTING,null)).validateMappings(catalog)).hasMessage("statement.error.cashMapping");
    }
    @Test void reviewedJournalAndVersionCannotBeConsumedTwice(){
        var d=definition(group("FIN",capital,Section.FINANCING,null));var proof=coverage(List.of(new Split(moneyLine,capital,Activity.FINANCING,new BigDecimal("1000000000000000.01"))));assertThatThrownBy(()->StatementCashFlow.resolve(d,List.of(),new Coverage(List.of(proof.versions().getFirst(),proof.versions().getFirst()),List.of()),cutoff)).hasMessage("statement.error.source");
    }
    @Test void internalTransferEvidenceMustReconcileExactly(){
        UUID mobile=UUID.randomUUID(),mobileLine=UUID.randomUUID(),journal=UUID.randomUUID();var source=new Source(journal,UUID.randomUUID(),1,"Synthetic mixed transfer",List.of(new SourceLine(moneyLine,bank,"BANK","ASSET","BANK",new BigDecimal("100.01")),new SourceLine(mobileLine,mobile,"MOBILE","ASSET","MOBILE_MONEY",new BigDecimal("-20.00")),new SourceLine(UUID.randomUUID(),capital,"CAPITAL","EQUITY","CAPITAL",new BigDecimal("-80.01"))));var splits=List.of(new Split(moneyLine,mobile,Activity.INTERNAL_TRANSFER,new BigDecimal("20.00")),new Split(mobileLine,bank,Activity.INTERNAL_TRANSFER,new BigDecimal("-20.00")),new Split(moneyLine,capital,Activity.FINANCING,new BigDecimal("80.01")));var version=new Version(UUID.randomUUID(),journal,1,UUID.randomUUID(),cutoff.minusHours(2),"Transfer evidence",null,"a".repeat(64),"b".repeat(64),source,splits,UUID.randomUUID(),cutoff.minusHours(1),"Independent review");var result=StatementCashFlow.resolve(definition(group("FIN",capital,Section.FINANCING,null)),List.of(),new Coverage(List.of(version),List.of()),cutoff);assertThat(result.total()).isEqualByComparingTo("80.01");assertThat(result.versions().getFirst().splits()).containsExactlyElementsOf(splits);
    }
    @Test void mandatoryActivityTotalsHaveEnglishAndKiswahiliLabels() throws java.io.IOException {for(String language:List.of("en","sw")){var messages=new Properties();try(var reader=new java.io.InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/messages_"+language+".properties")),java.nio.charset.StandardCharsets.UTF_8)){messages.load(reader);}for(var activity:List.of("OPERATING","INVESTING","FINANCING"))assertThat(messages.getProperty("statement.total.CASH_FLOW_"+activity)).isNotBlank();}}
}
