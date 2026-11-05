package com.sacco.mvp.accounting.batch;

import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.Balances;
import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.PlanLine;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.assertThat;

class ReceiptBatchBalanceTest {
    private final JsonMapper mapper=JsonMapper.builder().build();

    @Test void totalIncludesAllUnpaidPrincipalAndDueInterestWithoutAddingPrincipalDueTwice(){
        var balance=new Balances(UUID.randomUUID(),"LN-001",new BigDecimal("9007199254740993.99"),new BigDecimal("100.00"),new BigDecimal("0.02"));
        assertThat(balance.totalLoanOutstanding()).isEqualByComparingTo("9007199254740994.01");
        assertThat(mapper.readTree(mapper.writeValueAsString(balance)).get("totalLoanOutstanding").asString()).isEqualTo("9007199254740994.01");
    }

    @Test void retainedPreviewGetsTheSameDerivedTotalAndIgnoresSubmittedTotals(){
        var line=new PlanLine(UUID.randomUUID(),"LN-001",new BigDecimal("50.00"),new BigDecimal("1000.00"),new BigDecimal("100.00"),new BigDecimal("25.50"),new BigDecimal("24.50"),new BigDecimal("25.50"),new BigDecimal("975.50"),"Principal");
        String json=mapper.writeValueAsString(line);
        assertThat(mapper.readTree(json).get("totalLoanOutstanding").asString()).isEqualTo("1025.50");
        assertThat(mapper.readValue(json,PlanLine.class).totalLoanOutstanding()).isEqualByComparingTo("1025.50");
        var retained=(ObjectNode)mapper.readTree(json);retained.put("totalLoanOutstanding","0.00");
        assertThat(mapper.readValue(retained.toString(),PlanLine.class).totalLoanOutstanding()).isEqualByComparingTo("1025.50");
        retained.remove("totalLoanOutstanding");
        assertThat(mapper.readValue(retained.toString(),PlanLine.class).totalLoanOutstanding()).isEqualByComparingTo("1025.50");
    }

    @Test void missingLedgerInformationRemainsUnavailable(){
        assertThat(new Balances(UUID.randomUUID(),"LN-001",null,BigDecimal.ZERO,BigDecimal.ZERO).totalLoanOutstanding()).isNull();
        assertThat(new Balances(UUID.randomUUID(),"LN-001",BigDecimal.TEN,BigDecimal.ZERO,null).totalLoanOutstanding()).isNull();
    }
}
