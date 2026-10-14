package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class LoanInterestQuoteTest {
    private final GeneralLedgerService guard=mock(GeneralLedgerService.class);
    private final LoanProductSettingRepository products=mock(LoanProductSettingRepository.class);
    private final AppUserPrincipal actor=mock(AppUserPrincipal.class);
    private final LoanRecordingService service=new LoanRecordingService(null,null,products,null,null,null,guard,null,null,null,null,null,null);
    private RecordForm custom(){var f=new RecordForm();f.setLoanMode("CUSTOM");f.setPrincipal(new BigDecimal("1000.00"));f.setInterestPercent(new BigDecimal("12"));f.setTenureValue(6);return f;}
    @Test void recorderCanChooseWholeLoanAnnualOrAmountInterest(){
        var f=custom();assertThat(service.interestQuote(actor,f).interest()).isEqualByComparingTo("120.00");
        f.setInterestBasis("ANNUAL");assertThat(service.interestQuote(actor,f).interest()).isEqualByComparingTo("60.00");
        f.setInterestEntry("AMOUNT");f.setInterestAmount(new BigDecimal("17.03"));assertThat(service.interestQuote(actor,f).interest()).isEqualByComparingTo("17.03");
        f.setInterestAmount(new BigDecimal("-1"));assertThatThrownBy(()->service.interestQuote(actor,f)).hasMessage("recording.error.amount");
        verify(guard,times(4)).requireActor(actor,"LOAN_RECORDING_CREATE");
    }
    @Test void missingTenureIsAValidationErrorBeforeAnyRecordIsWritten(){
        var f=custom();f.setClientId(UUID.randomUUID());f.setTenureValue(null);f.setMonths(null);
        assertThatThrownBy(()->service.preview(actor,f)).hasMessage("recording.error.terms");
        verifyNoInteractions(products);
    }
    @Test void configuredProductUsesItsOwnRateAndRejectsInactiveOrOutOfScopeProducts(){
        when(actor.getSaccoId()).thenReturn("I1");var id=UUID.randomUUID();var p=LoanProductSetting.builder().id(id).productName("Business loan").interestRate(new BigDecimal("0.24")).interestMethod(InterestMethod.REDUCING_BALANCE).active(true).minRepaymentMonths(1).maxRepaymentMonths(12).build();when(products.findByIdAndSaccoId(id,"I1")).thenReturn(Optional.of(p));
        var f=custom();f.setLoanMode("CONFIGURED");f.setProductId(id);f.setInterestAmount(new BigDecimal("9000"));assertThat(service.interestQuote(actor,f).interest()).isLessThan(new BigDecimal("120.00"));
        p.setActive(false);assertThatThrownBy(()->service.interestQuote(actor,f)).hasMessage("recording.error.product");
        f.setProductId(UUID.randomUUID());assertThatThrownBy(()->service.interestQuote(actor,f)).hasMessage("recording.error.product");
    }
}
