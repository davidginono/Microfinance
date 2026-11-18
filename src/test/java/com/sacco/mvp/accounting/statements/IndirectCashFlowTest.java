package com.sacco.mvp.accounting.statements;

import com.sacco.mvp.accounting.statements.dto.StatementModels.*;
import com.sacco.mvp.accounting.statements.dto.StatementSetupModels.*;
import com.sacco.mvp.accounting.statements.service.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.sacco.mvp.accounting.statements.StatementCalculatorTest.PERIOD;

class IndirectCashFlowTest {
    @Test void reconcilesAccrualsProvisionsDepreciationAndLoanPrincipalForBothPeriods(){
        var s=businessSnapshot();
        assertThat(s.income().getLast().current()).isEqualByComparingTo("55");
        assertThat(s.cashFlow().getFirst().key()).isEqualTo("cashSurplus");
        assertThat(s.cashFlow().getFirst().current()).isEqualByComparingTo(s.income().getLast().current());
        assertThat(s.cashFlow().getFirst().previous()).isEqualByComparingTo(s.income().getLast().previous());
        assertThat(total(s,"OPERATING",false)).isEqualByComparingTo("-240");
        assertThat(total(s,"OPERATING",true)).isEqualByComparingTo("20");
        assertThat(total(s,"INVESTING",false)).isEqualByComparingTo("-100");
        assertThat(total(s,"FINANCING",false)).isEqualByComparingTo("460");
        assertThat(row(s,"cashMovement").current()).isEqualByComparingTo("120");
        assertThat(row(s,"cashOpening").current()).isEqualByComparingTo("1020");
        assertThat(row(s,"cashClosing").current()).isEqualByComparingTo("1140");
        assertThat(s.cashFlow()).filteredOn(r->r.key().startsWith("cashAdjustment-")&&r.label().equals("Loan loss expense"))
            .singleElement().satisfies(r->assertThat(r.current()).isEqualByComparingTo("25"));
        assertThat(s.cashFlow()).filteredOn(r->r.key().startsWith("cashAdjustment-")&&r.label().equals("Depreciation"))
            .singleElement().satisfies(r->assertThat(r.current()).isEqualByComparingTo("10"));
        assertThat(s.cashFlow()).filteredOn(r->r.key().startsWith("cashOperatingBalance-")&&r.label().equals("Client loans"))
            .singleElement().satisfies(r->assertThat(r.current()).isEqualByComparingTo("-300"));
        assertThat(s.checks()).extracting(Check::key).containsExactly("coverage");
    }

    @Test void nonOperatingIncomeAndExpenseAreRemovedFromSurplusOnlyOnce(){
        var l=new Ledger();var bank=l.account("Bank","ASSET","BANK","OPERATING");
        var dividend=l.account("Investment income","INCOME","INCOME","INVESTING");
        var borrowingCost=l.account("Funding expense","EXPENSE","EXPENSE","FINANCING");
        l.post("2026-01-10",bank,dividend,"60");l.post("2026-01-11",borrowingCost,bank,"20");
        var s=l.snapshot();
        assertThat(row(s,"cashSurplus").current()).isEqualByComparingTo("40");
        assertThat(total(s,"OPERATING",false)).isEqualByComparingTo("0");
        assertThat(total(s,"INVESTING",false)).isEqualByComparingTo("60");
        assertThat(total(s,"FINANCING",false)).isEqualByComparingTo("-20");
        assertThat(row(s,"cashMovement").current()).isEqualByComparingTo("40");
    }

    @Test void principalWriteOffsAndInternalLoanTransfersAreNeverCashReceipts(){
        var l=new Ledger();var loan=l.account("Client loans","ASSET","OTHER","OPERATING");
        var replacement=l.account("Refinanced loans","ASSET","OTHER","OPERATING");
        var allowance=l.account("Loss allowance","ASSET","OTHER","OPERATING");
        var expense=l.account("Loan loss expense","EXPENSE","EXPENSE","OPERATING");
        l.post("2026-01-01",expense,allowance,"100");
        l.post("2026-01-02",allowance,loan,"100");
        l.post("2026-01-03",replacement,loan,"200");
        var s=l.snapshot();
        assertThat(row(s,"cashSurplus").current()).isEqualByComparingTo("-100");
        assertThat(total(s,"OPERATING",false)).isEqualByComparingTo("0");
        assertThat(s.cashFlow()).noneMatch(r->r.key().startsWith("cashOperatingBalance-"));
    }

    @Test void cashExpenseIsNotAddedBackAndReceiptReversalNetsExactly(){
        var l=new Ledger();var bank=l.account("Bank","ASSET","BANK","OPERATING");
        var income=l.account("Interest income","INCOME","INCOME","OPERATING");
        var expense=l.account("Operating expenses","EXPENSE","EXPENSE","OPERATING");
        l.post("2026-01-01",bank,income,"100.01");l.post("2026-01-02",income,bank,"100.01");
        l.post("2026-01-03",expense,bank,"12.34");
        var s=l.snapshot();assertThat(total(s,"OPERATING",false)).isEqualByComparingTo("-12.34");
        assertThat(s.cashFlow()).noneMatch(r->r.key().startsWith("cashAdjustment-"));
    }

    @Test void earningsClosingDoesNotEraseTheSurplusStartingPoint(){
        var l=new Ledger();var bank=l.account("Bank","ASSET","BANK","OPERATING");
        var income=l.account("Interest income","INCOME","INCOME","OPERATING");
        var retained=l.account("Retained earnings","EQUITY","CAPITAL","FINANCING");
        l.post("2026-01-01",bank,income,"100");l.post("2026-01-31",income,retained,"100");
        var cleaned=EquityStatementCalculator.incomeWithoutClosings(l.balances(),List.of(
            new Movement(income,Category.EARNINGS_TRANSFER,false,new BigDecimal("100"),BigDecimal.ZERO),
            new Movement(retained,Category.EARNINGS_TRANSFER,false,BigDecimal.ZERO,new BigDecimal("100"))));
        var r=StatementCalculator.calculate(cleaned,l.cash(),PERIOD,false);
        assertThat(r.cash().getFirst().current()).isEqualByComparingTo("100");
        assertThat(r.cash().getFirst().current()).isEqualByComparingTo(r.income().getLast().current());
        assertThat(r.cash()).filteredOn(a->a.section().equals("OPERATING")).hasSize(1);
    }

    @Test void missingCashClassificationRemainsVisibleInsteadOfAssumingOperating(){
        var l=new Ledger();var bank=l.account("Bank","ASSET","BANK","OPERATING");
        var income=l.account("Unmapped income","INCOME","INCOME","UNCLASSIFIED");
        l.post("2026-01-01",bank,income,"100");var s=l.snapshot();
        assertThat(total(s,"OPERATING",false)).isEqualByComparingTo("0");
        assertThat(total(s,"UNCLASSIFIED",false)).isEqualByComparingTo("100");
        assertThat(s.checks()).extracting(Check::key).contains("cashMappings");
    }

    @Test void emptyPeriodStillHasOneZeroSurplusAndReconciles(){
        var s=new Ledger().snapshot();assertThat(s.cashFlow()).hasSize(4);
        assertThat(row(s,"cashSurplus").current()).isEqualByComparingTo("0");
        assertThat(row(s,"cashMovement").current()).isEqualByComparingTo("0");
        assertThat(s.checks()).extracting(Check::key).doesNotContain("cash","previousCash");
    }

    static Snapshot businessSnapshot(){
        var l=new Ledger();var bank=l.account("Bank","ASSET","BANK","OPERATING");
        var capital=l.account("Owner capital","EQUITY","CAPITAL","FINANCING");
        var income=l.account("Interest income","INCOME","INCOME","OPERATING");
        var interest=l.account("Interest receivable","ASSET","OTHER","OPERATING");
        var loans=l.account("Client loans","ASSET","OTHER","OPERATING");
        var wages=l.account("Staff costs","EXPENSE","EXPENSE","OPERATING");
        var payable=l.account("Staff payable","LIABILITY","OTHER","OPERATING");
        var provision=l.account("Loan loss expense","EXPENSE","EXPENSE","OPERATING");
        var allowance=l.account("Loss allowance","ASSET","OTHER","OPERATING");
        var depreciation=l.account("Depreciation","EXPENSE","EXPENSE","OPERATING");
        var accumulated=l.account("Accumulated depreciation","ASSET","OTHER","INVESTING");
        var equipment=l.account("Equipment","ASSET","OTHER","INVESTING");
        var debt=l.account("Institution borrowing","LIABILITY","OTHER","FINANCING");
        l.post("2024-12-31",bank,capital,"1000");
        l.post("2025-01-10",bank,income,"30");l.post("2025-01-11",wages,bank,"10");
        l.post("2026-01-01",interest,income,"120");l.post("2026-01-02",bank,interest,"80");
        l.post("2026-01-03",wages,payable,"30");l.post("2026-01-04",payable,bank,"20");
        l.post("2026-01-05",provision,allowance,"25");l.post("2026-01-06",depreciation,accumulated,"10");
        l.post("2026-01-07",loans,bank,"500");l.post("2026-01-08",bank,loans,"200");
        l.post("2026-01-09",equipment,bank,"100");l.post("2026-01-10",bank,debt,"300");
        l.post("2026-01-11",bank,capital,"200");l.post("2026-01-12",capital,bank,"40");
        return l.snapshot();
    }
    static BigDecimal total(Snapshot s,String section,boolean previous){return s.cashFlow().stream().filter(r->r.section().equals(section)).map(r->previous?r.previous():r.current()).reduce(BigDecimal.ZERO,BigDecimal::add);}
    static Row row(Snapshot s,String key){return s.cashFlow().stream().filter(r->r.key().equals(key)).findFirst().orElseThrow();}

    static class Ledger {
        final Map<UUID,Balance> accounts=new LinkedHashMap<>();final List<Cash> entries=new ArrayList<>();
        UUID account(String name,String type,String purpose,String cash){
            UUID id=UUID.randomUUID();String code=Integer.toString(100001+accounts.size());
            accounts.put(id,new Balance(id,code,name,type,purpose,new Mapping(id,name,type.equals("ASSET")?"CURRENT_ASSETS":type.equals("LIABILITY")?"CURRENT_LIABILITIES":type,cash,"Capital",0,"",UUID.randomUUID(),code),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO));return id;
        }
        void post(String date,UUID debit,UUID credit,String value){UUID id=UUID.randomUUID();BigDecimal amount=new BigDecimal(value);for(UUID account:List.of(debit,credit)){var a=accounts.get(account);entries.add(new Cash(id,LocalDate.parse(date),account,a.type(),a.purpose(),account.equals(debit)?amount:BigDecimal.ZERO,account.equals(credit)?amount:BigDecimal.ZERO));}}
        List<Balance> balances(){return accounts.values().stream().map(a->new Balance(a.id(),a.code(),a.name(),a.type(),a.purpose(),a.mapping(),sum(a.id(),null,PERIOD.from().minusDays(1),0),sum(a.id(),PERIOD.from(),PERIOD.through(),1),sum(a.id(),PERIOD.from(),PERIOD.through(),2),sum(a.id(),null,PERIOD.through(),0),sum(a.id(),null,PERIOD.previousFrom().minusDays(1),0),sum(a.id(),PERIOD.previousFrom(),PERIOD.previousThrough(),1),sum(a.id(),PERIOD.previousFrom(),PERIOD.previousThrough(),2),sum(a.id(),null,PERIOD.previousThrough(),0))).toList();}
        BigDecimal sum(UUID account,LocalDate from,LocalDate through,int mode){return entries.stream().filter(e->e.accountId().equals(account)&&(from==null||!e.date().isBefore(from))&&!e.date().isAfter(through)).map(e->mode==1?e.debit():mode==2?e.credit():e.signed()).reduce(BigDecimal.ZERO,BigDecimal::add);}
        List<Cash> cash(){var journals=new HashSet<UUID>();entries.stream().filter(e->accounts.get(e.accountId()).money()).forEach(e->journals.add(e.journalId()));return entries.stream().filter(e->journals.contains(e.journalId())&&(!e.date().isBefore(PERIOD.from())&&!e.date().isAfter(PERIOD.through())||!e.date().isBefore(PERIOD.previousFrom())&&!e.date().isAfter(PERIOD.previousThrough()))).toList();}
        Snapshot snapshot(){var balances=balances();var r=StatementCalculator.calculate(balances,cash(),PERIOD,false);return new Snapshot(4,UUID.randomUUID(),PERIOD,OffsetDateTime.parse("2026-01-31T12:00:00+03:00"),"I","B","Synthetic institution","Test branch",balances,r.position(),r.income(),r.cash(),List.of(),List.of(),r.checks());}
    }
}
