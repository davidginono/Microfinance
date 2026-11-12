package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.accounting.charges.repository.LoanChargeRepository;
import com.sacco.mvp.accounting.charges.service.LoanChargeService;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanFeeAccountingPolicyTest {
    @Test void editorShowsEffectiveMappingsWhilePreservingTheLatestFutureRevisionForTheNextSave() {
        var repository=mock(LoanChargeRepository.class);var actor=mock(AppUserPrincipal.class);when(actor.getSaccoId()).thenReturn("I1");
        var date=LocalDate.of(2026,11,12);var product=UUID.randomUUID();var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(date);
        when(repository.product("I1",product)).thenReturn(true);
        var old=new Policy(UUID.randomUUID(),product,date.minusDays(10),true,Timing.SEPARATE,Timing.SEPARATE,null,UUID.randomUUID(),null,"Current policy","Agreement","","",false,null);
        var future=new Policy(UUID.randomUUID(),product,date.plusDays(10),true,Timing.SEPARATE,Timing.SEPARATE,null,null,null,"Future policy","Agreement","","",false,null,2,UUID.randomUUID(),UUID.randomUUID());
        when(repository.policy("I1",product,date)).thenReturn(Optional.of(old));
        when(repository.policy("I1",product,LocalDate.of(9999,12,31))).thenReturn(Optional.of(future));
        var editor=new LoanChargeService(repository,mock(GeneralLedgerService.class),null,clock,null,null).editor(actor,product,null);
        assertThat(editor.current()).isSameAs(old);
        assertThat(editor.form().getExpectedVersion()).isEqualTo(future.id());
        assertThat(editor.form().getApplicationAccountId()).isEqualTo(future.applicationAccountId());
        assertThat(editor.form().getEffectiveFrom()).isEqualTo(future.effectiveFrom().plusDays(1));
    }
    @Test void newPolicyFreezesSeparateIncomeAccountsAndLegacyPolicyKeepsDeferredFees() {
        var repository=mock(LoanChargeRepository.class);var actor=mock(AppUserPrincipal.class);
        when(actor.getSaccoId()).thenReturn("I1");
        var date=LocalDate.of(2026,11,12);var productId=UUID.randomUUID();var receivable=UUID.randomUUID();
        var application=UUID.randomUUID();var processing=UUID.randomUUID();var insurance=UUID.randomUUID();var deferred=UUID.randomUUID();
        var product=LoanProductSetting.builder().id(productId).applicationFee(new BigDecimal("10"))
            .processingFeeRate(new BigDecimal("0.02")).insuranceRate(new BigDecimal("0.01")).build();
        var form=new RecordForm();form.setLoanMode("CONFIGURED");form.setPrincipal(new BigDecimal("1000"));form.setApplicationDate(date);
        var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(date);
        var service=new LoanChargeService(repository,mock(GeneralLedgerService.class),null,clock,null,null);
        var modern=new Policy(UUID.randomUUID(),productId,date,true,Timing.SEPARATE,Timing.SEPARATE,receivable,null,insurance,
            "Policy","Agreement","Insurer","Verified",false,null,2,application,processing);
        when(repository.policy("I1",productId,date)).thenReturn(Optional.of(modern));
        var result=service.quote(actor,form,product,true);
        assertThat(result.supported()).isTrue();
        assertThat(result.terms()).extracting(Term::liabilityAccountId).containsExactly(application,processing,insurance);
        verify(repository).account("I1",application,"APPLICATION");verify(repository).account("I1",processing,"PROCESSING");
        var legacy=new Policy(UUID.randomUUID(),productId,date,true,Timing.SEPARATE,Timing.SEPARATE,receivable,deferred,insurance,
            "Old policy","Agreement","Insurer","Verified",false,null);
        when(repository.policy("I1",productId,date)).thenReturn(Optional.of(legacy));
        assertThat(service.quote(actor,form,product,true).terms()).extracting(Term::liabilityAccountId).containsExactly(deferred,deferred,insurance);
        verify(repository,times(2)).account("I1",deferred,"MANAGEMENT");
    }
}
