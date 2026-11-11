package com.sacco.mvp.service;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LoanWorkflowService {
    private static final List<LoanStatus> APPLICATION_IN_PROGRESS_LOCK_STATUSES = List.of(
        LoanStatus.DRAFT,
        LoanStatus.RECORDED,
        LoanStatus.SUBMITTED,
        LoanStatus.AWAITING_GUARANTORS,
        LoanStatus.ALL_GUARANTORS_APPROVED,
        LoanStatus.READY_FOR_MANAGER,
        LoanStatus.MANAGER_ACCEPTED,
        LoanStatus.AWAITING_LOAN_OFFICER,
        LoanStatus.LOAN_OFFICER_APPROVED,
        LoanStatus.AWAITING_CHAIRPERSON,
        LoanStatus.CHAIRPERSON_APPROVED,
        LoanStatus.AWAITING_BOARD,
        LoanStatus.AWAITING_CREDIT_COMMITTEE,
        LoanStatus.BOARD_APPROVED,
        LoanStatus.CREDIT_COMMITTEE_APPROVED,
        LoanStatus.AWAITING_ACCOUNTANT,
        LoanStatus.ACCOUNTANT_APPROVED,
        LoanStatus.READY_FOR_DISBURSEMENT
    );
    private final LoanApplicationRepository loanApplicationRepository;
    private final MemberRepository memberRepository;
    private final FormSchemaService formSchemaService;
    private final LoanAttachmentService loanAttachmentService;
    private final ObjectMapper objectMapper;
    private final ApplicationNumberService applicationNumberService;
    private final AuditService auditService;
    private final PlatformTransactionManager transactionManager;
    private final ApplicationClock applicationClock;

    public LoanApplication getMine(UUID appId, UUID memberId) {
        return loanApplicationRepository.findByIdAndApplicantMemberId(appId, memberId)
            .orElseThrow(() -> new IllegalArgumentException("Loan application not found"));
    }

    private Optional<LoanApplication> findBlockingApplicationInProgress(String institution,String branch,UUID memberId) {
        return loanApplicationRepository.findFirstBySaccoIdAndStationIdAndApplicantMemberIdAndStatusInOrderByCreatedAtDesc(
            institution,branch,memberId,APPLICATION_IN_PROGRESS_LOCK_STATUSES);
    }

    @Transactional(timeout=30)
    public UUID saveRecordingDraft(String institution,String branch,UUID client,UUID actor,
            com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Form form,Map<String,String> fields,List<LoanAttachmentService.NamedUpload> files) {
        var member=memberRepository.findByIdAndSaccoIdAndStationIdIgnoreCase(client,institution,branch).orElseThrow(()->new org.springframework.security.access.AccessDeniedException("Client unavailable"));
        if(!member.isMemberAccess()||member.getStatus()!=MemberStatus.ACTIVE)throw new org.springframework.security.access.AccessDeniedException("Provisional client required");
        var product=formSchemaService.getSchema(institution,form.getProductId(),null);var tenure=LoanTenure.from(Map.of("tenureUnit",form.getTenureUnit(),"tenureValue",form.getTenureValue().toString()),form.getTenureValue());tenure.validate(product);validateRequestedAmount(product,form.getAmount());
        var app=form.getApplicationId()==null?null:loanApplicationRepository.findByIdAndApplicantMemberId(form.getApplicationId(),client).orElseThrow(()->new IllegalArgumentException("applications.error.changed"));
        if(app!=null&&(!institution.equals(app.getSaccoId())||!branch.equals(app.getStationId())||app.getStatus()!=LoanStatus.DRAFT))throw new IllegalArgumentException("applications.error.state");
        if(app==null){findBlockingApplicationInProgress(institution,branch,client).ifPresent(old->{throw new IllegalArgumentException("applications.error.ongoing");});app=LoanApplication.builder().id(UUID.randomUUID()).applicationNumber(applicationNumberService.nextFor(institution)).saccoId(institution).stationId(branch).applicantMemberId(client).createdAt(applicationClock.now()).build();}
        var data=formSchemaService.extractFormData(fields,product.getFormSchema());data.put("recordingDetails",objectMapper.readValue(objectMapper.writeValueAsString(form),new TypeReference<Map<String,Object>>(){}));data.put("informationSource","DECLARED");
        app.setLoanType(product.getLoanType());app.setLoanProductSettingId(product.getId());app.setAmount(form.getAmount());app.setTenorMonths(tenure.compatibilityMonths());app.setStatus(LoanStatus.DRAFT);app.setRequiredGuarantors(product.getGuarantorsRequired());app.setFormData(formSchemaService.toJson(data));
        app.setPolicySnapshot("{\"assessmentType\":\"NOT_ASSESSED\",\"verificationStatus\":\"DECLARED_NOT_VERIFIED\"}");app.setFinancialSnapshot(null);app.setSelectedGuarantors("[]");app.setApplicantSignatureText(null);app.setApplicantSignatureVerifiedAt(null);app.setUpdatedAt(applicationClock.now());
        app=loanApplicationRepository.saveAndFlush(app);app.setAttachmentsJson(loanAttachmentService.storeNamed(app.getId(),files,app.getAttachmentsJson()));app=loanApplicationRepository.saveAndFlush(app);auditLoan(app,actor,"LOAN_APPLICATION_RECORDED_DRAFT","Client application recorded as draft");return app.getId();
    }

    public LoanApplication removeApplicationAttachment(UUID appId, UUID memberId, String attachmentId, UUID actorId) {
        LoanApplication app = getMine(appId, memberId);
        if (app.getStatus() != LoanStatus.DRAFT) {
            throw new IllegalStateException("Only draft applications can be edited.");
        }
        return inTransaction(() -> {
            LoanAttachmentService.AttachmentRemoval removal = loanAttachmentService.removeApplicationAttachment(
                app.getId(),
                attachmentId,
                app.getAttachmentsJson()
            );
            app.setAttachmentsJson(removal.attachmentsJson());
            app.setUpdatedAt(OffsetDateTime.now());
            LoanApplication saved = loanApplicationRepository.save(app);
            auditLoan(saved, actorId, "LOAN_APPLICATION_ATTACHMENT_REMOVED", "Loan application attachment removed", Map.of(
                "attachmentId", attachmentId,
                "attachmentName", removal.originalName()
            ));
            return saved;
        });
    }

    private void validateRequestedAmount(LoanProductSetting product, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Loan amount must be greater than zero.");
        }
        if (product.getMinimumAmount() != null && amount.compareTo(product.getMinimumAmount()) < 0) {
            throw new IllegalArgumentException(
                "Loan amount cannot be below " + product.getMinimumAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    + " for this loan product"
            );
        }
        if (product.getMaximumAmount() != null && amount.compareTo(product.getMaximumAmount()) > 0) {
            throw new IllegalArgumentException(
                "Loan amount cannot exceed " + product.getMaximumAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    + " for this loan product"
            );
        }
    }

    private void auditLoan(LoanApplication app, UUID actorId, String action, String description) {
        auditLoan(app, actorId, action, description, Map.of());
    }

    private void auditLoan(LoanApplication app,
                           UUID actorId,
                           String action,
                           String description,
                           Map<String, Object> extraDetails) {
        if (app == null) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("result", "SUCCESS");
        details.put("saccoId", app.getSaccoId());
        details.put("stationId", app.getStationId());
        details.put("applicationNumber", app.getApplicationNumber());
        details.put("loanId", app.getLoanId());
        details.put("workflowStatus", app.getStatus() == null ? null : app.getStatus().name());
        if (extraDetails != null) {
            details.putAll(extraDetails);
        }
        String referenceValue = app.getLoanId() != null && !app.getLoanId().isBlank() && "LOAN_DISBURSED".equals(action)
            ? "Loan ID " + app.getLoanId()
            : "Loan Application #" + app.getApplicationNumber();
        auditService.logEvent(
            "LOAN_APPLICATION",
            app.getId(),
            action,
            actorId,
            AuditEventStatus.SUCCESS,
            description,
            "LOAN_APPLICATION",
            referenceValue,
            app.getSaccoId(),
            app.getStationId(),
            details
        );
    }

    private <T> T inTransaction(java.util.function.Supplier<T> work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> work.get());
    }

}
