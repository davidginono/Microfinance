package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.accounting.charges.repository.LoanChargeRepository;
import com.sacco.mvp.accounting.charges.service.LoanChargeService;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomLoanChargeEntryTest {
    private final LoanChargeRepository repository=mock(LoanChargeRepository.class);
    private final GeneralLedgerService accounting=mock(GeneralLedgerService.class);
    private final ApplicationClock clock=mock(ApplicationClock.class);
    private final AppUserPrincipal actor=mock(AppUserPrincipal.class);
    private final LoanChargeService service=new LoanChargeService(repository,accounting,null,clock,null,null);
    private final UUID productId=UUID.randomUUID();
    private final LoanProductSetting product=LoanProductSetting.builder().id(productId).applicationFee(money("10"))
        .processingFeeRate(money("0.02")).insuranceRate(money("0.01")).build();
    @BeforeEach void policy(){
        var date=LocalDate.of(2026,9,1);
        when(actor.getSaccoId()).thenReturn("I1");when(clock.today()).thenReturn(date);
        when(repository.policy("I1",productId,date)).thenReturn(Optional.of(new Policy(UUID.randomUUID(),productId,
            date,true,Timing.DEDUCT,Timing.DEDUCT,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
            "Approved fee policy","Signed agreement","Licensed insurer","Verified licence",true,money("20"))));
    }
    private RecordForm form(){var f=new RecordForm();f.setLoanMode("CUSTOM");f.setPrincipal(money("1000"));
        f.setCustomApplicationCharge(money("3.21"));f.setCustomProcessingCharge(money("6.78"));f.setInsurancePremium(money("7.65"));
        f.setEvidence("Signed agreed fees");f.setInsuranceEvidence("Actual premium document");return f;}
    @Test void recorderAmountsReplaceDefaultsWithoutCollectingMoney(){
        var plan=service.quote(actor,form(),product,true);
        assertThat(plan.terms()).extracting(Term::amount).containsExactly(money("3.21"),money("6.78"),money("7.65"));
        assertThat(plan.terms()).allSatisfy(t->assertThat(t.basis()).isEqualTo("CUSTOM_AMOUNT"));
        assertThat(plan.total()).isEqualByComparingTo("17.64");
        verify(accounting,atLeastOnce()).requireActor(actor,"LOAN_CHARGES_CUSTOM");
        verify(repository,never()).freeze(any(),any(),any(),any());
    }
    @Test void explicitZeroFeesDoNotInheritConfiguredDefaults(){
        var f=form();f.setCustomApplicationCharge(BigDecimal.ZERO);f.setCustomProcessingCharge(BigDecimal.ZERO);f.setInsurancePremium(BigDecimal.ZERO);
        assertThat(service.quote(actor,f,product,true).terms()).isEmpty();
    }
    @Test void missingAndNegativeFeesCannotBeSavedAsNoCharge(){
        var f=form();f.setCustomApplicationCharge(null);
        assertThat(service.quote(actor,f,product,false).error()).isEqualTo("charges.error.enterFees");
        assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.enterFees");
        f.setCustomApplicationCharge(money("-0.01"));
        assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.amount");
    }
    @Test void policyCapInsuranceLimitAndTopUpRulesStillApplyWithoutEvidenceInputs(){
        var f=form();f.setCustomApplicationCharge(money("21"));
        assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.custom");
        f.setCustomApplicationCharge(money("3.21"));f.setInsurancePremium(money("10.01"));
        assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.insuranceLimit");
        f.setInsurancePremium(money("7.65"));f.setInsuranceEvidence("");
        f.setEvidence("");
        assertThat(service.quote(actor,f,product,true).terms()).allSatisfy(t->assertThat(t.evidence()).startsWith("Loan charge entry / "));
        f.setInsuranceEvidence("Actual premium document");f.setTopUpSourceLoanId(UUID.randomUUID());
        assertThat(service.quote(actor,f,product,true).terms()).extracting(Term::kind).containsExactly("INSURANCE");
    }
    @Test void configuredProductsRetainTheirCalculatedFees(){
        var f=form();f.setLoanMode("CONFIGURED");f.setCustomApplicationCharge(money("99"));f.setCustomProcessingCharge(money("99"));f.setInsurancePremium(null);f.setInsuranceEvidence(null);
        assertThat(service.quote(actor,f,product,true).terms()).extracting(Term::amount).containsExactly(money("10.00"),money("20.00"),money("10.00"));
    }
    @Test void topUpUsesTheConfiguredProductWithoutCopyingItsOrdinaryLoanFees(){
        var f=form();f.setLoanMode("CONFIGURED");f.setTopUpSourceLoanId(UUID.randomUUID());
        assertThat(service.quote(actor,f,product,true).terms()).extracting(Term::kind).containsExactly("INSURANCE");
        assertThat(service.quoteView(actor,f,product).fields()).extracting(field->field.code()).containsExactly("INSURANCE");
    }
    @Test void zeroInsuranceCeilingStillRejectsPositiveCustomPremiums(){
        product.setInsuranceRate(BigDecimal.ZERO);
        var f=form();
        assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.insuranceLimit");
        f.setInsurancePremium(BigDecimal.ZERO);
        assertThat(service.quote(actor,f,product,true).terms()).noneMatch(t->t.kind().equals("INSURANCE"));
    }
    @Test void percentageEntriesAreCalculatedOnTheServerAndIgnoreTheDisplayedAmounts(){
        var f=form();f.setCustomProcessingBasis("PERCENTAGE");f.setCustomProcessingPercent(money("0.75"));f.setCustomProcessingCharge(money("999"));
        f.setCustomInsuranceBasis("PERCENTAGE");f.setCustomInsurancePercent(money("0.5"));f.setInsurancePremium(money("999"));
        var plan=service.quote(actor,f,product,true);
        assertThat(plan.terms()).extracting(Term::amount).containsExactly(money("3.21"),money("7.50"),money("5.00"));
        assertThat(plan.terms().get(1).basis()).isEqualTo("PRINCIPAL_RATE");assertThat(plan.terms().get(1).basisValue()).isEqualByComparingTo("0.0075");
        f.setPrincipal(money("2000"));assertThat(service.quote(actor,f,product,true).terms().get(1).amount()).isEqualByComparingTo("15.00");
    }
    @Test void amountEntriesRemainExactAndInvalidPercentageSourcesFailClosed(){
        var f=form();f.setCustomProcessingBasis("AMOUNT");f.setCustomProcessingPercent(money("99.99"));
        assertThat(service.quote(actor,f,product,true).terms().get(1).amount()).isEqualByComparingTo("6.78");
        f.setCustomProcessingBasis("PERCENTAGE");f.setCustomProcessingPercent(null);
        assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.enterFees");
        f.setCustomProcessingPercent(money("-1"));assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.rate");
        f.setCustomProcessingBasis("UNKNOWN");assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.input");
    }
    @Test void customPermissionIsRequiredEvenForZeroCharges(){
        doThrow(new org.springframework.security.access.AccessDeniedException("Custom charge permission required")).when(accounting).requireActor(actor,"LOAN_CHARGES_CUSTOM");
        var f=form();f.setCustomApplicationCharge(BigDecimal.ZERO);f.setCustomProcessingCharge(BigDecimal.ZERO);f.setInsurancePremium(BigDecimal.ZERO);
        assertThatThrownBy(()->service.quote(actor,f,product,true)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void configuredPercentagesRemainVisibleWhenTheRoundedAmountIsZero(){
        var f=form();f.setLoanMode("CONFIGURED");f.setPrincipal(money("0.01"));
        var quote=service.quoteView(actor,f,product);
        assertThat(quote.fields().get(1).percentage()).isEqualTo("2");assertThat(quote.fields().get(1).amount()).isEqualTo("0.00");
        assertThat(quote.fields().get(2).percentage()).isEqualTo("1");assertThat(quote.fields().get(2).amount()).isEqualTo("0.00");
    }
    private static BigDecimal money(String value){return new BigDecimal(value);}
}
