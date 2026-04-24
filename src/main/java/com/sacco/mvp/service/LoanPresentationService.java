package com.sacco.mvp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanPaymentTransaction;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.integration.memberportal.LoanPaymentSummaryDto;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

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
            putMoney(display, "Principal (TZS)", resolvePrincipalAmount(raw));
            addFinancialRow(display, "Interest (TZS)", raw.get("interestAmount"));
            BigDecimal principalPlusInterest = resolvePrincipalPlusInterest(raw);
            if (principalPlusInterest != null) {
                display.put("Principal + Interest (TZS)", formatMoney(principalPlusInterest));
            }
            addFinancialRow(display, "Monthly Repayment Amount (TZS)", raw.get("monthlyRepaymentAmount"));
            return display;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private BigDecimal resolvePrincipalAmount(Map<String, Object> raw) {
        BigDecimal principalAmount = readBigDecimal(raw.get("principalAmount"));
        if (principalAmount != null) {
            return principalAmount;
        }
        BigDecimal totalBeforeInterest = readBigDecimal(raw.get("loanToBePaid"));
        if (totalBeforeInterest == null) {
            return null;
        }
        BigDecimal applicationFee = readBigDecimal(raw.get("applicationFee"));
        BigDecimal insuranceFee = readBigDecimal(raw.get("insuranceFee"));
        return totalBeforeInterest
            .subtract(applicationFee == null ? BigDecimal.ZERO : applicationFee)
            .subtract(insuranceFee == null ? BigDecimal.ZERO : insuranceFee)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolvePrincipalPlusInterest(Map<String, Object> raw) {
        BigDecimal principalPlusInterest = readBigDecimal(raw.get("principalPlusInterest"));
        if (principalPlusInterest != null) {
            return principalPlusInterest;
        }
        BigDecimal principalAmount = resolvePrincipalAmount(raw);
        BigDecimal interestAmount = readBigDecimal(raw.get("interestAmount"));
        if (principalAmount == null || interestAmount == null) {
            return null;
        }
        return principalAmount.add(interestAmount).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (!text.matches("-?\\d+(\\.\\d+)?")) {
            return null;
        }
        return new BigDecimal(text);
    }

    public List<Map<String, Object>> buildProgressItems(LoanApplication app) {
        if (app == null || app.getStatus() == null) {
            return List.of(progressItem("Draft", true, true));
        }

        List<String> labels = new ArrayList<>();
        boolean hasGuarantorStage = app.getRequiredGuarantors() != null && app.getRequiredGuarantors() > 0;

        labels.add("Draft");
        if (hasGuarantorStage) {
            labels.add("Awaiting Guarantors");
            labels.add("All Guarantors Approved");
        }
        labels.add("On Review By Manager");

        int currentIndex;
        switch (app.getStatus()) {
            case DRAFT -> currentIndex = labels.size() - 1;
            case SUBMITTED, AWAITING_GUARANTORS -> currentIndex = hasGuarantorStage ? 1 : labels.size() - 1;
            case ALL_GUARANTORS_APPROVED -> currentIndex = hasGuarantorStage ? 2 : labels.size() - 1;
            case READY_FOR_MANAGER -> currentIndex = labels.size() - 1;
            case MANAGER_REJECTED -> {
                labels.add("Manager Rejected");
                currentIndex = labels.size() - 1;
            }
            default -> {
                labels.add("On Review By Board");
                switch (app.getStatus()) {
                    case MANAGER_ACCEPTED, AWAITING_BOARD -> currentIndex = labels.size() - 1;
                    case BOARD_REJECTED -> {
                        labels.add("Board Rejected");
                        currentIndex = labels.size() - 1;
                    }
                    default -> {
                        labels.add("Board Approved");
                        switch (app.getStatus()) {
                            case BOARD_APPROVED -> currentIndex = labels.size() - 1;
                            case FINAL_REJECTED -> {
                                labels.add("Final Rejected");
                                currentIndex = labels.size() - 1;
                            }
                            case FINAL_APPROVED -> {
                                labels.add("Disbursed Loan");
                                currentIndex = labels.size() - 1;
                            }
                            case DEFAULTED -> {
                                labels.add("Disbursed Loan");
                                labels.add("Defaulted");
                                currentIndex = labels.size() - 1;
                            }
                            case PAID -> {
                                labels.add("Disbursed Loan");
                                labels.add("Paid");
                                currentIndex = labels.size() - 1;
                            }
                            default -> currentIndex = labels.size() - 1;
                        }
                    }
                }
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            items.add(progressItem(labels.get(i), i <= currentIndex, i == currentIndex));
        }
        return items;
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
        return parseRepaymentSummary(json, null);
    }

    public Map<String, Object> parseRepaymentSummary(String json, java.time.OffsetDateTime paidAt) {
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
            if (paidAt != null) {
                display.put("Paid At", formatTimestamp(paidAt));
            }
            return display;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public LoanPaymentSummaryView parseLoanPaymentSummaryView(String json) {
        if (json == null || json.isBlank()) {
            return LoanPaymentSummaryView.empty();
        }
        try {
            LoanPaymentSummaryDto summary = objectMapper.readValue(json, LoanPaymentSummaryDto.class);
            return new LoanPaymentSummaryView(
                true,
                blankToDash(summary.loanDescription()),
                summary.lastPaymentDate(),
                blankToDash(summary.lastPaymentDate()),
                summary.totalOutstanding(),
                formatNullableMoney(summary.totalOutstanding()),
                formatNullableMoney(summary.outstandingPrincipal()),
                formatNullableMoney(summary.outstandingInterest()),
                formatNullableMoney(summary.totalPrincipalPaid()),
                formatNullableMoney(summary.totalInterestPaid())
            );
        } catch (Exception ex) {
            return LoanPaymentSummaryView.empty();
        }
    }

    public List<Map<String, Object>> parseRepaymentRows(String json) {
        return parseRepaymentRows(json, Collections.emptyList());
    }

    /**
     * Builds the view rows for the installment schedule, enriched with any
     * payment transactions whose {@code receiptDate} falls in the same
     * calendar month as the installment's {@code dueDate}.
     */
    public List<Map<String, Object>> parseRepaymentRows(String json, List<LoanPaymentTransaction> transactions) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Object scheduleObject = raw.get("schedule");
            if (!(scheduleObject instanceof List<?> schedule)) {
                return Collections.emptyList();
            }
            Map<YearMonth, PaidBucket> paidByMonth = bucketTransactionsByMonth(transactions);

            List<Map<String, Object>> rows = new java.util.ArrayList<>();
            for (Object entry : schedule) {
                if (!(entry instanceof Map<?, ?> item)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                Object installmentNumber = item.get("installmentNumber");
                row.put("installment", installmentNumber == null ? "-" : "Installment " + installmentNumber);
                Object dueDateValue = item.get("dueDate");
                row.put("dueDate", dueDateValue);
                row.put("amount", formatMoneyValue(item.get("amount")));

                PaidBucket bucket = lookupBucket(paidByMonth, dueDateValue);
                row.put("principalPaid", bucket == null ? "-" : formatMoney(bucket.principal));
                row.put("interestPaid", bucket == null ? "-" : formatMoney(bucket.interest));
                row.put("totalPaid", bucket == null ? "-" : formatMoney(bucket.total));
                row.put("paymentDate", bucket == null || bucket.lastDate == null ? "-" : bucket.lastDate.toString());

                String scheduledStatus = String.valueOf(item.get("status"));
                row.put("status", humanizeValue(deriveStatus(scheduledStatus, item.get("amount"), bucket)));
                rows.add(row);
            }
            return rows;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private Map<YearMonth, PaidBucket> bucketTransactionsByMonth(List<LoanPaymentTransaction> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<YearMonth, PaidBucket> map = new LinkedHashMap<>();
        for (LoanPaymentTransaction txn : transactions) {
            if (txn == null || txn.getReceiptDate() == null) {
                continue;
            }
            YearMonth key = YearMonth.from(txn.getReceiptDate());
            PaidBucket bucket = map.computeIfAbsent(key, k -> new PaidBucket());
            bucket.add(txn);
        }
        return map;
    }

    private PaidBucket lookupBucket(Map<YearMonth, PaidBucket> paidByMonth, Object dueDateValue) {
        if (paidByMonth.isEmpty() || dueDateValue == null) {
            return null;
        }
        String raw = String.valueOf(dueDateValue);
        if (raw.length() < 7) {
            return null;
        }
        try {
            YearMonth key = YearMonth.parse(raw.substring(0, 7));
            return paidByMonth.get(key);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String deriveStatus(String scheduledStatus, Object scheduledAmount, PaidBucket bucket) {
        if (bucket == null || bucket.total.signum() <= 0) {
            return scheduledStatus;
        }
        BigDecimal scheduled = toBigDecimal(scheduledAmount);
        if (scheduled != null && bucket.total.compareTo(scheduled) >= 0) {
            return "PAID";
        }
        return "PARTIAL";
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        String str = String.valueOf(value).trim();
        if (str.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(str);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static final class PaidBucket {
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        LocalDate lastDate;

        void add(LoanPaymentTransaction txn) {
            if (txn.getPrincipalPaid() != null) {
                principal = principal.add(txn.getPrincipalPaid());
            }
            if (txn.getInterestPaid() != null) {
                interest = interest.add(txn.getInterestPaid());
            }
            if (txn.getTotalPaid() != null) {
                total = total.add(txn.getTotalPaid());
            }
            if (lastDate == null || txn.getReceiptDate().isAfter(lastDate)) {
                lastDate = txn.getReceiptDate();
            }
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
                                     String saccoName,
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
        String printableSaccoName = saccoName == null || saccoName.isBlank() ? "SACCO" : saccoName.trim();
        html.append("<h1>").append(esc(printableSaccoName)).append(" Loan Application</h1>");
        html.append("<p class=\"meta\"><strong>Loan Application ID:</strong> ")
            .append(app.getApplicationNumber() == null ? "-" : app.getApplicationNumber().toString())
            .append("</p>");
        if (app.getLoanId() != null && !app.getLoanId().isBlank()) {
            html.append("<p class=\"meta\"><strong>Loan ID:</strong> ").append(esc(app.getLoanId())).append("</p>");
        }
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

    private Map<String, Object> progressItem(String label, boolean active, boolean current) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("active", active);
        item.put("current", current);
        return item;
    }

    private void appendGuarantorSummary(StringBuilder html,
                                        List<GuarantorRequest> guarantorRequests,
                                        Map<UUID, String> guarantorNames) {
        html.append("<h2>Guarantor Summary</h2>");
        html.append("<table><thead><tr><th>Guarantor</th><th>Status</th><th>Signature</th><th>Verified At</th></tr></thead><tbody>");
        if (guarantorRequests == null || guarantorRequests.isEmpty()) {
            html.append("<tr><td colspan=\"4\">No guarantor details available.</td></tr>");
        } else {
            for (GuarantorRequest request : guarantorRequests) {
                String name = guarantorNames == null ? null : guarantorNames.get(request.getGuarantorMemberId());
                html.append("<tr><td>")
                    .append(esc(name == null || name.isBlank() ? "Guarantor" : name))
                    .append("</td><td>")
                    .append(esc(humanizeValue(request.getStatus())))
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

    private String formatNullableMoney(BigDecimal amount) {
        return amount == null ? "-" : formatMoney(amount);
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

    private String blankToDash(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "-" : text;
    }

    public record LoanPaymentSummaryView(
        boolean available,
        String loanDescription,
        LocalDate lastPaymentDate,
        String lastPaymentDateLabel,
        BigDecimal totalOutstanding,
        String totalOutstandingLabel,
        String outstandingPrincipalLabel,
        String outstandingInterestLabel,
        String totalPrincipalPaidLabel,
        String totalInterestPaidLabel
    ) {
        public static LoanPaymentSummaryView empty() {
            return new LoanPaymentSummaryView(false, "-", null, "-", null, "-", "-", "-", "-", "-");
        }
    }

}
