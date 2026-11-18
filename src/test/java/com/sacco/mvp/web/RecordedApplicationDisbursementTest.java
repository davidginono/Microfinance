package com.sacco.mvp.web;

import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class RecordedApplicationDisbursementTest {
    @Mock ManagerService manager;
    @Mock LoanRecordingService recording;
    @Mock MemberDirectoryService members;
    @Mock EmailOtpService otp;
    @Mock StationOtpSettingsService otpSettings;
    @Mock MessageSource messages;
    @InjectMocks DisbursementController controller;
    AppUserPrincipal actor;UUID id=UUID.randomUUID(),token=UUID.randomUUID(),key=UUID.randomUUID(),money=UUID.randomUUID(),principal=UUID.randomUUID();
    LocalDate date=LocalDate.of(2026,10,14),due=date.plusMonths(1);
    @BeforeEach void fixture(){
        var member=Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").email("synthetic@example.invalid").position(Position.DISBURSEMENT_OFFICER).status(com.sacco.mvp.domain.MemberStatus.ACTIVE).staffAccessStatus(com.sacco.mvp.domain.StaffAccessStatus.ACTIVE).build();
        actor=new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class));
        when(otpSettings.requiresApprovalOtp(actor.getMemberId(),"I1","B1")).thenReturn(true);
        when(members.find(member.getId())).thenReturn(Optional.of(member));
        when(otp.validateOtp(member.getEmail(),EmailOtpPurpose.BOARD_SIGNATURE,"123456")).thenReturn(token);
        when(manager.get(id,"I1","B1")).thenReturn(LoanApplication.builder().id(id).amount(new BigDecimal("1000.00")).status(LoanStatus.READY_FOR_DISBURSEMENT).build());
        when(recording.recordedForStaff(actor,id)).thenReturn(true);
    }
    @Test void tellerPostsPathScopedApplicationWithAccountsAndDocumentsBeforeOtpConsumption(){
        UUID charge=UUID.randomUUID();
        var proof=new MockMultipartFile("disbursementProofFile","proof.pdf","application/pdf","%PDF fixture".getBytes());
        var document=new MockMultipartFile("attachments","agreement.pdf","application/pdf","%PDF fixture".getBytes());
        when(recording.fundingQuote(eq(actor),eq(id),any(),anyList())).thenReturn(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.FundingQuote(new BigDecimal("990.00"),BigDecimal.TEN,BigDecimal.ZERO,null));var flash=new RedirectAttributesModelMap();
        assertThat(controller.finalize(id,actor,date,due,new BigDecimal("990"),"untrusted-id","D1","Note","123456",proof,key,money,principal,null,null,null,null,null,null,null,false,List.of(document),List.of(charge),flash)).isEqualTo("redirect:/disbursement/archive?filter=DISBURSED");
        var order=inOrder(recording,otp);
        order.verify(recording).recordedForStaff(actor,id);
        order.verify(recording).disburse(eq(actor),argThat(f->f.getDeductChargeIds().equals(List.of(charge)) && id.equals(f.getLoanId()) && key.equals(f.getRequestKey()) && money.equals(f.getMoneyAccountId()) && principal.equals(f.getPrincipalAccountId()) && date.equals(f.getEffectiveDate()) && due.equals(f.getFirstPaymentDate()) && f.getAmount().compareTo(new BigDecimal("1000"))==0),argThat(files->files.equals(List.of(proof,document))));
        order.verify(otp).consumeOtpById(token);
        verify(manager,never()).disburseLoan(any(),any(),any(),any(),any(),any(),any(),any(),any(),any(),any());
    }
    @Test void changedAmountCannotPostOrConsumeOtpAndErrorRetainsAccountingFields(){
        when(recording.fundingQuote(eq(actor),eq(id),any(),anyList())).thenReturn(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.FundingQuote(new BigDecimal("1000.00"),BigDecimal.ZERO,BigDecimal.ZERO,null));var flash=new RedirectAttributesModelMap();
        assertThat(controller.finalize(id,actor,date,due,new BigDecimal("999"),null,"D1","Note","123456",null,key,money,principal,null,null,null,null,null,null,null,false,List.of(),List.of(),flash)).isEqualTo("redirect:/disbursement/loan-applications/"+id);
        verify(recording,never()).disburse(any(),any(),any());verify(otp,never()).consumeOtpById(any());
        assertThat(flash.getFlashAttributes().get("disbursementRequestKey")).isEqualTo(key);
        assertThat(flash.getFlashAttributes().get("postingMoneyAccountId")).isEqualTo(money);
        assertThat(flash.getFlashAttributes().get("postingPrincipalAccountId")).isEqualTo(principal);
        assertThat(flash.getFlashAttributes().get("postingFirstPaymentDate")).isEqualTo(due);
    }
    @Test void topUpPassesNetCashAndSettlementPreviewWithoutChangingContractualPrincipal(){
        UUID source=UUID.randomUUID(),interest=UUID.randomUUID();
        when(manager.get(id,"I1","B1")).thenReturn(LoanApplication.builder().id(id).topUpSourceLoanId(source).amount(new BigDecimal("1000.00")).status(LoanStatus.READY_FOR_DISBURSEMENT).build());
        when(recording.fundingQuote(eq(actor),eq(id),any(),anyList())).thenReturn(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.FundingQuote(new BigDecimal("480.00"),BigDecimal.ZERO,BigDecimal.ZERO,null));var flash=new RedirectAttributesModelMap();
        assertThat(controller.finalize(id,actor,date,due,new BigDecimal("480.00"),null,"TOPUP","Note","123456",null,key,money,principal,interest,3L,new BigDecimal("500.00"),new BigDecimal("20.00"),null,null,"Signed agreement",true,List.of(),List.of(),flash)).isEqualTo("redirect:/disbursement/archive?filter=DISBURSED");
        verify(recording).disburse(eq(actor),argThat(f->f.getAmount().compareTo(new BigDecimal("1000.00"))==0 && f.getSettlementPrincipal().compareTo(new BigDecimal("500.00"))==0
            && f.getSettlementInterest().compareTo(new BigDecimal("20.00"))==0 && f.getSettlementSequence()==3L && interest.equals(f.getInterestAccountId()) && f.getEvidence().equals("Signed agreement")),eq(List.of()));
        verify(otp).consumeOtpById(token);
    }
    @Test void duplicatePostingRetainsNonSecretDraftFieldsAndRequestKeyWithoutConsumingOtp(){
        UUID interest=UUID.randomUUID(),charge=UUID.randomUUID();
        when(recording.fundingQuote(eq(actor),eq(id),any(),anyList())).thenReturn(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.FundingQuote(new BigDecimal("990.00"),BigDecimal.TEN,BigDecimal.ZERO,null));
        doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate")).when(recording).disburse(eq(actor),any(),anyList());
        when(messages.getMessage(eq("recording.error.duplicate"),isNull(),eq("recording.error.duplicate"),any())).thenReturn("Already recorded");
        var flash=new RedirectAttributesModelMap();
        assertThat(controller.finalize(id,actor,date,due,new BigDecimal("990"),null,"Changed reference","Changed notes","123456",null,key,money,principal,interest,null,null,null,null,null,"Signed paper",true,List.of(),List.of(charge),flash)).isEqualTo("redirect:/disbursement/loan-applications/"+id);
        assertThat(new LinkedHashMap<String,Object>(flash.getFlashAttributes())).containsEntry("error","Already recorded")
            .containsEntry("disbursementRequestKey",key).containsEntry("postingMoneyAccountId",money)
            .containsEntry("postingPrincipalAccountId",principal).containsEntry("postingInterestAccountId",interest)
            .containsEntry("postingDisbursementReference","Changed reference").containsEntry("postingDisbursementNotes","Changed notes")
            .containsEntry("postingDisbursementDate",date).containsEntry("postingFirstPaymentDate",due)
            .containsEntry("postingSettlementEvidence","Signed paper").containsEntry("postingDeductChargeIds",List.of(charge))
            .doesNotContainKeys("disbursementOtpCode","attachments","disbursementProofFile");
        verify(otp,never()).consumeOtpById(any());
    }
    @Test void disabledApprovalOtpDoesNotRequireOrConsumeAnOtpDuringTellerPosting(){
        reset(otp,members);
        when(otpSettings.requiresApprovalOtp(actor.getMemberId(),"I1","B1")).thenReturn(false);
        when(recording.fundingQuote(eq(actor),eq(id),any(),anyList())).thenReturn(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.FundingQuote(new BigDecimal("1000.00"),BigDecimal.ZERO,BigDecimal.ZERO,null));var flash=new RedirectAttributesModelMap();
        assertThat(controller.finalize(id,actor,date,due,new BigDecimal("1000"),null,"D-NO-OTP","Synthetic test",null,null,key,money,principal,null,null,null,null,null,null,null,false,List.of(),List.of(),flash)).isEqualTo("redirect:/disbursement/archive?filter=DISBURSED");
        verify(recording).disburse(eq(actor),any(),eq(List.of()));
        verifyNoInteractions(otp);
    }
}
