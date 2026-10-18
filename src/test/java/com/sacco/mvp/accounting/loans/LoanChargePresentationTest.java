package com.sacco.mvp.accounting.loans;
import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.service.LoanPresentationService;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class LoanChargePresentationTest {
 @Test void frozenChargesRetainBasisTimingAndDeductionsInBothLanguages() {
  var mapper=JsonMapper.builder().findAndAddModules().build();var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
  var plan=new Plan(UUID.randomUUID(),"Approved policy reference","Approved agreement reference",List.of(new Term(UUID.randomUUID(),"APPLICATION","FIXED",new BigDecimal("10.00"),new BigDecimal("10.00"),Timing.DEDUCT,UUID.randomUUID(),UUID.randomUUID(),"","",""),new Term(UUID.randomUUID(),"PROCESSING","PRINCIPAL_RATE",new BigDecimal("0.01"),new BigDecimal("10.00"),Timing.DEDUCT,UUID.randomUUID(),UUID.randomUUID(),"","","")),true,"");
  var json=mapper.writeValueAsString(Map.of("recordedCharges",plan,"recordedChargeTotal",plan.total(),"recordedChargeDeductions",plan.deductions(),"recordedNetCashEstimate",new BigDecimal("980.00"),"recordedTotalPayable",new BigDecimal("1040.00")));
  var loan=LoanApplication.builder().amount(new BigDecimal("1000.00")).tenorMonths(1).status(LoanStatus.READY_FOR_MANAGER).financialSnapshot(json).build();var service=new LoanPresentationService(mapper,null,null,null,null,null,null,null,messages);
  try {for(var language:List.of("en","sw")){var locale=Locale.forLanguageTag(language);LocaleContextHolder.setLocale(locale);var fields=service.parseFinancialFields(loan);assertThat(fields).containsKey(messages.getMessage("charges.kind.APPLICATION",null,locale));assertThat(fields.values().toString()).contains("1%",messages.getMessage("charges.timing.DEDUCT",null,locale),"Approved agreement reference");assertThat(service.totalDeductions(loan)).isEqualByComparingTo("20.00");assertThat(service.deductibleFeeRows(loan)).hasSize(2);}
   var frozen=mapper.readValue(mapper.writeValueAsString(plan),Plan.class);assertThat(frozen.managementTotal()).isEqualByComparingTo("20.00");
  } finally {LocaleContextHolder.resetLocaleContext();}
 }
}
