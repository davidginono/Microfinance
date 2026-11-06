package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.service.StatementCalculator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class StatementCalculatorTest {
    static final Parameters PERIOD=new Parameters(LocalDate.of(2026,1,1),LocalDate.of(2026,1,31),false);
    static BigDecimal n(String value){return new BigDecimal(value);}
    static Balance balance(String type,String purpose,String section,String cash,String opening,String debit,String credit){UUID id=UUID.randomUUID();return new Balance(id,"100",purpose,type,purpose,new Mapping(id,purpose,section,cash,"Capital",3,"Supporting note",UUID.randomUUID(),"100000"),n(opening),n(debit),n(credit),n(opening).add(n(debit)).subtract(n(credit)),n("0"),n("0"),n("0"),n("0"));}
    @Test void surplusIsIncludedExactlyOnceInPositionAndEquity(){
        var bank=balance("ASSET","BANK","CURRENT_ASSETS","OPERATING","1000","200","0");var capital=balance("EQUITY","CAPITAL","EQUITY","FINANCING","-1000","0","0");var income=balance("INCOME","INCOME","INCOME","OPERATING","0","0","200");
        UUID journal=UUID.randomUUID();var result=StatementCalculator.calculate(List.of(bank,capital,income),List.of(cash(journal,bank,"200","0"),cash(journal,income,"0","200")),PERIOD);
        assertThat(result.income().getLast().current()).isEqualByComparingTo("200");
        assertThat(result.position().getLast().current()).isEqualByComparingTo("1200");
        assertThat(result.equity().getLast().total()).isEqualByComparingTo("1200");
        assertThat(result.checks()).extracting(Check::key).containsExactly("coverage");
    }
    @Test void moneyAccountTransfersDoNotInflateCashActivities(){
        var bank=balance("ASSET","BANK","CURRENT_ASSETS","OPERATING","100","0","25");var cash=balance("ASSET","CASH","CURRENT_ASSETS","OPERATING","0","25","0");var capital=balance("EQUITY","CAPITAL","EQUITY","FINANCING","-100","0","0");UUID journal=UUID.randomUUID();
        var result=StatementCalculator.calculate(List.of(bank,cash,capital),List.of(cash(journal,bank,"0","25"),cash(journal,cash,"25","0")),PERIOD);
        assertThat(result.cash().stream().filter(r->r.key().equals("cashMovement")).findFirst().orElseThrow().current()).isEqualByComparingTo("0");assertThat(result.checks()).extracting(Check::key).containsExactly("coverage");
    }
    @Test void ambiguousCompoundCashJournalsAreFlaggedWithoutInventingAllocations(){
        var bank=balance("ASSET","BANK","CURRENT_ASSETS","OPERATING","0","100","0");var expense=balance("EXPENSE","EXPENSE","EXPENSE","OPERATING","0","50","0");var income=balance("INCOME","INCOME","INCOME","OPERATING","0","0","150");UUID journal=UUID.randomUUID();
        var result=StatementCalculator.calculate(List.of(bank,expense,income),List.of(cash(journal,bank,"100","0"),cash(journal,expense,"50","0"),cash(journal,income,"0","150")),PERIOD);
        assertThat(result.checks()).extracting(Check::key).contains("cashAmbiguous","cash");
    }
    @Test void exactReversalNetsToZeroAndMissingMappingsAreVisible(){
        var raw=balance("ASSET","BANK","CURRENT_ASSETS","OPERATING","0","0.01","0.01");var bank=new Balance(raw.id(),raw.code(),raw.name(),raw.type(),raw.purpose(),null,n("0"),n("0.01"),n("0.01"),n("0"),n("0"),n("0"),n("0"),n("0"));
        var result=StatementCalculator.calculate(List.of(bank),List.of(),PERIOD);assertThat(result.position().getLast().current()).isEqualByComparingTo("0");assertThat(result.checks()).extracting(Check::key).contains("coverage");
    }
    @Test void unmappedNonzeroBalancesCannotAppearComplete(){var bank=balance("ASSET","BANK","CURRENT_ASSETS","OPERATING","1","0","0");var missing=new Balance(bank.id(),bank.code(),bank.name(),bank.type(),bank.purpose(),null,bank.opening(),bank.debit(),bank.credit(),bank.closing(),bank.previousOpening(),bank.previousDebit(),bank.previousCredit(),bank.previousClosing());assertThat(StatementCalculator.calculate(List.of(missing),List.of(),PERIOD).checks()).extracting(Check::key).contains("mappings","trial");}
    @Test void subgroupIdentityPreventsDuplicateNamesFromMergingAndRetainsComparatives() {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        var a=familyBalance("ASSET",first,"111000","Shared name","100","30","0");
        var b=familyBalance("ASSET",first,"111000","Shared name","-20","0","0");
        var c=familyBalance("ASSET",second,"112000","Shared name","0","0","5");
        var empty=familyBalance("ASSET",UUID.randomUUID(),"113000","Zero family","0","0","0");
        var rows=StatementCalculator.calculate(List.of(a,b,c,empty),List.of(),PERIOD).position().stream().filter(r->!r.accounts().isEmpty()).toList();
        assertThat(rows).hasSize(2);assertThat(rows.getFirst().code()).isEqualTo("111000");assertThat(rows.getFirst().accounts()).containsExactly(a.id(),b.id());
        assertThat(rows.getFirst().current()).isEqualByComparingTo("110");assertThat(rows.getLast().current()).isEqualByComparingTo("0");assertThat(rows.getLast().previous()).isEqualByComparingTo("5");
    }
    @Test void profitAndLossUsesPeriodMovementsRatherThanBroughtForwardBalances() {
        var income=familyBalance("INCOME",UUID.randomUUID(),"511000","Interest income","-900","0","0");
        var expense=familyBalance("EXPENSE",UUID.randomUUID(),"411000","Operating costs","100","20","0");
        var result=StatementCalculator.calculate(List.of(income,expense),List.of(),PERIOD);
        assertThat(result.income()).extracting(Row::label).doesNotContain("Interest income");assertThat(result.income().getLast().current()).isEqualByComparingTo("-20");
    }
    static Balance familyBalance(String type,UUID family,String code,String label,String opening,String debit,String previousClosing) {
        UUID id=UUID.randomUUID();return new Balance(id,code+"1","Posting account",type,"OTHER",new Mapping(id,label,type.equals("ASSET")?"CURRENT_ASSETS":type,"OPERATING","",0,"",family,code),n(opening),n(debit),n("0"),n(opening).add(n(debit)),n(previousClosing),n("0"),n("0"),n(previousClosing));
    }
    static Cash cash(UUID journal,Balance account,String debit,String credit){return new Cash(journal,LocalDate.of(2026,1,10),account.id(),account.type(),account.purpose(),n(debit),n(credit));}
}
