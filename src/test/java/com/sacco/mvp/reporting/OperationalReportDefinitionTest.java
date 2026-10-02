package com.sacco.mvp.reporting;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.sacco.mvp.reporting.OperationalReportDefinition.*;

class OperationalReportDefinitionTest {
    @Test void systemDefinitionsAreTypedBoundedAndNeverContainLedgerFields(){
        for(Dataset dataset:Dataset.values())standard(dataset,"en").validate();
        assertThat(catalog()).hasSize(3);
        assertThat(fields(Dataset.LOAN_PORTFOLIO)).doesNotContain(Field.INTEREST,Field.AMOUNT);
    }
    @Test void groupingCannotMultiplyPrincipalOrHideMeaning(){
        var d=standard(Dataset.COLLECTIONS,"sw");
        var valid=new OperationalReportDefinition(1,d.dataset(),d.title(),"","sw",true,
            List.of(new Column(Field.CHANNEL,"",100,true),new Column(Field.AMOUNT,"My receipts",180,true)),List.of(),
            List.of(new Sort(Field.CHANNEL,false)),List.of(Field.CHANNEL),List.of(Field.AMOUNT));
        valid.validate();
        var invalid=new OperationalReportDefinition(1,d.dataset(),d.title(),"","sw",true,d.columns(),List.of(),d.sorts(),List.of(Field.CHANNEL),d.totals());
        assertThatThrownBy(invalid::validate).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void hostileFieldsOperatorsTextAndFractionalCentsAreRejected(){
        var d=standard(Dataset.DISBURSEMENTS,"en");
        var invalid=new OperationalReportDefinition(1,d.dataset(),"<script>","","en",false,d.columns(),List.of(),d.sorts(),List.of(),d.totals());
        assertThatThrownBy(invalid::validate).hasMessage("report.error.definition");
        assertThatThrownBy(()->value(new Filter(Field.PRINCIPAL,Operator.EQ,"0.001"))).hasMessage("report.error.definition");
        assertThatThrownBy(()->value(new Filter(Field.EFFECTIVE_DATE,Operator.EQ,"DROP TABLE"))).hasMessage("report.error.definition");
        assertThatThrownBy(()->validateDates(java.time.LocalDate.of(2020,1,1),java.time.LocalDate.of(2026,1,1))).hasMessage("report.error.definition");
    }
}
