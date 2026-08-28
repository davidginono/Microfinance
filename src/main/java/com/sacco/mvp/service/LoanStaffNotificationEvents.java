package com.sacco.mvp.service;

import tools.jackson.databind.JsonNode;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.OutboxEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class LoanStaffNotificationEvents {
    static final String DETAIL_LOAN_ID = "loanId";
    static final String DETAIL_REVIEW_STAGE = "reviewStage";
    static final String DETAIL_REVIEWER_MEMBER_ID = "reviewerMemberId";

    private static final Map<String, ApprovalWorkflowStage> REVIEW_ASSIGNMENT_EVENTS = Map.of(
        "LOAN_READY_FOR_MANAGER", ApprovalWorkflowStage.MANAGER,
        "LOAN_OFFICER_REVIEW_ASSIGNED", ApprovalWorkflowStage.LOAN_OFFICER,
        "CHAIRPERSON_REVIEW_ASSIGNED", ApprovalWorkflowStage.CHAIRPERSON,
        "BOARD_REVIEW_ASSIGNED", ApprovalWorkflowStage.BOARD,
        "CREDIT_COMMITTEE_REVIEW_ASSIGNED", ApprovalWorkflowStage.CREDIT_COMMITTEE,
        "LOAN_READY_FOR_ACCOUNTANT", ApprovalWorkflowStage.ACCOUNTANT,
        "LOAN_READY_FOR_DISBURSEMENT", ApprovalWorkflowStage.DISBURSEMENT_OFFICER
    );

    private LoanStaffNotificationEvents() {
    }

    static boolean isReviewAssignmentEvent(String eventType) {
        return REVIEW_ASSIGNMENT_EVENTS.containsKey(eventType);
    }

    static ApprovalWorkflowStage reviewStage(String eventType) {
        return REVIEW_ASSIGNMENT_EVENTS.get(eventType);
    }

    static Map<String, Object> reviewAssignmentDetails(LoanApplication app,
                                                       ApprovalWorkflowStage stage,
                                                       UUID reviewerMemberId) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (app != null && app.getId() != null) {
            details.put(DETAIL_LOAN_ID, app.getId().toString());
        }
        if (stage != null) {
            details.put(DETAIL_REVIEW_STAGE, stage.name());
        }
        if (reviewerMemberId != null) {
            details.put(DETAIL_REVIEWER_MEMBER_ID, reviewerMemberId.toString());
        }
        return details;
    }

    static ReviewAssignmentContext reviewAssignmentContext(OutboxEvent event,
                                                           JsonNode payload,
                                                           UUID recipientId) {
        if (event == null || recipientId == null || !"LOAN".equals(event.getAggregateType())) {
            return null;
        }
        ApprovalWorkflowStage stage = reviewStage(event.getEventType());
        if (stage == null) {
            return null;
        }
        String loanId = detailTextOrNull(payload, DETAIL_LOAN_ID);
        if ((loanId == null || loanId.isBlank()) && event.getAggregateId() != null) {
            loanId = event.getAggregateId().toString();
        }
        if (loanId == null || loanId.isBlank()) {
            return null;
        }
        return new ReviewAssignmentContext(loanId, stage.name());
    }

    private static String detailTextOrNull(JsonNode payload, String field) {
        JsonNode details = payload == null ? null : payload.get("details");
        JsonNode value = details == null || details.isNull() ? null : details.get(field);
        String text = value == null || value.isNull() ? null : value.asString();
        return text == null || text.isBlank() ? null : text.trim();
    }

    record ReviewAssignmentContext(String loanId, String reviewStage) {
    }
}
