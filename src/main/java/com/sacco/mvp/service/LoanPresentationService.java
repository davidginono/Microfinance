package com.sacco.mvp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.repository.ManagerReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class LoanPresentationService {
    private final ObjectMapper objectMapper;
    private final ManagerReviewRepository managerReviewRepository;
    private final LoanAttachmentService loanAttachmentService;

    public Map<String, Object> parseFormFields(String json) {
        return parseNamedMap(json, Set.of("_csrf", "financialSnapshotJson", "purpose", "nationalId", "employerName", "hasExistingLoan", "additionalNotes"));
    }

    public Map<String, Object> parseFinancialFields(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> display = new LinkedHashMap<>();
            addFinancialRow(display, "Application Fee (TZS)", raw.get("applicationFee"));
            addFinancialRow(display, "Insurance Fee (TZS)", raw.get("insuranceFee"));
            addFinancialRow(display, "Principal (TZS) (Loan Amount + Application Costs)", raw.get("loanToBePaid"));
            Object loanPlusInterest = raw.get("loanPlusInterest");
            Object interestAmount = raw.get("interestAmount");
            if (loanPlusInterest != null && String.valueOf(loanPlusInterest).matches("-?\\d+(\\.\\d+)?")) {
                String combined = formatMoney(new BigDecimal(String.valueOf(loanPlusInterest)));
                if (interestAmount != null && String.valueOf(interestAmount).matches("-?\\d+(\\.\\d+)?")) {
                    combined += " (Interest: " + formatMoney(new BigDecimal(String.valueOf(interestAmount))) + ")";
                }
                display.put("Principal + Interest (TZS)", combined);
            }
            addFinancialRow(display, "Monthly Repayment Amount (TZS)", raw.get("monthlyRepaymentAmount"));
            return display;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public List<Map<String, Object>> parseAttachments(String json) {
        List<Map<String, Object>> raw = loanAttachmentService.parse(json);
        for (Map<String, Object> item : raw) {
            Object size = item.get("size");
            if (size != null) {
                item.put("sizeLabel", humanSize(Long.parseLong(String.valueOf(size))));
            }
        }
        return raw;
    }

    public Map<String, Object> parseRepaymentSummary(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> display = new LinkedHashMap<>();
            putValue(display, "Disbursement Date", raw.get("disbursementDate"));
            putValue(display, "First Repayment Date", raw.get("firstRepaymentDate"));
            putValue(display, "Final Due Date", raw.get("finalDueDate"));
            putValue(display, "Repayment Frequency", humanizeValue(raw.get("repaymentFrequency")));
            putMoney(display, "Installment Amount", raw.get("installmentAmount"));
            putValue(display, "Installments", raw.get("installments"));
            putValue(display, "Disbursement Reference", raw.get("disbursementReference"));
            putValue(display, "Manager Notes", raw.get("disbursementNotes"));
            putValue(display, "Paid At", raw.get("paidAt"));
            return display;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public List<Map<String, Object>> parseRepaymentRows(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Object scheduleObject = raw.get("schedule");
            if (!(scheduleObject instanceof List<?> schedule)) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> rows = new java.util.ArrayList<>();
            for (Object entry : schedule) {
                if (!(entry instanceof Map<?, ?> item)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                Object installmentNumber = item.get("installmentNumber");
                row.put("installment", installmentNumber == null ? "-" : "Installment " + installmentNumber);
                row.put("dueDate", item.get("dueDate"));
                row.put("amount", formatMoneyValue(item.get("amount")));
                row.put("status", humanizeValue(item.get("status")));
                rows.add(row);
            }
            return rows;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public String countdownLabel(LocalDate finalDueDate) {
        if (finalDueDate == null) {
            return "";
        }
        long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), finalDueDate);
        if (daysLeft > 1) {
            return daysLeft + " days left";
        }
        if (daysLeft == 1) {
            return "1 day left";
        }
        if (daysLeft == 0) {
            return "Final due date is today";
        }
        long overdue = Math.abs(daysLeft);
        return overdue == 1 ? "Overdue by 1 day" : "Overdue by " + overdue + " days";
    }

    public String formatMoneyDisplay(BigDecimal amount) {
        return formatMoney(amount);
    }

    public String latestManagerReason(UUID loanId) {
        return managerReviewRepository.findFirstByLoanApplicationIdOrderByCreatedAtDesc(loanId)
            .filter(review -> review.getDecision() == ManagerDecision.REJECT)
            .map(ManagerReview::getReasons)
            .filter(reason -> reason != null && !reason.isBlank())
            .orElse("");
    }

    public Map<UUID, String> latestManagerReasons(Collection<LoanApplication> apps) {
        if (apps == null || apps.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, LoanApplication> appById = apps.stream()
            .collect(java.util.stream.Collectors.toMap(LoanApplication::getId, app -> app));
        Map<UUID, String> reasons = new LinkedHashMap<>();
        Set<UUID> processed = new java.util.HashSet<>();
        List<ManagerReview> reviews = managerReviewRepository.findByLoanApplicationIdInOrderByCreatedAtDesc(
            apps.stream().map(LoanApplication::getId).toList());
        for (ManagerReview review : reviews) {
            if (!processed.add(review.getLoanApplicationId())) {
                continue;
            }
            LoanApplication app = appById.get(review.getLoanApplicationId());
            if (app != null
                && app.getStatus().name().equals("MANAGER_REJECTED")
                && review.getDecision() == ManagerDecision.REJECT
                && review.getReasons() != null
                && !review.getReasons().isBlank()) {
                reasons.put(review.getLoanApplicationId(), review.getReasons());
            }
        }
        return reasons;
    }

    public String buildPrintableHtml(LoanApplication app,
                                     Member applicant,
                                     Map<String, Object> formFields,
                                     Map<String, Object> financialFields,
                                     List<GuarantorRequest> guarantorRequests,
                                     Map<UUID, String> guarantorNames,
                                     List<BoardReview> boardReviews,
                                     Map<UUID, Member> boardMembers,
                                     String managerReason) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        html.append("<title>Loan Application</title>");
        html.append("<style>");
        html.append("body{font-family:Arial,sans-serif;margin:24px;color:#172033;}h1,h2{margin-bottom:8px;}table{width:100%;border-collapse:collapse;margin-top:12px;}th,td{border:1px solid #d8dee8;padding:8px;text-align:left;vertical-align:top;}th{background:#f4f6f8;} .meta{margin:4px 0;} .note{margin-top:12px;padding:10px;background:#f7f4ee;border:1px solid #e7d6ca;} .signature-box{margin-top:18px;padding:16px;border:1px solid #d8dee8;background:#f8fafc;} .signature-text{margin-top:8px;font-family:'Brush Script MT','Segoe Script','Lucida Handwriting',cursive;font-size:54px;line-height:1.05;color:#0f172a;} .muted{color:#5b6b82;font-size:12px;}");
        html.append("</style></head><body>");
        html.append("<h1>IAA SACCOS Loan Application</h1>");
        html.append("<p class=\"meta\"><strong>Loan Id:</strong> ").append(shortId(app.getId())).append("</p>");
        html.append("<p class=\"meta\"><strong>Applicant:</strong> ").append(esc(applicant.getFullName())).append(" (")
            .append(esc(applicant.getMemberNo())).append(")</p>");
        html.append("<p class=\"meta\"><strong>Loan Type:</strong> ").append(esc(String.valueOf(app.getLoanType()))).append("</p>");
        html.append("<p class=\"meta\"><strong>Amount:</strong> ").append(esc(formatMoney(app.getAmount()))).append("</p>");
        html.append("<p class=\"meta\"><strong>Status:</strong> ").append(esc(String.valueOf(app.getStatus()))).append("</p>");
        if (managerReason != null && !managerReason.isBlank()) {
            html.append("<div class=\"note\"><strong>Manager Reason:</strong> ").append(esc(managerReason)).append("</div>");
        }
        appendTable(html, "Application Details", formFields);
        appendTable(html, "SACCO Financial Details", financialFields);
        appendTable(html, "Repayment Summary", parseRepaymentSummary(app.getRepaymentScheduleJson()));

        List<Map<String, Object>> repaymentRows = parseRepaymentRows(app.getRepaymentScheduleJson());
        if (!repaymentRows.isEmpty()) {
            Map<String, Object> repaymentTable = new LinkedHashMap<>();
            for (Map<String, Object> row : repaymentRows) {
                repaymentTable.put(String.valueOf(row.get("installment")),
                    row.get("dueDate") + " - " + row.get("amount") + " - " + row.get("status"));
            }
            appendTable(html, "Repayment Timetable", repaymentTable);
        }

        appendGuarantorSummary(html, guarantorRequests, guarantorNames);
        appendBoardCommitteeSummary(html, boardReviews, boardMembers);
        appendApplicantSignature(html, app, applicant);
        html.append("</body></html>");
        return html.toString();
    }

    private void appendApplicantSignature(StringBuilder html, LoanApplication app, Member applicant) {
        String signatureText = app.getApplicantSignatureText();
        if ((signatureText == null || signatureText.isBlank()) && applicant != null) {
            signatureText = applicant.getSignatureText();
        }
        if (signatureText == null || signatureText.isBlank()) {
            return;
        }
        html.append("<div class=\"signature-box\">");
        html.append("<h2>Applicant Signature</h2>");
        html.append("<div class=\"signature-text\">").append(esc(signatureText)).append("</div>");
        if (applicant != null) {
            html.append("<p class=\"meta\"><strong>Member Number:</strong> ").append(esc(applicant.getMemberNo())).append("</p>");
        }
        if (app.getApplicantSignatureVerifiedAt() != null) {
            html.append("<p class=\"meta\"><strong>Verified At:</strong> ")
                .append(esc(app.getApplicantSignatureVerifiedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))))
                .append("</p>");
        }
        html.append("</div>");
    }

    private void appendGuarantorSummary(StringBuilder html,
                                        List<GuarantorRequest> guarantorRequests,
                                        Map<UUID, String> guarantorNames) {
        html.append("<h2>Guarantor Summary</h2>");
        html.append("<table><thead><tr><th>Guarantor</th><th>Status</th><th>Committed Amount</th><th>Signature</th><th>Verified At</th></tr></thead><tbody>");
        if (guarantorRequests == null || guarantorRequests.isEmpty()) {
            html.append("<tr><td colspan=\"5\">No guarantor details available.</td></tr>");
        } else {
            for (GuarantorRequest request : guarantorRequests) {
                String name = guarantorNames == null ? null : guarantorNames.get(request.getGuarantorMemberId());
                String amount = request.getCommittedAmount() == null
                    ? (request.getRequestedAmount() == null ? "-" : formatMoney(request.getRequestedAmount()))
                    : formatMoney(request.getCommittedAmount());
                html.append("<tr><td>")
                    .append(esc(name == null || name.isBlank() ? "Guarantor" : name))
                    .append("</td><td>")
                    .append(esc(humanizeValue(request.getStatus())))
                    .append("</td><td>")
                    .append(esc(amount))
                    .append("</td><td>");
                if (request.getStatus() == com.sacco.mvp.domain.GuarantorRequestStatus.APPROVED
                    && request.getGuarantorSignatureText() != null
                    && !request.getGuarantorSignatureText().isBlank()) {
                    html.append("<div class=\"signature-text\" style=\"font-size:34px;\">")
                        .append(esc(request.getGuarantorSignatureText()))
                        .append("</div>");
                } else {
                    html.append("-");
                }
                html.append("</td><td>")
                    .append(esc(formatTimestamp(request.getGuarantorSignatureVerifiedAt())))
                    .append("</td></tr>");
            }
        }
        html.append("</tbody></table>");
    }

    private void appendBoardCommitteeSummary(StringBuilder html,
                                             List<BoardReview> boardReviews,
                                             Map<UUID, Member> boardMembers) {
        html.append("<h2>Board Committee Assessors</h2>");
        html.append("<table><thead><tr><th>Assessor</th><th>Member Number</th><th>Decision</th><th>Comment</th><th>Decision Date</th><th>Signature</th><th>Verified At</th></tr></thead><tbody>");
        if (boardReviews == null || boardReviews.isEmpty()) {
            html.append("<tr><td colspan=\"7\">No board assessor details available.</td></tr>");
        } else {
            for (BoardReview review : boardReviews) {
                Member boardMember = boardMembers == null ? null : boardMembers.get(review.getBoardMemberId());
                html.append("<tr><td>")
                    .append(esc(boardMember == null ? shortId(review.getBoardMemberId()) : boardMember.getFullName()))
                    .append("</td><td>")
                    .append(esc(boardMember == null ? "-" : boardMember.getMemberNo()))
                    .append("</td><td>")
                    .append(esc(humanizeValue(review.getDecision())))
                    .append("</td><td>")
                    .append(esc(review.getComment() == null || review.getComment().isBlank() ? "-" : review.getComment()))
                    .append("</td><td>")
                    .append(esc(formatTimestamp(review.getDecidedAt())))
                    .append("</td><td>");
                if (review.getDecision() == BoardDecision.APPROVED
                    && review.getBoardSignatureText() != null
                    && !review.getBoardSignatureText().isBlank()) {
                    html.append("<div class=\"signature-text\" style=\"font-size:34px;\">")
                        .append(esc(review.getBoardSignatureText()))
                        .append("</div>");
                } else {
                    html.append("-");
                }
                html.append("</td><td>")
                    .append(esc(formatTimestamp(review.getBoardSignatureVerifiedAt())))
                    .append("</td></tr>");
            }
        }
        html.append("</tbody></table>");
    }

    private Map<String, Object> parseNamedMap(String json, Set<String> hiddenKeys) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (entry.getKey() == null || hiddenKeys.contains(entry.getKey())) {
                    continue;
                }
                cleaned.put(humanizeFieldLabel(entry.getKey()), prettyValue(entry.getKey(), entry.getValue()));
            }
            return cleaned;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private void addFinancialRow(Map<String, Object> display, String label, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        if (label.contains("(TZS)") && value instanceof Number) {
            display.put(label, formatMoney(new BigDecimal(String.valueOf(value))));
            return;
        }
        if (label.contains("(TZS)") && value instanceof String stringValue && stringValue.matches("-?\\d+(\\.\\d+)?")) {
            display.put(label, formatMoney(new BigDecimal(stringValue)));
            return;
        }
        display.put(label, value);
    }

    private void putValue(Map<String, Object> display, String label, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return;
        }
        display.put(label, value);
    }

    private void putMoney(Map<String, Object> display, String label, Object value) {
        String formatted = formatMoneyValue(value);
        if (!formatted.isBlank()) {
            display.put(label, formatted);
        }
    }

    private Object prettyValue(String key, Object value) {
        return value;
    }

    private String humanizeFieldLabel(String key) {
        return switch (key) {
            case "nationalId" -> "National ID";
            case "employerName" -> "Employer Name";
            case "additionalNotes" -> "Additional Notes";
            case "hasExistingLoan" -> "Existing Loan";
            default -> key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        };
    }

    private String formatMoney(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.US));
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        return "TSh " + format.format(safe);
    }

    private String formatTimestamp(java.time.OffsetDateTime timestamp) {
        return timestamp == null ? "-" : timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String formatMoneyValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return "";
        }
        String stringValue = String.valueOf(value);
        if (!stringValue.matches("-?\\d+(\\.\\d+)?")) {
            return stringValue;
        }
        return formatMoney(new BigDecimal(stringValue));
    }

    private String humanizeValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return "";
        }
        String[] parts = text.replace('_', ' ').toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.US, "%.1f KB", kb);
        }
        return String.format(Locale.US, "%.1f MB", kb / 1024.0);
    }

    private void appendTable(StringBuilder html, String title, Map<String, Object> rows) {
        html.append("<h2>").append(esc(title)).append("</h2>");
        html.append("<table><thead><tr><th>Field</th><th>Value</th></tr></thead><tbody>");
        if (rows == null || rows.isEmpty()) {
            html.append("<tr><td colspan=\"2\">No details available.</td></tr>");
        } else {
            for (Map.Entry<String, Object> entry : rows.entrySet()) {
                html.append("<tr><td>").append(esc(entry.getKey())).append("</td><td>")
                    .append(esc(String.valueOf(entry.getValue()))).append("</td></tr>");
            }
        }
        html.append("</tbody></table>");
    }

    private String shortId(UUID id) {
        return id == null ? "" : id.toString().substring(0, 8);
    }

    private String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

}
