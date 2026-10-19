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
    @Test void policyCapInsuranceEvidenceAndTopUpRulesStillApply(){
        var f=form();f.setCustomApplicationCharge(money("21"));
        assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.custom");
        f.setCustomApplicationCharge(money("3.21"));f.setInsurancePremium(money("10.01"));
        assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.insuranceLimit");
        f.setInsurancePremium(money("7.65"));f.setInsuranceEvidence("");
        assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.insuranceEvidence");
        f.setInsuranceEvidence("Actual premium document");f.setTopUpSourceLoanId(UUID.randomUUID());
        assertThatThrownBy(()->service.quote(actor,f,product,true)).hasMessage("charges.error.topUpFee");
    }
    @Test void configuredProductsRetainTheirCalculatedFees(){
        var f=form();f.setLoanMode("CONFIGURED");f.setCustomApplicationCharge(null);f.setCustomProcessingCharge(null);f.setInsurancePremium(money("10"));
        assertThat(service.quote(actor,f,product,true).terms()).extracting(Term::amount).containsExactly(money("10.00"),money("20.00"),money("10.00"));
    }
    private static BigDecimal money(String value){return new BigDecimal(value);}
}
