package com.sacco.mvp.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.InterestMethod;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class LoanPresentationService {
    private static final String LEGACY_FEE_INSURANCE_RECEIPT_CATEGORY = "FEE_INSURANCE_RECEIPT";

    private final ObjectMapper objectMapper;
    private final ManagerReviewRepository managerReviewRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final MemberRepository memberRepository;
    private final LoanAttachmentService loanAttachmentService;
    private final LoanProductWorkflowService loanProductWorkflowService;
    private final MessageSource messageSource;

    public Map<String, Object> parseFormFields(String json) {
        return parseNamedMap(json, Set.of("_csrf", "financialSnapshotJson", "nationalId", "employerName", "hasExistingLoan", "additionalNotes"));
    }

    public Map<String, Object> parseFinancialFields(String json) {
        return parseFinancialFields(json, null, null);
    }

    public Map<String, Object> parseFinancialFields(LoanApplication app) {
        return app == null
            ? Collections.emptyMap()
            : parseFinancialFields(app.getFinancialSnapshot(), app.getAmount(), app.getTenorMonths());
    }

    public Map<String, Object> parseFinancialFields(String json, BigDecimal effectivePrincipal) {
        return parseFinancialFields(json, effectivePrincipal, null);
    }

    public Map<String, Map<String, Object>> parseFinancialFieldSections(String json) {
        return parseFinancialFieldSections(json, null, null);
    }

    public Map<String, Map<String, Object>> parseFinancialFieldSections(LoanApplication app) {
        return app == null
            ? Collections.emptyMap()
            : parseFinancialFieldSections(app.getFinancialSnapshot(), app.getAmount(), app.getTenorMonths());
    }

    public BigDecimal totalDeductions(LoanApplication app) {
        if (app == null || app.getFinancialSnapshot() == null || app.getFinancialSnapshot().isBlank()) {
            return BigDecimal.ZERO.setScale(2);
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(app.getFinancialSnapshot(), new TypeReference<>() {});
            BigDecimal total = readBigDecimal(raw.get("totalDeductions"));
            return total == null ? BigDecimal.ZERO.setScale(2) : total.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception ex) {
            return BigDecimal.ZERO.setScale(2);
        }
    }

    public List<Map<String, Object>> deductibleFeeRows(LoanApplication app) {
        if (app == null || app.getFinancialSnapshot() == null || app.getFinancialSnapshot().isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(app.getFinancialSnapshot(), new TypeReference<>() {});
            List<Map<String, Object>> rows = new ArrayList<>();
            addDeductibleFeeRow(rows, "Application Fee", raw.get("applicationFee"));
            addDeductibleFeeRow(rows, feeLabelWithRate("Insurance Fee", raw.get("insuranceRate")), raw.get("insuranceFee"));
            addDeductibleFeeRow(rows, feeLabelWithRate("Loan Processing Fee", raw.get("processingFeeRate")), raw.get("processingFee"));
            return rows;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private void addDeductibleFeeRow(List<Map<String, Object>> rows, String label, Object value) {
        BigDecimal amount = readBigDecimal(value);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("amount", amount.setScale(2, RoundingMode.HALF_UP));
        row.put("amountLabel", formatMoney(amount));
        rows.add(row);
    }

    private Map<String, Object> parseFinancialFields(String json, BigDecimal effectivePrincipal, Integer fallbackTenorMonths) {
        Map<String, Map<String, Object>> sections = parseFinancialFieldSections(json, effectivePrincipal, fallbackTenorMonths);
        if (sections.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> display = new LinkedHashMap<>();
        sections.values().forEach(display::putAll);
        return display;
    }

    private Map<String, Map<String, Object>> parseFinancialFieldSections(String json, BigDecimal effectivePrincipal, Integer fallbackTenorMonths) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> fees = new LinkedHashMap<>();
            Map<String, Object> calculations = new LinkedHashMap<>();
            addFinancialRow(fees, "Application Fee (TZS)", raw.get("applicationFee"));
            addFinancialRow(fees, feeLabelWithRate("Insurance Fee", raw.get("insuranceRate")), raw.get("insuranceFee"));
            addFinancialRow(fees, feeLabelWithRate("Loan Processing Fee", raw.get("processingFeeRate")), raw.get("processingFee"));
            addFinancialRow(fees, "Total Fees (TZS)", raw.get("totalDeductions"));
            BigDecimal principalAmount = effectivePrincipal == null ? resolvePrincipalAmount(raw) : effectivePrincipal;
            putMoney(calculations, "Loan Amount (TZS)", principalAmount);
            putValue(calculations, "Annual Interest Rate", formatPercentValue(raw.get("interestRate")));
            putValue(calculations, "Interest Method", humanizeInterestMethod(raw.get("interestMethod")));
            addFinancialRow(calculations, "Interest (TZS)", raw.get("interestAmount"));
            Integer tenorMonths = readInteger(raw.get("tenorMonths"));
            if (tenorMonths == null) {
                tenorMonths = readInteger(raw.get("numberOfPayments"));
            }
            if (tenorMonths == null) {
                tenorMonths = fallbackTenorMonths;
            }
            putValue(calculations, "Loan Period in Years", formatYears(tenorMonths));
            putValue(calculations, "Number of Payments", tenorMonths);
            BigDecimal principalPlusInterest = resolvePrincipalPlusInterest(raw, principalAmount);
            if (principalPlusInterest != null) {
                calculations.put("Loan Amount + Interest (TZS)", formatMoney(principalPlusInterest));
            }
            addFinancialRow(calculations, "Monthly Repayment Amount (TZS)", raw.get("monthlyRepaymentAmount"));
            Map<String, Map<String, Object>> sections = new LinkedHashMap<>();
            sections.put("Loan Calculations", calculations);
            sections.put("Loan Fees", fees);
            return sections;
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
        BigDecimal processingFee = readBigDecimal(raw.get("processingFee"));
        return totalBeforeInterest
            .subtract(applicationFee == null ? BigDecimal.ZERO : applicationFee)
            .subtract(insuranceFee == null ? BigDecimal.ZERO : insuranceFee)
            .subtract(processingFee == null ? BigDecimal.ZERO : processingFee)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolvePrincipalPlusInterest(Map<String, Object> raw) {
        BigDecimal principalAmount = resolvePrincipalAmount(raw);
        return resolvePrincipalPlusInterest(raw, principalAmount);
    }

    private BigDecimal resolvePrincipalPlusInterest(Map<String, Object> raw, BigDecimal principalAmount) {
        BigDecimal snapshotPrincipal = resolvePrincipalAmount(raw);
        BigDecimal principalPlusInterest = readBigDecimal(raw.get("principalPlusInterest"));
        if (principalPlusInterest != null
            && (principalAmount == null || snapshotPrincipal == null || principalAmount.compareTo(snapshotPrincipal) == 0)) {
            return principalPlusInterest;
        }
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

    private Integer readInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).intValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            return null;
        }
    }

    public List<Map<String, Object>> buildProgressItems(LoanApplication app) {
        if (app == null || app.getStatus() == null) {
            return List.of(progressItem("Draft", true, true));
        }

        List<String> labels = new ArrayList<>();
        boolean hasGuarantorStage = app.getRequiredGuarantors() != null && app.getRequiredGuarantors() > 0;
        LoanProductWorkflowService.WorkflowDefinition workflow = loanProductWorkflowService.resolveForApplication(app);

        labels.add("Draft");
        if (hasGuarantorStage) {
            labels.add("Awaiting Guarantors");
            labels.add("All Guarantors Approved");
        }
        workflow.stages().stream()
            .filter(stage -> stage != ApprovalWorkflowStage.DISBURSEMENT_OFFICER)
            .map(this::progressLabel)
            .forEach(labels::add);
        labels.add("Approved For Disbursement");

        int currentIndex = 0;
        switch (app.getStatus()) {
            case DRAFT -> currentIndex = 0;
            case SUBMITTED, AWAITING_GUARANTORS -> currentIndex = hasGuarantorStage ? 1 : Math.max(labels.size() - 1, 0);
            case ALL_GUARANTORS_APPROVED -> currentIndex = hasGuarantorStage ? 2 : Math.max(labels.size() - 1, 0);
            case READY_FOR_MANAGER -> currentIndex = indexOfLabel(labels, "On Review By Manager");
            case MANAGER_REJECTED -> {
                labels.add("Manager Rejected");
                currentIndex = labels.size() - 1;
            }
            case MANAGER_ACCEPTED -> currentIndex = nextConfiguredStageIndex(labels, "On Review By Manager");
            case AWAITING_LOAN_OFFICER -> currentIndex = indexOfLabel(labels, "On Review By Loan Officer");
            case LOAN_OFFICER_REJECTED -> {
                labels.add("Loan Officer Rejected");
                currentIndex = labels.size() - 1;
            }
            case LOAN_OFFICER_APPROVED -> currentIndex = nextConfiguredStageIndex(labels, "On Review By Loan Officer");
            case AWAITING_CHAIRPERSON -> currentIndex = indexOfLabel(labels, "On Review By Chairperson");
            case CHAIRPERSON_REJECTED -> {
                labels.add("Chairperson Rejected");
                currentIndex = labels.size() - 1;
            }
            case CHAIRPERSON_APPROVED -> currentIndex = nextConfiguredStageIndex(labels, "On Review By Chairperson");
            case AWAITING_BOARD -> currentIndex = indexOfLabel(labels, "On Review By Board");
            case AWAITING_CREDIT_COMMITTEE -> currentIndex = indexOfLabel(labels, "On Review By Credit Committee");
            case BOARD_REJECTED -> {
                labels.add("Board Rejected");
                currentIndex = labels.size() - 1;
            }
            case BOARD_APPROVED -> currentIndex = nextConfiguredStageIndex(labels, "On Review By Board");
            case CREDIT_COMMITTEE_REJECTED -> {
                labels.add("Credit Committee Rejected");
                currentIndex = labels.size() - 1;
            }
            case CREDIT_COMMITTEE_APPROVED -> currentIndex = nextConfiguredStageIndex(labels, "On Review By Credit Committee");
            case AWAITING_ACCOUNTANT -> currentIndex = indexOfLabel(labels, "On Review By Accountant");
            case ACCOUNTANT_REJECTED -> {
                labels.add("Accountant Rejected");
                currentIndex = labels.size() - 1;
            }
            case ACCOUNTANT_APPROVED, READY_FOR_DISBURSEMENT -> currentIndex = indexOfLabel(labels, "Approved For Disbursement");
            case REJECTED -> {
                labels.add("Rejected");
                currentIndex = labels.size() - 1;
            }
            case DISBURSED -> {
                labels.add("Disbursed");
                currentIndex = labels.size() - 1;
            }
            case DEFAULTED -> {
                labels.add("Disbursed");
                labels.add("Defaulted");
                currentIndex = labels.size() - 1;
            }
            case PAID -> {
                labels.add("Disbursed");
                labels.add("Paid");
                currentIndex = labels.size() - 1;
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            items.add(progressItem(progressDisplayLabel(labels.get(i)), i <= currentIndex, i == currentIndex));
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

    public List<Map<String, Object>> parseApplicationAttachments(String json) {
        return parseAttachments(json).stream()
            .filter(item -> !LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF.equals(attachmentCategory(item)))
            .filter(item -> !LEGACY_FEE_INSURANCE_RECEIPT_CATEGORY.equals(attachmentCategory(item)))
            .toList();
    }

    public List<Map<String, Object>> parseDisbursementProofAttachments(String json) {
        return parseAttachments(json).stream()
            .filter(item -> LoanAttachmentService.CATEGORY_DISBURSEMENT_PROOF.equals(attachmentCategory(item)))
            .toList();
    }

    public Map<String, Object> parseRepaymentSummary(String json) {
        return parseRepaymentSummary(json, null);
    }

    private String attachmentCategory(Map<String, Object> item) {
        Object category = item.get("attachmentCategory");
        return category == null ? "" : String.valueOf(category);
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
            putValue(display, "Interest Method", humanizeInterestMethod(raw.get("interestMethod")));
            putValue(display, "Interest Rate", formatPercentValue(raw.get("interestRate")));
            putMoney(display, "Disbursed Principal", raw.get("disbursedPrincipal"));
            putMoney(display, "Deposit Amount", raw.get("depositAmount"));
            putMoney(display, "Installment Amount", raw.get("installmentAmount"));
            putRepaymentScheduleTotals(display, raw);
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

    public Map<String, Object> reviewRepaymentSummary(LoanApplication app) {
        if (app == null) {
            return Collections.emptyMap();
        }
        if (app.getRepaymentScheduleJson() != null && !app.getRepaymentScheduleJson().isBlank()) {
            Map<String, Object> display = new LinkedHashMap<>(parseRepaymentSummary(app.getRepaymentScheduleJson(), app.getPaidAt()));
            putFinancialSnapshotRepaymentTotals(display, app);
            return display;
        }
        if (app.getFinancialSnapshot() == null || app.getFinancialSnapshot().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(app.getFinancialSnapshot(), new TypeReference<>() {});
            Map<String, Object> display = new LinkedHashMap<>();
            putMoney(display, "Loan Amount", app.getAmount());
            putValue(display, "Repayment Period", app.getTenorMonths() == null ? null : app.getTenorMonths() + " month(s)");
            putValue(display, "Interest Method", humanizeInterestMethod(raw.get("interestMethod")));
            putValue(display, "Annual Interest Rate", formatPercentValue(raw.get("interestRate")));
            putValue(display, "Loan Period in Years", formatYears(app.getTenorMonths()));
            putValue(display, "Number of Payments", app.getTenorMonths());
            putMoney(display, "Estimated Installment", raw.get("monthlyRepaymentAmount"));
            putMoney(display, "Total Interest", raw.get("interestAmount"));
            putMoney(display, "Total Principal", app.getAmount());
            putMoney(display, "Total Amount", raw.get("principalPlusInterest"));
            putMoney(display, "Estimated Total Repayment", raw.get("principalPlusInterest"));
            return display;
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    public Map<String, Object> repaymentSummaryForReview(LoanApplication app) {
        return reviewRepaymentSummary(app);
    }

    public boolean isEstimatedReviewRepaymentSummary(LoanApplication app) {
        return app != null
            && (app.getRepaymentScheduleJson() == null || app.getRepaymentScheduleJson().isBlank())
            && app.getFinancialSnapshot() != null
            && !app.getFinancialSnapshot().isBlank();
    }

    public List<Map<String, Object>> parseRepaymentRows(String json) {
        return parseRepaymentRowsInternal(json, Collections.emptyList());
    }

    public List<Map<String, Object>> calculatedRepaymentRows(LoanApplication app) {
        return calculatedRepaymentRowsFromSnapshot(app);
    }

    public List<Map<String, Object>> reviewRepaymentRows(LoanApplication app) {
        if (app == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> storedRows = parseRepaymentRowsInternal(
            app.getRepaymentScheduleJson(),
            calculatedRepaymentRowsFromSnapshot(app)
        );
        if (!storedRows.isEmpty()) {
            return storedRows;
        }
        return estimatedReviewRepaymentRows(app);
    }

    private List<Map<String, Object>> parseRepaymentRowsInternal(String json,
                                                                 List<Map<String, Object>> calculatedScheduleRows) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            Object scheduleObject = raw.get("schedule");
            if (!(scheduleObject instanceof List<?> schedule)) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> correctedScheduleRows =
                calculatedScheduleRows != null && calculatedScheduleRows.size() == schedule.size()
                    ? calculatedScheduleRows
                    : Collections.emptyList();

            List<Map<String, Object>> rows = new java.util.ArrayList<>();
            int rowIndex = 0;
            for (Object entry : schedule) {
                if (!(entry instanceof Map<?, ?> item)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                Object installmentNumber = item.get("installmentNumber");
                row.put("installment", installmentNumber == null ? "-" : "Installment " + installmentNumber);
                row.put("installmentNumber", installmentNumber == null ? "-" : String.valueOf(installmentNumber));
                row.put("pmtNo", installmentNumber == null ? "-" : String.valueOf(installmentNumber));
                Object dueDateValue = item.get("dueDate");
                row.put("dueDate", dueDateValue);
                row.put("amount", formatMoneyValue(item.get("amount")));
                row.put("payment", formatMoneyValue(item.get("amount")));
                row.put("loanAmount", formatMoneyValue(item.get("principalComponent")));
                row.put("interest", formatMoneyValue(item.get("interestComponent")));
                row.put("scheduledBreakdown", scheduledAmountBreakdown(item));
                Map<String, Object> correctedRow = !correctedScheduleRows.isEmpty() && rowIndex < correctedScheduleRows.size()
                    ? correctedScheduleRows.get(rowIndex)
                    : Collections.emptyMap();
                if (!correctedScheduleRows.isEmpty() && rowIndex < correctedScheduleRows.size()) {
                    applyCalculatedScheduleDisplay(row, correctedRow);
                }
                rows.add(row);
                rowIndex++;
            }
            return rows;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private void applyCalculatedScheduleDisplay(Map<String, Object> row, Map<String, Object> calculatedRow) {
        copyDisplayValue(row, calculatedRow, "amount");
        copyDisplayValue(row, calculatedRow, "payment");
        copyDisplayValue(row, calculatedRow, "loanAmount");
        copyDisplayValue(row, calculatedRow, "interest");
        copyDisplayValue(row, calculatedRow, "scheduledBreakdown");
    }

    private void copyDisplayValue(Map<String, Object> target, Map<String, Object> source, String key) {
        if (source == null) {
            return;
        }
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            target.put(key, value);
        }
    }

    private List<Map<String, Object>> estimatedReviewRepaymentRows(LoanApplication app) {
        if (!isEstimatedReviewRepaymentSummary(app) || app.getAmount() == null) {
            return Collections.emptyList();
        }
        return calculatedRepaymentRowsFromSnapshot(app);
    }

    private List<Map<String, Object>> calculatedRepaymentRowsFromSnapshot(LoanApplication app) {
        if (app == null || app.getAmount() == null || app.getFinancialSnapshot() == null || app.getFinancialSnapshot().isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(app.getFinancialSnapshot(), new TypeReference<>() {});
            BigDecimal principal = app.getAmount().setScale(2, RoundingMode.HALF_UP);
            int months = app.getTenorMonths() == null || app.getTenorMonths() <= 0 ? 1 : app.getTenorMonths();
            BigDecimal annualRate = toBigDecimal(raw.get("interestRate"));
            if (annualRate == null) {
                annualRate = BigDecimal.ZERO;
            }
            InterestMethod interestMethod = resolveInterestMethod(raw.get("interestMethod"));
            BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 12, RoundingMode.HALF_UP);
            BigDecimal flatTotalInterest = toBigDecimal(raw.get("interestAmount"));
            if (flatTotalInterest == null) {
                flatTotalInterest = principal.multiply(annualRate)
                    .multiply(BigDecimal.valueOf(months))
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            }
            BigDecimal flatPrincipalBase = principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
            BigDecimal flatInterestBase = flatTotalInterest.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
            BigDecimal reducingInstallment = toBigDecimal(raw.get("monthlyRepaymentAmount"));
            if (reducingInstallment == null) {
                reducingInstallment = reducingInstallment(principal, monthlyRate, months);
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            BigDecimal runningPrincipal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal runningInterest = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            BigDecimal remainingPrincipal = principal;
            for (int month = 1; month <= months; month++) {
                BigDecimal principalComponent;
                BigDecimal interestComponent;
                BigDecimal installmentAmount;
                BigDecimal beginningBalance = remainingPrincipal;
                if (interestMethod == InterestMethod.REDUCING_BALANCE) {
                    interestComponent = remainingPrincipal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
                    principalComponent = reducingInstallment.subtract(interestComponent).setScale(2, RoundingMode.HALF_UP);
                    if (principalComponent.compareTo(BigDecimal.ZERO) < 0) {
                        principalComponent = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    }
                    if (month == months) {
                        principalComponent = principal.subtract(runningPrincipal).setScale(2, RoundingMode.HALF_UP);
                        installmentAmount = principalComponent.add(interestComponent).setScale(2, RoundingMode.HALF_UP);
                    } else {
                        installmentAmount = reducingInstallment;
                    }
                } else {
                    principalComponent = month == months
                        ? principal.subtract(runningPrincipal).setScale(2, RoundingMode.HALF_UP)
                        : flatPrincipalBase;
                    interestComponent = month == months
                        ? flatTotalInterest.subtract(runningInterest).setScale(2, RoundingMode.HALF_UP)
                        : flatInterestBase;
                    installmentAmount = principalComponent.add(interestComponent).setScale(2, RoundingMode.HALF_UP);
                }

                runningPrincipal = runningPrincipal.add(principalComponent).setScale(2, RoundingMode.HALF_UP);
                runningInterest = runningInterest.add(interestComponent).setScale(2, RoundingMode.HALF_UP);
                remainingPrincipal = principal.subtract(runningPrincipal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("installment", "Installment " + month);
                row.put("installmentNumber", String.valueOf(month));
                row.put("pmtNo", String.valueOf(month));
                row.put("month", "Month " + month);
                row.put("beginningBalance", formatMoney(beginningBalance));
                row.put("dueDate", "-");
                row.put("amount", formatMoney(installmentAmount));
                row.put("payment", formatMoney(installmentAmount));
                row.put("loanAmount", formatMoney(principalComponent));
                row.put("interest", formatMoney(interestComponent));
                row.put("scheduledBreakdown", "Loan Amount: " + formatMoney(principalComponent) + "\nInterest: " + formatMoney(interestComponent));
                row.put("scheduledPrincipalAmount", principalComponent);
                row.put("scheduledInterestAmount", interestComponent);
                row.put("scheduledTotalAmount", installmentAmount);
                row.put("outstandingBalance", formatMoney(remainingPrincipal));
                row.put("endingBalance", formatMoney(remainingPrincipal));
                row.put("principalPaid", "-");
                row.put("interestPaid", "-");
                row.put("totalPaid", "-");
                row.put("paymentDate", "-");
                rows.add(row);
            }
            return rows;
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private InterestMethod resolveInterestMethod(Object value) {
        if (value == null) {
            return InterestMethod.FLAT_RATE;
        }
        try {
            return InterestMethod.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return InterestMethod.FLAT_RATE;
        }
    }

    private BigDecimal reducingInstallment(BigDecimal principal, BigDecimal monthlyRate, int months) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (monthlyRate == null || monthlyRate.compareTo(BigDecimal.ZERO) <= 0) {
            return principal.divide(BigDecimal.valueOf(Math.max(months, 1)), 2, RoundingMode.HALF_UP);
        }
        double rate = monthlyRate.doubleValue();
        double factor = 1d - Math.pow(1d + rate, -Math.max(months, 1));
        return BigDecimal.valueOf(principal.doubleValue() * rate / factor)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private int indexOfLabel(List<String> labels, String target) {
        int index = labels.indexOf(target);
        return index >= 0 ? index : Math.max(labels.size() - 1, 0);
    }

    private int nextConfiguredStageIndex(List<String> labels, String currentLabel) {
        int current = indexOfLabel(labels, currentLabel);
        if (current >= 0 && current + 1 < labels.size()) {
            return current + 1;
        }
        return Math.max(labels.size() - 1, 0);
    }

    private String progressLabel(ApprovalWorkflowStage stage) {
        return switch (stage) {
            case MANAGER -> "On Review By Manager";
            case LOAN_OFFICER -> "On Review By Loan Officer";
            case CHAIRPERSON -> "On Review By Chairperson";
            case BOARD -> "On Review By Board";
            case CREDIT_COMMITTEE -> "On Review By Credit Committee";
            case ACCOUNTANT -> "On Review By Accountant";
            case DISBURSEMENT_OFFICER -> "Approved For Disbursement";
        };
    }

    private String progressDisplayLabel(String label) {
        return switch (label) {
            case "Draft" -> message("loan.status.DRAFT");
            case "Awaiting Guarantors" -> message("loan.status.AWAITING_GUARANTORS");
            case "All Guarantors Approved" -> message("loan.status.ALL_GUARANTORS_APPROVED");
            case "On Review By Manager" -> message("loan.status.READY_FOR_MANAGER");
            case "Manager Rejected" -> message("loan.status.MANAGER_REJECTED");
            case "On Review By Loan Officer" -> message("loan.status.AWAITING_LOAN_OFFICER");
            case "Loan Officer Rejected" -> message("loan.status.LOAN_OFFICER_REJECTED");
            case "On Review By Chairperson" -> message("loan.status.AWAITING_CHAIRPERSON");
            case "Chairperson Rejected" -> message("loan.status.CHAIRPERSON_REJECTED");
            case "On Review By Board" -> message("loan.status.AWAITING_BOARD");
            case "On Review By Credit Committee" -> message("loan.status.AWAITING_CREDIT_COMMITTEE");
            case "Board Rejected" -> message("loan.status.BOARD_REJECTED");
            case "Credit Committee Rejected" -> message("loan.status.CREDIT_COMMITTEE_REJECTED");
            case "On Review By Accountant" -> message("loan.status.AWAITING_ACCOUNTANT");
            case "Accountant Rejected" -> message("loan.status.ACCOUNTANT_REJECTED");
            case "Approved For Disbursement" -> message("loan.status.READY_FOR_DISBURSEMENT");
            case "Rejected" -> message("loan.status.REJECTED");
            case "Disbursed" -> message("loan.status.DISBURSED");
            case "Defaulted" -> message("loan.status.DEFAULTED");
            case "Paid" -> message("loan.status.PAID");
            default -> label;
        };
    }

    private String message(String code) {
        return messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
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

    private String humanizeInterestMethod(Object value) {
        if (value == null) {
            return "-";
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty()) {
            return "-";
        }
        return switch (raw) {
            case "REDUCING_BALANCE" -> "Reducing Balance";
            case "FLAT_RATE" -> "Flat Rate";
            default -> humanizeValue(raw);
        };
    }

    private String formatPercentValue(Object value) {
        BigDecimal amount = toBigDecimal(value);
        if (amount == null) {
            return "-";
        }
        return amount.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String feeLabelWithRate(String label, Object rate) {
        BigDecimal amount = toBigDecimal(rate);
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return label + " (TZS)";
        }
        String percent = amount.multiply(BigDecimal.valueOf(100))
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
        return label + " (" + percent + "%)";
    }

    private String formatYears(Integer tenorMonths) {
        if (tenorMonths == null || tenorMonths <= 0) {
            return null;
        }
        BigDecimal years = BigDecimal.valueOf(tenorMonths)
            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
            .stripTrailingZeros();
        String suffix = BigDecimal.ONE.compareTo(years) == 0 ? " year" : " years";
        return years.toPlainString() + suffix;
    }

    private String scheduledAmountBreakdown(Map<?, ?> item) {
        String principal = formatMoneyValue(item.get("principalComponent"));
        String interest = formatMoneyValue(item.get("interestComponent"));
        boolean hasPrincipal = principal != null && !principal.isBlank() && !"-".equals(principal);
        boolean hasInterest = interest != null && !interest.isBlank() && !"-".equals(interest);
        if (!hasPrincipal && !hasInterest) {
            return "";
        }
        if (!hasInterest || "TSh 0".equals(interest)) {
            return "Principal: " + principal;
        }
        return "Principal: " + principal + "\nInterest: " + interest;
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) < 0
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : value.setScale(2, RoundingMode.HALF_UP);
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

    public List<ApprovedReviewSummary> previousApprovedReviews(LoanApplication app, ApprovalWorkflowStage currentStage) {
        if (app == null || app.getId() == null || currentStage == null) {
            return List.of();
        }
        List<ApprovalWorkflowStage> stages = loanProductWorkflowService.resolveForApplication(app).stages();
        int currentIndex = stages.indexOf(currentStage);
        if (currentIndex <= 0) {
            return List.of();
        }
        Set<ApprovalWorkflowStage> previousStages = new LinkedHashSet<>(stages.subList(0, currentIndex));
        List<ManagerReview> staffReviews = managerReviewRepository.findByLoanApplicationIdOrderByCreatedAtAsc(app.getId()).stream()
            .filter(review -> previousStages.contains(review.getReviewStage()))
            .filter(review -> review.getDecision() == ManagerDecision.ACCEPT)
            .toList();
        List<BoardReview> committeeReviews = boardReviewRepository.findByLoanApplicationId(app.getId()).stream()
            .filter(review -> previousStages.contains(review.getReviewStage()))
            .filter(review -> review.getDecision() == BoardDecision.APPROVED)
            .toList();
        Set<UUID> reviewerIds = new LinkedHashSet<>();
        staffReviews.stream().map(ManagerReview::getManagerMemberId).forEach(reviewerIds::add);
        committeeReviews.stream().map(BoardReview::getBoardMemberId).forEach(reviewerIds::add);
        Map<UUID, Member> reviewers = memberRepository.findAllById(reviewerIds).stream()
            .collect(java.util.stream.Collectors.toMap(Member::getId, member -> member));

        List<ApprovedReviewSummary> summaries = new ArrayList<>();
        staffReviews.forEach(review -> summaries.add(new ApprovedReviewSummary(
            stageLabel(review.getReviewStage()),
            reviewerName(review.getManagerMemberId(), reviewers),
            reviewerMemberNo(review.getManagerMemberId(), reviewers),
            message("review.approved"),
            normalizeReviewNote(review.getReasons()),
            formatTimestamp(review.getCreatedAt())
        )));
        committeeReviews.forEach(review -> summaries.add(new ApprovedReviewSummary(
            stageLabel(review.getReviewStage()),
            reviewerName(review.getBoardMemberId(), reviewers),
            reviewerMemberNo(review.getBoardMemberId(), reviewers),
            message("review.approved"),
            normalizeReviewNote(review.getComment()),
            formatTimestamp(review.getDecidedAt() == null ? review.getCreatedAt() : review.getDecidedAt())
        )));
        summaries.sort(Comparator.comparing(ApprovedReviewSummary::getDecidedAtLabel));
        return summaries;
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

    public List<DecisionFeedback> rejectionFeedback(UUID loanId) {
        if (loanId == null) {
            return List.of();
        }
        List<DecisionFeedback> feedback = new ArrayList<>();
        managerReviewRepository.findByLoanApplicationIdOrderByCreatedAtAsc(loanId).stream()
            .filter(review -> review.getDecision() == ManagerDecision.REJECT)
            .filter(review -> review.getReasons() != null && !review.getReasons().isBlank())
            .forEach(review -> feedback.add(new DecisionFeedback(
                review.getReviewStage() == null ? "Staff Review" : review.getReviewStage().getDisplayLabel(),
                review.getReasons(),
                review.getCreatedAt()
            )));
        boardReviewRepository.findByLoanApplicationId(loanId).stream()
            .filter(review -> review.getDecision() == BoardDecision.REJECTED)
            .filter(review -> review.getComment() != null && !review.getComment().isBlank())
            .forEach(review -> feedback.add(new DecisionFeedback(
                review.getReviewStage() == null ? "Board Review" : review.getReviewStage().getDisplayLabel(),
                review.getComment(),
                review.getDecidedAt() == null ? review.getCreatedAt() : review.getDecidedAt()
            )));
        feedback.sort(Comparator.comparing(DecisionFeedback::decidedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return feedback;
    }

    public Map<UUID, String> rejectionFeedbackReasons(Collection<LoanApplication> apps) {
        if (apps == null || apps.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, String> reasons = new LinkedHashMap<>();
        for (LoanApplication app : apps) {
            if (app == null || app.getId() == null) {
                continue;
            }
            if (!isRejectedStatus(app.getStatus())) {
                continue;
            }
            rejectionFeedback(app.getId()).stream()
                .findFirst()
                .ifPresent(feedback -> reasons.put(app.getId(), feedback.reason()));
        }
        return reasons;
    }

    private boolean isRejectedStatus(com.sacco.mvp.domain.LoanStatus status) {
        return status == com.sacco.mvp.domain.LoanStatus.MANAGER_REJECTED
            || status == com.sacco.mvp.domain.LoanStatus.LOAN_OFFICER_REJECTED
            || status == com.sacco.mvp.domain.LoanStatus.CHAIRPERSON_REJECTED
            || status == com.sacco.mvp.domain.LoanStatus.BOARD_REJECTED
            || status == com.sacco.mvp.domain.LoanStatus.CREDIT_COMMITTEE_REJECTED
            || status == com.sacco.mvp.domain.LoanStatus.ACCOUNTANT_REJECTED
            || status == com.sacco.mvp.domain.LoanStatus.REJECTED;
    }

    public record DecisionFeedback(String role, String reason, java.time.OffsetDateTime decidedAt) {
    }

    public static class ApprovedReviewSummary {
        private final String stageLabel;
        private final String reviewerName;
        private final String reviewerMemberNo;
        private final String decisionLabel;
        private final String note;
        private final String decidedAtLabel;

        public ApprovedReviewSummary(String stageLabel,
                                     String reviewerName,
                                     String reviewerMemberNo,
                                     String decisionLabel,
                                     String note,
                                     String decidedAtLabel) {
            this.stageLabel = stageLabel;
            this.reviewerName = reviewerName;
            this.reviewerMemberNo = reviewerMemberNo;
            this.decisionLabel = decisionLabel;
            this.note = note;
            this.decidedAtLabel = decidedAtLabel;
        }

        public String getStageLabel() {
            return stageLabel;
        }

        public String getReviewerName() {
            return reviewerName;
        }

        public String getReviewerMemberNo() {
            return reviewerMemberNo;
        }

        public String getDecisionLabel() {
            return decisionLabel;
        }

        public String getNote() {
            return note;
        }

        public String getDecidedAtLabel() {
            return decidedAtLabel;
        }
    }

    public String buildPrintableHtml(LoanApplication app,
                                     String saccoName,
                                     Member applicant,
                                     Map<String, Object> formFields,
                                     Map<String, Object> financialFields,
                                     List<GuarantorRequest> guarantorRequests,
                                     Map<UUID, String> guarantorNames,
                                     Map<UUID, String> guarantorMemberNumbers,
                                     List<ManagerReview> staffReviews,
                                     Map<UUID, Member> staffReviewers,
                                     List<BoardReview> boardReviews,
                                     Map<UUID, Member> boardMembers,
                                     String managerReason) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\">");
        html.append("<title>Loan Application</title>");
        html.append("<style>");
        html.append("body{font-family:Arial,sans-serif;margin:24px;color:#172033;}h1{margin-bottom:8px;}h2{margin:18px 0 0;padding:8px 10px;border:1px solid #d8dee8;background:#f4f6f8;font-size:16px;}h2.super-title{background:#475569;color:#fff;border-color:#64748b;font-size:17px;}h2.super-title .helper{float:right;font-size:12px;font-weight:400;color:#f4f6f8;}table{width:100%;border-collapse:collapse;margin-top:0;}th,td{border:1px solid #d8dee8;padding:8px;text-align:left;vertical-align:top;}th{background:#f4f6f8;}tr{break-inside:avoid;page-break-inside:avoid;}thead{display:table-header-group;}h2,thead{break-after:avoid;page-break-after:avoid;} .print-section{break-inside:avoid;page-break-inside:avoid;margin-top:12px;} .meta{margin:4px 0;} .note{margin-top:12px;padding:10px;background:#f7f4ee;border:1px solid #e7d6ca;} .signature-box{margin-top:18px;padding:0 16px 16px;border:1px solid #d8dee8;background:#f8fafc;break-inside:avoid;page-break-inside:avoid;} .signature-text{margin-top:8px;font-family:'Times New Roman',Times,serif;font-style:italic;font-size:34px;line-height:1.05;color:#0f172a;} .muted{color:#5b6b82;font-size:12px;}@media print{table{page-break-inside:auto;}tr{page-break-inside:avoid;page-break-after:auto;}.print-section{page-break-inside:avoid;}}");
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
        appendSuperTitle(html, "Supporting Application Details", "System-record detail");
        appendTable(html, "Application Details", formFields);
        appendAttachmentTable(html, "Disbursement Proof", parseDisbursementProofAttachments(app.getAttachmentsJson()));
        appendTable(html, "Loan Calculation Details", financialFields);

        List<Map<String, Object>> calculatedRows = printableCalculatedRepaymentRows(app);
        if (!calculatedRows.isEmpty()) {
            Map<String, Object> repaymentTable = new LinkedHashMap<>();
            for (Map<String, Object> row : calculatedRows) {
                repaymentTable.put(printableRepaymentRowValue(row, "pmtNo", "installmentNumber"),
                    printableRepaymentRowValue(row, "month", "dueDate")
                        + " - " + printableRepaymentRowValue(row, "payment", "amount")
                        + " - " + printableRepaymentRowValue(row, "endingBalance", "outstandingBalance"));
            }
            appendTable(html, "Repayment Schedule", repaymentTable);
        }

        appendGuarantorSummary(html, guarantorRequests, guarantorNames, guarantorMemberNumbers);
        appendStaffReviewSummary(html, staffReviews, staffReviewers);
        appendBoardCommitteeSummary(html, boardReviews, boardMembers);
        appendApplicantSignature(html, app, applicant);
        html.append("</body></html>");
        return html.toString();
    }

    public byte[] buildPrintablePdf(LoanApplication app,
                                    String saccoName,
                                    Member applicant,
                                    Map<String, Object> formFields,
                                    Map<String, Object> financialFields,
                                    List<GuarantorRequest> guarantorRequests,
                                    Map<UUID, String> guarantorNames,
                                    Map<UUID, String> guarantorMemberNumbers,
                                    List<ManagerReview> staffReviews,
                                    Map<UUID, Member> staffReviewers,
                                    List<BoardReview> boardReviews,
                                    Map<UUID, Member> boardMembers,
                                    String managerReason,
                                    boolean includeRecordedSignatures) {
        return buildPrintablePdf(
            app,
            saccoName,
            applicant,
            null,
            null,
            formFields,
            financialFields,
            guarantorRequests,
            guarantorNames,
            guarantorMemberNumbers,
            staffReviews,
            staffReviewers,
            boardReviews,
            boardMembers,
            managerReason,
            includeRecordedSignatures
        );
    }

    public byte[] buildPrintablePdf(LoanApplication app,
                                    String saccoName,
                                    Member applicant,
                                    byte[] applicantProfileImage,
                                    Map<String, Object> formFields,
                                    Map<String, Object> financialFields,
                                    List<GuarantorRequest> guarantorRequests,
                                    Map<UUID, String> guarantorNames,
                                    Map<UUID, String> guarantorMemberNumbers,
                                    List<ManagerReview> staffReviews,
                                    Map<UUID, Member> staffReviewers,
                                    List<BoardReview> boardReviews,
                                    Map<UUID, Member> boardMembers,
                                    String managerReason,
                                    boolean includeRecordedSignatures) {
        return buildPrintablePdf(
            app,
            saccoName,
            applicant,
            applicantProfileImage,
            null,
            formFields,
            financialFields,
            guarantorRequests,
            guarantorNames,
            guarantorMemberNumbers,
            staffReviews,
            staffReviewers,
            boardReviews,
            boardMembers,
            managerReason,
            includeRecordedSignatures
        );
    }

    public byte[] buildPrintablePdf(LoanApplication app,
                                    String saccoName,
                                    Member applicant,
                                    byte[] applicantProfileImage,
                                    byte[] saccoLogoImage,
                                    Map<String, Object> formFields,
                                    Map<String, Object> financialFields,
                                    List<GuarantorRequest> guarantorRequests,
                                    Map<UUID, String> guarantorNames,
                                    Map<UUID, String> guarantorMemberNumbers,
                                    List<ManagerReview> staffReviews,
                                    Map<UUID, Member> staffReviewers,
                                    List<BoardReview> boardReviews,
                                    Map<UUID, Member> boardMembers,
                                    String managerReason,
                                    boolean includeRecordedSignatures) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PrintableLoanApplicationPdfRenderer renderer = new PrintableLoanApplicationPdfRenderer(
                document,
                app,
                saccoName,
                applicant,
                applicantProfileImage,
                saccoLogoImage,
                formFields,
                financialFields,
                parseDisbursementProofAttachments(app.getAttachmentsJson()),
                printableCalculatedRepaymentRows(app),
                guarantorRequests,
                guarantorNames,
                guarantorMemberNumbers,
                staffReviews,
                staffReviewers,
                boardReviews,
                boardMembers,
                includeRecordedSignatures
            );
            renderer.render();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate printable loan application PDF.", ex);
        }
    }

    private void appendApplicantSignature(StringBuilder html, LoanApplication app, Member applicant) {
        if (app.getApplicantSignatureVerifiedAt() == null) {
            return;
        }
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
                                        Map<UUID, String> guarantorNames,
                                        Map<UUID, String> guarantorMemberNumbers) {
        html.append("<div class=\"print-section\">");
        html.append("<h2>Guarantors</h2>");
        html.append("<table><thead><tr><th>Guarantor</th><th>Member Number</th><th>Status</th><th>Signature</th><th>Verified At</th></tr></thead><tbody>");
        if (guarantorRequests == null || guarantorRequests.isEmpty()) {
            html.append("<tr><td colspan=\"5\">No guarantor details available.</td></tr>");
        } else {
            for (GuarantorRequest request : guarantorRequests) {
                String name = guarantorNames == null ? null : guarantorNames.get(request.getGuarantorMemberId());
                String memberNumber = guarantorMemberNumbers == null ? null : guarantorMemberNumbers.get(request.getGuarantorMemberId());
                html.append("<tr><td>")
                    .append(esc(name == null || name.isBlank() ? "Guarantor" : name))
                    .append("</td><td>")
                    .append(esc(memberNumber == null || memberNumber.isBlank() ? "-" : memberNumber))
                    .append("</td><td>")
                    .append(esc(humanizeValue(request.getStatus())))
                    .append("</td><td>");
                if (request.getStatus() == com.sacco.mvp.domain.GuarantorRequestStatus.APPROVED
                    && request.getGuarantorSignatureVerifiedAt() != null
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
        html.append("</tbody></table></div>");
    }

    private void appendStaffReviewSummary(StringBuilder html,
                                          List<ManagerReview> staffReviews,
                                          Map<UUID, Member> staffReviewers) {
        html.append("<div class=\"print-section\">");
        html.append("<h2>Loan Committee</h2>");
        html.append("<table><thead><tr><th>Stage</th><th>Reviewer</th><th>Member Number</th><th>Decision</th><th>Notes</th><th>Decision Date</th><th>Signature</th></tr></thead><tbody>");
        if (staffReviews == null || staffReviews.isEmpty()) {
            html.append("<tr><td colspan=\"7\">No staff review decisions available.</td></tr>");
        } else {
            for (ManagerReview review : staffReviews) {
                Member reviewer = staffReviewers == null ? null : staffReviewers.get(review.getManagerMemberId());
                String signatureText = staffReviewSignatureText(review, reviewer);
                html.append("<tr><td>")
                    .append(esc(review.getReviewStage() == null ? "-" : review.getReviewStage().getDisplayLabel()))
                    .append("</td><td>")
                    .append(esc(reviewer == null ? shortId(review.getManagerMemberId()) : reviewer.getFullName()))
                    .append("</td><td>")
                    .append(esc(reviewer == null ? "-" : reviewer.getMemberNo()))
                    .append("</td><td>")
                    .append(esc(humanizeValue(review.getDecision())))
                    .append("</td><td>")
                    .append(esc(review.getReasons() == null || review.getReasons().isBlank() ? "-" : review.getReasons()))
                    .append("</td><td>")
                    .append(esc(formatTimestamp(review.getCreatedAt())))
                    .append("</td><td>");
                if (signatureText != null && !signatureText.isBlank()) {
                    html.append("<div class=\"signature-text\" style=\"font-size:34px;\">")
                        .append(esc(signatureText))
                        .append("</div>");
                } else {
                    html.append("-");
                }
                html.append("</td></tr>");
            }
        }
        html.append("</tbody></table></div>");
    }

    private void appendBoardCommitteeSummary(StringBuilder html,
                                             List<BoardReview> boardReviews,
                                             Map<UUID, Member> boardMembers) {
        html.append("<div class=\"print-section\">");
        html.append("<h2>Credit Committee Assessors</h2>");
        html.append("<table><thead><tr><th>Assessor</th><th>Member Number</th><th>Decision</th><th>Comment</th><th>Decision Date</th><th>Signature</th><th>Verified At</th></tr></thead><tbody>");
        if (boardReviews == null || boardReviews.isEmpty()) {
            html.append("<tr><td colspan=\"7\">No board assessor details available.</td></tr>");
        } else {
            for (BoardReview review : boardReviews) {
                Member boardMember = boardMembers == null ? null : boardMembers.get(review.getBoardMemberId());
                String signatureText = boardReviewSignatureText(review, boardMember);
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
                if (signatureText != null && !signatureText.isBlank()) {
                    html.append("<div class=\"signature-text\" style=\"font-size:34px;\">")
                        .append(esc(signatureText))
                        .append("</div>");
                } else {
                    html.append("-");
                }
                html.append("</td><td>")
                    .append(esc(formatTimestamp(review.getBoardSignatureVerifiedAt())))
                    .append("</td></tr>");
            }
        }
        html.append("</tbody></table></div>");
    }

    private String staffReviewSignatureText(ManagerReview review, Member reviewer) {
        if (review != null
            && review.getManagerSignatureText() != null
            && !review.getManagerSignatureText().isBlank()) {
            return review.getManagerSignatureText();
        }
        if (reviewer != null
            && reviewer.getSignatureRegisteredAt() != null
            && reviewer.getSignatureText() != null
            && !reviewer.getSignatureText().isBlank()) {
            return reviewer.getSignatureText();
        }
        return null;
    }

    private String boardReviewSignatureText(BoardReview review, Member reviewer) {
        if (review != null
            && review.getBoardSignatureText() != null
            && !review.getBoardSignatureText().isBlank()) {
            return review.getBoardSignatureText();
        }
        if (reviewer != null
            && reviewer.getSignatureRegisteredAt() != null
            && reviewer.getSignatureText() != null
            && !reviewer.getSignatureText().isBlank()) {
            return reviewer.getSignatureText();
        }
        return null;
    }

    private List<Map<String, Object>> printableCalculatedRepaymentRows(LoanApplication app) {
        List<Map<String, Object>> calculatedRows = calculatedRepaymentRows(app);
        return calculatedRows.isEmpty() ? reviewRepaymentRows(app) : calculatedRows;
    }

    private String printableRepaymentRowValue(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return "-";
        }
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null && !String.valueOf(value).isBlank() && !"null".equalsIgnoreCase(String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return "-";
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
        if (isMoneyRowLabel(label) && value instanceof Number) {
            display.put(label, formatMoney(new BigDecimal(String.valueOf(value))));
            return;
        }
        if (isMoneyRowLabel(label) && value instanceof String stringValue && stringValue.matches("-?\\d+(\\.\\d+)?")) {
            display.put(label, formatMoney(new BigDecimal(stringValue)));
            return;
        }
        display.put(label, value);
    }

    private boolean isMoneyRowLabel(String label) {
        return label.contains("(TZS)") || label.matches(".*\\([0-9]+(?:\\.[0-9]+)?%\\)$");
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

    private void putRepaymentScheduleTotals(Map<String, Object> display, Map<String, Object> raw) {
        Object scheduleObject = raw.get("schedule");
        if (!(scheduleObject instanceof List<?> schedule) || schedule.isEmpty()) {
            putMoney(display, "Total Interest", raw.get("interestAmount"));
            putMoney(display, "Total Principal", raw.get("disbursedPrincipal"));
            putMoney(display, "Total Amount", raw.get("principalPlusInterest"));
            return;
        }

        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalInterest = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Object entry : schedule) {
            if (!(entry instanceof Map<?, ?> row)) {
                continue;
            }
            totalPrincipal = totalPrincipal.add(nonNegative(toBigDecimal(row.get("principalComponent"))));
            totalInterest = totalInterest.add(nonNegative(toBigDecimal(row.get("interestComponent"))));
            totalAmount = totalAmount.add(nonNegative(toBigDecimal(row.get("amount"))));
        }
        putMoney(display, "Total Interest", totalInterest);
        putMoney(display, "Total Principal", totalPrincipal);
        putMoney(display, "Total Amount", totalAmount);
    }

    private void putFinancialSnapshotRepaymentTotals(Map<String, Object> display, LoanApplication app) {
        if (app == null || app.getFinancialSnapshot() == null || app.getFinancialSnapshot().isBlank()) {
            return;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(app.getFinancialSnapshot(), new TypeReference<>() {});
            putMoney(display, "Total Interest", raw.get("interestAmount"));
            putMoney(display, "Total Principal", app.getAmount());
            Object totalAmount = raw.get("principalPlusInterest");
            if (totalAmount == null) {
                totalAmount = raw.get("loanPlusInterest");
            }
            putMoney(display, "Total Amount", totalAmount);
        } catch (Exception ex) {
            // Keep the saved repayment schedule values if the snapshot cannot be parsed.
        }
    }

    private Object prettyValue(String key, Object value) {
        return value;
    }

    private String humanizeFieldLabel(String key) {
        return switch (key) {
            case "purpose" -> "Loan Purpose";
            case "nationalId" -> "National ID";
            case "employerName" -> "Employer Name";
            case "additionalNotes" -> "Additional Notes";
            case "hasExistingLoan" -> "Existing Loan";
            default -> key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').trim();
        };
    }

    private String formatMoney(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.##", new DecimalFormatSymbols(Locale.US));
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        return "TSh " + format.format(safe);
    }

    private String formatTimestamp(java.time.OffsetDateTime timestamp) {
        return timestamp == null ? "-" : timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String stageLabel(ApprovalWorkflowStage stage) {
        return stage == null ? "Staff Review" : stage.getDisplayLabel();
    }

    private String reviewerName(UUID reviewerId, Map<UUID, Member> reviewers) {
        Member reviewer = reviewers.get(reviewerId);
        if (reviewer != null && reviewer.getFullName() != null && !reviewer.getFullName().isBlank()) {
            return reviewer.getFullName();
        }
        return reviewerId == null ? "-" : "#" + reviewerId.toString().substring(0, 8);
    }

    private String reviewerMemberNo(UUID reviewerId, Map<UUID, Member> reviewers) {
        Member reviewer = reviewers.get(reviewerId);
        return reviewer == null || reviewer.getMemberNo() == null || reviewer.getMemberNo().isBlank()
            ? "-"
            : reviewer.getMemberNo();
    }

    private String normalizeReviewNote(String note) {
        return note == null || note.isBlank() ? "-" : note.trim();
    }

    private String attachmentUploadedAtLabel(Map<String, Object> attachment) {
        if (attachment == null) {
            return "-";
        }
        Object uploadedAt = attachment.get("uploadedAt");
        if (uploadedAt == null || String.valueOf(uploadedAt).isBlank()) {
            return "-";
        }
        String value = String.valueOf(uploadedAt);
        return value.length() >= 16 ? value.substring(0, 16).replace('T', ' ') : value.replace('T', ' ');
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
        if ("ACCEPT".equalsIgnoreCase(text) || "ACCEPTED".equalsIgnoreCase(text)) {
            return "Approved";
        }
        if ("MANAGER_ACCEPTED".equalsIgnoreCase(text)) {
            return "Manager Approved";
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

    private void appendSuperTitle(StringBuilder html, String title, String helper) {
        html.append("<div class=\"print-section\"><h2 class=\"super-title\">")
            .append(esc(title));
        if (helper != null && !helper.isBlank()) {
            html.append("<span class=\"helper\">").append(esc(helper)).append("</span>");
        }
        html.append("</h2></div>");
    }

    private void appendTable(StringBuilder html, String title, Map<String, Object> rows) {
        html.append("<div class=\"print-section\">");
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
        html.append("</tbody></table></div>");
    }

    private void appendAttachmentTable(StringBuilder html, String title, List<Map<String, Object>> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        html.append("<div class=\"print-section\">");
        html.append("<h2>").append(esc(title)).append("</h2>");
        html.append("<table><thead><tr><th>File</th><th>Size</th><th>Uploaded</th></tr></thead><tbody>");
        for (Map<String, Object> attachment : attachments) {
            html.append("<tr><td>")
                .append(esc(String.valueOf(attachment.getOrDefault("originalName", "-"))))
                .append("</td><td>")
                .append(esc(String.valueOf(attachment.getOrDefault("sizeLabel", "-"))))
                .append("</td><td>")
                .append(esc(attachmentUploadedAtLabel(attachment)))
                .append("</td></tr>");
        }
        html.append("</tbody></table></div>");
    }

    private String shortId(UUID id) {
        return id == null ? "" : id.toString().substring(0, 8);
    }

    private String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }

    private static String sanitizePdfText(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        return text.replace('\r', ' ').trim();
    }

    private static String sanitizePdfLineText(String text) {
        String sanitized = sanitizePdfText(text).replaceAll("\\p{Cntrl}+", " ").trim();
        return sanitized.isEmpty() ? "-" : sanitized;
    }

    private String blankToDash(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "-" : text;
    }

    private final class PrintableLoanApplicationPdfRenderer {
        private static final float MARGIN = 38f;
        private static final float TOP_MARGIN = 40f;
        private static final float BOTTOM_MARGIN = 34f;
        private static final float FOOTER_GAP = 22f;
        private static final float TITLE_SIZE = 19f;
        private static final float META_SIZE = 10f;
        private static final float SECTION_SIZE = 11.5f;
        private static final float BODY_SIZE = 9.2f;
        private static final float SMALL_SIZE = 8.1f;
        private static final float LINE_GAP = 3f;
        private static final float CELL_PADDING_X = 7f;
        private static final float CELL_PADDING_Y = 7f;
        private static final float SECTION_TOP_GAP = 12f;
        private static final float SECTION_BOTTOM_GAP = 18f;
        private static final float MAX_ATTACHMENT_PREVIEW_HEIGHT = 280f;
        private static final Color TEXT_COLOR = new Color(41, 55, 71);
        private static final Color MUTED_COLOR = new Color(87, 106, 126);
        private static final Color BORDER_COLOR = new Color(225, 232, 238);
        private static final Color HEADER_FILL = new Color(246, 248, 251);
        private static final Color SUPER_TITLE_FILL = new Color(71, 85, 105);
        private static final Color RULE_COLOR = new Color(60, 79, 97);
        private static final Color BRAND_NAVY = new Color(41, 52, 127);
        private static final Color BRAND_GREEN = new Color(61, 139, 61);
        private static final Color BRAND_BROWN = new Color(141, 67, 29);

        private final PDDocument document;
        private final LoanApplication app;
        private final String saccoName;
        private final Member applicant;
        private final byte[] applicantProfileImage;
        private final byte[] saccoLogoImage;
        private final Map<String, Object> formFields;
        private final Map<String, Object> loanCalculationFields;
        private final List<Map<String, Object>> applicationAttachments;
        private final List<Map<String, Object>> disbursementProofAttachments;
        private final List<Map<String, Object>> calculatedRepaymentRows;
        private final List<GuarantorRequest> guarantorRequests;
        private final Map<UUID, String> guarantorNames;
        private final Map<UUID, String> guarantorMemberNumbers;
        private final Map<UUID, Member> guarantorMembersById;
        private final List<ManagerReview> staffReviews;
        private final Map<UUID, Member> staffReviewers;
        private final List<BoardReview> boardReviews;
        private final Map<UUID, Member> boardMembers;
        private final boolean includeRecordedSignatures;
        private final LoanProductWorkflowService.WorkflowDefinition workflowDefinition;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDType1Font signatureFont = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private PrintableLoanApplicationPdfRenderer(PDDocument document,
                                                    LoanApplication app,
                                                    String saccoName,
                                                    Member applicant,
                                                    byte[] applicantProfileImage,
                                                    byte[] saccoLogoImage,
                                                    Map<String, Object> formFields,
                                                    Map<String, Object> loanCalculationFields,
                                                    List<Map<String, Object>> disbursementProofAttachments,
                                                    List<Map<String, Object>> calculatedRepaymentRows,
                                                    List<GuarantorRequest> guarantorRequests,
                                                    Map<UUID, String> guarantorNames,
                                                    Map<UUID, String> guarantorMemberNumbers,
                                                    List<ManagerReview> staffReviews,
                                                    Map<UUID, Member> staffReviewers,
                                                    List<BoardReview> boardReviews,
                                                    Map<UUID, Member> boardMembers,
                                                    boolean includeRecordedSignatures) {
            this.document = document;
            this.app = app;
            this.saccoName = saccoName;
            this.applicant = applicant;
            this.applicantProfileImage = applicantProfileImage == null ? new byte[0] : applicantProfileImage;
            this.saccoLogoImage = saccoLogoImage == null ? new byte[0] : saccoLogoImage;
            this.formFields = formFields == null ? Collections.emptyMap() : formFields;
            this.loanCalculationFields = loanCalculationFields == null ? Collections.emptyMap() : loanCalculationFields;
            this.applicationAttachments = parseApplicationAttachments(app.getAttachmentsJson());
            this.disbursementProofAttachments = disbursementProofAttachments == null ? Collections.emptyList() : disbursementProofAttachments;
            this.calculatedRepaymentRows = calculatedRepaymentRows == null ? Collections.emptyList() : calculatedRepaymentRows;
            this.guarantorRequests = guarantorRequests == null ? Collections.emptyList() : guarantorRequests;
            this.guarantorNames = guarantorNames == null ? Collections.emptyMap() : guarantorNames;
            this.guarantorMemberNumbers = guarantorMemberNumbers == null ? Collections.emptyMap() : guarantorMemberNumbers;
            this.guarantorMembersById = loadGuarantorMembersById(this.guarantorRequests);
            this.staffReviews = staffReviews == null ? Collections.emptyList() : staffReviews;
            this.staffReviewers = staffReviewers == null ? Collections.emptyMap() : staffReviewers;
            this.boardReviews = boardReviews == null ? Collections.emptyList() : boardReviews;
            this.boardMembers = boardMembers == null ? Collections.emptyMap() : boardMembers;
            this.includeRecordedSignatures = includeRecordedSignatures;
            this.workflowDefinition = loanProductWorkflowService.resolveForApplication(app);
        }

        private void render() throws IOException {
            startNewPage();
            drawHeader();
            drawApplicationSummary();
            drawRecordedInformationOverview();
            drawSuperSectionStrip("Supporting Application Details", "System-record detail");
            drawSectionTable("Application Details", formFields);
            drawSectionTable("Loan Calculation Details", loanCalculationFields);
            drawRepaymentRowsSection();
            drawGuarantorSection();
            drawApplicantSignatureSection();
            drawConfiguredReviewSignOffSection();
            drawAttachmentAppendixSection("Application Attachments", applicationAttachments);
            drawAttachmentAppendixSection("Disbursement Proof", disbursementProofAttachments);
            closePage();
        }

        private void startNewPage() throws IOException {
            if (stream != null) {
                drawFooter();
                stream.close();
            }
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            PdfWatermarkRenderer.draw(document, stream, page, saccoLogoImage);
            y = page.getMediaBox().getHeight() - TOP_MARGIN;
        }

        private void closePage() throws IOException {
            if (stream != null) {
                drawFooter();
                stream.close();
                stream = null;
            }
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (y - requiredHeight < (BOTTOM_MARGIN + FOOTER_GAP)) {
                startNewPage();
            }
        }

        private void drawHeader() throws IOException {
            ensureSpace(132f);
            float pageWidth = page.getMediaBox().getWidth();
            float headerTop = page.getMediaBox().getHeight();
            float profileSize = 78f;
            float profileX = pageWidth - MARGIN - profileSize;
            float profileY = headerTop - 150f;
            float logoSize = 34f;

            String saccoLabel = sanitizePdfText(saccoName == null || saccoName.isBlank() ? "SACCO" : saccoName.trim());
            boolean logoDrawn = drawSaccoLogo(MARGIN, headerTop - 67f, logoSize);
            float titleX = logoDrawn ? MARGIN + logoSize + 10f : MARGIN;
            writeText(saccoLabel.toUpperCase(Locale.ROOT), titleX, headerTop - 42f, bold, 16f, TEXT_COLOR);
            writeText("Official Loan Application document", titleX, headerTop - 59f, regular, META_SIZE, MUTED_COLOR);
            writeRightAligned("Generated: " + LocalDate.now(), pageWidth - MARGIN, headerTop - 42f, regular, META_SIZE, MUTED_COLOR);
            drawApplicantProfileImage(profileX, profileY, profileSize);

            stream.setStrokingColor(BORDER_COLOR);
            stream.setLineWidth(0.8f);
            stream.moveTo(MARGIN, headerTop - 92f);
            stream.lineTo(profileX - 18f, headerTop - 92f);
            stream.stroke();

            y = headerTop - 120f;
            writeText("Loan Application", MARGIN, y, bold, TITLE_SIZE, TEXT_COLOR);
            y -= 18f;
            writeText("Prepared from the system record for formal review and filing", MARGIN, y, regular, META_SIZE, MUTED_COLOR);
            y -= 20f;
        }

        private boolean drawSaccoLogo(float x, float y, float size) throws IOException {
            BufferedImage image = readSaccoLogoImage();
            if (image == null) {
                return false;
            }
            PDImageXObject pdfImage = LosslessFactory.createFromImage(document, image);
            float scale = Math.min(size / image.getWidth(), size / image.getHeight());
            float targetWidth = image.getWidth() * scale;
            float targetHeight = image.getHeight() * scale;
            float targetX = x + ((size - targetWidth) / 2f);
            float targetY = y + ((size - targetHeight) / 2f);
            stream.drawImage(pdfImage, targetX, targetY, targetWidth, targetHeight);
            return true;
        }

        private BufferedImage readSaccoLogoImage() {
            if (saccoLogoImage.length == 0) {
                return null;
            }
            try {
                return ImageIO.read(new ByteArrayInputStream(saccoLogoImage));
            } catch (IOException ex) {
                return null;
            }
        }

        private void drawApplicantProfileImage(float x, float y, float size) throws IOException {
            BufferedImage image = readApplicantProfileImage();
            if (image == null) {
                image = createProfileFallbackImage(220, 220);
            }
            PDImageXObject pdfImage = LosslessFactory.createFromImage(document, createCircularProfileImage(image, 220));
            stream.drawImage(pdfImage, x, y, size, size);
        }

        private BufferedImage readApplicantProfileImage() {
            if (applicantProfileImage.length == 0) {
                return null;
            }
            try {
                return ImageIO.read(new ByteArrayInputStream(applicantProfileImage));
            } catch (IOException ex) {
                return null;
            }
        }

        private BufferedImage createCircularProfileImage(BufferedImage source, int size) {
            BufferedImage output = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = output.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.setClip(new Ellipse2D.Float(0, 0, size, size));
                graphics.setColor(Color.WHITE);
                graphics.fillOval(0, 0, size, size);
                double scale = Math.max((double) size / source.getWidth(), (double) size / source.getHeight());
                int drawWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
                int drawHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
                int x = (size - drawWidth) / 2;
                int y = (size - drawHeight) / 2;
                graphics.drawImage(source, x, y, drawWidth, drawHeight, null);
                graphics.setClip(null);
                graphics.setStroke(new BasicStroke(3f));
                graphics.setColor(new Color(215, 225, 234));
                graphics.drawOval(1, 1, size - 2, size - 2);
            } finally {
                graphics.dispose();
            }
            return output;
        }

        private BufferedImage createProfileFallbackImage(int width, int height) {
            BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = output.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setColor(new Color(244, 246, 248));
                graphics.fillRect(0, 0, width, height);
                graphics.setColor(new Color(101, 116, 139));
                graphics.fillOval(width / 2 - 24, height / 2 - 54, 48, 48);
                graphics.fillOval(width / 2 - 54, height / 2, 108, 70);
            } finally {
                graphics.dispose();
            }
            return output;
        }

        private long completedReviewCount() {
            return staffReviews.stream()
                .filter(review -> review.getReviewStage() != null)
                .map(ManagerReview::getReviewStage)
                .distinct()
                .count()
                + boardReviews.stream()
                .filter(review -> review.getReviewStage() != null)
                .map(BoardReview::getReviewStage)
                .distinct()
                .count();
        }

        private void drawApplicationSummary() throws IOException {
            drawSuperSectionStrip("Application Summary", completedReviewCount() + " staff reviews complete");
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Application Reference", app.getApplicationNumber() == null ? "-" : app.getApplicationNumber().toString(), "Loan Reference", sanitizePdfText(app.getLoanId())});
            rows.add(new String[]{"Applicant", sanitizePdfText(applicant.getFullName()), "Member Number", sanitizePdfText(applicant.getMemberNo())});
            rows.add(new String[]{"Requested Facility", humanizeValue(app.getLoanType()), "Requested Amount", formatMoney(app.getAmount())});
            rows.add(new String[]{"Current State", humanizeValue(app.getStatus()), "Submitted", sanitizePdfText(formatTimestamp(app.getSubmittedAt()))});
            drawRowsWithoutHeader(
                new float[]{contentWidth() * 0.20f, contentWidth() * 0.30f, contentWidth() * 0.20f, contentWidth() * 0.30f},
                rows,
                BODY_SIZE,
                14f,
                5f,
                2f
            );
        }

        private void drawRowsWithoutHeader(float[] widths,
                                           List<String[]> rows,
                                           float bodyFontSize,
                                           float gapAfter,
                                           float cellPaddingY,
                                           float extraLineGap) throws IOException {
            for (String[] row : rows) {
                float rowHeight = measureRowHeight(row, widths, regular, bodyFontSize, cellPaddingY, extraLineGap);
                if (y - rowHeight < (BOTTOM_MARGIN + FOOTER_GAP)) {
                    startNewPage();
                }
                drawRow(row, widths, regular, bodyFontSize, false, cellPaddingY, extraLineGap);
            }
            y -= gapAfter;
        }

        private void drawRecordedInformationOverview() throws IOException {
            drawSuperSectionStrip("Recorded Information", "");
            List<String[]> cards = List.of(
                new String[]{
                    "MEMBER RECORD",
                    "Membership status: " + humanizedOrDash(applicant.getStatus()),
                    "Station: " + sanitizePdfText(app.getStationId()),
                    "Contact: " + applicantContact()
                },
                new String[]{
                    "FACILITY TERMS",
                    "Repayment period: " + valueOrDash(app.getTenorMonths(), " months"),
                    "Required guarantors: " + valueOrDash(app.getRequiredGuarantors(), ""),
                    "Submitted: " + sanitizePdfText(formatTimestamp(app.getSubmittedAt()))
                },
                new String[]{
                    "REPAYMENT ARRANGEMENT",
                    "Frequency: " + humanizedOrDash(app.getRepaymentFrequency()),
                    "Installment: " + formatNullableMoney(app.getInstallmentAmount()),
                    "First repayment: " + sanitizePdfText(String.valueOf(app.getFirstRepaymentDate()))
                },
                new String[]{
                    "SUPPORTING RECORDS",
                    "Guarantor confirmations: " + completedGuarantorCount() + " of " + guarantorRequests.size(),
                    "Disbursement proofs: " + disbursementProofAttachments.size(),
                    "Completed staff reviews: " + completedReviewCount()
                }
            );
            float gap = 10f;
            float cardWidth = (contentWidth() - gap) / 2f;
            float cardHeight = 68f;
            for (int i = 0; i < cards.size(); i++) {
                if (i % 2 == 0) {
                    ensureSpace(cardHeight + 8f);
                }
                float x = MARGIN + ((i % 2) * (cardWidth + gap));
                drawInformationCard(x, y, cardWidth, cardHeight, cards.get(i));
                if (i % 2 == 1) {
                    y -= cardHeight + 8f;
                }
            }
            y -= 4f;
        }

        private void drawInformationCard(float x, float topY, float width, float height, String[] details) throws IOException {
            stream.setNonStrokingColor(Color.WHITE);
            stream.addRect(x, topY - height, width, height);
            stream.fill();
            stream.setStrokingColor(BORDER_COLOR);
            stream.addRect(x, topY - height, width, height);
            stream.stroke();
            writeText(details[0], x + 10f, topY - 17f, bold, SMALL_SIZE + 0.4f, TEXT_COLOR);
            float textY = topY - 34f;
            for (int i = 1; i < details.length; i++) {
                String line = wrapText(details[i], regular, SMALL_SIZE, width - 20f).getFirst();
                writeText(line, x + 10f, textY, regular, SMALL_SIZE, MUTED_COLOR);
                textY -= SMALL_SIZE + 3f;
            }
        }

        private String applicantContact() {
            if (applicant.getPhone() != null && !applicant.getPhone().isBlank()) {
                return sanitizePdfText(applicant.getPhone());
            }
            return sanitizePdfText(applicant.getEmail());
        }

        private String valueOrDash(Object value, String suffix) {
            return value == null ? "-" : sanitizePdfText(String.valueOf(value)) + suffix;
        }

        private String humanizedOrDash(Object value) {
            String humanized = humanizeValue(value);
            return humanized.isBlank() ? "-" : humanized;
        }

        private long completedGuarantorCount() {
            return guarantorRequests.stream()
                .filter(request -> request.getGuarantorSignatureVerifiedAt() != null)
                .count();
        }

        private void drawSectionStrip(String title, String helper) throws IOException {
            ensureSpace(31f);
            stream.setNonStrokingColor(HEADER_FILL);
            stream.addRect(MARGIN, y - 24f, contentWidth(), 24f);
            stream.fill();
            stream.setStrokingColor(BORDER_COLOR);
            stream.addRect(MARGIN, y - 24f, contentWidth(), 24f);
            stream.stroke();
            writeText(title, MARGIN + 9f, y - 16f, bold, SECTION_SIZE, TEXT_COLOR);
            if (helper != null && !helper.isBlank()) {
                writeRightAligned(helper, page.getMediaBox().getWidth() - MARGIN - 9f, y - 16f, regular, SMALL_SIZE, MUTED_COLOR);
            }
            y -= 34f;
        }

        private void drawSuperSectionStrip(String title, String helper) throws IOException {
            ensureSpace(35f);
            stream.setNonStrokingColor(SUPER_TITLE_FILL);
            stream.addRect(MARGIN, y - 27f, contentWidth(), 27f);
            stream.fill();
            stream.setStrokingColor(RULE_COLOR);
            stream.addRect(MARGIN, y - 27f, contentWidth(), 27f);
            stream.stroke();
            writeText(title, MARGIN + 10f, y - 18f, bold, SECTION_SIZE + 0.8f, Color.WHITE);
            if (helper != null && !helper.isBlank()) {
                writeRightAligned(helper, page.getMediaBox().getWidth() - MARGIN - 10f, y - 18f, regular, SMALL_SIZE, HEADER_FILL);
            }
            y -= 38f;
        }

        private void ensureSectionTableStartSpace(String[] headers,
                                                  float[] widths,
                                                  List<String[]> rows,
                                                  float headerFontSize,
                                                  float bodyFontSize,
                                                  float cellPaddingY,
                                                  float extraLineGap) throws IOException {
            float required = 34f + measureRowHeight(headers, widths, bold, headerFontSize, cellPaddingY, extraLineGap);
            if (rows != null && !rows.isEmpty()) {
                required += measureRowHeight(rows.getFirst(), widths, regular, bodyFontSize, cellPaddingY, extraLineGap);
            }
            if (y - required < (BOTTOM_MARGIN + FOOTER_GAP)) {
                startNewPage();
            }
        }

        private void drawSectionTable(String title, Map<String, Object> rowsMap) throws IOException {
            if (rowsMap == null || rowsMap.isEmpty()) {
                drawSectionStrip(title, null);
                drawParagraph("No details available.", regular, META_SIZE, MUTED_COLOR);
                y -= 6f;
                return;
            }
            List<String[]> rows = new ArrayList<>();
            for (Map.Entry<String, Object> entry : rowsMap.entrySet()) {
                rows.add(new String[]{sanitizePdfText(entry.getKey()), sanitizePdfText(String.valueOf(entry.getValue()))});
            }
            String[] headers = new String[]{"Field", "Value"};
            float[] widths = new float[]{contentWidth() * 0.36f, contentWidth() * 0.64f};
            ensureSectionTableStartSpace(headers, widths, rows, BODY_SIZE, BODY_SIZE, CELL_PADDING_Y, 2f);
            drawSectionStrip(title, null);
            drawTable(headers, widths, rows, BODY_SIZE, BODY_SIZE, 14f);
        }

        private void drawAttachmentAppendixSection(String title, List<Map<String, Object>> attachments) throws IOException {
            if (attachments == null || attachments.isEmpty()) {
                return;
            }
            startNewPage();
            drawSectionStrip(title, "Attached documents");
            List<String[]> rows = new ArrayList<>();
            for (Map<String, Object> attachment : attachments) {
                String label = String.valueOf(attachment.getOrDefault("requiredAttachmentName",
                    attachment.getOrDefault("originalName", "-")));
                rows.add(new String[]{
                    sanitizePdfText(label),
                    sanitizePdfText(String.valueOf(attachment.getOrDefault("originalName", "-"))),
                    sanitizePdfText(String.valueOf(attachment.getOrDefault("sizeLabel", "-")))
                });
            }
            String[] headers = new String[]{"Document", "File", "Size"};
            float[] widths = new float[]{contentWidth() * 0.34f, contentWidth() * 0.46f, contentWidth() * 0.20f};
            drawTable(
                headers,
                widths,
                rows,
                BODY_SIZE,
                BODY_SIZE,
                14f
            );
            drawAttachmentPreviews(attachments);
        }

        private void drawAttachmentSection(String title, List<Map<String, Object>> attachments) throws IOException {
            if (attachments == null || attachments.isEmpty()) {
                return;
            }
            List<String[]> rows = new ArrayList<>();
            for (Map<String, Object> attachment : attachments) {
                rows.add(new String[]{
                    sanitizePdfText(String.valueOf(attachment.getOrDefault("originalName", "-"))),
                    sanitizePdfText(String.valueOf(attachment.getOrDefault("sizeLabel", "-"))),
                    sanitizePdfText(attachmentUploadedAtLabel(attachment))
                });
            }
            String[] headers = new String[]{"File", "Size", "Uploaded"};
            float[] widths = new float[]{contentWidth() * 0.50f, contentWidth() * 0.18f, contentWidth() * 0.32f};
            ensureSectionTableStartSpace(headers, widths, rows, BODY_SIZE, BODY_SIZE, CELL_PADDING_Y, 2f);
            drawSectionStrip(title, null);
            drawTable(
                headers,
                widths,
                rows,
                BODY_SIZE,
                BODY_SIZE,
                14f
            );
            drawAttachmentPreviews(attachments);
        }

        private void drawAttachmentPreviews(List<Map<String, Object>> attachments) throws IOException {
            if (attachments == null || attachments.isEmpty()) {
                return;
            }
            drawSectionHeading("Attachment Contents");
            for (Map<String, Object> attachment : attachments) {
                String attachmentId = String.valueOf(attachment.getOrDefault("id", ""));
                if (attachmentId.isBlank()) {
                    continue;
                }
                String displayName = sanitizePdfText(String.valueOf(attachment.getOrDefault("originalName", "Receipt")));
                try {
                    LoanAttachmentService.AttachmentResource resource = loanAttachmentService.load(app.getId(), attachmentId, app.getAttachmentsJson());
                    drawAttachmentResource(displayName, resource);
                } catch (Exception ex) {
                    drawParagraph(displayName + " could not be loaded for print preview.", regular, META_SIZE, MUTED_COLOR);
                    y -= 4f;
                }
            }
        }

        private void drawAttachmentResource(String displayName, LoanAttachmentService.AttachmentResource resource) throws IOException {
            String contentType = resource.getContentType() == null ? "" : resource.getContentType().toLowerCase(Locale.ROOT);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(resource.getContent()));
            if (image != null) {
                drawAttachmentImage(displayName, image);
                return;
            }
            if ("application/pdf".equals(contentType) || displayName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                drawAttachmentPdf(displayName, resource.getContent());
                return;
            }
            drawParagraph(displayName + " is attached. Preview is available from the application detail page.", regular, META_SIZE, MUTED_COLOR);
            y -= 4f;
        }

        private void drawAttachmentPdf(String displayName, byte[] content) throws IOException {
            try (PDDocument source = Loader.loadPDF(content)) {
                PDFRenderer pdfRenderer = new PDFRenderer(source);
                for (int pageIndex = 0; pageIndex < source.getNumberOfPages(); pageIndex++) {
                    BufferedImage pageImage = pdfRenderer.renderImageWithDPI(pageIndex, 120);
                    String label = source.getNumberOfPages() == 1
                        ? displayName
                        : displayName + " (page " + (pageIndex + 1) + " of " + source.getNumberOfPages() + ")";
                    drawAttachmentImage(label, pageImage);
                }
            }
        }

        private void drawAttachmentImage(String label, BufferedImage image) throws IOException {
            if (image == null) {
                return;
            }
            ensureSpace(36f);
            writeText(label, MARGIN, y, bold, BODY_SIZE, TEXT_COLOR);
            y -= 12f;

            float maxWidth = contentWidth();
            float availableHeight = y - (BOTTOM_MARGIN + FOOTER_GAP);
            if (availableHeight < 120f) {
                startNewPage();
                writeText(label, MARGIN, y, bold, BODY_SIZE, TEXT_COLOR);
                y -= 12f;
                availableHeight = y - (BOTTOM_MARGIN + FOOTER_GAP);
            }
            float targetWidth = maxWidth;
            float targetHeight = image.getHeight() * (targetWidth / image.getWidth());
            float maxHeight = Math.min(availableHeight, MAX_ATTACHMENT_PREVIEW_HEIGHT);
            if (targetHeight > maxHeight) {
                targetHeight = maxHeight;
                targetWidth = image.getWidth() * (targetHeight / image.getHeight());
            }
            PDImageXObject pdfImage = LosslessFactory.createFromImage(document, image);
            stream.drawImage(pdfImage, MARGIN, y - targetHeight, targetWidth, targetHeight);
            y -= targetHeight + 14f;
        }

        private void drawRepaymentRowsSection() throws IOException {
            if (calculatedRepaymentRows.isEmpty()) {
                drawSuperSectionStrip("Repayment Schedule", null);
                drawParagraph("No repayment schedule available.", regular, META_SIZE, MUTED_COLOR);
                y -= 6f;
                return;
            }
            drawEstimatedRepaymentRows("Repayment Schedule", calculatedRepaymentRows);
        }

        private void drawEstimatedRepaymentRows(String title, List<Map<String, Object>> repaymentRows) throws IOException {
            List<String[]> rows = new ArrayList<>();
            for (Map<String, Object> row : repaymentRows) {
                rows.add(new String[]{
                    repaymentRowValue(row, "pmtNo", "installmentNumber"),
                    repaymentRowValue(row, "month", "dueDate"),
                    repaymentRowValue(row, "beginningBalance"),
                    repaymentRowValue(row, "payment", "amount"),
                    repaymentRowValue(row, "loanAmount"),
                    repaymentRowValue(row, "interest"),
                    repaymentRowValue(row, "endingBalance", "outstandingBalance")
                });
            }
            String[] headers = new String[]{"No.", "Month", "Beginning Balance", "Amount to Pay", "Loan Amount", "Interest", "Ending Balance"};
            float[] widths = new float[]{38f, 56f, 86f, 84f, 78f, 70f, contentWidth() - 412f};
            ensureSectionTableStartSpace(headers, widths, rows, SMALL_SIZE, SMALL_SIZE, CELL_PADDING_Y, 2f);
            drawSuperSectionStrip(title, null);
            drawTable(
                headers,
                widths,
                rows,
                SMALL_SIZE,
                SMALL_SIZE,
                13f
            );
        }

        private String repaymentRowValue(Map<String, Object> row, String... keys) {
            if (row == null || keys == null) {
                return "-";
            }
            for (String key : keys) {
                Object value = row.get(key);
                if (value != null && !String.valueOf(value).isBlank() && !"null".equalsIgnoreCase(String.valueOf(value))) {
                    return sanitizePdfText(String.valueOf(value));
                }
            }
            return "-";
        }

        private String repaymentAmountWithBreakdown(Map<String, Object> row) {
            String amount = sanitizePdfText(String.valueOf(row.get("amount")));
            String breakdown = sanitizePdfText(String.valueOf(row.get("scheduledBreakdown")));
            if ("-".equals(breakdown)) {
                return amount;
            }
            return amount + "\n" + breakdown;
        }

        private void drawGuarantorSection() throws IOException {
            List<String[]> rows = new ArrayList<>();
            if (guarantorRequests.isEmpty()) {
                rows.add(new String[]{"-", "-", "No guarantor details available.", "-", "-"});
            } else {
                for (GuarantorRequest request : guarantorRequests) {
                    String guarantorLabel = guarantorNames.get(request.getGuarantorMemberId());
                    if (guarantorLabel == null || guarantorLabel.isBlank()) {
                        guarantorLabel = shortId(request.getGuarantorMemberId());
                    }
                    rows.add(new String[]{
                        sanitizePdfText(guarantorLabel),
                        sanitizePdfText(guarantorMemberNumbers.get(request.getGuarantorMemberId())),
                        humanizeValue(request.getStatus()),
                        guarantorSignatureForPdf(request),
                        sanitizePdfText(formatTimestamp(request.getGuarantorSignatureVerifiedAt()))
                    });
                }
            }
            String[] headers = new String[]{"Guarantor", "Member Number", "Status", "Signature", "Verified At"};
            float[] widths = new float[]{116f, 76f, 72f, 150f, contentWidth() - 414f};
            ensureSectionTableStartSpace(headers, widths, rows, SMALL_SIZE, BODY_SIZE, CELL_PADDING_Y, 2f);
            drawSuperSectionStrip("Guarantors", null);
            drawTable(
                headers,
                widths,
                rows,
                SMALL_SIZE,
                BODY_SIZE,
                14f
            );
        }

        private String guarantorSignatureForPdf(GuarantorRequest request) {
            String signature = request == null ? null : request.getGuarantorSignatureText();
            if (signature != null && !"Approved by guarantor OTP".equalsIgnoreCase(signature.trim())) {
                return sanitizePdfText(signature);
            }
            Member guarantor = request == null ? null : guarantorMembersById.get(request.getGuarantorMemberId());
            return sanitizePdfText(guarantor == null ? null : guarantor.getSignatureText());
        }

        private Map<UUID, Member> loadGuarantorMembersById(List<GuarantorRequest> requests) {
            if (requests == null || requests.isEmpty()) {
                return Collections.emptyMap();
            }
            Set<UUID> ids = new LinkedHashSet<>();
            for (GuarantorRequest request : requests) {
                if (request != null && request.getGuarantorMemberId() != null) {
                    ids.add(request.getGuarantorMemberId());
                }
            }
            if (ids.isEmpty()) {
                return Collections.emptyMap();
            }
            Iterable<Member> members = memberRepository.findAllById(ids);
            if (members == null) {
                return Collections.emptyMap();
            }
            Map<UUID, Member> result = new LinkedHashMap<>();
            for (Member member : members) {
                if (member != null && member.getId() != null) {
                    result.put(member.getId(), member);
                }
            }
            return result;
        }

        private void drawConfiguredReviewSignOffSection() throws IOException {
            ensureSpace(156f);
            drawSuperSectionStrip("Loan Committee", includeRecordedSignatures ? "Recorded signatures included" : "Signatures required");
            List<ApprovalWorkflowStage> stages = configuredReviewStages();
            if (stages.isEmpty()) {
                drawParagraph("No approval review roles are configured for this application.", regular, META_SIZE, MUTED_COLOR);
                y -= 6f;
                return;
            }
            if (includeRecordedSignatures) {
                drawSystemSignedReviewTable(stages);
                return;
            }
            for (ApprovalWorkflowStage stage : stages) {
                if (boardStyleReviewStage(stage)) {
                    drawBoardSignOffPanel(stage);
                } else {
                    drawStaffSignOffPanel(stage);
                }
            }
        }

        private void drawSystemSignedReviewTable(List<ApprovalWorkflowStage> stages) throws IOException {
            List<String[]> rows = new ArrayList<>();
            for (ApprovalWorkflowStage stage : stages) {
                if (stageClosedAfterRejection(stage, stages)) {
                    rows.add(new String[]{stage.getDisplayLabel(), "-", "Closed", "-", "-", "-"});
                    continue;
                }
                if (boardStyleReviewStage(stage)) {
                    List<BoardReview> stageReviews = boardReviewsForStage(stage);
                    if (stageReviews.isEmpty()) {
                        if (completedApplication()) {
                            continue;
                        }
                        rows.add(new String[]{stage.getDisplayLabel(), "-", "Pending", "-", "-", "-"});
                    } else {
                        for (BoardReview review : stageReviews) {
                            Member boardMember = boardMembers.get(review.getBoardMemberId());
                            rows.add(new String[]{
                                stage.getDisplayLabel(),
                                boardMember == null ? shortId(review.getBoardMemberId()) : sanitizePdfText(boardMember.getFullName()),
                                humanizeValue(review.getDecision()),
                                sanitizePdfText(review.getComment()),
                                valueOrDash(boardSignOffText(review, boardMember)),
                                sanitizePdfText(formatTimestamp(boardSignOffTimestamp(review)))
                            });
                        }
                    }
                    continue;
                }
                ManagerReview review = latestStaffReview(stage);
                if (review == null && completedApplication()) {
                    continue;
                }
                Member reviewer = review == null ? null : staffReviewers.get(review.getManagerMemberId());
                rows.add(new String[]{
                    stage.getDisplayLabel(),
                    review == null ? "-" : reviewerLabel(review, reviewer),
                    review == null ? "Pending" : humanizeValue(review.getDecision()),
                    review == null ? "-" : sanitizePdfText(review.getReasons()),
                    valueOrDash(review == null ? null : staffSignOffText(review, reviewer)),
                    review == null ? "-" : sanitizePdfText(formatTimestamp(staffSignOffTimestamp(review)))
                });
            }
            drawTable(
                new String[]{"Review Stage", "Reviewer", "Decision", "Review Note", "Signature", "Verified At"},
                new float[]{84f, 112f, 58f, 100f, 92f, contentWidth() - 446f},
                rows,
                SMALL_SIZE,
                SMALL_SIZE,
                14f,
                4f,
                2f
            );
        }

        private List<ApprovalWorkflowStage> configuredReviewStages() {
            if (workflowDefinition == null || workflowDefinition.stages() == null) {
                return List.of();
            }
            return workflowDefinition.stages().stream()
                .filter(stage -> stage != ApprovalWorkflowStage.DISBURSEMENT_OFFICER)
                .toList();
        }

        private void drawStaffSignOffPanel(ApprovalWorkflowStage stage) throws IOException {
            if (stageClosedAfterRejection(stage)) {
                drawRoleSignOffCard(
                    stage.getDisplayLabel(),
                    "CLOSED",
                    "-",
                    "-",
                    "Closed",
                    null,
                    "-",
                    MUTED_COLOR
                );
                return;
            }
            ManagerReview review = latestStaffReview(stage);
            Member reviewer = review == null ? null : staffReviewers.get(review.getManagerMemberId());
            drawRoleSignOffCard(
                stage.getDisplayLabel(),
                review == null ? "PENDING REVIEW" : "REVIEW COMPLETED",
                review == null ? "________________________________" : reviewerLabel(review, reviewer),
                review == null ? "________________________________" : sanitizePdfText(review.getReasons()),
                review == null ? "-" : humanizeValue(review.getDecision()),
                review == null ? null : staffSignOffText(review, reviewer),
                review == null ? "________________________________" : sanitizePdfText(formatTimestamp(staffSignOffTimestamp(review))),
                stage == ApprovalWorkflowStage.ACCOUNTANT ? BRAND_GREEN : BRAND_NAVY
            );
        }

        private void drawRoleSignOffCard(String role,
                                         String status,
                                         String reviewer,
                                         String note,
                                         String decision,
                                         String signature,
                                         String signedAt,
                                         Color accent) throws IOException {
            float height = 112f;
            ensureSpace(height + 10f);
            float topY = y;
            stream.setNonStrokingColor(Color.WHITE);
            stream.addRect(MARGIN, topY - height, contentWidth(), height);
            stream.fill();
            stream.setStrokingColor(BORDER_COLOR);
            stream.addRect(MARGIN, topY - height, contentWidth(), height);
            stream.stroke();
            stream.setNonStrokingColor(accent);
            stream.addRect(MARGIN, topY - height, 5f, height);
            stream.fill();

            writeText(role.toUpperCase(Locale.ROOT), MARGIN + 16f, topY - 22f, bold, SECTION_SIZE + 1f, TEXT_COLOR);
            writeRightAligned(status, page.getMediaBox().getWidth() - MARGIN - 12f, topY - 21f, bold, SMALL_SIZE, BRAND_GREEN);
            writeText("Reviewer", MARGIN + 16f, topY - 48f, regular, SMALL_SIZE, MUTED_COLOR);
            writeText(reviewer, MARGIN + 84f, topY - 48f, bold, BODY_SIZE, TEXT_COLOR);
            writeText("Decision", MARGIN + 16f, topY - 67f, regular, SMALL_SIZE, MUTED_COLOR);
            writeText(decision, MARGIN + 84f, topY - 67f, regular, BODY_SIZE, TEXT_COLOR);
            writeText("Review note", MARGIN + 16f, topY - 86f, regular, SMALL_SIZE, MUTED_COLOR);
            String safeNote = wrapText(note, regular, BODY_SIZE, contentWidth() * 0.42f).getFirst();
            writeText(safeNote, MARGIN + 84f, topY - 86f, regular, BODY_SIZE, TEXT_COLOR);

            float signatureX = MARGIN + contentWidth() * 0.62f;
            String signatureText = includeRecordedSignatures ? signature : null;
            boolean recordedSignature = hasRecordedSignature(signatureText);
            if (recordedSignature) {
                writeText(signatureText, signatureX, topY - 58f, signatureFont, BODY_SIZE + 3f, TEXT_COLOR);
            }
            stream.setStrokingColor(MUTED_COLOR);
            stream.moveTo(signatureX, topY - 64f);
            stream.lineTo(page.getMediaBox().getWidth() - MARGIN - 14f, topY - 64f);
            stream.stroke();
            String signatureLabel = recordedSignature
                ? "Authorized signature"
                : includeRecordedSignatures ? "Signature not verified" : "Signature";
            writeText(signatureLabel, signatureX, topY - 78f, regular, SMALL_SIZE, MUTED_COLOR);
            writeText(
                "Signed: " + (recordedSignature ? signedAt : includeRecordedSignatures ? "-" : "________________"),
                signatureX,
                topY - 95f,
                regular,
                SMALL_SIZE,
                MUTED_COLOR
            );
            y -= height + 10f;
        }

        private void drawBoardSignOffPanel(ApprovalWorkflowStage stage) throws IOException {
            if (stageClosedAfterRejection(stage)) {
                drawRoleSignOffCard(
                    stage.getDisplayLabel(),
                    "CLOSED",
                    "-",
                    "-",
                    "Closed",
                    null,
                    "-",
                    MUTED_COLOR
                );
                return;
            }
            List<BoardReview> stageReviews = boardReviewsForStage(stage);
            if (stageReviews.isEmpty()) {
                drawRoleSignOffCard(
                    stage.getDisplayLabel(),
                    "PENDING REVIEW",
                    null,
                    "________________________________",
                    "-",
                    "________________________________",
                    "________________________________",
                    BRAND_NAVY
                );
                return;
            }
            for (BoardReview review : stageReviews) {
                Member boardMember = boardMembers.get(review.getBoardMemberId());
                drawRoleSignOffCard(
                    stage.getDisplayLabel(),
                    "REVIEW RECORDED",
                    boardMember == null ? shortId(review.getBoardMemberId()) : sanitizePdfText(boardMember.getFullName()),
                    sanitizePdfText(review.getComment()),
                    humanizeValue(review.getDecision()),
                    boardSignOffText(review, boardMember),
                    sanitizePdfText(formatTimestamp(boardSignOffTimestamp(review))),
                    BRAND_NAVY
                );
            }
        }

        private boolean boardStyleReviewStage(ApprovalWorkflowStage stage) {
            return stage == ApprovalWorkflowStage.LOAN_OFFICER
                || stage == ApprovalWorkflowStage.CHAIRPERSON
                || stage == ApprovalWorkflowStage.BOARD
                || stage == ApprovalWorkflowStage.CREDIT_COMMITTEE;
        }

        private List<BoardReview> boardReviewsForStage(ApprovalWorkflowStage stage) {
            return boardReviews.stream()
                .filter(review -> review.getReviewStage() == stage)
                .toList();
        }

        private boolean completedApplication() {
            return app.getStatus() == com.sacco.mvp.domain.LoanStatus.READY_FOR_DISBURSEMENT
                || app.getStatus() == com.sacco.mvp.domain.LoanStatus.DISBURSED
                || app.getStatus() == com.sacco.mvp.domain.LoanStatus.DEFAULTED
                || app.getStatus() == com.sacco.mvp.domain.LoanStatus.PAID;
        }

        private boolean stageClosedAfterRejection(ApprovalWorkflowStage stage) {
            return stageClosedAfterRejection(stage, configuredReviewStages());
        }

        private boolean stageClosedAfterRejection(ApprovalWorkflowStage stage, List<ApprovalWorkflowStage> stages) {
            ApprovalWorkflowStage rejectedStage = rejectedReviewStage();
            if (stage == null || rejectedStage == null || stages == null || stages.isEmpty()) {
                return false;
            }
            int rejectedIndex = stages.indexOf(rejectedStage);
            int stageIndex = stages.indexOf(stage);
            return rejectedIndex >= 0 && stageIndex > rejectedIndex;
        }

        private ApprovalWorkflowStage rejectedReviewStage() {
            if (app == null || app.getStatus() == null || !isRejectedStatus(app.getStatus())) {
                return null;
            }
            return switch (app.getStatus()) {
                case MANAGER_REJECTED -> ApprovalWorkflowStage.MANAGER;
                case LOAN_OFFICER_REJECTED -> ApprovalWorkflowStage.LOAN_OFFICER;
                case CHAIRPERSON_REJECTED -> ApprovalWorkflowStage.CHAIRPERSON;
                case BOARD_REJECTED -> ApprovalWorkflowStage.BOARD;
                case CREDIT_COMMITTEE_REJECTED -> ApprovalWorkflowStage.CREDIT_COMMITTEE;
                case ACCOUNTANT_REJECTED -> ApprovalWorkflowStage.ACCOUNTANT;
                default -> null;
            };
        }

        private ManagerReview latestStaffReview(ApprovalWorkflowStage stage) {
            ManagerReview latest = null;
            for (ManagerReview review : staffReviews) {
                if (review.getReviewStage() == stage) {
                    latest = review;
                }
            }
            return latest;
        }

        private String reviewerLabel(ManagerReview review, Member reviewer) {
            if (reviewer == null) {
                return shortId(review.getManagerMemberId());
            }
            return sanitizePdfText(reviewer.getFullName());
        }

        private String staffSignOffText(ManagerReview review, Member reviewer) {
            String signature = LoanPresentationService.this.staffReviewSignatureText(review, reviewer);
            return signature == null || signature.isBlank() ? null : sanitizePdfText(signature);
        }

        private java.time.OffsetDateTime staffSignOffTimestamp(ManagerReview review) {
            if (review == null) {
                return null;
            }
            return review.getManagerSignatureVerifiedAt() == null
                ? review.getCreatedAt()
                : review.getManagerSignatureVerifiedAt();
        }

        private String boardSignOffText(BoardReview review, Member reviewer) {
            String signature = LoanPresentationService.this.boardReviewSignatureText(review, reviewer);
            return signature == null || signature.isBlank() ? null : sanitizePdfText(signature);
        }

        private java.time.OffsetDateTime boardSignOffTimestamp(BoardReview review) {
            if (review == null) {
                return null;
            }
            if (review.getBoardSignatureVerifiedAt() != null) {
                return review.getBoardSignatureVerifiedAt();
            }
            return review.getDecidedAt() == null ? review.getCreatedAt() : review.getDecidedAt();
        }

        private void drawApplicantSignatureSection() throws IOException {
            String signatureText = app.getApplicantSignatureText();
            if ((signatureText == null || signatureText.isBlank()) && applicant.getSignatureText() != null && !applicant.getSignatureText().isBlank()) {
                signatureText = applicant.getSignatureText();
            }
            ensureSpace(100f);
            drawSuperSectionStrip("Applicant Declaration and Signature", "Verified applicant signature");
            drawApplicantSignatureTable(signatureText);
        }

        private void drawApplicantSignatureTable(String signatureText) throws IOException {
            String displayedSignature = app.getApplicantSignatureVerifiedAt() != null && signatureText != null && !signatureText.isBlank()
                ? sanitizePdfText(signatureText)
                : "-";
            drawTable(
                new String[]{"Applicant", "Member Number", "Declaration", "Signature", "Verified At"},
                new float[]{112f, 80f, 126f, 116f, contentWidth() - 434f},
                List.<String[]>of(new String[]{
                    sanitizePdfText(applicant.getFullName()),
                    sanitizePdfText(applicant.getMemberNo()),
                    "Application details confirmed",
                    displayedSignature,
                    sanitizePdfText(formatTimestamp(app.getApplicantSignatureVerifiedAt()))
                }),
                SMALL_SIZE,
                BODY_SIZE,
                14f
            );
        }

        private String valueOrDash(String value) {
            return value == null || value.isBlank() ? "-" : value;
        }

        private boolean hasRecordedSignature(String signatureText) {
            return includeRecordedSignatures
                && signatureText != null
                && !signatureText.isBlank();
        }

        private void drawSectionHeading(String text) throws IOException {
            ensureSpace(SECTION_TOP_GAP + SECTION_SIZE + SECTION_BOTTOM_GAP);
            y -= SECTION_TOP_GAP;
            writeText(sanitizePdfText(text), MARGIN, y, bold, SECTION_SIZE, TEXT_COLOR);
            y -= SECTION_BOTTOM_GAP;
        }

        private void drawParagraph(String text, PDType1Font font, float fontSize, Color color) throws IOException {
            float width = contentWidth();
            List<String> lines = wrapText(text, font, fontSize, width);
            for (String line : lines) {
                ensureSpace(fontSize + LINE_GAP + 2f);
                writeText(line, MARGIN, y, font, fontSize, color);
                y -= fontSize + LINE_GAP;
            }
        }

        private void drawTable(String[] headers,
                               float[] widths,
                               List<String[]> rows,
                               float headerFontSize,
                               float bodyFontSize,
                               float gapAfter) throws IOException {
            drawTable(headers, widths, rows, headerFontSize, bodyFontSize, gapAfter, CELL_PADDING_Y, 2f);
        }

        private void drawTable(String[] headers,
                               float[] widths,
                               List<String[]> rows,
                               float headerFontSize,
                               float bodyFontSize,
                               float gapAfter,
                               float cellPaddingY,
                               float extraLineGap) throws IOException {
            drawRow(headers, widths, bold, headerFontSize, true, cellPaddingY, extraLineGap);
            for (String[] row : rows) {
                if (row == null) {
                    continue;
                }
                float rowHeight = measureRowHeight(row, widths, regular, bodyFontSize, cellPaddingY, extraLineGap);
                if (y - rowHeight < (BOTTOM_MARGIN + FOOTER_GAP)) {
                    startNewPage();
                    drawRow(headers, widths, bold, headerFontSize, true, cellPaddingY, extraLineGap);
                }
                drawRow(row, widths, regular, bodyFontSize, false, cellPaddingY, extraLineGap);
            }
            y -= gapAfter;
        }

        private void drawRow(String[] cells,
                             float[] widths,
                             PDType1Font font,
                             float fontSize,
                             boolean header) throws IOException {
            drawRow(cells, widths, font, fontSize, header, CELL_PADDING_Y, 2f);
        }

        private void drawRow(String[] cells,
                             float[] widths,
                             PDType1Font font,
                             float fontSize,
                             boolean header,
                             float cellPaddingY,
                             float extraLineGap) throws IOException {
            float rowHeight = measureRowHeight(cells, widths, font, fontSize, cellPaddingY, extraLineGap);
            ensureSpace(rowHeight);

            float x = MARGIN;
            float lineHeight = fontSize + extraLineGap;
            List<List<String>> wrappedCells = new ArrayList<>();
            for (int i = 0; i < cells.length; i++) {
                wrappedCells.add(wrapText(cells[i], font, fontSize, widths[i] - (CELL_PADDING_X * 2f)));
            }

            for (int i = 0; i < cells.length; i++) {
                stream.setStrokingColor(BORDER_COLOR);
                stream.addRect(x, y - rowHeight, widths[i], rowHeight);
                stream.stroke();

                float textY = y - cellPaddingY - fontSize;
                for (String line : wrappedCells.get(i)) {
                    writeText(line, x + CELL_PADDING_X, textY, font, fontSize, TEXT_COLOR);
                    textY -= lineHeight;
                }
                x += widths[i];
            }
            y -= rowHeight;
        }

        private float measureRowHeight(String[] cells,
                                       float[] widths,
                                       PDType1Font font,
                                       float fontSize) throws IOException {
            return measureRowHeight(cells, widths, font, fontSize, CELL_PADDING_Y, 2f);
        }

        private float measureRowHeight(String[] cells,
                                       float[] widths,
                                       PDType1Font font,
                                       float fontSize,
                                       float cellPaddingY,
                                       float extraLineGap) throws IOException {
            float lineHeight = fontSize + extraLineGap;
            int maxLines = 1;
            for (int i = 0; i < cells.length; i++) {
                List<String> lines = wrapText(cells[i], font, fontSize, widths[i] - (CELL_PADDING_X * 2f));
                maxLines = Math.max(maxLines, lines.size());
            }
            return (cellPaddingY * 2f) + (maxLines * lineHeight);
        }

        private List<String> wrapText(String text,
                                      PDType1Font font,
                                      float fontSize,
                                      float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            String safeText = sanitizePdfText(text);
            for (String paragraph : safeText.split("\\n", -1)) {
                if (paragraph.isBlank()) {
                    lines.add("-");
                    continue;
                }
                lines.addAll(wrapParagraph(paragraph, font, fontSize, maxWidth));
            }
            return lines.isEmpty() ? List.of("-") : lines;
        }

        private List<String> wrapParagraph(String text,
                                           PDType1Font font,
                                           float fontSize,
                                           float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String token : text.split("\\s+")) {
                List<String> pieces = splitLongToken(token, font, fontSize, maxWidth);
                for (String piece : pieces) {
                    String candidate = current.isEmpty() ? piece : current + " " + piece;
                    if (stringWidth(candidate, font, fontSize) > maxWidth && !current.isEmpty()) {
                        lines.add(current.toString());
                        current = new StringBuilder(piece);
                    } else {
                        current = new StringBuilder(candidate);
                    }
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines.isEmpty() ? List.of("-") : lines;
        }

        private List<String> splitLongToken(String token,
                                            PDType1Font font,
                                            float fontSize,
                                            float maxWidth) throws IOException {
            if (stringWidth(token, font, fontSize) <= maxWidth) {
                return List.of(token);
            }
            List<String> parts = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (char ch : token.toCharArray()) {
                String candidate = current.toString() + ch;
                if (stringWidth(candidate, font, fontSize) > maxWidth && !current.isEmpty()) {
                    parts.add(current.toString());
                    current = new StringBuilder(String.valueOf(ch));
                } else {
                    current.append(ch);
                }
            }
            if (!current.isEmpty()) {
                parts.add(current.toString());
            }
            return parts;
        }

        private float stringWidth(String text, PDType1Font font, float fontSize) throws IOException {
            return font.getStringWidth(text) / 1000f * fontSize;
        }

        private void drawRule() throws IOException {
            stream.setStrokingColor(RULE_COLOR);
            stream.moveTo(MARGIN, y);
            stream.lineTo(page.getMediaBox().getWidth() - MARGIN, y);
            stream.stroke();
        }

        private void drawFooter() throws IOException {
            float footerY = BOTTOM_MARGIN + 10f;
            stream.setStrokingColor(BORDER_COLOR);
            stream.moveTo(MARGIN, footerY + 10f);
            stream.lineTo(page.getMediaBox().getWidth() - MARGIN, footerY + 10f);
            stream.stroke();
            writeText("Prepared for printing", MARGIN, footerY, regular, 7.4f, MUTED_COLOR);
            writeRightAligned("Date: " + LocalDate.now(), page.getMediaBox().getWidth() - MARGIN, footerY, regular, 7.4f, MUTED_COLOR);
        }

        private void writeText(String text,
                               float x,
                               float baselineY,
                               PDType1Font font,
                               float fontSize,
                               Color color) throws IOException {
            stream.beginText();
            stream.setNonStrokingColor(color);
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(x, baselineY);
            stream.showText(sanitizePdfLineText(text));
            stream.endText();
        }

        private void writeRightAligned(String text,
                                       float rightX,
                                       float baselineY,
                                       PDType1Font font,
                                       float fontSize,
                                       Color color) throws IOException {
            float width = stringWidth(sanitizePdfLineText(text), font, fontSize);
            writeText(text, rightX - width, baselineY, font, fontSize, color);
        }

        private void writeCentered(String text,
                                   float centerX,
                                   float baselineY,
                                   PDType1Font font,
                                   float fontSize,
                                   Color color) throws IOException {
            float width = stringWidth(sanitizePdfLineText(text), font, fontSize);
            writeText(text, centerX - (width / 2f), baselineY, font, fontSize, color);
        }

        private float contentWidth() {
            return page.getMediaBox().getWidth() - (MARGIN * 2f);
        }
    }

}
