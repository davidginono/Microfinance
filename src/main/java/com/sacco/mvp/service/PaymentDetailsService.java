package com.sacco.mvp.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.MemberPaymentDetails;
import com.sacco.mvp.domain.PaymentDestinationType;
import com.sacco.mvp.repository.MemberPaymentDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentDetailsService {
    private final MemberPaymentDetailsRepository repository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public PaymentDetailsView currentForMember(UUID memberId) {
        return repository.findById(memberId)
            .map(details -> toView(details, "Current member settings"))
            .orElse(PaymentDetailsView.empty());
    }

    public PaymentDetailsView resolveForLoan(LoanApplication application) {
        PaymentDetailsView captured = parseSnapshot(application.getPaymentDetailsSnapshot());
        return captured.available()
            ? captured
            : currentForMember(application.getApplicantMemberId());
    }

    @Transactional
    public PaymentDetailsView update(UUID memberId,
                                     PaymentDestinationType destinationType,
                                     String provider,
                                     String accountHolderName,
                                     String accountIdentifier) {
        MemberPaymentDetails existing = repository.findById(memberId).orElse(null);
        PaymentDetailsView before = existing == null ? PaymentDetailsView.empty() : toView(existing, "Current member settings");
        OffsetDateTime now = OffsetDateTime.now();
        MemberPaymentDetails details = existing == null
            ? MemberPaymentDetails.builder().memberId(memberId).createdAt(now).build()
            : existing;
        details.setDestinationType(requireType(destinationType));
        details.setProvider(normalizeRequired(provider, "Enter the bank, mobile-money provider, or payment provider.", 120));
        details.setAccountHolderName(normalizeRequired(accountHolderName, "Enter the account-holder name.", 160));
        details.setAccountIdentifier(normalizeIdentifier(accountIdentifier));
        details.setUpdatedAt(now);
        MemberPaymentDetails saved = repository.save(details);
        PaymentDetailsView result = toView(saved, "Current member settings");
        auditService.log("MEMBER_PAYMENT_DETAILS", memberId, "MEMBER_UPDATE_PAYMENT_DETAILS", memberId,
            auditSnapshot(before), auditSnapshot(result));
        return result;
    }

    public String snapshotJsonForMember(UUID memberId) {
        return repository.findById(memberId)
            .map(details -> toJson(toView(details, "Captured when submitted")))
            .orElse(null);
    }

    private PaymentDetailsView parseSnapshot(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return PaymentDetailsView.empty();
        }
        try {
            PaymentDetailsView view = objectMapper.readValue(snapshot, PaymentDetailsView.class);
            return view == null ? PaymentDetailsView.empty() : view;
        } catch (JacksonException ignored) {
            return PaymentDetailsView.empty();
        }
    }

    private String toJson(PaymentDetailsView view) {
        try {
            return objectMapper.writeValueAsString(view);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Financial details for the disbursement deposit could not be captured.", ex);
        }
    }

    private PaymentDetailsView toView(MemberPaymentDetails details, String sourceLabel) {
        return new PaymentDetailsView(
            true,
            details.getDestinationType(),
            typeLabel(details.getDestinationType()),
            details.getProvider(),
            details.getAccountHolderName(),
            details.getAccountIdentifier(),
            sourceLabel
        );
    }

    private Map<String, Object> auditSnapshot(PaymentDetailsView view) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("available", view.available());
        snapshot.put("destinationType", view.destinationType());
        snapshot.put("provider", view.provider());
        snapshot.put("accountIdentifier", maskIdentifier(view.accountIdentifier()));
        return snapshot;
    }

    private String maskIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim();
        int visible = Math.min(4, normalized.length());
        return "*".repeat(Math.max(0, normalized.length() - visible)) + normalized.substring(normalized.length() - visible);
    }

    private PaymentDestinationType requireType(PaymentDestinationType type) {
        if (type == null) {
            throw new IllegalArgumentException("Select a payment destination type.");
        }
        return type;
    }

    private String normalizeIdentifier(String value) {
        String normalized = normalizeRequired(value, "Enter the account or payment number.", 120);
        if (!normalized.matches("[\\p{L}\\p{N} ._+/@-]+")) {
            throw new IllegalArgumentException("The account or payment number contains unsupported characters.");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String message, int maxLength) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(message + " Maximum length is " + maxLength + " characters.");
        }
        return normalized;
    }

    private String typeLabel(PaymentDestinationType type) {
        return switch (type) {
            case BANK_ACCOUNT -> "Bank Account";
            case MOBILE_MONEY -> "Mobile Money";
            case OTHER -> "Other Payment Method";
        };
    }

    public record PaymentDetailsView(
        boolean available,
        PaymentDestinationType destinationType,
        String destinationTypeLabel,
        String provider,
        String accountHolderName,
        String accountIdentifier,
        String sourceLabel
    ) {
        public static PaymentDetailsView empty() {
            return new PaymentDetailsView(false, null, "Not provided", "", "", "", "Not provided");
        }

        public boolean isAvailable() {
            return available;
        }

        public PaymentDestinationType getDestinationType() {
            return destinationType;
        }

        public String getDestinationTypeLabel() {
            return destinationTypeLabel;
        }

        public String getProvider() {
            return provider;
        }

        public String getAccountHolderName() {
            return accountHolderName;
        }

        public String getAccountIdentifier() {
            return accountIdentifier;
        }

        public String getSourceLabel() {
            return sourceLabel;
        }
    }
}
