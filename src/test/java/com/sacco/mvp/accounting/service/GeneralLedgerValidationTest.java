package com.sacco.mvp.accounting.service;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
class GeneralLedgerValidationTest {
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID();
    private JournalCommand c(String debit,String credit) {return new JournalCommand(UUID.randomUUID(),"REF",LocalDate.of(2026,10,1),"Evidence",null,List.of(new Line(a,new BigDecimal(debit),BigDecimal.ZERO),new Line(b,BigDecimal.ZERO,new BigDecimal(credit))));}
    @Test void highAmountsAndExactCentsBalanceWithoutFloatingPoint() {GeneralLedgerService.validateCommand(c("9999999999999999.99","9999999999999999.99"));}
    @Test void imbalanceZeroNegativeExtraPrecisionAndOverflowAreRejected() {for(var amounts:List.of(new String[]{"1.01","1.00"},new String[]{"0","0"},new String[]{"-1","-1"},new String[]{"0.001","0.001"},new String[]{"10000000000000000.00","10000000000000000.00"}))assertThatThrownBy(()->GeneralLedgerService.validateCommand(c(amounts[0],amounts[1]))).isInstanceOf(IllegalArgumentException.class);}
    @Test void canonicalHashPreservesDecimalsButRejectsChangedEvidence() {var c=c("10.00","10.00");var policy=UUID.randomUUID();String hash=GeneralLedgerService.payloadHash("MANUAL",c,null,policy);assertThat(GeneralLedgerService.payloadHash("MANUAL",new JournalCommand(c.requestKey(),c.sourceReference(),c.effectiveDate(),c.evidenceReference(),null,List.of(c.lines().get(1),c.lines().get(0))),null,policy)).isEqualTo(hash);assertThat(GeneralLedgerService.payloadHash("OPENING",c,null,policy)).isNotEqualTo(hash);}
    @Test void jsonMoneyPreservesExactDecimalAndExcessPrecision() {var mapper=JsonMapper.builder().findAndAddModules().build();var raw="{\"requestKey\":\""+UUID.randomUUID()+"\",\"sourceReference\":\"REF\",\"effectiveDate\":\"2026-10-01\",\"evidenceReference\":\"Evidence\",\"lines\":[{\"accountId\":\""+a+"\",\"debit\":9007199254740993.01,\"credit\":0},{\"accountId\":\""+b+"\",\"debit\":0,\"credit\":9007199254740993.01}]}";var c=mapper.readValue(raw,JournalCommand.class);assertThat(c.lines().getFirst().debit().toPlainString()).isEqualTo("9007199254740993.01");GeneralLedgerService.validateCommand(c);assertThatThrownBy(()->GeneralLedgerService.validateCommand(mapper.readValue(raw.replace("9007199254740993.01","0.001"),JournalCommand.class))).isInstanceOf(IllegalArgumentException.class);}
}
