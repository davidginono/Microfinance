package com.sacco.mvp.accounting.statements;

import org.junit.jupiter.api.Test;
import java.util.*;
import static com.sacco.mvp.accounting.statements.StatementDefinition.*;
import static org.assertj.core.api.Assertions.*;

class StandardStatementTemplatesTest {
    private final List<Account> accounts=List.of(
        new Account(UUID.randomUUID(),"101","Bank","ASSET","BANK"),
        new Account(UUID.randomUUID(),"102","Allowance","ASSET","ALLOWANCE"),
        new Account(UUID.randomUUID(),"201","Borrowings","LIABILITY","FUNDING"),
        new Account(UUID.randomUUID(),"301","Owner capital","EQUITY","CAPITAL"),
        new Account(UUID.randomUUID(),"401","Interest income","INCOME","INCOME"),
        new Account(UUID.randomUUID(),"501","Rent","EXPENSE","EXPENSE"));
    @Test void standardMappingsPlaceEveryEligibleAccountExactlyOnce(){for(var kind:List.of(Kind.PROFIT_AND_LOSS,Kind.BALANCE_SHEET,Kind.CHANGES_IN_EQUITY)){var definition=StandardStatementTemplates.create(kind,accounts);definition.validateMappings(accounts);assertThat(definition.rows().stream().flatMap(r->r.accounts().stream()).toList()).doesNotHaveDuplicates();}}
    @Test void cashFlowDoesNotGuessAnActivityFromAnAccountType(){var definition=StandardStatementTemplates.create(Kind.CASH_FLOW,accounts);assertThat(definition.calculationVersion()).isEqualTo(2);assertThat(definition.rows()).allMatch(r->r.kind()==RowKind.HEADING);assertThatThrownBy(()->definition.validateMappings(accounts)).hasMessage("statement.error.omitted");}
    @Test void newAccountsRequireAnExplicitMappingRevision(){var definition=StandardStatementTemplates.create(Kind.BALANCE_SHEET,accounts);var revised=new ArrayList<>(accounts);revised.add(new Account(UUID.randomUUID(),"103","New bank","ASSET","BANK"));assertThatThrownBy(()->definition.validateMappings(revised)).hasMessage("statement.error.omitted");}
    @Test void nonDepositTakingDefaultsDoNotSeedCooperativeFunds(){var definition=StandardStatementTemplates.create(Kind.CHANGES_IN_EQUITY,accounts);assertThat(definition.rows()).extracting(Row::labelEn).contains("Owner capital","Accumulated results").doesNotContain("Member shares","Education fund","Share transfer fund");}
}
