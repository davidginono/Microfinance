package com.sacco.mvp.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum UserClaim {
    STATEMENT_DESIGN(AccessFeature.FINANCIAL_STATEMENTS, AccessAction.CREATE),
    STATEMENT_APPROVE(AccessFeature.FINANCIAL_STATEMENTS, AccessAction.APPROVE),
    STATEMENT_VIEW(AccessFeature.FINANCIAL_STATEMENTS, AccessAction.VIEW),
    STATEMENT_EXPORT(AccessFeature.FINANCIAL_STATEMENTS, AccessAction.EXPORT),
    STATEMENT_FINALIZE(AccessFeature.FINANCIAL_STATEMENTS, AccessAction.CONFIGURE),
    REGULATORY_SUBMISSION_CREATE(AccessFeature.REGULATORY_STATEMENTS, AccessAction.CREATE),
    REGULATORY_SUBMISSION_APPROVE(AccessFeature.REGULATORY_STATEMENTS, AccessAction.APPROVE),
    REGULATORY_SUBMISSION_VIEW(AccessFeature.REGULATORY_STATEMENTS, AccessAction.VIEW),
    ACCESS_MATRIX_VIEW(AccessFeature.ACCESS_MATRIX, AccessAction.VIEW),
    ACCESS_MATRIX_UPDATE(AccessFeature.ACCESS_MATRIX, AccessAction.UPDATE),

    USER_ACCESS_VIEW(AccessFeature.USER_ACCESS, AccessAction.VIEW),
    USER_ACCESS_CREATE(AccessFeature.USER_ACCESS, AccessAction.CREATE),
    USER_ACCESS_UPDATE(AccessFeature.USER_ACCESS, AccessAction.UPDATE),
    USER_ACCESS_DELETE(AccessFeature.USER_ACCESS, AccessAction.DELETE),

    ADMIN_DASHBOARD_VIEW(AccessFeature.ADMIN_DASHBOARD, AccessAction.VIEW),

    SACCO_REGISTRY_VIEW(AccessFeature.SACCO_REGISTRY, AccessAction.VIEW),
    SACCO_REGISTRY_CREATE(AccessFeature.SACCO_REGISTRY, AccessAction.CREATE),
    SACCO_REGISTRY_UPDATE(AccessFeature.SACCO_REGISTRY, AccessAction.UPDATE),
    SACCO_REGISTRY_DELETE(AccessFeature.SACCO_REGISTRY, AccessAction.DELETE),

    PLATFORM_SETTINGS_VIEW(AccessFeature.PLATFORM_SETTINGS, AccessAction.VIEW),
    PLATFORM_SETTINGS_UPDATE(AccessFeature.PLATFORM_SETTINGS, AccessAction.UPDATE),

    WORKSPACE_SETTINGS_VIEW(AccessFeature.WORKSPACE_SETTINGS, AccessAction.VIEW),
    WORKSPACE_SETTINGS_UPDATE(AccessFeature.WORKSPACE_SETTINGS, AccessAction.UPDATE),
    WORKSPACE_SETTINGS_CONFIGURE(AccessFeature.WORKSPACE_SETTINGS, AccessAction.CONFIGURE),

    LOAN_PRODUCTS_VIEW(AccessFeature.LOAN_PRODUCTS, AccessAction.VIEW),
    LOAN_PRODUCTS_CREATE(AccessFeature.LOAN_PRODUCTS, AccessAction.CREATE),
    LOAN_PRODUCTS_UPDATE(AccessFeature.LOAN_PRODUCTS, AccessAction.UPDATE),
    LOAN_PRODUCTS_CONFIGURE(AccessFeature.LOAN_PRODUCTS, AccessAction.CONFIGURE),
    LOAN_PRODUCTS_DELETE(AccessFeature.LOAN_PRODUCTS, AccessAction.DELETE),

    APPROVAL_FLOW_VIEW(AccessFeature.APPROVAL_FLOW, AccessAction.VIEW),
    APPROVAL_FLOW_CONFIGURE(AccessFeature.APPROVAL_FLOW, AccessAction.CONFIGURE),

    SMS_USAGE_VIEW(AccessFeature.SMS_USAGE, AccessAction.VIEW),
    SMS_USAGE_ADD(AccessFeature.SMS_USAGE, AccessAction.ADD),
    SMS_USAGE_CONFIGURE(AccessFeature.SMS_USAGE, AccessAction.CONFIGURE),

    OUTBOX_VIEW(AccessFeature.OUTBOX, AccessAction.VIEW),
    OUTBOX_UPDATE(AccessFeature.OUTBOX, AccessAction.UPDATE),

    MEMBER_SETTINGS_VIEW(AccessFeature.MEMBER_SETTINGS, AccessAction.VIEW),
    MEMBER_SETTINGS_UPDATE(AccessFeature.MEMBER_SETTINGS, AccessAction.UPDATE),

    PAYMENT_DETAILS_VIEW(AccessFeature.PAYMENT_DETAILS, AccessAction.VIEW),
    PAYMENT_DETAILS_UPDATE(AccessFeature.PAYMENT_DETAILS, AccessAction.UPDATE),

    LOAN_REPAYMENTS_VIEW(AccessFeature.LOAN_REPAYMENTS, AccessAction.VIEW),
    LOAN_REPAYMENTS_CREATE(AccessFeature.LOAN_REPAYMENTS, AccessAction.CREATE),
    LOAN_REPAYMENTS_REVERSE(AccessFeature.LOAN_REPAYMENTS, AccessAction.REVERSE),

    ACCOUNTING_ACCOUNTS_VIEW(AccessFeature.ACCOUNTING_ACCOUNTS, AccessAction.VIEW),
    ACCOUNTING_ACCOUNTS_CREATE(AccessFeature.ACCOUNTING_ACCOUNTS, AccessAction.CREATE),
    ACCOUNTING_ACCOUNTS_UPDATE(AccessFeature.ACCOUNTING_ACCOUNTS, AccessAction.UPDATE),
    ACCOUNTING_JOURNALS_VIEW(AccessFeature.ACCOUNTING_JOURNALS, AccessAction.VIEW),
    ACCOUNTING_JOURNALS_CREATE(AccessFeature.ACCOUNTING_JOURNALS, AccessAction.CREATE),
    ACCOUNTING_JOURNALS_APPROVE(AccessFeature.ACCOUNTING_JOURNALS, AccessAction.APPROVE),
    ACCOUNTING_JOURNALS_REVERSE(AccessFeature.ACCOUNTING_JOURNALS, AccessAction.REVERSE),
    ACCOUNTING_OPENINGS_CREATE(AccessFeature.ACCOUNTING_OPENINGS, AccessAction.CREATE),
    ACCOUNTING_OPENINGS_APPROVE(AccessFeature.ACCOUNTING_OPENINGS, AccessAction.APPROVE),
    ACCOUNTING_PERIODS_CREATE(AccessFeature.ACCOUNTING_PERIODS, AccessAction.CREATE),
    ACCOUNTING_POLICIES_VIEW(AccessFeature.ACCOUNTING_POLICIES, AccessAction.VIEW),
    ACCOUNTING_POLICIES_CREATE(AccessFeature.ACCOUNTING_POLICIES, AccessAction.CREATE),
    ACCOUNTING_POLICIES_APPROVE(AccessFeature.ACCOUNTING_POLICIES, AccessAction.APPROVE),
    ACCOUNTING_BUSINESS_VIEW(AccessFeature.ACCOUNTING_BUSINESS, AccessAction.VIEW),
    ACCOUNTING_BUSINESS_CREATE(AccessFeature.ACCOUNTING_BUSINESS, AccessAction.CREATE),
    ACCOUNTING_BUSINESS_APPROVE(AccessFeature.ACCOUNTING_BUSINESS, AccessAction.APPROVE),
    ACCOUNTING_BUSINESS_REVERSE(AccessFeature.ACCOUNTING_BUSINESS, AccessAction.REVERSE),

    MEMBER_LOANS_VIEW(AccessFeature.MEMBER_LOANS, AccessAction.VIEW),
    MEMBER_LOANS_CREATE(AccessFeature.MEMBER_LOANS, AccessAction.CREATE),
    MEMBER_LOANS_UPDATE(AccessFeature.MEMBER_LOANS, AccessAction.UPDATE),
    MEMBER_LOANS_ASSIGN(AccessFeature.MEMBER_LOANS, AccessAction.ASSIGN),
    MEMBER_LOANS_DELETE(AccessFeature.MEMBER_LOANS, AccessAction.DELETE),

    GUARANTOR_REQUESTS_VIEW(AccessFeature.GUARANTOR_REQUESTS, AccessAction.VIEW),
    GUARANTOR_REQUESTS_UPDATE(AccessFeature.GUARANTOR_REQUESTS, AccessAction.UPDATE),
    GUARANTOR_REQUESTS_APPROVE(AccessFeature.GUARANTOR_REQUESTS, AccessAction.APPROVE),
    GUARANTOR_REQUESTS_REJECT(AccessFeature.GUARANTOR_REQUESTS, AccessAction.REJECT),

    LOAN_DOCUMENTS_VIEW(AccessFeature.LOAN_DOCUMENTS, AccessAction.VIEW),
    LOAN_DOCUMENTS_CREATE(AccessFeature.LOAN_DOCUMENTS, AccessAction.CREATE),
    LOAN_DOCUMENTS_EXPORT(AccessFeature.LOAN_DOCUMENTS, AccessAction.EXPORT),

    STAFF_ANALYTICS_VIEW(AccessFeature.STAFF_ANALYTICS, AccessAction.VIEW),
    STAFF_ANALYTICS_EXPORT(AccessFeature.STAFF_ANALYTICS, AccessAction.EXPORT),

    LOAN_OFFICER_QUEUE_VIEW(AccessFeature.LOAN_OFFICER_QUEUE, AccessAction.VIEW),
    LOAN_OFFICER_QUEUE_ASSIGN(AccessFeature.LOAN_OFFICER_QUEUE, AccessAction.ASSIGN),
    LOAN_OFFICER_QUEUE_APPROVE(AccessFeature.LOAN_OFFICER_QUEUE, AccessAction.APPROVE),
    LOAN_OFFICER_QUEUE_REJECT(AccessFeature.LOAN_OFFICER_QUEUE, AccessAction.REJECT),
    LOAN_OFFICER_QUEUE_EXPORT(AccessFeature.LOAN_OFFICER_QUEUE, AccessAction.EXPORT),

    MANAGER_QUEUE_VIEW(AccessFeature.MANAGER_QUEUE, AccessAction.VIEW),
    MANAGER_QUEUE_APPROVE(AccessFeature.MANAGER_QUEUE, AccessAction.APPROVE),
    MANAGER_QUEUE_REJECT(AccessFeature.MANAGER_QUEUE, AccessAction.REJECT),
    MANAGER_QUEUE_EXPORT(AccessFeature.MANAGER_QUEUE, AccessAction.EXPORT),

    ACCOUNTANT_QUEUE_VIEW(AccessFeature.ACCOUNTANT_QUEUE, AccessAction.VIEW),
    ACCOUNTANT_QUEUE_APPROVE(AccessFeature.ACCOUNTANT_QUEUE, AccessAction.APPROVE),
    ACCOUNTANT_QUEUE_REJECT(AccessFeature.ACCOUNTANT_QUEUE, AccessAction.REJECT),
    ACCOUNTANT_QUEUE_EXPORT(AccessFeature.ACCOUNTANT_QUEUE, AccessAction.EXPORT),

    BOARD_QUEUE_VIEW(AccessFeature.BOARD_QUEUE, AccessAction.VIEW),
    BOARD_QUEUE_APPROVE(AccessFeature.BOARD_QUEUE, AccessAction.APPROVE),
    BOARD_QUEUE_REJECT(AccessFeature.BOARD_QUEUE, AccessAction.REJECT),
    BOARD_QUEUE_EXPORT(AccessFeature.BOARD_QUEUE, AccessAction.EXPORT),

    CHAIRPERSON_QUEUE_VIEW(AccessFeature.CHAIRPERSON_QUEUE, AccessAction.VIEW),
    CHAIRPERSON_QUEUE_APPROVE(AccessFeature.CHAIRPERSON_QUEUE, AccessAction.APPROVE),
    CHAIRPERSON_QUEUE_REJECT(AccessFeature.CHAIRPERSON_QUEUE, AccessAction.REJECT),
    CHAIRPERSON_QUEUE_EXPORT(AccessFeature.CHAIRPERSON_QUEUE, AccessAction.EXPORT),

    PROCESSED_LOANS_VIEW(AccessFeature.PROCESSED_LOANS, AccessAction.VIEW),
    SACCO_CONFIGURATIONS_VIEW(AccessFeature.SACCO_CONFIGURATIONS, AccessAction.VIEW),

    CREDIT_COMMITTEE_QUEUE_VIEW(AccessFeature.CREDIT_COMMITTEE_QUEUE, AccessAction.VIEW),
    CREDIT_COMMITTEE_QUEUE_APPROVE(AccessFeature.CREDIT_COMMITTEE_QUEUE, AccessAction.APPROVE),
    CREDIT_COMMITTEE_QUEUE_REJECT(AccessFeature.CREDIT_COMMITTEE_QUEUE, AccessAction.REJECT),
    CREDIT_COMMITTEE_QUEUE_EXPORT(AccessFeature.CREDIT_COMMITTEE_QUEUE, AccessAction.EXPORT),

    DISBURSEMENT_QUEUE_VIEW(AccessFeature.DISBURSEMENT_QUEUE, AccessAction.VIEW),
    DISBURSEMENT_QUEUE_EXPORT(AccessFeature.DISBURSEMENT_QUEUE, AccessAction.EXPORT),
    DISBURSEMENT_QUEUE_DISBURSE(AccessFeature.DISBURSEMENT_QUEUE, AccessAction.DISBURSE),

    LOAN_REPORTS_VIEW(AccessFeature.LOAN_REPORTS, AccessAction.VIEW),
    LOAN_REPORTS_EXPORT(AccessFeature.LOAN_REPORTS, AccessAction.EXPORT),

    NOTIFICATIONS_VIEW(AccessFeature.NOTIFICATIONS, AccessAction.VIEW),
    NOTIFICATIONS_UPDATE(AccessFeature.NOTIFICATIONS, AccessAction.UPDATE),

    SUPPORT_VIEW(AccessFeature.SUPPORT, AccessAction.VIEW),
    SUPPORT_CREATE(AccessFeature.SUPPORT, AccessAction.CREATE),
    SUPPORT_UPDATE(AccessFeature.SUPPORT, AccessAction.UPDATE),
    REPORT_TEMPLATE_DESIGN(AccessFeature.REPORT_BUILDER, AccessAction.CREATE),
    REPORT_TEMPLATE_PUBLISH(AccessFeature.REPORT_BUILDER, AccessAction.APPROVE),
    REPORT_RUN(AccessFeature.REPORT_BUILDER, AccessAction.VIEW),
    REPORT_EXPORT(AccessFeature.REPORT_BUILDER, AccessAction.EXPORT),
    REPORT_TEMPLATE_SHARE(AccessFeature.REPORT_BUILDER, AccessAction.ASSIGN),
    ACCOUNTING_CASH_FLOW_CREATE(AccessFeature.ACCOUNTING_CASH_FLOW, AccessAction.CREATE),
    ACCOUNTING_CASH_FLOW_APPROVE(AccessFeature.ACCOUNTING_CASH_FLOW, AccessAction.APPROVE),
    FINANCIAL_REPORTS_VIEW(AccessFeature.FINANCIAL_REPORTS, AccessAction.VIEW),
    FINANCIAL_REPORTS_EXPORT(AccessFeature.FINANCIAL_REPORTS, AccessAction.EXPORT),
    FINANCIAL_REPORTS_FINALIZE(AccessFeature.FINANCIAL_REPORTS, AccessAction.APPROVE),
    FINANCIAL_REPORTS_INSTITUTION(AccessFeature.FINANCIAL_REPORTS, AccessAction.CONFIGURE),
    ACCOUNTING_RECONCILIATION_VIEW(AccessFeature.ACCOUNTING_RECONCILIATION, AccessAction.VIEW),
    ACCOUNTING_RECONCILIATION_CREATE(AccessFeature.ACCOUNTING_RECONCILIATION, AccessAction.CREATE),
    ACCOUNTING_RECONCILIATION_APPROVE(AccessFeature.ACCOUNTING_RECONCILIATION, AccessAction.APPROVE),
    ACCOUNTING_CLOSING_VIEW(AccessFeature.ACCOUNTING_CLOSING, AccessAction.VIEW),
    ACCOUNTING_CLOSING_CREATE(AccessFeature.ACCOUNTING_CLOSING, AccessAction.CREATE),
    ACCOUNTING_CLOSING_APPROVE(AccessFeature.ACCOUNTING_CLOSING, AccessAction.APPROVE),
    ACCOUNTING_CLOSING_REOPEN(AccessFeature.ACCOUNTING_CLOSING, AccessAction.REVERSE),
    ACCOUNTING_CLOSING_INSTITUTION(AccessFeature.ACCOUNTING_CLOSING, AccessAction.CONFIGURE),
    REPORT_RUN_APPROVE(AccessFeature.REPORT_EXECUTION, AccessAction.APPROVE),
    REPORT_JOBS_REVIEW(AccessFeature.REPORT_EXECUTION, AccessAction.VIEW),

    ACCOUNTING_RELEASE_VIEW(AccessFeature.ACCOUNTING_RELEASE, AccessAction.VIEW),
    ACCOUNTING_RELEASE_REQUEST(AccessFeature.ACCOUNTING_RELEASE, AccessAction.CREATE),
    ACCOUNTING_RELEASE_APPROVE(AccessFeature.ACCOUNTING_RELEASE, AccessAction.APPROVE),
    ACCOUNTING_COMPLIANCE_RELEASE_APPROVE(AccessFeature.ACCOUNTING_COMPLIANCE_RELEASE, AccessAction.APPROVE),
    ACCOUNTING_RELEASE_ACCEPT(AccessFeature.ACCOUNTING_STAFF_ACCEPTANCE, AccessAction.APPROVE);

    private static final Map<AccessFeature, Map<AccessAction, UserClaim>> BY_FEATURE_ACTION = buildLookup();

    private final AccessFeature feature;
    private final AccessAction action;

    UserClaim(AccessFeature feature, AccessAction action) {
        this.feature = feature;
        this.action = action;
    }

    public AccessFeature getFeature() {
        return feature;
    }

    public AccessAction getAction() {
        return action;
    }

    public String getDisplayName() {
        return feature.getDisplayName() + " - " + action.getDisplayName();
    }

    public static Optional<UserClaim> forFeatureAction(AccessFeature feature, AccessAction action) {
        if (feature == null || action == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_FEATURE_ACTION.getOrDefault(feature, Map.of()).get(action));
    }

    public static List<AccessMatrixRow> matrixRows() {
        List<AccessMatrixRow> rows = new ArrayList<>();
        for (AccessFeature feature : AccessFeature.values()) {
            Map<AccessAction, UserClaim> claimsByAction = BY_FEATURE_ACTION.get(feature);
            if (claimsByAction != null && !claimsByAction.isEmpty()) {
                rows.add(new AccessMatrixRow(feature, feature.getDisplayName(), claimsByAction));
            }
        }
        return rows;
    }

    public static Set<UserClaim> fromStoredName(String claimName) {
        if (claimName == null || claimName.isBlank()) {
            return EnumSet.noneOf(UserClaim.class);
        }
        try {
            return EnumSet.of(UserClaim.valueOf(claimName.trim()));
        } catch (IllegalArgumentException ignored) {
            return legacyClaims(claimName.trim());
        }
    }

    public static Set<UserClaim> expandLegacy(Collection<UserClaim> claims) {
        if (claims == null || claims.isEmpty()) {
            return EnumSet.noneOf(UserClaim.class);
        }
        EnumSet<UserClaim> expanded = EnumSet.noneOf(UserClaim.class);
        expanded.addAll(claims);
        return expanded;
    }

    public static Set<UserClaim> legacyClaims(String legacyClaimName) {
        EnumSet<UserClaim> claims = EnumSet.noneOf(UserClaim.class);
        switch (legacyClaimName) {
            case "APPLY_LOANS" -> addAll(claims,
                MEMBER_SETTINGS_VIEW, MEMBER_SETTINGS_UPDATE,
                PAYMENT_DETAILS_VIEW, PAYMENT_DETAILS_UPDATE,
                MEMBER_LOANS_VIEW, MEMBER_LOANS_CREATE, MEMBER_LOANS_UPDATE, MEMBER_LOANS_ASSIGN, MEMBER_LOANS_DELETE,
                LOAN_DOCUMENTS_VIEW, LOAN_DOCUMENTS_CREATE, LOAN_DOCUMENTS_EXPORT,
                LOAN_REPORTS_VIEW, LOAN_REPORTS_EXPORT,
                NOTIFICATIONS_VIEW, NOTIFICATIONS_UPDATE, SUPPORT_VIEW, SUPPORT_CREATE);
            case "APPROVE_GUARANTOR_REQUESTS" -> addAll(claims,
                GUARANTOR_REQUESTS_VIEW, GUARANTOR_REQUESTS_UPDATE, GUARANTOR_REQUESTS_APPROVE, GUARANTOR_REQUESTS_REJECT,
                NOTIFICATIONS_VIEW, NOTIFICATIONS_UPDATE, SUPPORT_VIEW, SUPPORT_CREATE);
            case "REVIEW_MANAGER_QUEUE" -> addReviewClaims(claims,
                MANAGER_QUEUE_VIEW, MANAGER_QUEUE_APPROVE, MANAGER_QUEUE_REJECT, MANAGER_QUEUE_EXPORT);
            case "REVIEW_ACCOUNTANT_QUEUE" -> addReviewClaims(claims,
                ACCOUNTANT_QUEUE_VIEW, ACCOUNTANT_QUEUE_APPROVE, ACCOUNTANT_QUEUE_REJECT, ACCOUNTANT_QUEUE_EXPORT);
            case "ACCESS_DISBURSEMENT_QUEUE" -> addAll(claims,
                DISBURSEMENT_QUEUE_VIEW, DISBURSEMENT_QUEUE_EXPORT,
                LOAN_DOCUMENTS_VIEW, STAFF_ANALYTICS_VIEW, LOAN_REPORTS_VIEW, LOAN_REPORTS_EXPORT,
                NOTIFICATIONS_VIEW, NOTIFICATIONS_UPDATE);
            case "DISBURSE_LOAN" -> addAll(claims,
                DISBURSEMENT_QUEUE_VIEW, DISBURSEMENT_QUEUE_DISBURSE,
                LOAN_DOCUMENTS_VIEW, NOTIFICATIONS_VIEW, NOTIFICATIONS_UPDATE);
            case "REVIEW_CHAIRPERSON_QUEUE" -> addReviewClaims(claims,
                CHAIRPERSON_QUEUE_VIEW, CHAIRPERSON_QUEUE_APPROVE, CHAIRPERSON_QUEUE_REJECT,
                CHAIRPERSON_QUEUE_EXPORT, PROCESSED_LOANS_VIEW, SACCO_CONFIGURATIONS_VIEW);
            case "REVIEW_BOARD_QUEUE" -> addReviewClaims(claims,
                BOARD_QUEUE_VIEW, BOARD_QUEUE_APPROVE, BOARD_QUEUE_REJECT, BOARD_QUEUE_EXPORT);
            case "REVIEW_CREDIT_COMMITTEE_QUEUE" -> addReviewClaims(claims,
                CREDIT_COMMITTEE_QUEUE_VIEW, CREDIT_COMMITTEE_QUEUE_APPROVE, CREDIT_COMMITTEE_QUEUE_REJECT,
                CREDIT_COMMITTEE_QUEUE_EXPORT);
            case "REVIEW_LOAN_OFFICER_QUEUE" -> addReviewClaims(claims,
                LOAN_OFFICER_QUEUE_VIEW, LOAN_OFFICER_QUEUE_ASSIGN, LOAN_OFFICER_QUEUE_APPROVE,
                LOAN_OFFICER_QUEUE_REJECT, LOAN_OFFICER_QUEUE_EXPORT);
            case "ACCESS_ADMIN_SETTINGS" -> addAll(claims,
                ACCESS_MATRIX_VIEW, ACCESS_MATRIX_UPDATE,
                USER_ACCESS_VIEW, USER_ACCESS_CREATE, USER_ACCESS_UPDATE, USER_ACCESS_DELETE,
                ADMIN_DASHBOARD_VIEW,
                WORKSPACE_SETTINGS_VIEW, WORKSPACE_SETTINGS_UPDATE, WORKSPACE_SETTINGS_CONFIGURE,
                LOAN_PRODUCTS_VIEW, LOAN_PRODUCTS_CREATE, LOAN_PRODUCTS_UPDATE, LOAN_PRODUCTS_CONFIGURE, LOAN_PRODUCTS_DELETE,
                APPROVAL_FLOW_VIEW, APPROVAL_FLOW_CONFIGURE,
                SMS_USAGE_VIEW, SMS_USAGE_CONFIGURE,
                NOTIFICATIONS_VIEW, NOTIFICATIONS_UPDATE,
                SUPPORT_VIEW, SUPPORT_CREATE, SUPPORT_UPDATE);
            case "ACCESS_OUTBOX_MONITOR" -> addAll(claims, OUTBOX_VIEW, OUTBOX_UPDATE);
            default -> {
            }
        }
        return claims;
    }

    public static Set<UserClaim> defaultClaims(Collection<Position> staffRoles, boolean memberAccess) {
        EnumSet<UserClaim> claims = EnumSet.noneOf(UserClaim.class);
        if (memberAccess) {
            claims.addAll(legacyClaims("APPLY_LOANS"));
            claims.addAll(legacyClaims("APPROVE_GUARANTOR_REQUESTS"));
        }
        for (Position position : Position.normalizeStaffRoles(staffRoles)) {
            switch (position) {
                case MEMBER -> {
                }
                case LOAN_OFFICER -> claims.addAll(legacyClaims("REVIEW_LOAN_OFFICER_QUEUE"));
                case MANAGER -> claims.addAll(legacyClaims("REVIEW_MANAGER_QUEUE"));
                case ACCOUNTANT -> {
                    claims.addAll(legacyClaims("REVIEW_ACCOUNTANT_QUEUE"));
                    addAll(claims, ACCOUNTING_ACCOUNTS_VIEW, ACCOUNTING_ACCOUNTS_CREATE, ACCOUNTING_ACCOUNTS_UPDATE);
                }
                case DISBURSEMENT_OFFICER -> {
                    claims.addAll(legacyClaims("ACCESS_DISBURSEMENT_QUEUE"));
                    claims.addAll(legacyClaims("DISBURSE_LOAN"));
                }
                case CHAIRPERSON -> claims.addAll(legacyClaims("REVIEW_CHAIRPERSON_QUEUE"));
                case BOARD -> claims.addAll(legacyClaims("REVIEW_BOARD_QUEUE"));
                case CREDIT_COMMITTEE -> claims.addAll(legacyClaims("REVIEW_CREDIT_COMMITTEE_QUEUE"));
                case MINOR_ADMIN -> claims.addAll(workspaceAdminDefaults());
                case ADMIN -> claims.addAll(platformAdminDefaults());
            }
        }
        return claims;
    }

    public static Set<UserClaim> workspaceAdminDefaults() {
        EnumSet<UserClaim> claims = EnumSet.noneOf(UserClaim.class);
        claims.addAll(legacyClaims("ACCESS_ADMIN_SETTINGS"));
        claims.addAll(legacyClaims("ACCESS_OUTBOX_MONITOR"));
        return claims;
    }

    public static Set<UserClaim> platformAdminDefaults() {
        EnumSet<UserClaim> claims = EnumSet.copyOf(workspaceAdminDefaults());
        addAll(claims,
            SACCO_REGISTRY_VIEW, SACCO_REGISTRY_CREATE, SACCO_REGISTRY_UPDATE, SACCO_REGISTRY_DELETE,
            PLATFORM_SETTINGS_VIEW, PLATFORM_SETTINGS_UPDATE,
            SMS_USAGE_ADD);
        return claims;
    }

    public static Optional<UserClaim> requiredWorkspaceViewClaim(Position position) {
        if (position == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (position) {
            case LOAN_OFFICER -> LOAN_OFFICER_QUEUE_VIEW;
            case MANAGER -> MANAGER_QUEUE_VIEW;
            case ACCOUNTANT -> ACCOUNTANT_QUEUE_VIEW;
            case DISBURSEMENT_OFFICER -> DISBURSEMENT_QUEUE_VIEW;
            case CHAIRPERSON -> CHAIRPERSON_QUEUE_VIEW;
            case BOARD -> BOARD_QUEUE_VIEW;
            case CREDIT_COMMITTEE -> CREDIT_COMMITTEE_QUEUE_VIEW;
            case MEMBER, MINOR_ADMIN, ADMIN -> null;
        });
    }

    public static Set<UserClaim> administratorRecoveryClaims() {
        return Set.of(
            ADMIN_DASHBOARD_VIEW,
            ACCESS_MATRIX_VIEW,
            ACCESS_MATRIX_UPDATE,
            USER_ACCESS_VIEW,
            USER_ACCESS_UPDATE
        );
    }

    private static void addReviewClaims(EnumSet<UserClaim> claims, UserClaim... queueClaims) {
        addAll(claims, queueClaims);
        addAll(claims,
            LOAN_DOCUMENTS_VIEW, STAFF_ANALYTICS_VIEW, STAFF_ANALYTICS_EXPORT,
            LOAN_REPORTS_VIEW, LOAN_REPORTS_EXPORT,
            NOTIFICATIONS_VIEW, NOTIFICATIONS_UPDATE, SUPPORT_VIEW, SUPPORT_CREATE);
    }

    private static void addAll(EnumSet<UserClaim> claims, UserClaim... additions) {
        for (UserClaim addition : additions) {
            claims.add(addition);
        }
    }

    private static Map<AccessFeature, Map<AccessAction, UserClaim>> buildLookup() {
        EnumMap<AccessFeature, Map<AccessAction, UserClaim>> byFeature = new EnumMap<>(AccessFeature.class);
        for (UserClaim claim : values()) {
            byFeature.computeIfAbsent(claim.feature, ignored -> new EnumMap<>(AccessAction.class))
                .put(claim.action, claim);
        }
        Map<AccessFeature, Map<AccessAction, UserClaim>> immutable = new LinkedHashMap<>();
        byFeature.forEach((feature, claimsByAction) -> immutable.put(feature, Map.copyOf(claimsByAction)));
        return Map.copyOf(immutable);
    }

    public record AccessMatrixRow(AccessFeature feature, String label, Map<AccessAction, UserClaim> claimsByAction) {
    }
}
