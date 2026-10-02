package com.sacco.mvp.reporting.execution;

import com.sacco.mvp.reporting.OperationalReportDefinition;
import com.sacco.mvp.reporting.execution.service.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReportResultCodecTest {
 private final ReportResultCodec codec=new ReportResultCodec(JsonMapper.builder().findAndAddModules().build());
 @Test void frozenRowsPreserveExactHighAmountsDatesNegativeSignsNullsAndHostileText(){
  var row=new LinkedHashMap<String,Object>();row.put("LOAN_ID"," =HYPERLINK(\"hostile\")");row.put("EFFECTIVE_DATE",LocalDate.of(2026,10,2));row.put("AMOUNT",new BigDecimal("-9999999999999999.99"));row.put("INTEREST",null);
  var decoded=codec.rows(codec.rows(List.of(row)),OperationalReportDefinition.standard(OperationalReportDefinition.Dataset.COLLECTIONS,"sw"));
  assertThat(decoded).containsExactly(row);assertThat(decoded.getFirst().get("AMOUNT")).isInstanceOf(BigDecimal.class);assertThat(decoded.getFirst().get("EFFECTIVE_DATE")).isInstanceOf(LocalDate.class);
 }
 @Test void totalsRemainDecimalAndUnknownValuesRemainUnknown(){
  Map<String,BigDecimal> totals=new LinkedHashMap<>();totals.put("PRINCIPAL",new BigDecimal("1000000000000000.01"));totals.put("INTEREST",null);
  assertThat(codec.totals(codec.totals(totals))).isEqualTo(totals);
 }
 @Test void frozenFieldCannotBeRelabeledAsAnotherDataset(){
  assertThatThrownBy(()->codec.rows("[{\"AMOUNT\":\"1.00\"}]",OperationalReportDefinition.standard(OperationalReportDefinition.Dataset.LOAN_PORTFOLIO,"en"))).isInstanceOf(IllegalStateException.class);
 }
}
