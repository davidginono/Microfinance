package com.sacco.mvp.accounting.business.service;

import com.sacco.mvp.accounting.business.dto.BusinessAccountingDtos.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class BusinessAccountingValidationTest {
    @Test void preservesFinalCentsAndRejectsFractionalCentsAndNegativeAmounts(){
        assertThat(BusinessAccountingService.money(new BigDecimal("9999999999999999.99"))).isEqualByComparingTo("9999999999999999.99");
        assertThatThrownBy(()->BusinessAccountingService.money(new BigDecimal("0.001"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->BusinessAccountingService.validate(command("-1.00","BANK","REF"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->BusinessAccountingService.money(new BigDecimal("10000000000000000"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void actualMoneyRequiresChannelEvidenceAndAnApprovedChannelKey(){
        assertThatCode(()->BusinessAccountingService.validate(command("1.01","BANK","Verified-REF"))).doesNotThrowAnyException();
        assertThatThrownBy(()->BusinessAccountingService.validate(command("1.01",null,"REF"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->BusinessAccountingService.validate(command("1.01","BANK",null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->BusinessAccountingService.validate(command("1.01","SQL_ACCOUNT","REF"))).isInstanceOf(IllegalArgumentException.class);
    }
    private Command command(String amount,String channel,String reference){return new Command(UUID.randomUUID(),Kind.CAPITAL_RECEIPT,LocalDate.of(2026,10,2),new BigDecimal(amount),null,null,null,"Verified contribution","Synthetic evidence",reference,channel,null,null,null,null,null);}
}
