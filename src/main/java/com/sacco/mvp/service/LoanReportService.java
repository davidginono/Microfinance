package com.sacco.mvp.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sacco.mvp.domain.BoardDecision;
import com.sacco.mvp.domain.BoardReview;
import com.sacco.mvp.domain.GuarantorRequest;
import com.sacco.mvp.domain.GuarantorRequestStatus;
import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.ApprovalWorkflowStage;
import com.sacco.mvp.domain.ManagerDecision;
import com.sacco.mvp.domain.ManagerReview;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.domain.LoanProductSetting;
import com.sacco.mvp.domain.LoanType;
import com.sacco.mvp.domain.Position;
import com.sacco.mvp.domain.RegisteredSacco;
import com.sacco.mvp.domain.SaccoStation;
import com.sacco.mvp.repository.BoardReviewRepository;
import com.sacco.mvp.repository.GuarantorRequestRepository;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.repository.ManagerReviewRepository;
import com.sacco.mvp.repository.MemberRepository;
import com.sacco.mvp.repository.RegisteredSaccoRepository;
import com.sacco.mvp.repository.SaccoStationRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisCrosses;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoanReportService {
    private static final List<LoanStatus> DISBURSED_STATUSES = List.of(
        LoanStatus.DISBURSED,
        LoanStatus.DEFAULTED,
        LoanStatus.PAID
    );
    private static final List<LoanStatus> REJECTED_STATUSES = List.of(
        LoanStatus.MANAGER_REJECTED,
        LoanStatus.LOAN_OFFICER_REJECTED,
        LoanStatus.CHAIRPERSON_REJECTED,
        LoanStatus.BOARD_REJECTED,
        LoanStatus.CREDIT_COMMITTEE_REJECTED,
        LoanStatus.ACCOUNTANT_REJECTED,
        LoanStatus.REJECTED
    );
    private static final List<LoanStatus> ACTIVE_STATUSES = List.of(
        LoanStatus.DISBURSED,
        LoanStatus.DEFAULTED
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HUMAN_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanProductSettingRepository loanProductSettingRepository;
    private final MemberRepository memberRepository;
    private final GuarantorRequestRepository guarantorRequestRepository;
    private final BoardReviewRepository boardReviewRepository;
    private final ManagerReviewRepository managerReviewRepository;
    private final ObjectMapper objectMapper;
    private final LoanAnalyticsService loanAnalyticsService;
    private final RegisteredSaccoRepository registeredSaccoRepository;
    private final SaccoStationRepository saccoStationRepository;
    private final ApplicationClock applicationClock;
    private final SaccoLogoStorageService saccoLogoStorageService;

    public MemberLoanReport memberReport(UUID memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        List<LoanApplication> loans = loanApplicationRepository.findByApplicantMemberIdOrderByCreatedAtDesc(memberId).stream()
            .sorted(Comparator.comparing(this::memberReportSortDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        List<LoanApplication> summaryLoans = loans.stream()
            .filter(loan -> DISBURSED_STATUSES.contains(loan.getStatus()))
            .toList();
        return new MemberLoanReport(member, summarize(summaryLoans), summarizeMemberAnalytics(loans), loans, buildMemberLoanDetails(member, loans));
    }

    public ManagerLoanReport managerReport(String saccoId, Integer year, boolean returnedOnly) {
        int effectiveYear = year == null ? applicationClock.today().getYear() : year;
        LocalDate fromDate = LocalDate.of(effectiveYear, 1, 1);
        LocalDate toDate = LocalDate.of(effectiveYear, 12, 31);
        List<LoanApplication> loans = loanApplicationRepository.findBySaccoIdAndStatusInOrderByCreatedAtAsc(
                saccoId, DISBURSED_STATUSES).stream()
            .filter(loan -> loan.getDisbursementDate() != null)
            .filter(loan -> fromDate == null || !loan.getDisbursementDate().isBefore(fromDate))
            .filter(loan -> toDate == null || !loan.getDisbursementDate().isAfter(toDate))
            .filter(loan -> !returnedOnly || loan.getStatus() == LoanStatus.PAID)
            .sorted(Comparator.comparing(LoanApplication::getDisbursementDate, Comparator.reverseOrder())
                .thenComparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        Set<UUID> applicantIds = loans.stream().map(LoanApplication::getApplicantMemberId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, Member> applicantMap = new LinkedHashMap<>();
        if (!applicantIds.isEmpty()) {
            for (Member member : memberRepository.findAllById(applicantIds)) {
                applicantMap.put(member.getId(), member);
            }
        }
        return new ManagerLoanReport(saccoId, effectiveYear, returnedOnly, summarize(loans), loans, applicantMap);
    }

    public List<Integer> managerReportYears(String saccoId) {
        List<Integer> years = loanApplicationRepository.findBySaccoIdAndStatusInOrderByCreatedAtAsc(
                saccoId, DISBURSED_STATUSES).stream()
            .map(LoanApplication::getDisbursementDate)
            .filter(java.util.Objects::nonNull)
            .map(LocalDate::getYear)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        int currentYear = applicationClock.today().getYear();
        if (!years.contains(currentYear)) {
            years.add(0, currentYear);
        }
        return years;
    }

    public AccountantLoanReport accountantReport(UUID accountantId,
                                                 String saccoId,
                                                 String stationId,
                                                 LocalDate fromDate,
                                                 LocalDate toDate,
                                                 String decisionFilter) {
        LocalDate effectiveTo = toDate == null ? applicationClock.today() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfMonth(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("From date cannot be after to date.");
        }

        OffsetDateTime fromAt = applicationClock.startOfDay(effectiveFrom);
        OffsetDateTime toAt = applicationClock.endOfDay(effectiveTo);
        ManagerDecision expectedDecision = resolveAccountantDecisionFilter(decisionFilter);
        String effectiveFilter = expectedDecision == null
            ? "ALL"
            : (expectedDecision == ManagerDecision.ACCEPT ? "APPROVED" : "REJECTED");

        List<ManagerReview> reviews = managerReviewRepository
            .findByManagerMemberIdAndReviewStageAndCreatedAtBetweenOrderByCreatedAtDesc(
                accountantId, ApprovalWorkflowStage.ACCOUNTANT, fromAt, toAt).stream()
            .filter(review -> expectedDecision == null || review.getDecision() == expectedDecision)
            .toList();

        Map<UUID, LoanApplication> loanMap = loanApplicationRepository.findAllById(
                reviews.stream().map(ManagerReview::getLoanApplicationId).collect(Collectors.toSet()))
            .stream()
            .filter(loan -> loan.getSaccoId().equals(saccoId))
            .filter(loan -> matchesApplicantStation(loan, stationId))
            .collect(Collectors.toMap(LoanApplication::getId, loan -> loan, (left, right) -> left, LinkedHashMap::new));

        List<AccountantReviewEntry> entries = reviews.stream()
            .map(review -> {
                LoanApplication loan = loanMap.get(review.getLoanApplicationId());
                return loan == null ? null : new AccountantReviewEntry(review, loan);
            })
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing((AccountantReviewEntry entry) -> entry.review().getCreatedAt()).reversed())
            .toList();

        Map<UUID, Member> applicantMap = memberRepository.findAllById(
                entries.stream().map(entry -> entry.loan().getApplicantMemberId()).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> member, (left, right) -> left, LinkedHashMap::new));

        long approvedCount = entries.stream().filter(entry -> entry.review().getDecision() == ManagerDecision.ACCEPT).count();
        long rejectedCount = entries.stream().filter(entry -> entry.review().getDecision() == ManagerDecision.REJECT).count();
        BigDecimal totalAmount = entries.stream()
            .map(entry -> entry.loan().getAmount())
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new AccountantLoanReport(
            accountantId,
            saccoId,
            effectiveFrom,
            effectiveTo,
            effectiveFilter,
            new AccountantReviewSummary(entries.size(), approvedCount, rejectedCount, totalAmount),
            entries,
            applicantMap
        );
    }

    public ManagerWorkflowReport managerWorkflowReport(UUID managerId,
                                                       String saccoId,
                                                       String stationId,
                                                       LocalDate fromDate,
                                                       LocalDate toDate,
                                                       String decisionFilter) {
        LocalDate effectiveTo = toDate == null ? applicationClock.today() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfMonth(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("From date cannot be after to date.");
        }

        OffsetDateTime fromAt = applicationClock.startOfDay(effectiveFrom);
        OffsetDateTime toAt = applicationClock.endOfDay(effectiveTo);
        ManagerDecision expectedDecision = resolveAccountantDecisionFilter(decisionFilter);
        String effectiveFilter = expectedDecision == null
            ? "ALL"
            : (expectedDecision == ManagerDecision.ACCEPT ? "APPROVED" : "REJECTED");

        List<ManagerReview> reviews = managerReviewRepository
            .findByManagerMemberIdAndReviewStageAndCreatedAtBetweenOrderByCreatedAtDesc(
                managerId, ApprovalWorkflowStage.MANAGER, fromAt, toAt).stream()
            .filter(review -> expectedDecision == null || review.getDecision() == expectedDecision)
            .toList();

        Map<UUID, LoanApplication> loanMap = loanApplicationRepository.findAllById(
                reviews.stream().map(ManagerReview::getLoanApplicationId).collect(Collectors.toSet()))
            .stream()
            .filter(loan -> loan.getSaccoId().equals(saccoId))
            .filter(loan -> matchesApplicantStation(loan, stationId))
            .collect(Collectors.toMap(LoanApplication::getId, loan -> loan, (left, right) -> left, LinkedHashMap::new));

        List<ManagerWorkflowEntry> entries = reviews.stream()
            .map(review -> {
                LoanApplication loan = loanMap.get(review.getLoanApplicationId());
                return loan == null ? null : new ManagerWorkflowEntry(review, loan);
            })
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing((ManagerWorkflowEntry entry) -> entry.review().getCreatedAt()).reversed())
            .toList();

        Map<UUID, Member> applicantMap = memberRepository.findAllById(
                entries.stream().map(entry -> entry.loan().getApplicantMemberId()).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> member, (left, right) -> left, LinkedHashMap::new));

        long approvedCount = entries.stream().filter(entry -> entry.review().getDecision() == ManagerDecision.ACCEPT).count();
        long rejectedCount = entries.stream().filter(entry -> entry.review().getDecision() == ManagerDecision.REJECT).count();
        BigDecimal totalAmount = entries.stream()
            .map(entry -> entry.loan().getAmount())
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ManagerWorkflowReport(
            managerId,
            saccoId,
            effectiveFrom,
            effectiveTo,
            effectiveFilter,
            new ManagerWorkflowSummary(entries.size(), approvedCount, rejectedCount, totalAmount),
            entries,
            applicantMap
        );
    }

    public BoardWorkflowReport boardWorkflowReport(UUID reviewerId,
                                                   String saccoId,
                                                   String stationId,
                                                   ApprovalWorkflowStage reviewStage,
                                                   LocalDate fromDate,
                                                   LocalDate toDate,
                                                   String decisionFilter) {
        LocalDate effectiveTo = toDate == null ? applicationClock.today() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfMonth(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("From date cannot be after to date.");
        }

        OffsetDateTime fromAt = applicationClock.startOfDay(effectiveFrom);
        OffsetDateTime toExclusive = applicationClock.dayAfter(effectiveTo);
        BoardDecision expectedDecision = resolveBoardDecisionFilter(decisionFilter);
        String effectiveFilter = expectedDecision == null ? "ALL" : expectedDecision.name();

        List<BoardReview> reviews = boardReviewRepository
            .findForAnalytics(reviewerId, reviewStage, fromAt, toExclusive).stream()
            .filter(review -> review.getDecision() != BoardDecision.PENDING)
            .filter(review -> expectedDecision == null || review.getDecision() == expectedDecision)
            .toList();

        Map<UUID, LoanApplication> loanMap = loanApplicationRepository.findAllById(
                reviews.stream().map(BoardReview::getLoanApplicationId).collect(Collectors.toSet()))
            .stream()
            .filter(loan -> loan.getSaccoId().equals(saccoId))
            .filter(loan -> matchesApplicantStation(loan, stationId))
            .collect(Collectors.toMap(LoanApplication::getId, loan -> loan, (left, right) -> left, LinkedHashMap::new));

        List<BoardWorkflowEntry> entries = reviews.stream()
            .map(review -> {
                LoanApplication loan = loanMap.get(review.getLoanApplicationId());
                return loan == null ? null : new BoardWorkflowEntry(review, loan);
            })
            .filter(Objects::nonNull)
            .sorted(Comparator.comparing((BoardWorkflowEntry entry) -> reviewSortTime(entry.review())).reversed())
            .toList();

        Map<UUID, Member> applicantMap = memberRepository.findAllById(
                entries.stream().map(entry -> entry.loan().getApplicantMemberId()).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> member, (left, right) -> left, LinkedHashMap::new));

        long approvedCount = entries.stream().filter(entry -> entry.review().getDecision() == BoardDecision.APPROVED).count();
        long rejectedCount = entries.stream().filter(entry -> entry.review().getDecision() == BoardDecision.REJECTED).count();
        BigDecimal totalAmount = entries.stream()
            .map(entry -> entry.loan().getAmount())
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new BoardWorkflowReport(
            reviewerId,
            saccoId,
            reviewStage,
            effectiveFrom,
            effectiveTo,
            effectiveFilter,
            new BoardWorkflowSummary(entries.size(), approvedCount, rejectedCount, totalAmount),
            entries,
            applicantMap
        );
    }

    public DisbursementLoanReport disbursementReport(UUID disbursementOfficerId,
                                                     String saccoId,
                                                     String stationId,
                                                     LocalDate fromDate,
                                                     LocalDate toDate) {
        LocalDate effectiveTo = toDate == null ? applicationClock.today() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.withDayOfMonth(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new IllegalArgumentException("From date cannot be after to date.");
        }

        OffsetDateTime fromAt = applicationClock.startOfDay(effectiveFrom);
        OffsetDateTime toAt = applicationClock.endOfDay(effectiveTo);
        List<ManagerReview> reviews = managerReviewRepository
            .findByManagerMemberIdAndReviewStageAndCreatedAtBetweenOrderByCreatedAtDesc(
                disbursementOfficerId, ApprovalWorkflowStage.DISBURSEMENT_OFFICER, fromAt, toAt).stream()
            .filter(review -> review.getDecision() == ManagerDecision.ACCEPT)
            .toList();

        Map<UUID, LoanApplication> loanMap = loanApplicationRepository.findAllById(
                reviews.stream().map(ManagerReview::getLoanApplicationId).collect(Collectors.toSet()))
            .stream()
            .filter(loan -> loan.getSaccoId().equals(saccoId))
            .filter(loan -> matchesApplicantStation(loan, stationId))
            .collect(Collectors.toMap(LoanApplication::getId, loan -> loan, (left, right) -> left, LinkedHashMap::new));

        List<DisbursementReviewEntry> entries = reviews.stream()
            .map(review -> {
                LoanApplication loan = loanMap.get(review.getLoanApplicationId());
                return loan == null ? null : new DisbursementReviewEntry(review, loan);
            })
            .filter(java.util.Objects::nonNull)
            .sorted(Comparator.comparing((DisbursementReviewEntry entry) -> entry.review().getCreatedAt()).reversed())
            .toList();

        Map<UUID, Member> applicantMap = memberRepository.findAllById(
                entries.stream().map(entry -> entry.loan().getApplicantMemberId()).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(Member::getId, member -> member, (left, right) -> left, LinkedHashMap::new));

        long paidCount = entries.stream().filter(entry -> entry.loan().getStatus() == LoanStatus.PAID).count();
        long defaultedCount = entries.stream().filter(entry -> entry.loan().getStatus() == LoanStatus.DEFAULTED).count();
        BigDecimal totalAmount = entries.stream()
            .map(entry -> entry.loan().getAmount())
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DisbursementLoanReport(
            disbursementOfficerId,
            saccoId,
            effectiveFrom,
            effectiveTo,
            new DisbursementReviewSummary(entries.size(), paidCount, defaultedCount, totalAmount),
            entries,
            applicantMap
        );
    }

    public StationAnalyticsExportReport stationAnalyticsReport(String saccoId,
                                                               String stationId,
                                                               LocalDate fromDate,
                                                               LocalDate toDate,
                                                               LoanType loanType,
                                                               String preparedBy,
                                                               String preparedByRole) {
        return stationAnalyticsReport(saccoId, stationId, fromDate, toDate, loanType, null, preparedBy, preparedByRole);
    }

    public StationAnalyticsExportReport stationAnalyticsReport(String saccoId,
                                                               String stationId,
                                                               LocalDate fromDate,
                                                               LocalDate toDate,
                                                               LoanType loanType,
                                                               UUID loanProductId,
                                                               String preparedBy,
                                                               String preparedByRole) {
        LocalDate effectiveTo = toDate == null ? applicationClock.today() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.minusYears(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            LocalDate swap = effectiveFrom;
            effectiveFrom = effectiveTo;
            effectiveTo = swap;
        }

        List<LoanApplication> loans = filterByLoanProductId(loanApplicationRepository.findScopeLoansForAnalytics(
            saccoId,
            stationId,
            applicationClock.startOfDay(effectiveFrom),
            applicationClock.dayAfter(effectiveTo),
            loanType,
            null
        ), loanProductId);
        List<LoanApplication> appliedLoans = loans.stream()
            .filter(loan -> loan.getStatus() != LoanStatus.DRAFT)
            .toList();
        List<LoanApplication> financialLoans = filterByLoanProductId(stationFinancialLoans(saccoId, stationId, loanType), loanProductId);
        long activeMembers = memberRepository.countActiveMemberAccountsForScope(saccoId, stationId);
        StationParticipationSummary participation = buildStationParticipation(activeMembers, appliedLoans);
        List<StationStatusRow> statusRows = stationStatusRows(appliedLoans);
        List<StationProductRow> productRows = stationProductRows(appliedLoans, financialLoans, loanType);
        List<StationYearlySummaryRow> yearlyRows = stationYearlyRows(appliedLoans, financialLoans, effectiveFrom, effectiveTo);

        return new StationAnalyticsExportReport(
            saccoId,
            saccoReportTitle(saccoId),
            stationId == null || stationId.isBlank() ? "-" : stationId,
            effectiveFrom,
            effectiveTo,
            loanType,
            preparedBy == null || preparedBy.isBlank() ? "System" : preparedBy,
            preparedByRole == null || preparedByRole.isBlank() ? "-" : preparedByRole,
            applicationClock.today(),
            statusRows,
            participation,
            productRows,
            yearlyRows,
            loanProductLabel(saccoId, loanProductId, loanType)
        );
    }

    public byte[] buildStationAnalyticsPdf(StationAnalyticsExportReport report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            StationPdfRenderer renderer = new StationPdfRenderer(document, report, saccoLogoBytes(report.saccoId()));
            renderer.render();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate station analytics PDF report.", ex);
        }
    }

    public byte[] buildStationAnalyticsExcel(StationAnalyticsExportReport report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ExcelStyles styles = new ExcelStyles(workbook);

            XSSFSheet summary = workbook.createSheet("Summary");
            writeStationExportSummarySheet(summary, report, styles);
            autosize(summary, 10);

            XSSFSheet status = workbook.createSheet("Loan Status Analysis");
            int row = titleRows(status, report.saccoName(), "LOAN STATUS ANALYSIS", styles);
            writeStationStatusTable(status, row, report.statusRows(), styles);
            autosize(status, 5);

            XSSFSheet product = workbook.createSheet("Product Performance");
            row = titleRows(product, report.saccoName(), "LOAN PRODUCT PERFORMANCE", styles);
            writeStationProductTable(product, row, report.productRows(), styles);
            autosize(product, 10);

            XSSFSheet trends = workbook.createSheet("Trends");
            row = titleRows(trends, report.saccoName(), "YEARLY LOAN TREND AND INTEREST SUMMARY", styles);
            int tableStart = writeStationYearlySummaryTable(trends, row, report.yearlyRows(), styles);
            makeStationYearlyValuesNumeric(trends, tableStart, report.yearlyRows().size());
            addStationYearlyChart(trends, tableStart + 1, report.yearlyRows().size());
            autosize(trends, 9);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate station analytics Excel report.", ex);
        }
    }

    public byte[] buildMemberPdf(MemberLoanReport report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            MemberPdfRenderer renderer = new MemberPdfRenderer(
                document,
                report,
                DATE_FORMATTER.format(applicationClock.today()),
                saccoLogoBytes(report.member().getSaccoId())
            );
            renderer.render();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate PDF report.", ex);
        }
    }

    public byte[] buildMemberExcel(MemberLoanReport report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Member Loan Report");
            int rowIndex = 0;
            Row header = sheet.createRow(rowIndex++);
            String[] columns = {
                "Loan Application ID",
                "Loan ID",
                "Member Details",
                "Approved Product",
                "Approved Amount",
                "Tenure",
                "Estimated Fee / Insurance / Processing Deductions",
                "Application Fee",
                "Insurance Fee",
                "Loan Processing Fee",
                "Total Deductions",
                "Guarantor Details",
                "Approval Decision Summary",
                "Disbursement Date",
                "Final Due Date",
                "Status",
                "Prepared By",
                "Prepared Date"
            };
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
            }
            for (MemberLoanDetail detail : report.details()) {
                Row row = sheet.createRow(rowIndex++);
                int column = 0;
                row.createCell(column++).setCellValue(detail.loanApplicationIdLabel());
                row.createCell(column++).setCellValue(detail.loanIdLabel());
                row.createCell(column++).setCellValue(detail.memberDetailsLabel());
                row.createCell(column++).setCellValue(detail.approvedProductLabel());
                row.createCell(column++).setCellValue(detail.approvedAmountLabel());
                row.createCell(column++).setCellValue(detail.tenureLabel());
                row.createCell(column++).setCellValue(detail.deductionSummaryLabel());
                row.createCell(column++).setCellValue(detail.applicationFeeLabel());
                row.createCell(column++).setCellValue(detail.insuranceFeeLabel());
                row.createCell(column++).setCellValue(detail.processingFeeLabel());
                row.createCell(column++).setCellValue(detail.totalDeductionsLabel());
                row.createCell(column++).setCellValue(detail.guarantorDetailsLabel());
                row.createCell(column++).setCellValue(detail.approvalDecisionSummaryLabel());
                row.createCell(column++).setCellValue(detail.disbursementDateLabel());
                row.createCell(column++).setCellValue(detail.finalDueDateLabel());
                row.createCell(column++).setCellValue(detail.statusLabel());
                row.createCell(column++).setCellValue(detail.preparedByLabel());
                row.createCell(column).setCellValue(detail.preparedDateLabel());
            }
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate Excel report.", ex);
        }
    }

    public AnalyticsExportReport memberAnalyticsExportReport(AppUserPrincipal principal,
                                                             LocalDate fromDate,
                                                             LocalDate toDate,
                                                             com.sacco.mvp.domain.LoanType loanType) {
        return memberAnalyticsExportReport(principal, fromDate, toDate, loanType, null);
    }

    public AnalyticsExportReport memberAnalyticsExportReport(AppUserPrincipal principal,
                                                             LocalDate fromDate,
                                                             LocalDate toDate,
                                                             com.sacco.mvp.domain.LoanType loanType,
                                                             UUID loanProductId) {
        DateRange range = resolveReportRange(fromDate, toDate);
        Member member = memberRepository.findById(principal.getMemberId())
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        List<LoanApplication> loans = filterByLoanProductId(loanApplicationRepository.findMemberLoansForAnalytics(
                principal.getMemberId(), startOfDay(range.fromDate()), dayAfter(range.toDate()), loanType, null)
            .stream()
            .sorted(Comparator.comparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList(), loanProductId);
        LoanAnalyticsService.MemberLoanAnalytics analytics =
            loanAnalyticsService.forMember(principal.getMemberId(), range.fromDate(), range.toDate(), loanType, loanProductId, null);
        List<LoanAnalyticsService.MetricTrendSeries> trendSeries =
            loanAnalyticsService.statusTrendForMember(principal.getMemberId(), range.fromDate(), range.toDate(), loanType, loanProductId, null);
        ExportContext context = exportContext(principal, range, loanType);
        return new AnalyticsExportReport(
            ReportKind.MEMBER,
            context.saccoName(),
            principal.getSaccoId(),
            "MEMBER LOAN REPORT",
            context.stationId(),
            context.branchName(),
            range.fromDate(),
            range.toDate(),
            applicationClock.today(),
            principal.getFullName(),
            exporterRoleLabel(principal),
            loanProductLabel(principal.getSaccoId(), loanProductId, loanType),
            member,
            analytics,
            memberPortfolio(analytics),
            productRowsFromLoans(loans),
            productFinancialRowsFromLoans(loans),
            null,
            trendSeries,
            recentActivityRows(loans),
            activeLoanRowsFromLoans(loans),
            financialSummary(loans, analytics),
            observations(analytics),
            "This report summarizes the loan performance and status of the member within the selected period."
        );
    }

    public List<ActiveLoanDetailRow> memberActiveLoanDetails(UUID memberId,
                                                             LocalDate fromDate,
                                                             LocalDate toDate,
                                                             com.sacco.mvp.domain.LoanType loanType) {
        DateRange range = resolveReportRange(fromDate, toDate);
        List<LoanApplication> loans = loanApplicationRepository.findMemberLoansForAnalytics(
                memberId, startOfDay(range.fromDate()), dayAfter(range.toDate()), loanType, null)
            .stream()
            .sorted(Comparator.comparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        return activeLoanRowsFromLoans(loans);
    }

    public AnalyticsExportReport staffAnalyticsExportReport(AppUserPrincipal principal,
                                                            LocalDate fromDate,
                                                            LocalDate toDate,
                                                            com.sacco.mvp.domain.LoanType loanType,
                                                            String viewAs) {
        return staffAnalyticsExportReport(principal, fromDate, toDate, loanType, null, viewAs);
    }

    public AnalyticsExportReport staffAnalyticsExportReport(AppUserPrincipal principal,
                                                            LocalDate fromDate,
                                                            LocalDate toDate,
                                                            com.sacco.mvp.domain.LoanType loanType,
                                                            UUID loanProductId,
                                                            String viewAs) {
        DateRange range = resolveReportRange(fromDate, toDate);
        boolean stationWideStaffView = "staff".equalsIgnoreCase(viewAs);
        LoanAnalyticsService.StaffReviewAnalytics staffReviewAnalytics = stationWideStaffView
            ? null
            : loanAnalyticsService.staffReviewAnalytics(principal, range.fromDate(), range.toDate(), loanType, loanProductId, null);
        LoanAnalyticsService.MemberLoanAnalytics analytics = stationWideStaffView
            ? loanAnalyticsService.forStation(principal.getSaccoId(), principal.getStationId(), range.fromDate(), range.toDate(), loanType, loanProductId, null)
            : loanAnalyticsService.forStaff(principal, range.fromDate(), range.toDate(), loanType, loanProductId, null);
        List<LoanAnalyticsService.MetricTrendSeries> trendSeries = stationWideStaffView
            ? loanAnalyticsService.statusTrendForStation(principal.getSaccoId(), principal.getStationId(), range.fromDate(), range.toDate(), loanType, loanProductId, null)
            : staffReviewAnalytics.trendSeries();
        LoanAnalyticsService.StaffPortfolioSummary portfolio = stationWideStaffView
            ? loanAnalyticsService.stationPortfolio(principal.getSaccoId(), principal.getStationId(), range.fromDate(), range.toDate(), loanType, loanProductId, null)
            : loanAnalyticsService.staffPortfolio(principal, range.fromDate(), range.toDate(), loanType, loanProductId, null);
        List<LoanApplication> stationLoans = filterByLoanProductId(stationWideStaffView
            ? loanApplicationRepository.findScopeLoansForAnalytics(
                principal.getSaccoId(), principal.getStationId(), startOfDay(range.fromDate()), dayAfter(range.toDate()), loanType, null)
            : loanAnalyticsService.loansForStaffAnalytics(principal, range.fromDate(), range.toDate(), loanType, null), loanProductId);
        List<LoanApplication> financialLoans = stationWideStaffView
            ? filterByLoanProductId(stationFinancialLoans(principal.getSaccoId(), principal.getStationId(), loanType), loanProductId)
            : stationLoans;
        List<ProductPerformanceRow> productRows = productRowsFromLoans(stationLoans, financialLoans);
        List<ProductFinancialBreakdownRow> productFinancialRows = productFinancialRowsFromLoans(financialLoans);
        ExportContext context = exportContext(principal, range, loanType);
        return new AnalyticsExportReport(
            stationWideStaffView ? ReportKind.STATION : ReportKind.STAFF,
            context.saccoName(),
            principal.getSaccoId(),
            stationWideStaffView ? "STATION LOAN STATUS REPORT" : "STAFF LOAN REVIEW REPORT",
            context.stationId(),
            context.branchName(),
            range.fromDate(),
            range.toDate(),
            applicationClock.today(),
            principal.getFullName(),
            exporterRoleLabel(principal),
            loanProductLabel(principal.getSaccoId(), loanProductId, loanType),
            null,
            analytics,
            portfolio,
            productRows,
            productFinancialRows,
            staffReviewAnalytics,
            trendSeries,
            stationWideStaffView ? recentActivityRows(stationLoans) : List.of(),
            List.of(),
            stationWideStaffView ? financialSummary(financialLoans, analytics) : financialSummary(List.of(), analytics),
            observations(analytics),
            stationWideStaffView
                ? "This report summarizes station loan performance and status within the selected period."
                : "This report summarizes loans handled by the staff member within the selected period."
        );
    }

    public byte[] buildMemberAnalyticsPdf(AnalyticsExportReport report) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            new FormalPdf(document, report, PDRectangle.A4, saccoLogoBytes(report.saccoId())).renderMemberTwoPage();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate member analytics PDF.", ex);
        }
    }

    public byte[] buildStationAnalyticsPdf(AnalyticsExportReport report) {
        if (report.kind() == ReportKind.MEMBER) {
            return buildMemberAnalyticsPdf(report);
        }
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            FormalPdf renderer = new FormalPdf(document, report, PDRectangle.A4, saccoLogoBytes(report.saccoId()));
            if (report.kind() == ReportKind.STAFF) {
                renderer.renderStaffTwoPage();
            } else {
                renderer.renderStationTwoPage();
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate station analytics PDF.", ex);
        }
    }

    public byte[] buildMemberAnalyticsExcel(AnalyticsExportReport report) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ExcelStyles styles = new ExcelStyles(workbook);
            XSSFSheet summary = workbook.createSheet("Summary");
            writeMemberSummarySheet(summary, report, styles);
            writeStatusSheet(workbook.createSheet("Status Analysis"), report, styles);
            writeProductSheet(workbook.createSheet("Product Performance"), report, styles);
            writeTrendSheet(workbook.createSheet("Trends"), report, styles);
            writeFinancialSheet(workbook.createSheet("Financial Summary"), report, styles);
            writeActivitySheet(workbook.createSheet("Activity Log"), report, styles);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate member analytics Excel report.", ex);
        }
    }

    public byte[] buildStationAnalyticsExcel(AnalyticsExportReport report) {
        if (report.kind() == ReportKind.MEMBER) {
            return buildMemberAnalyticsExcel(report);
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ExcelStyles styles = new ExcelStyles(workbook);
            if (report.kind() == ReportKind.STAFF) {
                writeStaffSummarySheet(workbook.createSheet("Summary"), report, styles);
                writeStaffProductReviewSheet(workbook.createSheet("Product Review Breakdown"), report, styles);
            } else {
                writeStationSummarySheet(workbook.createSheet("Summary"), report, styles);
                writeStatusSheet(workbook.createSheet("Loan Status Analysis"), report, styles);
                writeProductSheet(workbook.createSheet("Product Performance"), report, styles);
            }
            writeTrendSheet(workbook.createSheet("Trends"), report, styles);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate staff analytics Excel report.", ex);
        }
    }

    public byte[] buildManagerPdf(ManagerLoanReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Manager Disbursed Loans Report");
        lines.add("SACCO: " + report.saccoId());
        lines.add("Generated: " + DATE_FORMATTER.format(applicationClock.today()));
        lines.add("Year: " + report.year());
        lines.add("Returned Only: " + (report.returnedOnly() ? "Yes" : "No"));
        lines.add("");
        lines.add("Summary");
        lines.add("Disbursed loans in report: " + report.summary().disbursedCount());
        lines.add("Returned / paid: " + report.summary().paidCount());
        lines.add("Ongoing loans: " + report.summary().ongoingCount());
        lines.add("Disbursed amount: " + formatMoney(report.summary().disbursedAmount()));
        lines.add("Returned amount: " + formatMoney(report.summary().paidAmount()));
        lines.add("");
        lines.add("Loan List");
        for (LoanApplication loan : report.loans()) {
            Member applicant = report.applicantMap().get(loan.getApplicantMemberId());
            lines.add(shortLoanRow(loan, applicant));
        }
        return renderPdf(lines, report.saccoId());
    }

    public byte[] buildAccountantPdf(AccountantLoanReport report) {
        List<String[]> rows = new ArrayList<>();
        for (AccountantReviewEntry entry : report.entries()) {
            Member applicant = report.applicantMap().get(entry.loan().getApplicantMemberId());
            LoanApplication loan = entry.loan();
            rows.add(new String[]{
                applicationLabel(loan),
                loanIdLabel(loan),
                applicantLabel(applicant),
                loanTypeLabel(loan),
                formatMoney(loan.getAmount()),
                entry.review().getDecision() == ManagerDecision.ACCEPT ? "Ready for Disbursement" : "Rejected",
                formatTimestamp(entry.review().getCreatedAt()),
                humanizeLoanStatusForReport(loan.getStatus())
            });
        }
        return renderStaffReviewPdf(
            report.saccoId(),
            "ACCOUNTANT REVIEW REPORTS",
            "Accountant Decisions on Loan Applications",
            "Decision Filter: " + accountantDecisionFilterLabel(report.decisionFilter())
                + " | Reviewed: " + report.summary().reviewedCount()
                + " | Amount: " + formatMoney(report.summary().reviewedAmount()),
            new String[]{"LOAN APPLICATION ID", "LOAN ID", "APPLICANT", "LOAN TYPE", "AMOUNT", "DECISION", "REVIEWED AT", "CURRENT STATUS"},
            new float[]{78f, 66f, 80f, 70f, 72f, 74f, 74f, 84f},
            rows,
            "No accountant-reviewed loans matched the selected period.",
            "Accountant Review Reports",
            report.fromDate(),
            report.toDate()
        );
    }

    public byte[] buildManagerWorkflowPdf(ManagerWorkflowReport report) {
        List<String[]> rows = new ArrayList<>();
        for (ManagerWorkflowEntry entry : report.entries()) {
            Member applicant = report.applicantMap().get(entry.loan().getApplicantMemberId());
            LoanApplication loan = entry.loan();
            rows.add(new String[]{
                applicationLabel(loan),
                loanIdLabel(loan),
                applicantLabel(applicant),
                loanTypeLabel(loan),
                formatMoney(loan.getAmount()),
                entry.review().getDecision() == ManagerDecision.ACCEPT ? "Approved" : "Rejected",
                formatTimestamp(entry.review().getCreatedAt()),
                humanizeLoanStatusForReport(loan.getStatus())
            });
        }
        return renderStaffReviewPdf(
            report.saccoId(),
            "MANAGER REVIEW REPORTS",
            "Manager Decisions on Loan Applications",
            "Decision Filter: " + managerDecisionFilterLabel(report.decisionFilter())
                + " | Reviewed: " + report.summary().reviewedCount()
                + " | Amount: " + formatMoney(report.summary().reviewedAmount()),
            new String[]{"LOAN APPLICATION ID", "LOAN ID", "APPLICANT", "LOAN TYPE", "AMOUNT", "MANAGER DECISION", "REVIEWED AT", "CURRENT STATUS"},
            new float[]{78f, 66f, 80f, 70f, 72f, 74f, 74f, 84f},
            rows,
            "No manager-reviewed loans matched the selected period.",
            "Manager Review Reports",
            report.fromDate(),
            report.toDate()
        );
    }

    public byte[] buildBoardWorkflowPdf(BoardWorkflowReport report) {
        return buildBoardStageWorkflowPdf(
            report,
            "BOARD REVIEW REPORTS",
            "Board Decisions on Loan Applications",
            "BOARD DECISION",
            "No board-reviewed loans matched the selected period.",
            "Board Review Reports"
        );
    }

    public byte[] buildLoanOfficerWorkflowPdf(BoardWorkflowReport report) {
        return buildBoardStageWorkflowPdf(
            report,
            "LOAN OFFICER REVIEW REPORTS",
            "Loan Officer Decisions on Loan Applications",
            "LOAN OFFICER DECISION",
            "No loan officer-reviewed loans matched the selected period.",
            "Loan Officer Review Reports"
        );
    }

    private byte[] buildBoardStageWorkflowPdf(BoardWorkflowReport report,
                                              String title,
                                              String subtitle,
                                              String decisionHeader,
                                              String emptyMessage,
                                              String footerTitle) {
        List<String[]> rows = new ArrayList<>();
        for (BoardWorkflowEntry entry : report.entries()) {
            Member applicant = report.applicantMap().get(entry.loan().getApplicantMemberId());
            LoanApplication loan = entry.loan();
            rows.add(new String[]{
                applicationLabel(loan),
                loanIdLabel(loan),
                applicantLabel(applicant),
                loanTypeLabel(loan),
                formatMoney(loan.getAmount()),
                boardDecisionLabel(entry.review().getDecision()),
                formatTimestamp(reviewSortTime(entry.review())),
                humanizeLoanStatusForReport(loan.getStatus())
            });
        }
        return renderStaffReviewPdf(
            report.saccoId(),
            title,
            subtitle,
            "Decision Filter: " + boardDecisionFilterLabel(report.decisionFilter())
                + " | Reviewed: " + report.summary().reviewedCount()
                + " | Amount: " + formatMoney(report.summary().reviewedAmount()),
            new String[]{"LOAN APPLICATION ID", "LOAN ID", "APPLICANT", "LOAN TYPE", "AMOUNT", decisionHeader, "REVIEWED AT", "CURRENT STATUS"},
            new float[]{78f, 66f, 80f, 70f, 72f, 74f, 74f, 84f},
            rows,
            emptyMessage,
            footerTitle,
            report.fromDate(),
            report.toDate()
        );
    }

    public byte[] buildDisbursementPdf(DisbursementLoanReport report) {
        List<String[]> rows = new ArrayList<>();
        for (DisbursementReviewEntry entry : report.entries()) {
            Member applicant = report.applicantMap().get(entry.loan().getApplicantMemberId());
            LoanApplication loan = entry.loan();
            rows.add(new String[]{
                applicationLabel(loan),
                loanIdLabel(loan),
                applicantLabel(applicant),
                loanTypeLabel(loan),
                formatMoney(loan.getAmount()),
                formatTimestamp(entry.review().getCreatedAt()),
                humanizeLoanStatusForReport(loan.getStatus())
            });
        }
        return renderStaffReviewPdf(
            report.saccoId(),
            "DISBURSEMENT REPORTS",
            "Disbursement Decisions on Loan Applications",
            "Disbursed: " + report.summary().disbursedCount()
                + " | Paid: " + report.summary().paidCount()
                + " | Amount: " + formatMoney(report.summary().disbursedAmount()),
            new String[]{"LOAN APPLICATION ID", "LOAN ID", "APPLICANT", "LOAN TYPE", "AMOUNT", "DISBURSED AT", "CURRENT STATUS"},
            new float[]{84f, 70f, 88f, 74f, 76f, 78f, 100f},
            rows,
            "No disbursed loans matched the selected period.",
            "Disbursement Reports",
            report.fromDate(),
            report.toDate()
        );
    }

    private DateRange resolveReportRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate effectiveTo = toDate == null ? applicationClock.today() : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.minusYears(1) : fromDate;
        if (effectiveFrom.isAfter(effectiveTo)) {
            return new DateRange(effectiveTo, effectiveFrom);
        }
        return new DateRange(effectiveFrom, effectiveTo);
    }

    private OffsetDateTime startOfDay(LocalDate value) {
        return applicationClock.startOfDay(value);
    }

    private OffsetDateTime dayAfter(LocalDate value) {
        return applicationClock.dayAfter(value);
    }

    private ExportContext exportContext(AppUserPrincipal principal, DateRange range, com.sacco.mvp.domain.LoanType loanType) {
        String saccoName = registeredSaccoRepository.findById(principal.getSaccoId())
            .map(RegisteredSacco::getSaccoName)
            .filter(name -> !name.isBlank())
            .orElse(principal.getSaccoId());
        SaccoStation station = principal.getStationId() == null || principal.getStationId().isBlank()
            ? null
            : saccoStationRepository.findBySaccoIdAndStationId(principal.getSaccoId(), principal.getStationId()).orElse(null);
        String stationId = station == null || station.getStationId() == null || station.getStationId().isBlank()
            ? valueOrDash(principal.getStationId())
            : station.getStationId();
        String branchName = station == null || station.getAddressLocation() == null || station.getAddressLocation().isBlank()
            ? stationId
            : station.getAddressLocation();
        return new ExportContext(saccoName, stationId, branchName, range.fromDate(), range.toDate(), "All Products");
    }

    private String exporterRoleLabel(AppUserPrincipal principal) {
        if (principal == null || principal.getPosition() == null) {
            return "-";
        }
        return principal.getPosition().getDisplayName();
    }

    private String loanProductLabel(String saccoId, UUID loanProductId, com.sacco.mvp.domain.LoanType loanType) {
        if (saccoId == null || saccoId.isBlank()) {
            return "All Products";
        }
        if (loanProductId != null) {
            return loanProductSettingRepository.findByIdAndSaccoIdAndActiveTrue(loanProductId, saccoId)
                .filter(LoanProductSetting::isAvailableForApplications)
                .map(LoanProductSetting::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("All Products");
        }
        if (loanType == null) {
            return "All Products";
        }
        return loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId).stream()
            .filter(LoanProductSetting::isAvailableForApplications)
            .filter(product -> product.getLoanType() == loanType)
            .sorted(Comparator.comparingInt(LoanProductSetting::getResolvedDisplayOrder))
            .map(LoanProductSetting::getDisplayName)
            .filter(name -> name != null && !name.isBlank())
            .findFirst()
            .orElse("All Products");
    }

    private LoanAnalyticsService.StaffPortfolioSummary memberPortfolio(LoanAnalyticsService.MemberLoanAnalytics analytics) {
        BigDecimal defaultedRate = analytics.disbursedLoans() == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(analytics.defaultedLoans())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(analytics.disbursedLoans()), 2, RoundingMode.HALF_UP);
        String riskLevel = defaultedRate.compareTo(BigDecimal.valueOf(10)) >= 0
            ? "High"
            : defaultedRate.compareTo(BigDecimal.valueOf(5)) >= 0 ? "Moderate" : "Low";
        return new LoanAnalyticsService.StaffPortfolioSummary(
            analytics.appliedLoans(),
            analytics.disbursedLoans(),
            analytics.rejectedLoans(),
            analytics.disbursedLoans(),
            analytics.defaultedLoans(),
            defaultedRate,
            riskLevel
        );
    }

    private List<ProductPerformanceRow> productRowsFromLoans(List<LoanApplication> loans) {
        return productRowsFromLoans(loans, loans);
    }

    private List<ProductPerformanceRow> productRowsFromLoans(List<LoanApplication> countLoans,
                                                             List<LoanApplication> financialLoans) {
        List<ProductRef> productRefs = configuredProductRefs(countLoans, financialLoans);
        if (productRefs.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<LoanApplication>> byProductId = groupByLoanProductId(countLoans);
        Map<UUID, List<LoanApplication>> financialByProductId = groupByLoanProductId(financialLoans);
        return productRefs.stream()
            .map(product -> {
                List<LoanApplication> values = byProductId.getOrDefault(product.loanProductId(), List.of());
                List<LoanApplication> financialValues = financialByProductId.getOrDefault(product.loanProductId(), List.of());
                long applied = values.stream().filter(loan -> loan.getStatus() != LoanStatus.DRAFT).count();
                long active = values.stream().filter(loan -> ACTIVE_STATUSES.contains(loan.getStatus())).count();
                long disbursed = values.stream().filter(loan -> DISBURSED_STATUSES.contains(loan.getStatus())).count();
                long paid = values.stream().filter(loan -> loan.getStatus() == LoanStatus.PAID).count();
                long defaulted = values.stream().filter(loan -> loan.getStatus() == LoanStatus.DEFAULTED).count();
                long rejected = values.stream().filter(loan -> REJECTED_STATUSES.contains(loan.getStatus())).count();
                BigDecimal interestPaid = financialValues.stream()
                    .map(this::interestPaidAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal fullyPaidInterest = financialValues.stream()
                    .filter(loan -> loan.getStatus() == LoanStatus.PAID)
                    .map(this::interestPaidAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                return new ProductPerformanceRow(product.label(), applied, active, disbursed, paid, defaulted, rejected, interestPaid, fullyPaidInterest);
            })
            .toList();
    }

    private List<ProductFinancialBreakdownRow> productFinancialRowsFromLoans(List<LoanApplication> loans) {
        List<ProductRef> productRefs = configuredProductRefs(loans);
        if (productRefs.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<LoanApplication>> byProductId = groupByLoanProductId(loans);
        return productRefs.stream()
            .map(product -> {
                BigDecimal interestPaid = BigDecimal.ZERO;
                BigDecimal interestUnpaid = BigDecimal.ZERO;
                BigDecimal loanAmountPaid = BigDecimal.ZERO;
                BigDecimal loanAmountUnpaid = BigDecimal.ZERO;
                for (LoanApplication loan : byProductId.getOrDefault(product.loanProductId(), List.of())) {
                    if (!DISBURSED_STATUSES.contains(loan.getStatus())) {
                        continue;
                    }
                    interestPaid = interestPaid.add(interestPaidAmount(loan));
                    interestUnpaid = interestUnpaid.add(interestUnpaidAmount(loan));
                    loanAmountPaid = loanAmountPaid.add(principalPaidAmount(loan));
                    loanAmountUnpaid = loanAmountUnpaid.add(outstandingPrincipalAmount(loan));
                }
                return new ProductFinancialBreakdownRow(product.label(), interestPaid, interestUnpaid, loanAmountPaid, loanAmountUnpaid);
            })
            .toList();
    }

    @SafeVarargs
    private final List<ProductRef> configuredProductRefs(List<LoanApplication>... loanSets) {
        String saccoId = null;
        for (List<LoanApplication> loans : loanSets) {
            if (loans == null) {
                continue;
            }
            saccoId = loans.stream()
                .map(LoanApplication::getSaccoId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(null);
            if (saccoId != null) {
                break;
            }
        }
        if (saccoId == null) {
            return List.of();
        }
        List<LoanProductSetting> products = loanProductSettingRepository.findBySaccoIdAndActiveTrue(saccoId);
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        return products.stream()
            .filter(product -> product.getLoanType() != null)
            .filter(LoanProductSetting::isAvailableForApplications)
            .sorted(Comparator.comparingInt(LoanProductSetting::getResolvedDisplayOrder))
            .map(product -> new ProductRef(product.getId(), product.getLoanType(), product.getDisplayName()))
            .toList();
    }

    private Map<UUID, List<LoanApplication>> groupByLoanProductId(List<LoanApplication> loans) {
        if (loans == null || loans.isEmpty()) {
            return Map.of();
        }
        return loans.stream()
            .filter(loan -> loan.getLoanProductSettingId() != null)
            .collect(Collectors.groupingBy(
                LoanApplication::getLoanProductSettingId,
                LinkedHashMap::new,
                Collectors.toList()
            ));
    }

    private List<LoanApplication> stationFinancialLoans(String saccoId, String stationId, com.sacco.mvp.domain.LoanType loanType) {
        if (saccoId == null || saccoId.isBlank()) {
            return List.of();
        }
        List<LoanApplication> loans = loanApplicationRepository.findScopeLoansForStationFinancialAnalytics(
            saccoId,
            stationId,
            DISBURSED_STATUSES,
            loanType
        );
        return loans == null ? List.of() : loans;
    }

    private List<LoanApplication> filterByLoanProductId(List<LoanApplication> loans, UUID loanProductId) {
        if (loans == null || loans.isEmpty()) {
            return List.of();
        }
        if (loanProductId == null) {
            return loans;
        }
        return loans.stream()
            .filter(loan -> loanProductId.equals(loan.getLoanProductSettingId()))
            .toList();
    }

    private List<ActiveLoanDetailRow> activeLoanRowsFromLoans(List<LoanApplication> loans) {
        return loans.stream()
            .filter(loan -> ACTIVE_STATUSES.contains(loan.getStatus()))
            .sorted(Comparator.comparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .map(loan -> {
                BigDecimal outstanding = outstandingBalanceAmount(loan);
                BigDecimal requiredInterest = requiredInterestAmount(loan);
                BigDecimal remainingInterest = interestUnpaidAmount(loan);
                return new ActiveLoanDetailRow(
                    loan.getLoanId() == null || loan.getLoanId().isBlank() ? "-" : loan.getLoanId(),
                    loanProductName(loan),
                    moneyPlain(loan.getAmount()),
                    moneyPlain(outstanding),
                    moneyPlain(requiredInterest),
                    moneyPlain(principalPaidAmount(loan)),
                    moneyPlain(interestPaidAmount(loan)),
                    moneyPlain(remainingInterest)
                );
            })
            .toList();
    }

    private BigDecimal requiredInterestAmount(LoanApplication loan) {
        BigDecimal snapshotInterest = financialSnapshotAmount(loan, "interestAmount");
        return snapshotInterest == null ? BigDecimal.ZERO : snapshotInterest;
    }

    private BigDecimal outstandingBalanceAmount(LoanApplication loan) {
        return outstandingPrincipalAmount(loan).add(interestUnpaidAmount(loan)).max(BigDecimal.ZERO);
    }

    private BigDecimal outstandingPrincipalAmount(LoanApplication loan) {
        if (loan != null && loan.getStatus() == LoanStatus.PAID) {
            return BigDecimal.ZERO;
        }
        if (loan == null || !DISBURSED_STATUSES.contains(loan.getStatus())) {
            return BigDecimal.ZERO;
        }
        return loan.getAmount() == null ? BigDecimal.ZERO : loan.getAmount();
    }

    private BigDecimal interestPaidAmount(LoanApplication loan) {
        return loan != null && loan.getStatus() == LoanStatus.PAID
            ? requiredInterestAmount(loan)
            : BigDecimal.ZERO;
    }

    private BigDecimal principalPaidAmount(LoanApplication loan) {
        return loan != null && loan.getStatus() == LoanStatus.PAID && loan.getAmount() != null
            ? loan.getAmount()
            : BigDecimal.ZERO;
    }

    private BigDecimal interestUnpaidAmount(LoanApplication loan) {
        if (loan != null && loan.getStatus() == LoanStatus.PAID) {
            return BigDecimal.ZERO;
        }
        if (loan == null || !DISBURSED_STATUSES.contains(loan.getStatus())) {
            return BigDecimal.ZERO;
        }
        return requiredInterestAmount(loan);
    }

    private BigDecimal financialSnapshotAmount(LoanApplication loan, String key) {
        if (loan == null || loan.getFinancialSnapshot() == null || loan.getFinancialSnapshot().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(loan.getFinancialSnapshot(), new TypeReference<>() {});
            return readBigDecimal(raw.get(key));
        } catch (JacksonException ex) {
            return null;
        }
    }

    private FinancialSummary financialSummary(List<LoanApplication> loans, LoanAnalyticsService.MemberLoanAnalytics analytics) {
        BigDecimal disbursed = loans.stream()
            .filter(loan -> DISBURSED_STATUSES.contains(loan.getStatus()))
            .map(LoanApplication::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal repaid = loans.stream()
            .filter(loan -> loan.getStatus() == LoanStatus.PAID)
            .map(LoanApplication::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal defaulted = loans.stream()
            .filter(loan -> loan.getStatus() == LoanStatus.DEFAULTED)
            .map(LoanApplication::getAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal active = analytics.activeLoanAmount() == null ? BigDecimal.ZERO : analytics.activeLoanAmount();
        return new FinancialSummary(active, disbursed, repaid, active, defaulted);
    }

    private List<ActivityRow> recentActivityRows(List<LoanApplication> loans) {
        return loans.stream()
            .sorted(Comparator.comparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(6)
            .map(loan -> new ActivityRow(
                loan.getCreatedAt() == null ? "-" : HUMAN_DATE_FORMATTER.format(loan.getCreatedAt().toLocalDate()),
                activityLabel(loan),
                loanProductName(loan),
                moneyPlain(loan.getAmount()),
                humanizeLoanStatusForReport(loan.getStatus())
            ))
            .toList();
    }

    private String activityLabel(LoanApplication loan) {
        if (loan.getStatus() == LoanStatus.PAID) {
            return "Loan Paid";
        }
        if (DISBURSED_STATUSES.contains(loan.getStatus())) {
            return "Loan Disbursed";
        }
        if (REJECTED_STATUSES.contains(loan.getStatus())) {
            return "Application Rejected";
        }
        return "Application Submitted";
    }

    private List<String> observations(LoanAnalyticsService.MemberLoanAnalytics analytics) {
        List<String> rows = new ArrayList<>();
        rows.add(analytics.appliedLoans() + " loan application(s) were recorded in the selected period.");
        rows.add(analytics.defaultedLoans() == 0
            ? "No defaults recorded during the reporting period."
            : analytics.defaultedLoans() + " defaulted loan(s) recorded during the reporting period.");
        BigDecimal approvalRate = analytics.appliedLoans() == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(analytics.disbursedLoans())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(analytics.appliedLoans()), 0, RoundingMode.HALF_UP);
        rows.add("Approval rate remains at " + approvalRate + "%.");
        rows.add("Portfolio risk level remains " + memberPortfolio(analytics).riskLevel() + ".");
        return rows;
    }

    private boolean fitsMemberOnePage(AnalyticsExportReport report) {
        return report.activityRows().size() <= 4 && report.productRows().size() <= 5 && report.trendSeries().size() <= 7;
    }

    private List<String[]> statusRows(AnalyticsExportReport report) {
        LoanAnalyticsService.MemberLoanAnalytics analytics = report.analytics();
        return List.of(
            statusRow(report, "Applied", analytics.appliedLoans()),
            statusRow(report, "Active", analytics.activeLoans()),
            statusRow(report, "Disbursed", analytics.disbursedLoans()),
            statusRow(report, "Paid", analytics.paidLoans()),
            statusRow(report, "Defaulted", analytics.defaultedLoans()),
            statusRow(report, "Rejected", analytics.rejectedLoans())
        );
    }

    private String[] statusRow(AnalyticsExportReport report, String label, long count) {
        long applicantCount = report.kind() == ReportKind.MEMBER ? (count > 0 ? 1 : 0) : count;
        return new String[]{label, String.valueOf(count), String.valueOf(applicantCount)};
    }

    private String periodLabel(AnalyticsExportReport report) {
        return HUMAN_DATE_FORMATTER.format(report.fromDate()) + " - " + HUMAN_DATE_FORMATTER.format(report.toDate());
    }

    private String humanDate(LocalDate date) {
        return date == null ? "-" : HUMAN_DATE_FORMATTER.format(date);
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String moneyPlain(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.##", new java.text.DecimalFormatSymbols(Locale.US));
        return format.format(safe);
    }

    private String totalRiskLevel(AnalyticsExportReport report) {
        return report.portfolioSummary() == null ? "Low" : report.portfolioSummary().riskLevel();
    }

    private void writeMemberSummarySheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "MEMBER LOAN REPORT", styles);
        row = writeKeyValueBlock(sheet, row, "MEMBER INFORMATION", List.of(
            new String[]{"Member Name", report.member() == null ? "-" : report.member().getFullName()},
            new String[]{"Member Number", report.member() == null ? "-" : report.member().getMemberNo()},
            new String[]{"Station", report.stationId() + " - " + report.branchName()}
        ), styles);
        row = writeKeyValueBlock(sheet, row + 1, "REPORT DETAILS", List.of(
            new String[]{"Reporting Period", periodLabel(report)},
            new String[]{"Generated On", humanDate(report.generatedOn())},
            new String[]{"Generated By", report.generatedBy()},
            new String[]{"Loan Product", report.loanProductLabel()}
        ), styles);
        row = writeMetricSummary(sheet, row + 1, report, styles);
        row = writeTable(sheet, row + 1, "LOAN STATUS BREAKDOWN",
            new String[]{"Status", "Count", "Number of Applicants"},
            statusRowsWithoutDelta(report), styles);
        row = writeProductTable(sheet, row + 1, report, styles);
        row = writeActiveLoanTable(sheet, row + 1, report, styles);
        row = writeFinancialTable(sheet, row + 1, report, styles);
        writeSignOff(sheet, row + 1, report, styles);
        autosize(sheet, 9);
    }

    private void writeStationSummarySheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), report.title(), styles);
        row = writeReportDetails(sheet, row, report, styles);
        row = writeKeyValueBlock(sheet, row, "SUMMARY", List.of(
            new String[]{"Applications", String.valueOf(report.analytics().appliedLoans())},
            new String[]{"Approved", String.valueOf(report.analytics().disbursedLoans())},
            new String[]{"Disbursed", String.valueOf(report.analytics().disbursedLoans())},
            new String[]{"Active", String.valueOf(report.analytics().activeLoans())},
            new String[]{"Paid", String.valueOf(report.analytics().paidLoans())},
            new String[]{"Rejected", String.valueOf(report.analytics().rejectedLoans())},
            new String[]{"Defaulted", String.valueOf(report.analytics().defaultedLoans())},
            new String[]{"Total Paid Interest Accumulated", moneyPlain(totalProductInterest(report))},
            new String[]{"Risk Level", totalRiskLevel(report)}
        ), styles);
        row = writeTable(sheet, row + 1, "LOAN STATUS ANALYSIS",
            new String[]{"Status", "Count", "Number of Applicants"},
            statusRowsWithoutDelta(report), styles);
        row = writeProductTable(sheet, row + 1, report, styles);
        row = writeProductFinancialBreakdownTable(sheet, row + 1, report, styles);
        row = writeTable(sheet, row + 1, "KEY OBSERVATIONS",
            new String[]{"Observation"},
            report.observations().stream().map(value -> new String[]{value}).toList(), styles);
        writeSignOff(sheet, row + 1, report, styles);
        autosize(sheet, 9);
    }

    private void writeStaffSummarySheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), report.title(), styles);
        row = writeKeyValueBlock(sheet, row, "STAFF INFORMATION", List.of(
            new String[]{"Staff Name", report.generatedBy()},
            new String[]{"Position", report.generatedByRole()},
            new String[]{"Branch/Station", report.stationId() + " - " + report.branchName()}
        ), styles);
        row = writeReportDetails(sheet, row + 1, report, styles);
        row = writeStaffReviewSummaryTable(sheet, row + 1, report, styles);
        row = writeStaffProductReviewTable(sheet, row + 1, report, styles);
        row = writeStaffKpiTable(sheet, row + 1, report, styles);
        writeSignOff(sheet, row + 1, report, styles);
        autosize(sheet, 8);
    }

    private void writeStaffProductReviewSheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "PRODUCT REVIEW BREAKDOWN", styles);
        row = writeReportDetails(sheet, row, report, styles);
        writeStaffProductReviewTable(sheet, row, report, styles);
        autosize(sheet, 8);
    }

    private void writeStatusSheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "LOAN STATUS ANALYSIS", styles);
        row = writeReportDetails(sheet, row, report, styles);
        writeTable(sheet, row, "STATUS BREAKDOWN",
            new String[]{"Status", "Count", "Number of Applicants"},
            statusRowsWithoutDelta(report), styles);
        autosize(sheet, 8);
    }

    private void writeProductSheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "PRODUCT PERFORMANCE", styles);
        row = writeReportDetails(sheet, row, report, styles);
        row = writeProductTable(sheet, row, report, styles);
        if (report.kind() != ReportKind.MEMBER) {
            writeProductFinancialBreakdownTable(sheet, row + 1, report, styles);
        }
        autosize(sheet, 9);
    }

    private void writeTrendSheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "TRENDS", styles);
        row = writeReportDetails(sheet, row, report, styles);
        List<String[]> rows = trendTableRows(report);
        int tableStart = row;
        if (report.kind() == ReportKind.STAFF) {
            writeTable(sheet, tableStart, "REVIEW TREND OVER TIME",
                new String[]{"Period", "Reviewed", "Approved", "Rejected", "Pending"},
                rows, styles);
            makeTrendValuesNumeric(sheet, tableStart, rows.size(), 4);
            addTrendChart(sheet, tableStart, rows.size(), new int[]{1, 2, 3, 4}, new String[]{"Reviewed", "Approved", "Rejected", "Pending"});
        } else {
            writeTable(sheet, tableStart, "LOAN TREND OVER TIME",
                new String[]{"Period", "Applied", "Active", "Disbursed", "Paid", "Defaulted", "Rejected"},
                rows, styles);
            makeTrendValuesNumeric(sheet, tableStart, rows.size(), 6);
            addTrendChart(sheet, tableStart, rows.size(), new int[]{1, 3, 4, 5}, new String[]{"Applied", "Disbursed", "Paid", "Defaulted"});
        }
        autosize(sheet, 8);
    }

    private void writeFinancialSheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "FINANCIAL SUMMARY", styles);
        row = writeReportDetails(sheet, row, report, styles);
        writeFinancialTable(sheet, row, report, styles);
        autosize(sheet, 5);
    }

    private void writeActivitySheet(XSSFSheet sheet, AnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "ACTIVITY LOG", styles);
        row = writeReportDetails(sheet, row, report, styles);
        writeTable(sheet, row, "RECENT LOAN ACTIVITY",
            new String[]{"Date", "Activity", "Loan Product", "Amount (TZS)", "Status"},
            report.activityRows().stream()
                .map(item -> new String[]{item.date(), item.activity(), item.loanProduct(), item.amount(), item.status()})
                .toList(), styles);
        autosize(sheet, 8);
    }

    private int titleRows(XSSFSheet sheet, String saccoName, String title, ExcelStyles styles) {
        Row row0 = sheet.createRow(0);
        Cell c0 = row0.createCell(0);
        c0.setCellValue(saccoName);
        c0.setCellStyle(styles.title);
        Row row1 = sheet.createRow(1);
        Cell c1 = row1.createCell(0);
        c1.setCellValue(title);
        c1.setCellStyle(styles.subtitle);
        return 2;
    }

    private int writeReportDetails(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        return writeKeyValueBlock(sheet, startRow, "REPORT DETAILS", List.of(
            new String[]{"Reporting Period", periodLabel(report)},
            new String[]{"Generated On", humanDate(report.generatedOn())},
            new String[]{"Name", report.generatedBy()},
            new String[]{"Role", report.generatedByRole()},
            new String[]{"Loan Product", report.loanProductLabel()}
        ), styles);
    }

    private int writeKeyValueBlock(XSSFSheet sheet, int startRow, String title, List<String[]> rows, ExcelStyles styles) {
        Row titleRow = sheet.createRow(startRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(styles.section);
        for (String[] data : rows) {
            Row row = sheet.createRow(startRow++);
            writeCell(row, 0, data[0], styles.header);
            writeCell(row, 1, data.length > 1 ? data[1] : "", styles.body);
        }
        return startRow;
    }

    private int writeMetricSummary(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        return writeKeyValueBlock(sheet, startRow, "LOAN STATUS SUMMARY", List.of(
            new String[]{"Applied Loans", String.valueOf(report.analytics().appliedLoans())},
            new String[]{"Active Loans", String.valueOf(report.analytics().activeLoans())},
            new String[]{"Disbursed Loans", String.valueOf(report.analytics().disbursedLoans())},
            new String[]{"Paid Loans", String.valueOf(report.analytics().paidLoans())},
            new String[]{"Defaulted Loans", String.valueOf(report.analytics().defaultedLoans())},
            new String[]{"Rejected Loans", String.valueOf(report.analytics().rejectedLoans())}
        ), styles);
    }

    private int writeProductTable(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        List<String[]> rows = new ArrayList<>();
        long applied = 0;
        long active = 0;
        long disbursed = 0;
        long paid = 0;
        long defaulted = 0;
        long rejected = 0;
        for (ProductPerformanceRow item : report.productRows()) {
            rows.add(new String[]{
                item.label(), String.valueOf(item.applied()), String.valueOf(item.active()),
                String.valueOf(item.disbursed()), String.valueOf(item.paid()),
                String.valueOf(item.defaulted()), String.valueOf(item.rejected()),
                moneyPlain(item.interestPaid()),
                moneyPlain(item.fullyPaidInterest())
            });
            applied += item.applied();
            active += item.active();
            disbursed += item.disbursed();
            paid += item.paid();
            defaulted += item.defaulted();
            rejected += item.rejected();
        }
        BigDecimal interestPaid = BigDecimal.ZERO;
        BigDecimal fullyPaidInterest = BigDecimal.ZERO;
        for (ProductPerformanceRow item : report.productRows()) {
            interestPaid = interestPaid.add(item.interestPaid() == null ? BigDecimal.ZERO : item.interestPaid());
            fullyPaidInterest = fullyPaidInterest.add(item.fullyPaidInterest() == null ? BigDecimal.ZERO : item.fullyPaidInterest());
        }
        rows.add(new String[]{"TOTAL", String.valueOf(applied), String.valueOf(active), String.valueOf(disbursed), String.valueOf(paid), String.valueOf(defaulted), String.valueOf(rejected), moneyPlain(interestPaid), moneyPlain(fullyPaidInterest)});
        return writeTable(sheet, startRow, "LOAN PRODUCT PERFORMANCE",
            new String[]{"Loan Product", "Applied", "Active", "Disbursed", "Paid", "Defaulted", "Rejected", report.kind() == ReportKind.STATION ? "Total Paid Interest Accumulated" : "Total Interest Paid", "Total Interest of Fully Paid Loan"},
            rows, styles);
    }

    private int writeStaffReviewSummaryTable(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        LoanAnalyticsService.StaffReviewAnalytics review = staffReview(report);
        return writeTable(sheet, startRow, "REVIEW SUMMARY OVERVIEW",
            new String[]{"Metric", "Count", "Number of Applicants"},
            List.of(
                new String[]{"Total Applications Reviewed", String.valueOf(review.reviewedLoans()), String.valueOf(review.reviewedLoans())},
                new String[]{"Approved Loans", String.valueOf(review.approvedLoans()), String.valueOf(review.approvedLoans())},
                new String[]{"Rejected Loans", String.valueOf(review.rejectedLoans()), String.valueOf(review.rejectedLoans())},
                new String[]{"Pending / In Progress", String.valueOf(review.pendingLoans()), String.valueOf(review.pendingLoans())}
            ), styles);
    }

    private int writeStaffProductReviewTable(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        LoanAnalyticsService.StaffReviewAnalytics review = staffReview(report);
        List<String[]> rows = review.productRows().stream()
            .map(row -> new String[]{
                row.label(),
                String.valueOf(row.reviewed()),
                String.valueOf(row.approved()),
                String.valueOf(row.rejected()),
                String.valueOf(row.pending()),
                row.approvalRate().setScale(2, RoundingMode.HALF_UP) + "%"
            })
            .collect(Collectors.toCollection(ArrayList::new));
        long reviewed = review.productRows().stream().mapToLong(LoanAnalyticsService.StaffReviewProductPerformance::reviewed).sum();
        long approved = review.productRows().stream().mapToLong(LoanAnalyticsService.StaffReviewProductPerformance::approved).sum();
        long rejected = review.productRows().stream().mapToLong(LoanAnalyticsService.StaffReviewProductPerformance::rejected).sum();
        long pending = review.productRows().stream().mapToLong(LoanAnalyticsService.StaffReviewProductPerformance::pending).sum();
        BigDecimal approvalRate = reviewed == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(approved).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(reviewed), 2, RoundingMode.HALF_UP);
        rows.add(new String[]{"TOTAL", String.valueOf(reviewed), String.valueOf(approved), String.valueOf(rejected), String.valueOf(pending), approvalRate + "%"});
        return writeTable(sheet, startRow, "LOAN PRODUCT REVIEW BREAKDOWN",
            new String[]{"Loan Product", "Reviewed", "Approved", "Rejected", "Pending", "Approval Rate"},
            rows, styles);
    }

    private int writeStaffKpiTable(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        LoanAnalyticsService.StaffReviewAnalytics review = staffReview(report);
        BigDecimal reviewed = BigDecimal.valueOf(Math.max(review.reviewedLoans(), 1));
        return writeTable(sheet, startRow, "KEY PERFORMANCE INDICATORS",
            new String[]{"KPI", "This Period"},
            List.of(
                new String[]{"Approval Rate", percent(review.approvedLoans(), reviewed)},
                new String[]{"Rejection Rate", percent(review.rejectedLoans(), reviewed)},
                new String[]{"Pending Rate", percent(review.pendingLoans(), reviewed)}
            ), styles);
    }

    private int writeActiveLoanTable(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        return writeTable(sheet, startRow, "ACTIVE LOAN DETAILS",
            new String[]{"Loan ID", "Loan Product", "Loan Amount", "Outstanding Balance", "Required Interest Amount", "Paid Loan Amount", "Interest Paid", "Interest Not Yet Paid"},
            report.activeLoanRows().stream()
                .map(row -> new String[]{row.loanId(), row.loanProduct(), row.loanAmount(), row.outstandingBalance(), row.requiredInterestAmount(), row.paidLoanAmount(), row.interestPaid(), row.interestNotYetPaid()})
                .toList(), styles);
    }

    private int writeProductFinancialBreakdownTable(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        return writeTable(sheet, startRow, "LOAN PRODUCT FINANCIAL BREAKDOWN",
            new String[]{"Loan Product", "Total Interest Paid", "Total Interest Unpaid Yet", "Total Loan Amount Paid", "Total Loan Amount Unpaid Yet"},
            productFinancialRowsForExcel(report), styles);
    }

    private int writeFinancialTable(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        FinancialSummary financial = report.financialSummary();
        return writeKeyValueBlock(sheet, startRow, "LOAN FINANCIAL SUMMARY", List.of(
            new String[]{"Current Active Loan Amount", moneyPlain(financial.activeLoanAmount())},
            new String[]{"Total Disbursed Amount", moneyPlain(financial.totalDisbursedAmount())},
            new String[]{"Total Repaid Amount", moneyPlain(financial.totalRepaidAmount())},
            new String[]{"Outstanding Balance", moneyPlain(financial.outstandingBalance())},
            new String[]{"Defaulted Amount", moneyPlain(financial.defaultedAmount())}
        ), styles);
    }

    private BigDecimal totalProductInterest(AnalyticsExportReport report) {
        return report.productRows().stream()
            .map(ProductPerformanceRow::interestPaid)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LoanAnalyticsService.StaffReviewAnalytics staffReview(AnalyticsExportReport report) {
        if (report.staffReviewAnalytics() != null) {
            return report.staffReviewAnalytics();
        }
        return new LoanAnalyticsService.StaffReviewAnalytics(0, 0, 0, 0, 0, 0, List.of(), List.of());
    }

    private String percent(long numerator, BigDecimal denominator) {
        BigDecimal safeDenominator = denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ONE
            : denominator;
        return BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(100))
            .divide(safeDenominator, 2, RoundingMode.HALF_UP) + "%";
    }

    private List<String[]> productFinancialRowsForExcel(AnalyticsExportReport report) {
        List<String[]> rows = report.productFinancialRows().stream()
            .map(row -> new String[]{
                row.loanProduct(),
                moneyPlain(row.totalInterestPaid()),
                moneyPlain(row.totalInterestUnpaid()),
                moneyPlain(row.totalLoanAmountPaid()),
                moneyPlain(row.totalLoanAmountUnpaid())
            })
            .collect(Collectors.toCollection(ArrayList::new));
        BigDecimal interestUnpaid = BigDecimal.ZERO;
        BigDecimal interestPaid = BigDecimal.ZERO;
        BigDecimal loanAmountPaid = BigDecimal.ZERO;
        BigDecimal loanAmountUnpaid = BigDecimal.ZERO;
        for (ProductFinancialBreakdownRow row : report.productFinancialRows()) {
            interestPaid = interestPaid.add(row.totalInterestPaid() == null ? BigDecimal.ZERO : row.totalInterestPaid());
            interestUnpaid = interestUnpaid.add(row.totalInterestUnpaid() == null ? BigDecimal.ZERO : row.totalInterestUnpaid());
            loanAmountPaid = loanAmountPaid.add(row.totalLoanAmountPaid() == null ? BigDecimal.ZERO : row.totalLoanAmountPaid());
            loanAmountUnpaid = loanAmountUnpaid.add(row.totalLoanAmountUnpaid() == null ? BigDecimal.ZERO : row.totalLoanAmountUnpaid());
        }
        rows.add(new String[]{"TOTAL", moneyPlain(interestPaid), moneyPlain(interestUnpaid), moneyPlain(loanAmountPaid), moneyPlain(loanAmountUnpaid)});
        return rows;
    }

    private void writeSignOff(XSSFSheet sheet, int startRow, AnalyticsExportReport report, ExcelStyles styles) {
        writeKeyValueBlock(sheet, startRow, "SIGN-OFF", List.of(
            new String[]{"Prepared By", report.generatedBy()},
            new String[]{"Prepared Date", humanDate(report.generatedOn())},
            new String[]{"Reviewed By", "____________________________"},
            new String[]{"Approved By", "____________________________"}
        ), styles);
    }

    private void writeStationExportSummarySheet(XSSFSheet sheet, StationAnalyticsExportReport report, ExcelStyles styles) {
        int row = titleRows(sheet, report.saccoName(), "STATION LOAN STATUS REPORT", styles);
        row = writeKeyValueBlock(sheet, row, "REPORT DETAILS", List.of(
            new String[]{"Reporting Period", report.periodLabel()},
            new String[]{"Generated On", formatDate(report.generatedOn())},
            new String[]{"Name", report.preparedBy()},
            new String[]{"Role", report.preparedByRole()},
            new String[]{"Loan Product", report.loanProductLabel()}
        ), styles);
        row = writeKeyValueBlock(sheet, row + 1, "STATION PARTICIPATION SUMMARY", List.of(
            new String[]{"Total Members", String.valueOf(report.participation().activeStationMembers())},
            new String[]{"Total Applicants", String.valueOf(report.participation().uniqueApplicants())},
            new String[]{"Participation Rate", report.participation().participationRateLabel()},
            new String[]{"Applications per Applicant", report.participation().applicationsPerApplicantLabel()},
            new String[]{"Repeat Applicants", String.valueOf(report.participation().repeatApplicants())}
        ), styles);
        row = writeStationStatusTable(sheet, row + 1, report.statusRows(), styles);
        row = writeStationProductTable(sheet, row + 1, report.productRows(), styles);
        writeStationYearlySummaryTable(sheet, row + 1, report.yearlyRows(), styles);
    }

    private int writeStationStatusTable(XSSFSheet sheet, int startRow, List<StationStatusRow> rows, ExcelStyles styles) {
        return writeTable(sheet, startRow, "LOAN STATUS SUMMARY",
            new String[]{"Metric", "Count", "Number of Applicants"},
            rows.stream()
                .map(row -> new String[]{row.metric(), String.valueOf(row.count()), String.valueOf(row.applicantCount())})
                .toList(),
            styles);
    }

    private int writeStationProductTable(XSSFSheet sheet, int startRow, List<StationProductRow> rows, ExcelStyles styles) {
        List<String[]> tableRows = rows.stream()
            .map(row -> new String[]{
                row.loanProduct(),
                String.valueOf(row.applications()),
                String.valueOf(row.approved()),
                String.valueOf(row.active()),
                String.valueOf(row.disbursed()),
                String.valueOf(row.paid()),
                String.valueOf(row.defaulted()),
                String.valueOf(row.rejected()),
                row.totalPaidInterestLabel(),
                row.fullyPaidLoanInterestLabel()
            })
            .toList();
        return writeTable(sheet, startRow, "LOAN PRODUCT PERFORMANCE",
            new String[]{"Loan Product", "Applications", "Approved", "Active", "Disbursed", "Paid", "Defaulted", "Rejected", "Total Paid Interest Accumulated", "Total Interest of Fully Paid Loan"},
            tableRows,
            styles);
    }

    private int writeStationYearlySummaryTable(XSSFSheet sheet, int startRow, List<StationYearlySummaryRow> rows, ExcelStyles styles) {
        List<String[]> tableRows = rows.stream()
            .map(row -> new String[]{
                String.valueOf(row.year()),
                String.valueOf(row.totalLoanApplications()),
                String.valueOf(row.uniqueApplicants()),
                String.valueOf(row.defaultedLoans()),
                row.defaultRateLabel(),
                String.valueOf(row.totalPaidLoans()),
                row.totalPaidInterestAccumulatedLabel(),
                row.fullyPaidLoanInterestLabel()
            })
            .toList();
        return writeTable(sheet, startRow, "YEARLY LOAN AND INTEREST SUMMARY",
            new String[]{"Year", "Total Loan Applications", "Unique Applicants", "Defaulted Loans", "Default Rate", "Total Paid Loans", "Total Paid Interest Accumulated", "Total Interest of Fully Paid Loan"},
            tableRows,
            styles);
    }

    private void makeStationYearlyValuesNumeric(XSSFSheet sheet, int tableStartRow, int dataRowCount) {
        int dataStart = tableStartRow + 2;
        int dataEnd = dataStart + dataRowCount - 1;
        for (int rowIndex = dataStart; rowIndex <= dataEnd; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            makeCellNumeric(row, 0);
            makeCellNumeric(row, 1);
            makeCellNumeric(row, 2);
            makeCellNumeric(row, 3);
            makeCellNumeric(row, 5);
        }
    }

    private void makeCellNumeric(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null || cell.getCellType() != org.apache.poi.ss.usermodel.CellType.STRING) {
            return;
        }
        String value = cell.getStringCellValue();
        if (value == null || !value.matches("-?\\d+(\\.\\d+)?")) {
            return;
        }
        CellStyle style = cell.getCellStyle();
        cell.setCellValue(Double.parseDouble(value));
        cell.setCellStyle(style);
    }

    private int writeTable(XSSFSheet sheet, int startRow, String title, String[] headers, List<String[]> rows, ExcelStyles styles) {
        Row titleRow = sheet.createRow(startRow++);
        writeCell(titleRow, 0, title, styles.section);
        Row headerRow = sheet.createRow(startRow++);
        headerRow.setHeightInPoints(28f);
        for (int i = 0; i < headers.length; i++) {
            writeCell(headerRow, i, headers[i], styles.header);
        }
        if (rows.isEmpty()) {
            Row empty = sheet.createRow(startRow++);
            writeCell(empty, 0, "No records for the selected period.", styles.body);
            return startRow;
        }
        for (String[] rowValues : rows) {
            Row row = sheet.createRow(startRow++);
            row.setHeightInPoints(24f);
            for (int i = 0; i < rowValues.length; i++) {
                writeCell(row, i, rowValues[i], styles.body);
            }
        }
        return startRow;
    }

    private void writeCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private void autosize(XSSFSheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private List<String[]> trendTableRows(AnalyticsExportReport report) {
        Map<String, Map<String, Long>> byPeriod = new LinkedHashMap<>();
        for (LoanAnalyticsService.MetricTrendSeries series : report.trendSeries()) {
            for (Map<String, Object> point : series.dataPoints()) {
                String period = trendPeriodLabel(point.get("x"));
                byPeriod.computeIfAbsent(period, key -> new LinkedHashMap<>()).put(series.name(), numberValue(point.get("y")));
            }
        }
        if (report.kind() == ReportKind.STAFF) {
            return byPeriod.entrySet().stream()
                .map(entry -> {
                    Map<String, Long> values = entry.getValue();
                    return new String[]{
                        entry.getKey(),
                        String.valueOf(values.getOrDefault("Reviewed", 0L)),
                        String.valueOf(values.getOrDefault("Approved", 0L)),
                        String.valueOf(values.getOrDefault("Rejected", 0L)),
                        String.valueOf(values.getOrDefault("Pending", 0L))
                    };
                })
                .toList();
        }
        return byPeriod.entrySet().stream()
            .map(entry -> {
                Map<String, Long> values = entry.getValue();
                return new String[]{
                    entry.getKey(),
                    String.valueOf(values.getOrDefault("Applied", 0L)),
                    String.valueOf(values.getOrDefault("Active", 0L)),
                    String.valueOf(values.getOrDefault("Disbursed", 0L)),
                    String.valueOf(values.getOrDefault("Paid", 0L)),
                    String.valueOf(values.getOrDefault("Defaulted", 0L)),
                    String.valueOf(values.getOrDefault("Rejected", 0L))
                };
            })
            .toList();
    }

    private void makeTrendValuesNumeric(XSSFSheet sheet, int tableStartRow, int dataRowCount, int lastColumn) {
        int dataStart = tableStartRow + 2;
        int dataEnd = dataStart + dataRowCount - 1;
        for (int rowIndex = dataStart; rowIndex <= dataEnd; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (int column = 1; column <= lastColumn; column++) {
                Cell cell = row.getCell(column);
                if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                    String value = cell.getStringCellValue();
                    if (value != null && value.matches("-?\\d+(\\.\\d+)?")) {
                        CellStyle style = cell.getCellStyle();
                        cell.setCellValue(Double.parseDouble(value));
                        cell.setCellStyle(style);
                    }
                }
            }
        }
    }

    private void addTrendChart(XSSFSheet sheet, int tableStartRow, int dataRowCount, int[] columns, String[] titles) {
        if (dataRowCount <= 0) {
            return;
        }
        int dataStart = tableStartRow + 2;
        int dataEnd = dataStart + dataRowCount - 1;
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, dataEnd + 3, 8, dataEnd + 21);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Loan Trend Over Time");
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.TOP);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("Period");
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("Loans");
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFDataSource<String> periods = XDDFDataSourcesFactory.fromStringCellRange(
            sheet, new CellRangeAddress(dataStart, dataEnd, 0, 0));
        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);
        for (int i = 0; i < columns.length && i < titles.length; i++) {
            addTrendSeries(data, periods, sheet, dataStart, dataEnd, columns[i], titles[i]);
        }
        chart.plot(data);
    }

    private void addTrendSeries(XDDFChartData data,
                                XDDFDataSource<String> periods,
                                XSSFSheet sheet,
                                int dataStart,
                                int dataEnd,
                                int column,
                                String title) {
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, new CellRangeAddress(dataStart, dataEnd, column, column));
        XDDFChartData.Series series = data.addSeries(periods, values);
        series.setTitle(title, null);
        if (series instanceof XDDFLineChartData.Series lineSeries) {
            lineSeries.setSmooth(false);
        }
    }

    private List<String[]> statusRowsWithoutDelta(AnalyticsExportReport report) {
        return statusRows(report);
    }

    private String trendPeriodLabel(Object value) {
        if (value instanceof Number number) {
            return YearMonth.from(java.time.Instant.ofEpochMilli(number.longValue()).atZone(ZoneOffset.UTC)).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH));
        }
        return String.valueOf(value);
    }

    private long numberValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private LoanSummary summarize(Collection<LoanApplication> loans) {
        long paidCount = loans.stream().filter(loan -> loan.getStatus() == LoanStatus.PAID).count();
        long ongoingCount = loans.stream()
            .filter(loan -> loan.getStatus() == LoanStatus.DISBURSED || loan.getStatus() == LoanStatus.DEFAULTED)
            .count();
        BigDecimal disbursedAmount = loans.stream().map(LoanApplication::getAmount).filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidAmount = loans.stream()
            .filter(loan -> loan.getStatus() == LoanStatus.PAID)
            .map(LoanApplication::getAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LoanSummary(loans.size(), paidCount, ongoingCount, disbursedAmount, paidAmount);
    }

    private MemberLoanAnalyticsSummary summarizeMemberAnalytics(Collection<LoanApplication> loans) {
        Collection<LoanApplication> safeLoans = loans == null ? List.of() : loans;
        long appliedCount = safeLoans.stream().filter(loan -> loan.getStatus() != LoanStatus.DRAFT).count();
        long activeCount = safeLoans.stream().filter(loan -> ACTIVE_STATUSES.contains(loan.getStatus())).count();
        long disbursedCount = safeLoans.stream().filter(loan -> DISBURSED_STATUSES.contains(loan.getStatus())).count();
        long paidCount = safeLoans.stream().filter(loan -> loan.getStatus() == LoanStatus.PAID).count();
        long defaultedCount = safeLoans.stream().filter(loan -> loan.getStatus() == LoanStatus.DEFAULTED).count();
        long rejectedCount = safeLoans.stream().filter(loan -> REJECTED_STATUSES.contains(loan.getStatus())).count();
        BigDecimal activeAmount = safeLoans.stream()
            .filter(loan -> ACTIVE_STATUSES.contains(loan.getStatus()))
            .map(LoanApplication::getAmount)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MemberLoanAnalyticsSummary(
            appliedCount,
            activeCount,
            disbursedCount,
            paidCount,
            defaultedCount,
            rejectedCount,
            activeAmount
        );
    }

    private byte[] renderPdf(List<String> lines, String saccoId) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFCursor cursor = new PDFCursor(document, saccoLogoBytes(saccoId));
            cursor.openPage();
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                cursor.writeWrapped(line);
            }
            cursor.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate PDF report.", ex);
        }
    }

    private byte[] renderStaffReviewPdf(String saccoId,
                                        String title,
                                        String subtitle,
                                        String summaryLine,
                                        String[] headers,
                                        float[] widths,
                                        List<String[]> rows,
                                        String emptyMessage,
                                        String footerTitle,
                                        LocalDate fromDate,
                                        LocalDate toDate) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            StaffReviewPdfRenderer renderer = new StaffReviewPdfRenderer(
                document,
                saccoReportTitle(saccoId),
                title,
                subtitle,
                "Period: " + DATE_FORMATTER.format(fromDate) + " to " + DATE_FORMATTER.format(toDate),
                summaryLine,
                DATE_FORMATTER.format(applicationClock.today()),
                footerTitle,
                headers,
                widths,
                rowsOrEmpty(rows, headers.length, emptyMessage),
                saccoLogoBytes(saccoId)
            );
            renderer.render();
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate PDF report.", ex);
        }
    }

    private String saccoReportTitle(String saccoId) {
        if (saccoId == null || saccoId.isBlank()) {
            return "SACCOS LMS";
        }
        return registeredSaccoRepository.findById(saccoId)
            .map(RegisteredSacco::getSaccoName)
            .filter(name -> !name.isBlank())
            .orElse(saccoId);
    }

    private byte[] saccoLogoBytes(String saccoId) {
        try {
            SaccoLogoStorageService.LogoResource resource = saccoLogoStorageService.load(saccoId);
            return resource == null ? null : resource.content();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return null;
        }
    }

    private static String[] singleCellRow(int columns, String message) {
        String[] row = new String[columns];
        row[0] = message;
        for (int i = 1; i < columns; i++) {
            row[i] = "";
        }
        return row;
    }

    private static List<String[]> rowsOrEmpty(List<String[]> rows, int columns, String message) {
        if (rows == null || rows.isEmpty()) {
            List<String[]> emptyRows = new ArrayList<>();
            emptyRows.add(singleCellRow(columns, message));
            return emptyRows;
        }
        return rows;
    }

    private String applicationLabel(LoanApplication loan) {
        if (loan == null) {
            return "-";
        }
        Object applicationNumber = loan.getApplicationNumber();
        return applicationNumber == null ? shortId(loan.getId()) : applicationNumber.toString();
    }

    private String loanIdLabel(LoanApplication loan) {
        return loan == null ? "-" : blankToFallback(loan.getLoanId(), "-");
    }

    private String applicantLabel(Member applicant) {
        if (applicant == null) {
            return "-";
        }
        return blankToFallback(applicant.getFullName(), applicant.getMemberNo());
    }

    private String loanTypeLabel(LoanApplication loan) {
        return loanProductName(loan);
    }

    private String loanProductName(LoanApplication loan) {
        if (loan == null || loan.getLoanProductSettingId() == null || loan.getSaccoId() == null || loan.getSaccoId().isBlank()) {
            return "-";
        }
        return loanProductSettingRepository.findByIdAndSaccoIdAndActiveTrue(loan.getLoanProductSettingId(), loan.getSaccoId())
            .filter(LoanProductSetting::isAvailableForApplications)
            .map(LoanProductSetting::getDisplayName)
            .filter(name -> name != null && !name.isBlank())
            .orElse("-");
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        StringBuilder label = new StringBuilder();
        int wordIndex = 0;
        for (String part : value.toLowerCase(Locale.ROOT).split("\\s+")) {
            if (part.isBlank()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(wordIndex > 0 && isLowercaseConnectorWord(part)
                ? part
                : Character.toUpperCase(part.charAt(0)) + part.substring(1));
            wordIndex++;
        }
        return label.isEmpty() ? value : label.toString();
    }

    private boolean isLowercaseConnectorWord(String value) {
        return "wa".equals(value) || "ya".equals(value) || "la".equals(value) || "na".equals(value);
    }

    private String shortLoanRow(LoanApplication loan, Member applicant) {
        StringBuilder line = new StringBuilder();
        line.append(shortId(loan.getId())).append(" | ");
        if (applicant != null) {
            line.append(applicant.getFullName()).append(" (").append(applicant.getMemberNo()).append(") | ");
        }
        line.append(loan.getLoanType() == null ? "-" : humanizeLoanType(loan.getLoanType().name())).append(" | ");
        line.append(formatMoney(loan.getAmount())).append(" | ");
        line.append("Disbursed ").append(formatDate(loan.getDisbursementDate())).append(" | ");
        line.append("Due ").append(formatDate(loan.getFinalDueDate())).append(" | ");
        line.append(switch (loan.getStatus()) {
            case PAID -> "PAID on " + formatTimestamp(loan.getPaidAt());
            case DEFAULTED -> "DEFAULTED";
            default -> "ONGOING";
        });
        return line.toString();
    }

    private String accountantLoanRow(AccountantReviewEntry entry, Member applicant) {
        LoanApplication loan = entry.loan();
        ManagerReview review = entry.review();
        StringBuilder line = new StringBuilder();
        line.append(loan.getApplicationNumber() == null ? shortId(loan.getId()) : loan.getApplicationNumber()).append(" | ");
        if (applicant != null) {
            line.append(applicant.getFullName()).append(" (").append(applicant.getMemberNo()).append(") | ");
        }
        line.append(loan.getLoanType() == null ? "-" : humanizeLoanType(loan.getLoanType().name())).append(" | ");
        line.append(formatMoney(loan.getAmount())).append(" | ");
        line.append(review.getDecision() == ManagerDecision.ACCEPT ? "READY FOR DISBURSEMENT" : "REJECTED").append(" | ");
        line.append("Reviewed ").append(formatTimestamp(review.getCreatedAt()));
        return line.toString();
    }

    private String managerWorkflowRow(ManagerWorkflowEntry entry, Member applicant) {
        LoanApplication loan = entry.loan();
        ManagerReview review = entry.review();
        StringBuilder line = new StringBuilder();
        line.append(loan.getApplicationNumber() == null ? shortId(loan.getId()) : loan.getApplicationNumber()).append(" | ");
        if (applicant != null) {
            line.append(applicant.getFullName()).append(" (").append(applicant.getMemberNo()).append(") | ");
        }
        line.append(loan.getLoanType() == null ? "-" : humanizeLoanType(loan.getLoanType().name())).append(" | ");
        line.append(formatMoney(loan.getAmount())).append(" | ");
        line.append(review.getDecision() == ManagerDecision.ACCEPT ? "APPROVED" : "REJECTED").append(" | ");
        line.append("Reviewed ").append(formatTimestamp(review.getCreatedAt())).append(" | ");
        line.append("Current status ").append(humanizeLoanStatusForReport(loan.getStatus()));
        return line.toString();
    }

    private String disbursementLoanRow(DisbursementReviewEntry entry, Member applicant) {
        LoanApplication loan = entry.loan();
        StringBuilder line = new StringBuilder();
        line.append(loan.getLoanId() == null || loan.getLoanId().isBlank() ? shortId(loan.getId()) : loan.getLoanId()).append(" | ");
        line.append(loan.getApplicationNumber() == null ? shortId(loan.getId()) : loan.getApplicationNumber()).append(" | ");
        if (applicant != null) {
            line.append(applicant.getFullName()).append(" (").append(applicant.getMemberNo()).append(") | ");
        }
        line.append(loan.getLoanType() == null ? "-" : humanizeLoanType(loan.getLoanType().name())).append(" | ");
        line.append(formatMoney(loan.getAmount())).append(" | ");
        line.append("Disbursed ").append(formatTimestamp(entry.review().getCreatedAt())).append(" | ");
        line.append("Current status ").append(humanizeLoanStatusForReport(loan.getStatus()));
        return line.toString();
    }

    private String humanizeLoanStatusForReport(LoanStatus status) {
        if (status == null) {
            return "-";
        }
        return switch (status) {
            case READY_FOR_MANAGER -> "On Review By Manager";
            case AWAITING_LOAN_OFFICER -> "On Review By Loan Officer";
            case AWAITING_CHAIRPERSON -> "On Review By Chairperson";
            case AWAITING_BOARD -> "On Review By Board";
            case AWAITING_CREDIT_COMMITTEE -> "On Review By Credit Committee";
            case AWAITING_ACCOUNTANT -> "On Review By Accountant";
            case READY_FOR_DISBURSEMENT -> "Ready for Disbursement";
            case MANAGER_REJECTED -> "Manager Rejected";
            case LOAN_OFFICER_REJECTED -> "Loan Officer Rejected";
            case CHAIRPERSON_REJECTED -> "Chairperson Rejected";
            case BOARD_REJECTED -> "Board Rejected";
            case CREDIT_COMMITTEE_REJECTED -> "Credit Committee Rejected";
            case ACCOUNTANT_REJECTED -> "Accountant Rejected";
            case REJECTED -> "Rejected";
            case DISBURSED -> "Disbursed";
            case DEFAULTED -> "Defaulted";
            case PAID -> "Paid";
            case MANAGER_ACCEPTED -> "Manager Approved";
            case LOAN_OFFICER_APPROVED -> "Loan Officer Approved";
            case CHAIRPERSON_APPROVED -> "Chairperson Approved";
            case BOARD_APPROVED -> "Board Approved";
            case CREDIT_COMMITTEE_APPROVED -> "Credit Committee Approved";
            case ACCOUNTANT_APPROVED -> "Accountant Approved";
            case AWAITING_GUARANTORS -> "Awaiting Guarantors";
            case ALL_GUARANTORS_APPROVED -> "All Guarantors Approved";
            case SUBMITTED -> "Submitted";
            case DRAFT -> "Draft";
        };
    }

    private ManagerDecision resolveAccountantDecisionFilter(String decisionFilter) {
        if (decisionFilter == null || decisionFilter.isBlank()) {
            return null;
        }
        return switch (decisionFilter.trim().toUpperCase(Locale.ENGLISH)) {
            case "APPROVED", "ACCEPT", "ACCEPTED" -> ManagerDecision.ACCEPT;
            case "REJECTED", "REJECT" -> ManagerDecision.REJECT;
            default -> null;
        };
    }

    private BoardDecision resolveBoardDecisionFilter(String decisionFilter) {
        if (decisionFilter == null || decisionFilter.isBlank()) {
            return null;
        }
        return switch (decisionFilter.trim().toUpperCase(Locale.ENGLISH)) {
            case "APPROVED", "ACCEPT", "ACCEPTED" -> BoardDecision.APPROVED;
            case "REJECTED", "REJECT" -> BoardDecision.REJECTED;
            default -> null;
        };
    }

    private String boardDecisionLabel(BoardDecision decision) {
        return decision == BoardDecision.APPROVED ? "Approved" : "Rejected";
    }

    private String accountantDecisionFilterLabel(String decisionFilter) {
        if (decisionFilter == null || decisionFilter.isBlank() || "ALL".equalsIgnoreCase(decisionFilter)) {
            return "All decisions";
        }
        return "APPROVED".equalsIgnoreCase(decisionFilter)
            ? "Ready for disbursement"
            : "Rejected";
    }

    private String managerDecisionFilterLabel(String decisionFilter) {
        if (decisionFilter == null || decisionFilter.isBlank() || "ALL".equalsIgnoreCase(decisionFilter)) {
            return "All decisions";
        }
        return "APPROVED".equalsIgnoreCase(decisionFilter)
            ? "Approved"
            : "Rejected";
    }

    private String boardDecisionFilterLabel(String decisionFilter) {
        if (decisionFilter == null || decisionFilter.isBlank() || "ALL".equalsIgnoreCase(decisionFilter)) {
            return "All decisions";
        }
        return "APPROVED".equalsIgnoreCase(decisionFilter)
            ? "Approved"
            : "Rejected";
    }

    private OffsetDateTime reviewSortTime(BoardReview review) {
        return review.getDecidedAt() == null ? review.getCreatedAt() : review.getDecidedAt();
    }

    private boolean matchesApplicantStation(LoanApplication loan, String stationId) {
        if (loan == null || stationId == null || stationId.isBlank()) {
            return true;
        }
        return loan.getStationId() != null
            && !loan.getStationId().isBlank()
            && stationId.equalsIgnoreCase(loan.getStationId().trim());
    }

    private LocalDate memberReportSortDate(LoanApplication loan) {
        if (loan == null) {
            return null;
        }
        if (loan.getDisbursementDate() != null) {
            return loan.getDisbursementDate();
        }
        return loan.getCreatedAt() == null ? null : loan.getCreatedAt().toLocalDate();
    }

    private List<MemberLoanDetail> buildMemberLoanDetails(Member member, List<LoanApplication> loans) {
        if (loans.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<GuarantorRequest>> guarantorsByLoan = new LinkedHashMap<>();
        Map<UUID, List<BoardReview>> boardReviewsByLoan = new LinkedHashMap<>();
        Set<UUID> memberIds = new java.util.LinkedHashSet<>();

        for (LoanApplication loan : loans) {
            List<GuarantorRequest> requests = guarantorRequestRepository.findByLoanApplicationId(loan.getId());
            guarantorsByLoan.put(loan.getId(), requests);
            requests.stream()
                .map(GuarantorRequest::getGuarantorMemberId)
                .forEach(memberIds::add);

            List<BoardReview> boardReviews = boardReviewRepository.findByLoanApplicationId(loan.getId());
            boardReviewsByLoan.put(loan.getId(), boardReviews);
            boardReviews.stream()
                .map(BoardReview::getBoardMemberId)
                .forEach(memberIds::add);
        }

        Map<UUID, Member> memberMap = memberRepository.findAllById(memberIds).stream()
            .collect(Collectors.toMap(Member::getId, item -> item));

        return loans.stream()
            .map(loan -> buildMemberLoanDetail(
                member,
                loan,
                guarantorsByLoan.getOrDefault(loan.getId(), List.of()),
                boardReviewsByLoan.getOrDefault(loan.getId(), List.of()),
                memberMap
            ))
            .toList();
    }

    private MemberLoanDetail buildMemberLoanDetail(Member member,
                                                   LoanApplication loan,
                                                   List<GuarantorRequest> guarantorRequests,
                                                   List<BoardReview> boardReviews,
                                                   Map<UUID, Member> memberMap) {
        Map<String, Object> financialSnapshot = parseJsonMap(loan.getFinancialSnapshot());
        String applicationFeeLabel = formatMoney(readBigDecimal(financialSnapshot.get("applicationFee")));
        String insuranceFeeLabel = formatMoney(readBigDecimal(financialSnapshot.get("insuranceFee")));
        String processingFeeLabel = formatMoney(readBigDecimal(financialSnapshot.get("processingFee")));
        String totalDeductionsLabel = formatMoney(resolveTotalDeductions(financialSnapshot));
        String deductionSummary = "Application Fee: " + applicationFeeLabel
            + ", Insurance Fee: " + insuranceFeeLabel
            + ", Loan Processing Fee: " + processingFeeLabel
            + ", Total Deductions: " + totalDeductionsLabel;
        return new MemberLoanDetail(
            loan.getId(),
            loan.getApplicationNumber() == null ? shortId(loan.getId()) : loan.getApplicationNumber().toString(),
            loan.getLoanId() == null || loan.getLoanId().isBlank() ? "-" : loan.getLoanId(),
            formatMemberDetails(member),
            loanTypeLabel(loan),
            formatMoney(loan.getAmount()),
            loan.getTenorMonths() == null ? "-" : loan.getTenorMonths() + " month(s)",
            applicationFeeLabel,
            insuranceFeeLabel,
            processingFeeLabel,
            totalDeductionsLabel,
            deductionSummary,
            formatGuarantorDetails(guarantorRequests, memberMap),
            formatApprovalSummary(boardReviews, memberMap),
            formatDate(loan.getDisbursementDate()),
            formatDate(loan.getFinalDueDate()),
            formatMemberLoanStatus(loan),
            member.getFullName(),
            DATE_FORMATTER.format(applicationClock.today())
        );
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private BigDecimal readBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal resolveTotalDeductions(Map<String, Object> financialSnapshot) {
        BigDecimal explicit = readBigDecimal(financialSnapshot.get("totalDeductions"));
        if (explicit.compareTo(BigDecimal.ZERO) > 0) {
            return explicit;
        }
        return readBigDecimal(financialSnapshot.get("applicationFee"))
            .add(readBigDecimal(financialSnapshot.get("insuranceFee")))
            .add(readBigDecimal(financialSnapshot.get("processingFee")))
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String formatMemberDetails(Member member) {
        List<String> parts = new ArrayList<>();
        parts.add(member.getFullName() + " (" + member.getMemberNo() + ")");
        if (member.getEmail() != null && !member.getEmail().isBlank()) {
            parts.add(member.getEmail());
        }
        if (member.getPhone() != null && !member.getPhone().isBlank()) {
            parts.add(member.getPhone());
        }
        return String.join(" | ", parts);
    }

    private String formatGuarantorDetails(List<GuarantorRequest> guarantorRequests, Map<UUID, Member> memberMap) {
        if (guarantorRequests == null || guarantorRequests.isEmpty()) {
            return "No guarantor details available";
        }
        return guarantorRequests.stream()
            .map(request -> {
                Member guarantor = memberMap.get(request.getGuarantorMemberId());
                String guarantorLabel = guarantor == null
                    ? shortId(request.getGuarantorMemberId())
                    : guarantor.getFullName() + " (" + guarantor.getMemberNo() + ")";
                return guarantorLabel + " - " + humanizeGuarantorStatus(request.getStatus());
            })
            .collect(Collectors.joining("; "));
    }

    private String humanizeGuarantorStatus(GuarantorRequestStatus status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
            case EXPIRED -> "Expired";
            case PENDING -> "Pending";
        };
    }

    private String formatApprovalSummary(List<BoardReview> boardReviews,
                                         Map<UUID, Member> memberMap) {
        if (boardReviews != null && !boardReviews.isEmpty()) {
            return boardReviews.stream()
                .map(review -> {
                    Member boardMember = memberMap.get(review.getBoardMemberId());
                    String boardName = boardMember == null ? "Board Member" : boardMember.getFullName();
                    String status = review.getDecision() == null ? "Pending" : switch (review.getDecision()) {
                        case APPROVED -> "Approved";
                        case REJECTED -> "Rejected";
                        case PENDING -> "Pending";
                    };
                    String note = review.getComment() == null || review.getComment().isBlank()
                        ? ""
                        : " (" + review.getComment().trim() + ")";
                    return boardName + ": " + status + note;
                })
                .collect(Collectors.joining("; "));
        }
        return "No board committee decision details available";
    }

    private StationParticipationSummary buildStationParticipation(long activeMembers, List<LoanApplication> appliedLoans) {
        Map<UUID, Long> applicationsByApplicant = appliedLoans.stream()
            .filter(loan -> loan.getApplicantMemberId() != null)
            .collect(Collectors.groupingBy(LoanApplication::getApplicantMemberId, LinkedHashMap::new, Collectors.counting()));
        long uniqueApplicants = applicationsByApplicant.size();
        long repeatApplicants = applicationsByApplicant.values().stream().filter(count -> count > 1).count();
        BigDecimal participationRate = activeMembers == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(uniqueApplicants)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(activeMembers), 2, RoundingMode.HALF_UP);
        BigDecimal applicationsPerApplicant = uniqueApplicants == 0
            ? BigDecimal.ZERO
            : BigDecimal.valueOf(appliedLoans.size())
                .divide(BigDecimal.valueOf(uniqueApplicants), 2, RoundingMode.HALF_UP);
        return new StationParticipationSummary(
            activeMembers,
            uniqueApplicants,
            participationRate,
            applicationsPerApplicant,
            repeatApplicants
        );
    }

    private List<StationStatusRow> stationStatusRows(List<LoanApplication> loans) {
        return List.of(
            stationStatusRow("Applied Loans", loans, loan -> loan.getStatus() != LoanStatus.DRAFT),
            stationStatusRow("Active Loans", loans, loan -> ACTIVE_STATUSES.contains(loan.getStatus())),
            stationStatusRow("Disbursed Loans", loans, loan -> DISBURSED_STATUSES.contains(loan.getStatus())),
            stationStatusRow("Paid Loans", loans, loan -> loan.getStatus() == LoanStatus.PAID),
            stationStatusRow("Defaulted Loans", loans, loan -> loan.getStatus() == LoanStatus.DEFAULTED),
            stationStatusRow("Rejected Loans", loans, loan -> REJECTED_STATUSES.contains(loan.getStatus()))
        );
    }

    private StationStatusRow stationStatusRow(String label,
                                              List<LoanApplication> loans,
                                              java.util.function.Predicate<LoanApplication> predicate) {
        List<LoanApplication> matching = loans.stream().filter(predicate).toList();
        long applicants = matching.stream()
            .map(LoanApplication::getApplicantMemberId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .count();
        return new StationStatusRow(label, matching.size(), applicants);
    }

    private List<StationProductRow> stationProductRows(List<LoanApplication> loans,
                                                       LoanType selectedLoanType) {
        return stationProductRows(loans, loans, selectedLoanType);
    }

    private List<StationProductRow> stationProductRows(List<LoanApplication> loans,
                                                       List<LoanApplication> financialLoans,
                                                       LoanType selectedLoanType) {
        List<ProductRef> productRefs = configuredProductRefs(loans, financialLoans).stream()
            .filter(product -> selectedLoanType == null || product.loanType() == selectedLoanType)
            .toList();
        if (productRefs.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<LoanApplication>> loansByProductId = groupByLoanProductId(loans);
        Map<UUID, List<LoanApplication>> financialLoansByProductId = groupByLoanProductId(financialLoans);
        List<StationProductRow> rows = new ArrayList<>();
        for (ProductRef product : productRefs) {
            List<LoanApplication> typedLoans = loansByProductId.getOrDefault(product.loanProductId(), List.of());
            List<LoanApplication> typedFinancialLoans = financialLoansByProductId.getOrDefault(product.loanProductId(), List.of());
            rows.add(new StationProductRow(
                product.label(),
                typedLoans.stream().filter(loan -> loan.getStatus() != LoanStatus.DRAFT).count(),
                typedLoans.stream().filter(loan -> loan.getStatus() == LoanStatus.MANAGER_ACCEPTED
                    || loan.getStatus() == LoanStatus.LOAN_OFFICER_APPROVED
                    || loan.getStatus() == LoanStatus.BOARD_APPROVED
                    || loan.getStatus() == LoanStatus.ACCOUNTANT_APPROVED
                    || DISBURSED_STATUSES.contains(loan.getStatus())).count(),
                typedLoans.stream().filter(loan -> ACTIVE_STATUSES.contains(loan.getStatus())).count(),
                typedLoans.stream().filter(loan -> DISBURSED_STATUSES.contains(loan.getStatus())).count(),
                typedLoans.stream().filter(loan -> loan.getStatus() == LoanStatus.PAID).count(),
                typedLoans.stream().filter(loan -> loan.getStatus() == LoanStatus.DEFAULTED).count(),
                typedLoans.stream().filter(loan -> REJECTED_STATUSES.contains(loan.getStatus())).count(),
                sumInterest(typedFinancialLoans, false),
                sumInterest(typedFinancialLoans, true)
            ));
        }
        return rows;
    }

    private List<StationYearlySummaryRow> stationYearlyRows(List<LoanApplication> loans,
                                                            LocalDate fromDate,
                                                            LocalDate toDate) {
        return stationYearlyRows(loans, loans, fromDate, toDate);
    }

    private List<StationYearlySummaryRow> stationYearlyRows(List<LoanApplication> loans,
                                                            List<LoanApplication> financialLoans,
                                                            LocalDate fromDate,
                                                            LocalDate toDate) {
        List<StationYearlySummaryRow> rows = new ArrayList<>();
        int startYear = Year.from(fromDate).getValue();
        int endYear = Year.from(toDate).getValue();
        for (int year = startYear; year <= endYear; year++) {
            int currentYear = year;
            List<LoanApplication> yearlyApplicationLoans = loans.stream()
                .filter(loan -> loan.getStatus() != LoanStatus.DRAFT)
                .filter(loan -> loan.getCreatedAt() != null && loan.getCreatedAt().getYear() == currentYear)
                .toList();
            List<LoanApplication> yearlyPaidLoans = loans.stream()
                .filter(loan -> loan.getStatus() == LoanStatus.PAID)
                .filter(loan -> loanYear(loan) == currentYear)
                .toList();
            long defaulted = loans.stream()
                .filter(loan -> loan.getStatus() == LoanStatus.DEFAULTED)
                .filter(loan -> loanYear(loan) == currentYear)
                .count();
            long uniqueApplicants = yearlyApplicationLoans.stream()
                .map(LoanApplication::getApplicantMemberId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
            BigDecimal defaultRate = yearlyApplicationLoans.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(defaulted)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(yearlyApplicationLoans.size()), 2, RoundingMode.HALF_UP);
            BigDecimal paidInterest = sumInterestForYear(financialLoans, currentYear, false);
            BigDecimal fullyPaidInterest = sumInterestForYear(financialLoans, currentYear, true);
            rows.add(new StationYearlySummaryRow(
                currentYear,
                yearlyApplicationLoans.size(),
                uniqueApplicants,
                defaulted,
                defaultRate,
                yearlyPaidLoans.size(),
                paidInterest,
                fullyPaidInterest
            ));
        }
        return rows;
    }

    private int loanYear(LoanApplication loan) {
        if (loan.getPaidAt() != null) {
            return loan.getPaidAt().getYear();
        }
        if (loan.getUpdatedAt() != null) {
            return loan.getUpdatedAt().getYear();
        }
        return loan.getCreatedAt() == null ? 0 : loan.getCreatedAt().getYear();
    }

    private BigDecimal sumInterest(List<LoanApplication> loans, boolean fullyPaidOnly) {
        return loans.stream()
            .filter(loan -> !fullyPaidOnly || loan.getStatus() == LoanStatus.PAID)
            .map(this::stationInterestPaidAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumInterestForYear(List<LoanApplication> loans,
                                          int year,
                                          boolean fullyPaidOnly) {
        return loans.stream()
            .filter(loan -> !fullyPaidOnly || loan.getStatus() == LoanStatus.PAID)
            .map(loan -> stationInterestPaidForYear(loan, year))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal stationInterestPaidAmount(LoanApplication loan) {
        return loan == null || loan.getStatus() != LoanStatus.PAID
            ? BigDecimal.ZERO
            : requiredInterestAmount(loan);
    }

    private BigDecimal stationInterestPaidForYear(LoanApplication loan, int year) {
        return loan != null && loan.getStatus() == LoanStatus.PAID && loanYear(loan) == year
            ? requiredInterestAmount(loan)
            : BigDecimal.ZERO;
    }

    private int writeTitle(XSSFSheet sheet, CellStyle titleStyle, String title) {
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(titleStyle);
        return 1;
    }

    private int writeKeyValueTable(XSSFSheet sheet, int rowIndex, CellStyle headerStyle, List<String[]> rows) {
        Row header = sheet.createRow(rowIndex++);
        header.createCell(0).setCellValue("Field");
        header.createCell(1).setCellValue("Value");
        header.getCell(0).setCellStyle(headerStyle);
        header.getCell(1).setCellStyle(headerStyle);
        for (String[] item : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(item[0]);
            row.createCell(1).setCellValue(item[1]);
        }
        return rowIndex;
    }

    private int writeStatusTable(XSSFSheet sheet, int rowIndex, CellStyle headerStyle, List<StationStatusRow> rows) {
        Row title = sheet.createRow(rowIndex++);
        title.createCell(0).setCellValue("LOAN STATUS SUMMARY");
        title.getCell(0).setCellStyle(headerStyle);
        Row header = sheet.createRow(rowIndex++);
        String[] columns = {"Metric", "Count", "Number of Applicants"};
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
            header.getCell(i).setCellStyle(headerStyle);
        }
        for (StationStatusRow item : rows) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(item.metric());
            row.createCell(1).setCellValue(item.count());
            row.createCell(2).setCellValue(item.applicantCount());
        }
        return rowIndex;
    }

    private int writeProductTable(XSSFSheet sheet, int rowIndex, CellStyle headerStyle, List<StationProductRow> rows) {
        Row header = sheet.createRow(rowIndex++);
        String[] columns = {
            "Loan Product",
            "Applications",
            "Approved",
            "Active",
            "Disbursed",
            "Paid",
            "Defaulted",
            "Rejected",
            "Total Paid Interest Accumulated",
            "Total Interest of Fully Paid Loan"
        };
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
            header.getCell(i).setCellStyle(headerStyle);
        }
        for (StationProductRow item : rows) {
            Row row = sheet.createRow(rowIndex++);
            int column = 0;
            row.createCell(column++).setCellValue(item.loanProduct());
            row.createCell(column++).setCellValue(item.applications());
            row.createCell(column++).setCellValue(item.approved());
            row.createCell(column++).setCellValue(item.active());
            row.createCell(column++).setCellValue(item.disbursed());
            row.createCell(column++).setCellValue(item.paid());
            row.createCell(column++).setCellValue(item.defaulted());
            row.createCell(column++).setCellValue(item.rejected());
            row.createCell(column++).setCellValue(item.totalPaidInterest().doubleValue());
            row.createCell(column).setCellValue(item.fullyPaidLoanInterest().doubleValue());
        }
        return rowIndex;
    }

    private int writeYearlyTable(XSSFSheet sheet, int rowIndex, CellStyle headerStyle, List<StationYearlySummaryRow> rows) {
        Row header = sheet.createRow(rowIndex++);
        String[] columns = {
            "Year",
            "Total Loan Applications",
            "Unique Applicants",
            "Defaulted Loans",
            "Default Rate",
            "Total Paid Loans",
            "Total Paid Interest Accumulated",
            "Total Interest of Fully Paid Loan"
        };
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
            header.getCell(i).setCellStyle(headerStyle);
        }
        for (StationYearlySummaryRow item : rows) {
            Row row = sheet.createRow(rowIndex++);
            int column = 0;
            row.createCell(column++).setCellValue(item.year());
            row.createCell(column++).setCellValue(item.totalLoanApplications());
            row.createCell(column++).setCellValue(item.uniqueApplicants());
            row.createCell(column++).setCellValue(item.defaultedLoans());
            row.createCell(column++).setCellValue(item.defaultRateLabel());
            row.createCell(column++).setCellValue(item.totalPaidLoans());
            row.createCell(column++).setCellValue(item.totalPaidInterestAccumulated().doubleValue());
            row.createCell(column).setCellValue(item.fullyPaidLoanInterest().doubleValue());
        }
        return rowIndex;
    }

    private void addStationYearlyChart(XSSFSheet sheet, int tableStartRow, int dataRowCount) {
        if (dataRowCount <= 0) {
            return;
        }
        int dataStart = tableStartRow + 1;
        int dataEnd = dataStart + dataRowCount - 1;
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, dataEnd + 3, 8, dataEnd + 21);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Yearly Loan and Interest Summary");
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.TOP);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle("Year");
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle("Loans");
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);

        XDDFDataSource<Double> years = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, new CellRangeAddress(dataStart, dataEnd, 0, 0));
        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);
        addStationYearlySeries(data, years, sheet, dataStart, dataEnd, 1, "Applications");
        addStationYearlySeries(data, years, sheet, dataStart, dataEnd, 5, "Paid Loans");
        addStationYearlySeries(data, years, sheet, dataStart, dataEnd, 3, "Defaulted Loans");
        chart.plot(data);
    }

    private void addStationYearlySeries(XDDFChartData data,
                                        XDDFDataSource<Double> years,
                                        XSSFSheet sheet,
                                        int dataStart,
                                        int dataEnd,
                                        int column,
                                        String title) {
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
            sheet, new CellRangeAddress(dataStart, dataEnd, column, column));
        XDDFChartData.Series series = data.addSeries(years, values);
        series.setTitle(title, null);
        if (series instanceof XDDFLineChartData.Series lineSeries) {
            lineSeries.setSmooth(false);
        }
    }

    private String formatMemberLoanStatus(LoanApplication loan) {
        if (loan == null || loan.getStatus() == null) {
            return "Unknown";
        }
        return switch (loan.getStatus()) {
            case DRAFT -> "Draft";
            case SUBMITTED -> "Submitted";
            case AWAITING_GUARANTORS -> "Awaiting Guarantors";
            case ALL_GUARANTORS_APPROVED -> "All Guarantors Approved";
            case READY_FOR_MANAGER -> "On Review By Manager";
            case MANAGER_REJECTED -> "Manager Rejected";
            case MANAGER_ACCEPTED -> "Ready for Disbursement";
            case AWAITING_LOAN_OFFICER -> "On Review By Loan Officer";
            case LOAN_OFFICER_REJECTED -> "Loan Officer Rejected";
            case LOAN_OFFICER_APPROVED -> "Loan Officer Approved";
            case AWAITING_CHAIRPERSON -> "On Review By Chairperson";
            case CHAIRPERSON_REJECTED -> "Chairperson Rejected";
            case CHAIRPERSON_APPROVED -> "Chairperson Approved";
            case AWAITING_BOARD -> "On Review By Board";
            case AWAITING_CREDIT_COMMITTEE -> "On Review By Credit Committee";
            case BOARD_REJECTED -> "Board Rejected";
            case BOARD_APPROVED -> "Reviewed";
            case CREDIT_COMMITTEE_REJECTED -> "Credit Committee Rejected";
            case CREDIT_COMMITTEE_APPROVED -> "Reviewed";
            case AWAITING_ACCOUNTANT -> "On Review By Accountant";
            case ACCOUNTANT_REJECTED -> "Accountant Rejected";
            case ACCOUNTANT_APPROVED -> "Accountant Approved";
            case READY_FOR_DISBURSEMENT -> "Ready for Disbursement";
            case REJECTED -> "Rejected";
            case DISBURSED -> "Disbursed";
            case DEFAULTED -> "Defaulted / Not Paid";
            case PAID -> "Paid";
        };
    }

    private String formatMoney(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.##", new java.text.DecimalFormatSymbols(Locale.US));
        return "TSh " + format.format(safe);
    }

    private String shortId(UUID id) {
        return id == null ? "-" : id.toString().substring(0, 8);
    }

    private String humanizeLoanType(String text) {
        return text == null ? "" : text.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : DATE_FORMATTER.format(date);
    }

    private String formatTimestamp(OffsetDateTime dateTime) {
        return dateTime == null ? "-" : applicationClock.zoned(dateTime).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
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

    public enum ReportKind {
        MEMBER,
        STATION,
        STAFF
    }

    private record DateRange(LocalDate fromDate, LocalDate toDate) {}

    private record ExportContext(
        String saccoName,
        String stationId,
        String branchName,
        LocalDate fromDate,
        LocalDate toDate,
        String loanProductLabel
    ) {}

    private record ProductRef(UUID loanProductId, LoanType loanType, String label) {}

    public record ProductPerformanceRow(
        String label,
        long applied,
        long active,
        long disbursed,
        long paid,
        long defaulted,
        long rejected,
        BigDecimal interestPaid,
        BigDecimal fullyPaidInterest
    ) {
        public String getInterestPaidLabel() {
            return formatMoneyPlainStatic(interestPaid);
        }

        public String getFullyPaidInterestLabel() {
            return formatMoneyPlainStatic(fullyPaidInterest);
        }
    }

    public record ProductFinancialBreakdownRow(
        String loanProduct,
        BigDecimal totalInterestPaid,
        BigDecimal totalInterestUnpaid,
        BigDecimal totalLoanAmountPaid,
        BigDecimal totalLoanAmountUnpaid
    ) {
        public String getLoanProduct() {
            return loanProduct;
        }

        public String getTotalInterestPaidLabel() {
            return formatMoneyPlainStatic(totalInterestPaid);
        }

        public String totalInterestPaidLabel() {
            return getTotalInterestPaidLabel();
        }

        public String getTotalInterestUnpaidLabel() {
            return formatMoneyPlainStatic(totalInterestUnpaid);
        }

        public String totalInterestUnpaidLabel() {
            return getTotalInterestUnpaidLabel();
        }

        public String getTotalLoanAmountPaidLabel() {
            return formatMoneyPlainStatic(totalLoanAmountPaid);
        }

        public String totalLoanAmountPaidLabel() {
            return getTotalLoanAmountPaidLabel();
        }

        public String getTotalLoanAmountUnpaidLabel() {
            return formatMoneyPlainStatic(totalLoanAmountUnpaid);
        }

        public String totalLoanAmountUnpaidLabel() {
            return getTotalLoanAmountUnpaidLabel();
        }
    }

    public record ActiveLoanDetailRow(
        String loanId,
        String loanProduct,
        String loanAmount,
        String outstandingBalance,
        String requiredInterestAmount,
        String paidLoanAmount,
        String interestPaid,
        String interestNotYetPaid
    ) {
        public String getLoanId() {
            return loanId;
        }

        public String getLoanProduct() {
            return loanProduct;
        }

        public String getLoanAmount() {
            return loanAmount;
        }

        public String getOutstandingBalance() {
            return outstandingBalance;
        }

        public String getRequiredInterestAmount() {
            return requiredInterestAmount;
        }

        public String getPaidLoanAmount() {
            return paidLoanAmount;
        }

        public String getInterestPaid() {
            return interestPaid;
        }

        public String getInterestNotYetPaid() {
            return interestNotYetPaid;
        }
    }

    public record FinancialSummary(
        BigDecimal activeLoanAmount,
        BigDecimal totalDisbursedAmount,
        BigDecimal totalRepaidAmount,
        BigDecimal outstandingBalance,
        BigDecimal defaultedAmount
    ) {}

    public record ActivityRow(
        String date,
        String activity,
        String loanProduct,
        String amount,
        String status
    ) {}

    public record AnalyticsExportReport(
        ReportKind kind,
        String saccoName,
        String saccoId,
        String title,
        String stationId,
        String branchName,
        LocalDate fromDate,
        LocalDate toDate,
        LocalDate generatedOn,
        String generatedBy,
        String generatedByRole,
        String loanProductLabel,
        Member member,
        LoanAnalyticsService.MemberLoanAnalytics analytics,
        LoanAnalyticsService.StaffPortfolioSummary portfolioSummary,
        List<ProductPerformanceRow> productRows,
        List<ProductFinancialBreakdownRow> productFinancialRows,
        LoanAnalyticsService.StaffReviewAnalytics staffReviewAnalytics,
        List<LoanAnalyticsService.MetricTrendSeries> trendSeries,
        List<ActivityRow> activityRows,
        List<ActiveLoanDetailRow> activeLoanRows,
        FinancialSummary financialSummary,
        List<String> observations,
        String remarks
    ) {}

    private static final class ExcelStyles {
        private final CellStyle title;
        private final CellStyle subtitle;
        private final CellStyle section;
        private final CellStyle header;
        private final CellStyle body;

        private ExcelStyles(XSSFWorkbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title = workbook.createCellStyle();
            title.setFont(titleFont);

            Font subtitleFont = workbook.createFont();
            subtitleFont.setBold(true);
            subtitleFont.setFontHeightInPoints((short) 11);
            subtitle = workbook.createCellStyle();
            subtitle.setFont(subtitleFont);

            Font sectionFont = workbook.createFont();
            sectionFont.setBold(true);
            section = bordered(workbook);
            section.setFont(sectionFont);
            section.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            section.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            header = bordered(workbook);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            header.setFont(headerFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);

            body = bordered(workbook);
            body.setVerticalAlignment(VerticalAlignment.CENTER);
            body.setIndention((short) 1);
        }

        private static CellStyle bordered(XSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setWrapText(true);
            return style;
        }
    }

    private final class FormalPdf {
        private static final float MARGIN = 28f;
        private static final float SMALL = 7.2f;
        private static final float BODY = 8.2f;
        private static final float HEADER = 10f;
        private static final float TITLE = 16f;

        private final PDDocument document;
        private final AnalyticsExportReport report;
        private final PDRectangle size;
        private final byte[] watermarkLogo;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private FormalPdf(PDDocument document, AnalyticsExportReport report, PDRectangle size, byte[] watermarkLogo) {
            this.document = document;
            this.report = report;
            this.size = size;
            this.watermarkLogo = watermarkLogo;
        }

        private void renderMemberOnePage() throws IOException {
            open();
            float width = page.getMediaBox().getWidth();
            write(report.saccoName(), MARGIN + 52, y, bold, TITLE);
            write("LOAN APPLICATION PORTAL", MARGIN + 52, y - 16, bold, HEADER);
            right(report.title(), width - MARGIN, y, bold, TITLE);
            right("Loan Reports and Analytics", width - MARGIN, y - 18, regular, HEADER + 1);
            line(MARGIN, y - 30, width - MARGIN, y - 30);
            y -= 48;

            float third = (width - (MARGIN * 2)) / 3f;
            drawInfoPanel(MARGIN, y, third - 8, "MEMBER INFORMATION", List.of(
                new String[]{"Member Name", report.member().getFullName()},
                new String[]{"Member Number", report.member().getMemberNo()},
                new String[]{"Station", report.stationId() + " - " + report.branchName()}
            ));
            drawInfoPanel(MARGIN + third, y, third - 8, "REPORT DETAILS", List.of(
                new String[]{"Reporting Period", periodLabel(report)},
                new String[]{"Generated On", humanDate(report.generatedOn())},
                new String[]{"Generated By", report.generatedBy()}
            ));
            drawInfoPanel(MARGIN + (third * 2), y, third - 8, "REPORT SUMMARY", List.of(
                new String[]{"Loan Product", report.loanProductLabel()},
                new String[]{"Report Type", "Member Loan Summary"}
            ));
            y -= 88;

            section("1. LOAN STATUS SUMMARY");
            metricBoxes(MARGIN, y, width - (MARGIN * 2));
            y -= 78;

            float leftWidth = (width - (MARGIN * 2) - 18) * 0.44f;
            float rightWidth = (width - (MARGIN * 2) - 18) - leftWidth;
            float tableTop = y;
            sectionAt(MARGIN, tableTop, "2. LOAN STATUS BREAKDOWN");
            table(MARGIN, tableTop - 16, new float[]{leftWidth * .45f, leftWidth * .22f, leftWidth * .33f},
                new String[]{"Status", "Count", "Applicants"},
                statusRows(report), SMALL, 15f);
            sectionAt(MARGIN + leftWidth + 18, tableTop, "3. LOAN PRODUCT PERFORMANCE");
            table(MARGIN + leftWidth + 18, tableTop - 16,
                new float[]{rightWidth * .34f, rightWidth * .13f, rightWidth * .13f, rightWidth * .15f, rightWidth * .12f, rightWidth * .13f},
                new String[]{"Loan Product", "Applied", "Active", "Disbursed", "Paid", "Rejected"},
                report.productRows().stream()
                    .map(row -> new String[]{row.label(), String.valueOf(row.applied()), String.valueOf(row.active()), String.valueOf(row.disbursed()), String.valueOf(row.paid()), String.valueOf(row.rejected())})
                    .toList(), SMALL, 15f);
            y -= 136;

            section("4. RISK / ELIGIBILITY SUMMARY");
            table(MARGIN, y, new float[]{142, 86, 104, 88, 88, 120},
                new String[]{"Current Active Loan Amount", "Defaulted Loans", "Rejected Loans", "Total Guaranteed Amount", "Can Apply", "Can Guarantee"},
                List.of(new String[]{moneyPlain(report.financialSummary().activeLoanAmount()), String.valueOf(report.analytics().defaultedLoans()), String.valueOf(report.analytics().rejectedLoans()), moneyPlain(BigDecimal.ZERO), "Yes", "Yes"},
                    new String[]{"Eligibility Reason", report.remarks(), "", "", "", ""}), SMALL, 20f);
            y -= 60;

            section("5. LOAN TREND OVER TIME");
            drawTrendChart(MARGIN, y, width - (MARGIN * 2), 105);
            y -= 125;

            float half = (width - (MARGIN * 2) - 18) / 2f;
            sectionAt(MARGIN, y, "6. LOAN FINANCIAL SUMMARY");
            table(MARGIN, y - 16, new float[]{half * .62f, half * .38f}, new String[]{"Item", "Amount (TZS)"}, financialRows(), SMALL, 15f);
            sectionAt(MARGIN + half + 18, y, "7. RECENT LOAN ACTIVITY");
            table(MARGIN + half + 18, y - 16, new float[]{58, 94, 95, 70, half - 317}, new String[]{"Date", "Activity", "Loan Product", "Amount", "Status"}, activityRows(), SMALL, 15f);
            y -= 96;
            sectionAt(MARGIN, y, "8. REMARKS");
            box(MARGIN, y - 64, half, 58);
            writeWrapped(report.remarks(), MARGIN + 8, y - 20, half - 16, SMALL);
            sectionAt(MARGIN + half + 18, y, "9. SIGN-OFF");
            signOff(MARGIN + half + 18, y - 18, half);
            close(1, 1, "Member Loan Report");
        }

        private void renderMemberTwoPage() throws IOException {
            int pageCount = activeLoanRowsForPdf().size() > 6 ? 3 : 2;
            open();
            memberHeader(false);
            section("1. MEMBER INFORMATION");
            table(MARGIN, y, new float[]{170, 250}, new String[]{"Field", "Value"}, List.of(
                new String[]{"Member Name", report.member().getFullName()},
                new String[]{"Member Number", report.member().getMemberNo()},
                new String[]{"Station", report.stationId() + " - " + report.branchName()}
            ), BODY, 16f);
            y -= 80;
            section("2. REPORT DETAILS");
            table(MARGIN, y, new float[]{170, 250}, new String[]{"Field", "Value"}, List.of(
                new String[]{"Reporting Period", periodLabel(report)},
                new String[]{"Generated On", humanDate(report.generatedOn())},
                new String[]{"Generated By", report.generatedBy()},
                new String[]{"Report Type", "Member Loan Summary"},
                new String[]{"Loan Product", report.loanProductLabel()}
            ), BODY, 16f);
            y -= 112;
            section("3. LOAN STATUS SUMMARY");
            table(MARGIN, y, new float[]{220, 120, 145}, new String[]{"Metric", "Count", "Number of Applicants"},
                statusRows(report), BODY, 17f);
            y -= 170;
            section("5. LOAN PRODUCT PERFORMANCE");
            table(MARGIN, y, new float[]{96, 38, 38, 46, 34, 42, 40, 82, 109},
                new String[]{"Loan Product", "Applied", "Active", "Disbursed", "Paid", "Defaulted", "Rejected", "Total Interest Paid", "Total Interest of Fully Paid Loan"},
                productRowsForPdf(false), SMALL - 0.4f, 24f);
            close(1, pageCount, "Member Loan Report");

            open();
            right("Generated: " + humanDate(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y, regular, SMALL);
            y -= 34;
            section("6. RISK / ELIGIBILITY SUMMARY");
            table(MARGIN, y, new float[]{122, 72, 88, 108, 55, 72},
                new String[]{"Current Active Loan Amount", "Defaulted Loans", "Rejected Loans", "Total Guaranteed Amount", "Can Apply", "Can Guarantee"},
                java.util.Collections.singletonList(new String[]{
                    moneyPlain(report.financialSummary().activeLoanAmount()),
                    String.valueOf(report.analytics().defaultedLoans()),
                    String.valueOf(report.analytics().rejectedLoans()),
                    moneyPlain(BigDecimal.ZERO),
                    "Yes",
                    "Yes"
                }), SMALL, 30f);
            y -= 72;
            section("7. LOAN TREND OVER TIME");
            drawTrendChart(MARGIN, y, page.getMediaBox().getWidth() - (MARGIN * 2), 150);
            y -= 175;
            section("8. ACTIVE LOAN DETAILS");
            y = table(MARGIN, y, new float[]{46, 88, 65, 78, 78, 64, 58, 58},
                new String[]{"Loan ID", "Loan Product", "Loan Amount", "Outstanding Balance", "Required Interest", "Paid Loan Amount", "Interest Paid", "Remaining Interest"},
                activeLoanRowsForPdf(), SMALL, 22f);
            y -= 24;
            if (pageCount == 3) {
                close(2, pageCount, "Member Loan Report");
                open();
                right("Generated: " + humanDate(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y, regular, SMALL);
                y -= 50;
            }
            float half = (page.getMediaBox().getWidth() - (MARGIN * 2)) / 2f;
            box(MARGIN, y - 118, half - 8, 110);
            sectionAt(MARGIN + 10, y - 20, "10. REMARKS");
            writeWrapped(report.remarks(), MARGIN + 10, y - 38, half - 26, SMALL);
            box(MARGIN + half + 8, y - 118, half - 8, 110);
            sectionAt(MARGIN + half + 18, y - 20, "11. SIGN-OFF");
            signOff(MARGIN + half + 18, y - 38, half - 18);
            close(pageCount, pageCount, "Member Loan Report");
        }

        private void renderStaffTwoPage() throws IOException {
            open();
            write(report.saccoName(), MARGIN, y, bold, TITLE);
            write(report.title(), MARGIN, y - 18, bold, HEADER + 2);
            right("Generated: " + humanDate(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y - 8, regular, SMALL);
            write("Loan Review Performance and Analytics", MARGIN, y - 42, regular, HEADER + 1);
            line(MARGIN, y - 56, page.getMediaBox().getWidth() - MARGIN, y - 56);
            y -= 86;

            float half = (page.getMediaBox().getWidth() - (MARGIN * 2) - 18) / 2f;
            sectionAt(MARGIN, y, "1. STAFF INFORMATION");
            table(MARGIN, y - 18, new float[]{105, half - 105}, new String[]{"Field", "Value"}, List.of(
                new String[]{"Staff Name", report.generatedBy()},
                new String[]{"Position", report.generatedByRole()},
                new String[]{"Branch/Station", report.stationId() + " - " + report.branchName()}
            ), BODY, 18f);
            sectionAt(MARGIN + half + 18, y, "2. REPORT DETAILS");
            table(MARGIN + half + 18, y - 18, new float[]{115, half - 115}, new String[]{"Field", "Value"}, List.of(
                new String[]{"Reporting Period", periodLabel(report)},
                new String[]{"Generated On", humanDate(report.generatedOn())},
                new String[]{"Generated By", report.generatedBy()},
                new String[]{"Report Type", "Staff Loan Review Summary"},
                new String[]{"Loan Product", report.loanProductLabel()}
            ), BODY, 18f);
            y -= 130;

            section("3. REVIEW SUMMARY OVERVIEW");
            y = table(MARGIN, y, new float[]{205, 145, 175}, new String[]{"Metric", "Count", "Number of Applicants"},
                staffReviewSummaryRows(), BODY, 18f);
            y -= 28;
            section("4. LOAN PRODUCT REVIEW BREAKDOWN");
            y = table(MARGIN, y, new float[]{132, 72, 72, 72, 72, 105},
                new String[]{"Loan Product", "Reviewed", "Approved", "Rejected", "Pending", "Approval Rate"},
                staffProductReviewRows(), SMALL, 19f);
            close(1, 2, "Staff Loan Review Report");

            open();
            right("Generated: " + humanDate(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y, regular, SMALL);
            y -= 34;
            section("5. REVIEW TREND OVER TIME");
            drawTrendChart(MARGIN, y, page.getMediaBox().getWidth() - (MARGIN * 2), 160);
            y -= 188;
            sectionAt(MARGIN, y, "6. KEY PERFORMANCE INDICATORS");
            table(MARGIN, y - 16, new float[]{half * .58f, half * .42f}, new String[]{"KPI", "This Period"}, staffKpiRows(), BODY, 18f);
            sectionAt(MARGIN + half + 18, y, "7. PRODUCTIVITY SUMMARY");
            table(MARGIN + half + 18, y - 16, new float[]{half * .58f, half * .42f}, new String[]{"Metric", "This Period"}, staffProductivityRows(), BODY, 18f);
            y -= 118;
            box(MARGIN, y - 118, half - 8, 110);
            sectionAt(MARGIN + 10, y - 20, "8. REMARKS");
            writeWrapped(report.remarks(), MARGIN + 10, y - 40, half - 26, SMALL);
            box(MARGIN + half + 8, y - 118, half - 8, 110);
            sectionAt(MARGIN + half + 18, y - 20, "9. SIGN-OFF");
            signOff(MARGIN + half + 18, y - 40, half - 18);
            close(2, 2, "Staff Loan Review Report");
        }

        private void renderStationTwoPage() throws IOException {
            open();
            center(report.saccoName(), page.getMediaBox().getWidth() / 2, y, bold, TITLE);
            center(report.title(), page.getMediaBox().getWidth() / 2, y - 18, bold, HEADER + 2);
            right("Generated: " + humanDate(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y - 8, regular, SMALL);
            line(MARGIN, y - 34, page.getMediaBox().getWidth() - MARGIN, y - 34);
            y -= 58;
            stationSection("1. MEMBER INFORMATION");
            y = table(MARGIN, y, new float[]{120, 235}, new String[]{"Field", "Value"}, List.of(
                new String[]{"Station", report.stationId()},
                new String[]{"Name", report.generatedBy()},
                new String[]{"Role", report.generatedByRole()}
            ), BODY, 17f);
            y -= 20;
            stationSection("2. REPORT DETAILS");
            y = table(MARGIN, y, new float[]{120, 235}, new String[]{"Field", "Value"}, List.of(
                new String[]{"Reporting Period", periodLabel(report)},
                new String[]{"Generated On", humanDate(report.generatedOn())},
                new String[]{"Name", report.generatedBy()},
                new String[]{"Role", report.generatedByRole()},
                new String[]{"Report Type", report.kind() == ReportKind.STAFF ? "Staff Loan Analytics" : "Station Loan Summary"},
                new String[]{"Loan Product", report.loanProductLabel()}
            ), BODY, 17f);
            y -= 20;
            stationSection("3. LOAN STATUS SUMMARY");
            y = table(MARGIN, y, new float[]{190, 75, 120}, new String[]{"Metric", "Count", "Number of Applicants"}, statusRows(report).stream()
                .map(row -> new String[]{row[0], row[1], row[2]})
                .toList(), SMALL, 14f);
            y -= 20;
            stationSection("4. LOAN PRODUCT PERFORMANCE");
            y = table(MARGIN, y, new float[]{98, 48, 46, 48, 34, 42, 42, 70, 97},
                new String[]{"Loan Product", "Applications", "Approved", "Disbursed", "Paid", "Defaulted", "Rejected", "Total Paid Interest", "Total Interest of Fully Paid Loan"},
                productRowsForPdf(true), SMALL - 0.5f, 28f);
            close(1, 2, "Station Loan Status Report");

            open();
            right("Generated: " + humanDate(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y, regular, SMALL);
            y -= 30;
            stationSection("5. PORTFOLIO SUMMARY");
            y = table(MARGIN, y, new float[]{260, 120}, new String[]{"Indicator", "Value"}, portfolioRows(), BODY, 14f);
            y -= 20;
            stationSection("6. TREND ANALYSIS");
            y = table(MARGIN, y, new float[]{70, 75, 75, 70, 70}, new String[]{"Year", "Applied", "Disbursed", "Paid", "Defaulted"}, yearlyTrendRows(), BODY, 14f);
            y -= 18;
            drawTrendChart(MARGIN, y, page.getMediaBox().getWidth() - (MARGIN * 2), 108);
            y -= 128;
            stationSection("7. LOAN PRODUCT FINANCIAL BREAKDOWN");
            y = table(MARGIN, y, new float[]{140, 85, 100, 95, 95},
                new String[]{"Loan Product", "Total Interest Paid", "Total Interest Unpaid Yet", "Total Loan Amount Paid", "Total Loan Amount Unpaid Yet"},
                productFinancialRowsForPdf(), SMALL - 0.5f, 22f);
            y -= 20;
            float half = (page.getMediaBox().getWidth() - (MARGIN * 2)) / 2f;
            box(MARGIN, y - 120, half - 8, 112);
            sectionAt(MARGIN + 10, y - 22, "8. REMARKS");
            writeWrapped(report.remarks(), MARGIN + 10, y - 42, half - 26, SMALL);
            box(MARGIN + half + 8, y - 120, half - 8, 112);
            sectionAt(MARGIN + half + 18, y - 22, "9. SIGN-OFF");
            signOff(MARGIN + half + 18, y - 42, half - 18);
            close(2, 2, "Station Loan Status Report");
        }

        private void memberHeader(boolean compact) throws IOException {
            write(report.saccoName(), MARGIN, y, bold, TITLE);
            write("MEMBER LOAN REPORT", MARGIN, y - 18, bold, HEADER + 2);
            right("Generated: " + humanDate(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y - 8, regular, SMALL);
            line(MARGIN, y - 34, page.getMediaBox().getWidth() - MARGIN, y - 34);
            y -= compact ? 44 : 58;
        }

        private List<String[]> metricRows() {
            return List.of(
                new String[]{"Applied Loans", String.valueOf(report.analytics().appliedLoans())},
                new String[]{"Active Loans", String.valueOf(report.analytics().activeLoans())},
                new String[]{"Disbursed Loans", String.valueOf(report.analytics().disbursedLoans())},
                new String[]{"Paid Loans", String.valueOf(report.analytics().paidLoans())},
                new String[]{"Defaulted Loans", String.valueOf(report.analytics().defaultedLoans())},
                new String[]{"Rejected Loans", String.valueOf(report.analytics().rejectedLoans())}
            );
        }

        private List<String[]> executiveRows() {
            return List.of(
                new String[]{"Loan Applications Received", String.valueOf(report.analytics().appliedLoans())},
                new String[]{"Loans Approved", String.valueOf(report.analytics().disbursedLoans())},
                new String[]{"Loans Disbursed", String.valueOf(report.analytics().disbursedLoans())},
                new String[]{"Active Loans", String.valueOf(report.analytics().activeLoans())},
                new String[]{"Paid Loans", String.valueOf(report.analytics().paidLoans())},
                new String[]{"Defaulted Loans", String.valueOf(report.analytics().defaultedLoans())},
                new String[]{"Rejected Loans", String.valueOf(report.analytics().rejectedLoans())},
                new String[]{"Portfolio Risk Level", totalRiskLevel(report)}
            );
        }

        private List<String[]> portfolioRows() {
            LoanAnalyticsService.StaffPortfolioSummary portfolio = report.portfolioSummary();
            return List.of(
                new String[]{"Loans Handled", String.valueOf(portfolio.handledLoans())},
                new String[]{"Loans Approved", String.valueOf(portfolio.approvedLoans())},
                new String[]{"Loans Disbursed", String.valueOf(portfolio.disbursedLoans())},
                new String[]{"Loans Rejected", String.valueOf(portfolio.rejectedLoans())},
                new String[]{"Defaulted", String.valueOf(portfolio.defaultedAfterApproval())},
                new String[]{"Default Rate", portfolio.defaultedAfterApprovalRate() + "%"},
                new String[]{"Portfolio Risk Rating", portfolio.riskLevel()}
            );
        }

        private List<String[]> financialRows() {
            FinancialSummary financial = report.financialSummary();
            return List.of(
                new String[]{"Current Active Loan Amount", moneyPlain(financial.activeLoanAmount())},
                new String[]{"Total Disbursed Amount", moneyPlain(financial.totalDisbursedAmount())},
                new String[]{"Total Repaid Amount", moneyPlain(financial.totalRepaidAmount())},
                new String[]{"Outstanding Balance", moneyPlain(financial.outstandingBalance())},
                new String[]{"Defaulted Amount", moneyPlain(financial.defaultedAmount())}
            );
        }

        private List<String[]> activityRows() {
            return report.activityRows().stream()
                .map(row -> new String[]{row.date(), row.activity(), row.loanProduct(), row.amount(), row.status()})
                .toList();
        }

        private List<String[]> productRowsForPdf(boolean stationReport) {
            List<String[]> rows = new ArrayList<>();
            long applied = 0;
            long active = 0;
            long disbursed = 0;
            long paid = 0;
            long defaulted = 0;
            long rejected = 0;
            BigDecimal interest = BigDecimal.ZERO;
            for (ProductPerformanceRow row : report.productRows()) {
                rows.add(new String[]{
                    row.label(),
                    String.valueOf(row.applied()),
                    String.valueOf(row.active()),
                    String.valueOf(row.disbursed()),
                    String.valueOf(row.paid()),
                    String.valueOf(row.defaulted()),
                    String.valueOf(row.rejected()),
                    moneyPlain(row.interestPaid()),
                    moneyPlain(row.fullyPaidInterest())
                });
                applied += row.applied();
                active += row.active();
                disbursed += row.disbursed();
                paid += row.paid();
                defaulted += row.defaulted();
                rejected += row.rejected();
                interest = interest.add(row.interestPaid() == null ? BigDecimal.ZERO : row.interestPaid());
            }
            rows.add(new String[]{
                "TOTAL",
                String.valueOf(applied),
                stationReport ? String.valueOf(active) : String.valueOf(active),
                String.valueOf(disbursed),
                String.valueOf(paid),
                String.valueOf(defaulted),
                String.valueOf(rejected),
                moneyPlain(interest),
                moneyPlain(report.productRows().stream()
                    .map(ProductPerformanceRow::fullyPaidInterest)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add))
            });
            return rows;
        }

        private List<String[]> staffReviewSummaryRows() {
            LoanAnalyticsService.StaffReviewAnalytics review = staffReview(report);
            return List.of(
                new String[]{"Total Applications Reviewed", String.valueOf(review.reviewedLoans()), String.valueOf(review.reviewedLoans())},
                new String[]{"Approved Loans", String.valueOf(review.approvedLoans()), String.valueOf(review.approvedLoans())},
                new String[]{"Rejected Loans", String.valueOf(review.rejectedLoans()), String.valueOf(review.rejectedLoans())},
                new String[]{"Pending / In Progress", String.valueOf(review.pendingLoans()), String.valueOf(review.pendingLoans())}
            );
        }

        private List<String[]> staffProductReviewRows() {
            LoanAnalyticsService.StaffReviewAnalytics review = staffReview(report);
            List<String[]> rows = review.productRows().stream()
                .map(row -> new String[]{
                    row.label(),
                    String.valueOf(row.reviewed()),
                    String.valueOf(row.approved()),
                    String.valueOf(row.rejected()),
                    String.valueOf(row.pending()),
                    row.approvalRate().setScale(2, RoundingMode.HALF_UP) + "%"
                })
                .collect(Collectors.toCollection(ArrayList::new));
            long reviewed = review.productRows().stream().mapToLong(LoanAnalyticsService.StaffReviewProductPerformance::reviewed).sum();
            long approved = review.productRows().stream().mapToLong(LoanAnalyticsService.StaffReviewProductPerformance::approved).sum();
            long rejected = review.productRows().stream().mapToLong(LoanAnalyticsService.StaffReviewProductPerformance::rejected).sum();
            long pending = review.productRows().stream().mapToLong(LoanAnalyticsService.StaffReviewProductPerformance::pending).sum();
            BigDecimal approvalRate = reviewed == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(approved).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(reviewed), 2, RoundingMode.HALF_UP);
            rows.add(new String[]{"TOTAL", String.valueOf(reviewed), String.valueOf(approved), String.valueOf(rejected), String.valueOf(pending), approvalRate + "%"});
            return rows;
        }

        private List<String[]> staffKpiRows() {
            LoanAnalyticsService.StaffReviewAnalytics review = staffReview(report);
            BigDecimal reviewed = BigDecimal.valueOf(Math.max(review.reviewedLoans(), 1));
            return List.of(
                new String[]{"Approval Rate", percent(review.approvedLoans(), reviewed)},
                new String[]{"Rejection Rate", percent(review.rejectedLoans(), reviewed)},
                new String[]{"Pending Rate", percent(review.pendingLoans(), reviewed)}
            );
        }

        private List<String[]> staffProductivityRows() {
            LoanAnalyticsService.StaffReviewAnalytics review = staffReview(report);
            long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(report.fromDate(), report.toDate()) + 1);
            return List.of(
                new String[]{"Applications Reviewed per Day", BigDecimal.valueOf(review.reviewedLoans()).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP).toPlainString()},
                new String[]{"Approvals per Day", BigDecimal.valueOf(review.approvedLoans()).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP).toPlainString()},
                new String[]{"Rejections per Day", BigDecimal.valueOf(review.rejectedLoans()).divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP).toPlainString()},
                new String[]{"Pending Reviews", String.valueOf(review.pendingLoans())}
            );
        }

        private List<String[]> activeLoanRowsForPdf() {
            return report.activeLoanRows().stream()
                .map(row -> new String[]{
                    row.loanId(),
                    row.loanProduct(),
                    row.loanAmount(),
                    row.outstandingBalance(),
                    row.requiredInterestAmount(),
                    row.paidLoanAmount(),
                    row.interestPaid(),
                    row.interestNotYetPaid()
                })
                .toList();
        }

        private List<String[]> productFinancialRowsForPdf() {
            List<String[]> rows = productFinancialRowsForExcel(report);
            return rows.size() > 7 ? rows.subList(0, 7) : rows;
        }

        private List<String[]> yearlyTrendRows() {
            Map<String, long[]> years = new LinkedHashMap<>();
            for (String[] row : trendTableRows(report)) {
                String year = row[0].length() >= 4 ? row[0].substring(row[0].length() - 4) : row[0];
                long[] values = years.computeIfAbsent(year, key -> new long[4]);
                values[0] += Long.parseLong(row[1]);
                values[1] += Long.parseLong(row[3]);
                values[2] += Long.parseLong(row[4]);
                values[3] += Long.parseLong(row[5]);
            }
            return years.entrySet().stream()
                .map(entry -> new String[]{entry.getKey(), String.valueOf(entry.getValue()[0]), String.valueOf(entry.getValue()[1]), String.valueOf(entry.getValue()[2]), String.valueOf(entry.getValue()[3])})
                .toList();
        }

        private void open() throws IOException {
            page = new PDPage(size);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            PdfWatermarkRenderer.draw(document, stream, page, watermarkLogo);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void close(int pageNumber, int pageCount, String footerTitle) throws IOException {
            line(MARGIN, 36, page.getMediaBox().getWidth() - MARGIN, 36);
            write(report.saccoName() + "  |  " + footerTitle, MARGIN, 22, regular, SMALL);
            right("Page " + pageNumber + " of " + pageCount, page.getMediaBox().getWidth() - MARGIN, 22, regular, SMALL);
            stream.close();
        }

        private void section(String title) throws IOException {
            write(title, MARGIN, y, bold, HEADER);
            y -= 17;
        }

        private void stationSection(String title) throws IOException {
            write(title, MARGIN, y, bold, HEADER);
            y -= 12;
        }

        private void sectionAt(float x, float yPos, String title) throws IOException {
            write(title, x, yPos, bold, HEADER);
        }

        private void metricBoxes(float x, float yPos, float width) throws IOException {
            List<String[]> rows = metricRows();
            float gap = 12f;
            float boxWidth = (width - (gap * (rows.size() - 1))) / rows.size();
            for (int i = 0; i < rows.size(); i++) {
                float boxX = x + (i * (boxWidth + gap));
                box(boxX, yPos - 52, boxWidth, 48);
                center(rows.get(i)[0].toUpperCase(Locale.ENGLISH), boxX + boxWidth / 2, yPos - 14, bold, SMALL);
                center(rows.get(i)[1], boxX + boxWidth / 2, yPos - 31, bold, TITLE);
            }
        }

        private void drawInfoPanel(float x, float top, float width, String title, List<String[]> rows) throws IOException {
            box(x, top - 70, width, 66);
            write(title, x + 8, top - 16, bold, BODY);
            float rowY = top - 34;
            for (String[] row : rows) {
                write(row[0] + " :", x + 8, rowY, bold, SMALL);
                write(row[1], x + 86, rowY, regular, SMALL);
                rowY -= 16;
            }
        }

        private float table(float x, float top, float[] widths, String[] headers, List<String[]> rows, float fontSize, float rowHeight) throws IOException {
            float tableWidth = 0;
            for (float width : widths) {
                tableWidth += width;
            }
            drawRow(x, top, widths, headers, bold, fontSize, rowHeight);
            float rowY = top - rowHeight;
            List<String[]> safeRows = rows.isEmpty() ? java.util.Collections.singletonList(new String[]{"No records for the selected period."}) : rows;
            for (String[] row : safeRows) {
                drawRow(x, rowY, widths, row, regular, fontSize, rowHeight);
                rowY -= rowHeight;
            }
            return rowY;
        }

        private void drawRow(float x, float top, float[] widths, String[] values, PDType1Font font, float fontSize, float rowHeight) throws IOException {
            float cursor = x;
            for (int i = 0; i < widths.length; i++) {
                box(cursor, top - rowHeight, widths[i], rowHeight);
                String value = i < values.length ? sanitizePdfLineText(values[i]) : "";
                writeCellText(value, cursor + 4, top, widths[i] - 8, rowHeight, font, fontSize);
                cursor += widths[i];
            }
        }

        private void writeCellText(String text, float x, float top, float maxWidth, float rowHeight, PDType1Font font, float size) throws IOException {
            String safe = sanitizePdfLineText(text);
            if (font.getStringWidth(safe) / 1000f * size <= maxWidth || !safe.contains(" ")) {
                writeClipped(safe, x, top - rowHeight + 5, maxWidth, font, size);
                return;
            }
            List<String> lines = wrapText(safe, font, size, maxWidth);
            if (lines.size() <= 1) {
                writeClipped(safe, x, top - rowHeight + 5, maxWidth, font, size);
                return;
            }
            float firstY = top - 7;
            int maxLines = Math.max(1, (int) Math.floor((rowHeight - 8f) / (size + 2f)));
            int count = Math.min(maxLines, lines.size());
            for (int line = 0; line < count; line++) {
                writeClipped(lines.get(line), x, firstY - (line * (size + 2)), maxWidth, font, Math.max(6f, size - 0.4f));
            }
        }

        private void drawTrendChart(float x, float top, float width, float height) throws IOException {
            box(x, top - height, width, height);
            float chartX = x + 28;
            float chartY = top - height + 34;
            float chartWidth = width - 52;
            float chartHeight = height - 64;
            line(chartX, chartY, chartX + chartWidth, chartY);
            line(chartX, chartY, chartX, chartY + chartHeight);
            long max = 1;
            for (LoanAnalyticsService.MetricTrendSeries series : report.trendSeries()) {
                for (Map<String, Object> point : series.dataPoints()) {
                    max = Math.max(max, numberValue(point.get("y")));
                }
            }
            write("0", chartX - 12, chartY - 2, regular, SMALL);
            write(String.valueOf(max), chartX - 12, chartY + chartHeight - 2, regular, SMALL);
            List<Map<String, Object>> labelPoints = report.trendSeries().stream()
                .filter(series -> !series.dataPoints().isEmpty())
                .findFirst()
                .map(LoanAnalyticsService.MetricTrendSeries::dataPoints)
                .orElse(List.of());
            for (int i = 0; i < labelPoints.size(); i++) {
                if (labelPoints.size() > 6 && i != 0 && i != labelPoints.size() - 1 && i % 2 != 0) {
                    continue;
                }
                float labelX = chartX + chartWidth * i / Math.max(labelPoints.size() - 1, 1);
                center(shortTrendLabel(labelPoints.get(i).get("x")), labelX, chartY - 14, regular, SMALL);
            }
            int seriesIndex = 0;
            int visibleSeries = Math.min(4, report.trendSeries().size());
            float legendStep = visibleSeries <= 1 ? 0 : Math.max(42f, (width - 72f) / visibleSeries);
            for (LoanAnalyticsService.MetricTrendSeries series : report.trendSeries()) {
                if (series.dataPoints().isEmpty() || seriesIndex > 3) {
                    continue;
                }
                Color color = seriesColor(series, seriesIndex);
                stream.setStrokingColor(color);
                stream.setLineWidth(seriesIndex == 0 ? 1.1f : 0.7f);
                List<Map<String, Object>> points = series.dataPoints();
                for (int i = 1; i < points.size(); i++) {
                    float x1 = chartX + chartWidth * (i - 1) / Math.max(points.size() - 1, 1);
                    float y1 = chartY + chartHeight * numberValue(points.get(i - 1).get("y")) / max;
                    float x2 = chartX + chartWidth * i / Math.max(points.size() - 1, 1);
                    float y2 = chartY + chartHeight * numberValue(points.get(i).get("y")) / max;
                    line(x1, y1, x2, y2);
                }
                float legendX = x + 42 + (seriesIndex * legendStep);
                line(legendX - 14, top - 12, legendX - 4, top - 12);
                stream.setNonStrokingColor(color);
                write(series.name(), legendX, top - 14, regular, SMALL);
                stream.setNonStrokingColor(Color.BLACK);
                seriesIndex++;
            }
            stream.setStrokingColor(Color.BLACK);
        }

        private Color seriesColor(LoanAnalyticsService.MetricTrendSeries series, int fallbackIndex) {
            String color = series.color();
            if (color != null && color.matches("#[0-9a-fA-F]{6}")) {
                return Color.decode(color);
            }
            return switch (fallbackIndex) {
                case 0 -> new Color(37, 99, 235);
                case 1 -> new Color(5, 150, 105);
                case 2 -> new Color(124, 58, 237);
                case 3 -> new Color(220, 38, 38);
                default -> Color.DARK_GRAY;
            };
        }

        private String shortTrendLabel(Object value) {
            String label = trendPeriodLabel(value);
            return label != null && label.length() >= 4 ? label.substring(label.length() - 4) : valueOrDash(label);
        }

        private void signOff(float x, float top, float width) throws IOException {
            write("Prepared By:", x, top, regular, BODY);
            line(x + 70, top - 2, x + width - 20, top - 2);
            write(report.generatedBy(), x + 74, top + 2, regular, SMALL);
            write("Date: " + humanDate(report.generatedOn()), x + width - 110, top - 18, regular, SMALL);
            write("Reviewed By:", x, top - 32, regular, BODY);
            line(x + 70, top - 34, x + width - 20, top - 34);
            write("Approved By:", x, top - 58, regular, BODY);
            line(x + 70, top - 60, x + width - 20, top - 60);
        }

        private void box(float x, float bottom, float width, float height) throws IOException {
            stream.addRect(x, bottom, width, height);
            stream.stroke();
        }

        private void line(float x1, float y1, float x2, float y2) throws IOException {
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
            stream.stroke();
        }

        private void write(String text, float x, float yPos, PDType1Font font, float size) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, yPos);
            stream.showText(sanitizePdfLineText(text));
            stream.endText();
        }

        private void center(String text, float x, float yPos, PDType1Font font, float size) throws IOException {
            String safe = sanitizePdfLineText(text);
            float width = font.getStringWidth(safe) / 1000f * size;
            write(safe, x - width / 2f, yPos, font, size);
        }

        private void right(String text, float x, float yPos, PDType1Font font, float size) throws IOException {
            String safe = sanitizePdfLineText(text);
            float width = font.getStringWidth(safe) / 1000f * size;
            write(safe, x - width, yPos, font, size);
        }

        private void writeClipped(String text, float x, float yPos, float maxWidth, PDType1Font font, float size) throws IOException {
            String safe = sanitizePdfLineText(text);
            while (!safe.isEmpty() && font.getStringWidth(safe) / 1000f * size > maxWidth) {
                safe = safe.substring(0, safe.length() - 1);
            }
            write(safe, x, yPos, font, size);
        }

        private void writeWrapped(String text, float x, float yPos, float maxWidth, float size) throws IOException {
            List<String> lines = wrapText(sanitizePdfLineText(text), regular, size, maxWidth);
            float cursor = yPos;
            for (String line : lines.stream().limit(3).toList()) {
                write(line, x, cursor, regular, size);
                cursor -= size + 4;
            }
        }

        private List<String> wrapText(String text, PDType1Font font, float fontSize, float width) throws IOException {
            if (text == null || text.isBlank()) {
                return List.of("");
            }
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : text.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (font.getStringWidth(candidate) / 1000f * fontSize > width && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }
    }

    public record LoanSummary(
        long disbursedCount,
        long paidCount,
        long ongoingCount,
        BigDecimal disbursedAmount,
        BigDecimal paidAmount
    ) {
        public String getDisbursedAmountLabel() {
            return formatMoneyStatic(disbursedAmount);
        }

        public String getPaidAmountLabel() {
            return formatMoneyStatic(paidAmount);
        }
    }

    public record MemberLoanReport(
        Member member,
        LoanSummary summary,
        MemberLoanAnalyticsSummary analytics,
        List<LoanApplication> loans,
        List<MemberLoanDetail> details
    ) {}

    public record MemberLoanAnalyticsSummary(
        long appliedCount,
        long activeCount,
        long disbursedCount,
        long paidCount,
        long defaultedCount,
        long rejectedCount,
        BigDecimal activeAmount
    ) {
        public String getActiveAmountLabel() {
            return formatMoneyStatic(activeAmount);
        }
    }

    public record MemberLoanDetail(
        UUID loanId,
        String loanApplicationIdLabel,
        String loanIdLabel,
        String memberDetailsLabel,
        String approvedProductLabel,
        String approvedAmountLabel,
        String tenureLabel,
        String applicationFeeLabel,
        String insuranceFeeLabel,
        String processingFeeLabel,
        String totalDeductionsLabel,
        String deductionSummaryLabel,
        String guarantorDetailsLabel,
        String approvalDecisionSummaryLabel,
        String disbursementDateLabel,
        String finalDueDateLabel,
        String statusLabel,
        String preparedByLabel,
        String preparedDateLabel
    ) {}

    public record StationAnalyticsExportReport(
        String saccoId,
        String saccoName,
        String stationId,
        LocalDate fromDate,
        LocalDate toDate,
        LoanType loanType,
        String preparedBy,
        String preparedByRole,
        LocalDate generatedOn,
        List<StationStatusRow> statusRows,
        StationParticipationSummary participation,
        List<StationProductRow> productRows,
        List<StationYearlySummaryRow> yearlyRows,
        String loanProductLabel
    ) {
        public String periodLabel() {
            return DATE_FORMATTER.format(fromDate) + " - " + DATE_FORMATTER.format(toDate);
        }
    }

    public record StationStatusRow(String metric, long count, long applicantCount) {}

    public record StationParticipationSummary(
        long activeStationMembers,
        long uniqueApplicants,
        BigDecimal participationRate,
        BigDecimal applicationsPerApplicant,
        long repeatApplicants
    ) {
        public long getActiveStationMembers() {
            return activeStationMembers;
        }

        public long getUniqueApplicants() {
            return uniqueApplicants;
        }

        public String participationRateLabel() {
            return participationRate.setScale(2, RoundingMode.HALF_UP) + "%";
        }

        public String getParticipationRateLabel() {
            return participationRateLabel();
        }

        public String applicationsPerApplicantLabel() {
            return applicationsPerApplicant.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }

        public String getApplicationsPerApplicantLabel() {
            return applicationsPerApplicantLabel();
        }

        public long getRepeatApplicants() {
            return repeatApplicants;
        }
    }

    public record StationProductRow(
        String loanProduct,
        long applications,
        long approved,
        long active,
        long disbursed,
        long paid,
        long defaulted,
        long rejected,
        BigDecimal totalPaidInterest,
        BigDecimal fullyPaidLoanInterest
    ) {
        public String totalPaidInterestLabel() {
            return formatMoneyStatic(totalPaidInterest);
        }

        public String fullyPaidLoanInterestLabel() {
            return formatMoneyStatic(fullyPaidLoanInterest);
        }
    }

    public record StationYearlySummaryRow(
        int year,
        long totalLoanApplications,
        long uniqueApplicants,
        long defaultedLoans,
        BigDecimal defaultRate,
        long totalPaidLoans,
        BigDecimal totalPaidInterestAccumulated,
        BigDecimal fullyPaidLoanInterest
    ) {
        public String defaultRateLabel() {
            return defaultRate.setScale(2, RoundingMode.HALF_UP) + "%";
        }

        public String totalPaidInterestAccumulatedLabel() {
            return formatMoneyStatic(totalPaidInterestAccumulated);
        }

        public String fullyPaidLoanInterestLabel() {
            return formatMoneyStatic(fullyPaidLoanInterest);
        }
    }

    public record ManagerLoanReport(
        String saccoId,
        int year,
        boolean returnedOnly,
        LoanSummary summary,
        List<LoanApplication> loans,
        Map<UUID, Member> applicantMap
    ) {}

    public record AccountantLoanReport(
        UUID accountantId,
        String saccoId,
        LocalDate fromDate,
        LocalDate toDate,
        String decisionFilter,
        AccountantReviewSummary summary,
        List<AccountantReviewEntry> entries,
        Map<UUID, Member> applicantMap
    ) {}

    public record AccountantReviewSummary(
        long reviewedCount,
        long approvedCount,
        long rejectedCount,
        BigDecimal reviewedAmount
    ) {
        public String getReviewedAmountLabel() {
            return formatMoneyStatic(reviewedAmount);
        }
    }

    public record AccountantReviewEntry(
        ManagerReview review,
        LoanApplication loan
    ) {}

    public record ManagerWorkflowReport(
        UUID managerId,
        String saccoId,
        LocalDate fromDate,
        LocalDate toDate,
        String decisionFilter,
        ManagerWorkflowSummary summary,
        List<ManagerWorkflowEntry> entries,
        Map<UUID, Member> applicantMap
    ) {}

    public record ManagerWorkflowSummary(
        long reviewedCount,
        long approvedCount,
        long rejectedCount,
        BigDecimal reviewedAmount
    ) {
        public String getReviewedAmountLabel() {
            return formatMoneyStatic(reviewedAmount);
        }
    }

    public record ManagerWorkflowEntry(
        ManagerReview review,
        LoanApplication loan
    ) {}

    public record BoardWorkflowReport(
        UUID reviewerId,
        String saccoId,
        ApprovalWorkflowStage reviewStage,
        LocalDate fromDate,
        LocalDate toDate,
        String decisionFilter,
        BoardWorkflowSummary summary,
        List<BoardWorkflowEntry> entries,
        Map<UUID, Member> applicantMap
    ) {}

    public record BoardWorkflowSummary(
        long reviewedCount,
        long approvedCount,
        long rejectedCount,
        BigDecimal reviewedAmount
    ) {
        public String getReviewedAmountLabel() {
            return formatMoneyStatic(reviewedAmount);
        }
    }

    public record BoardWorkflowEntry(
        BoardReview review,
        LoanApplication loan
    ) {}

    public record DisbursementLoanReport(
        UUID disbursementOfficerId,
        String saccoId,
        LocalDate fromDate,
        LocalDate toDate,
        DisbursementReviewSummary summary,
        List<DisbursementReviewEntry> entries,
        Map<UUID, Member> applicantMap
    ) {}

    public record DisbursementReviewSummary(
        long disbursedCount,
        long paidCount,
        long defaultedCount,
        BigDecimal disbursedAmount
    ) {
        public String getDisbursedAmountLabel() {
            return formatMoneyStatic(disbursedAmount);
        }
    }

    public record DisbursementReviewEntry(
        ManagerReview review,
        LoanApplication loan
    ) {}

    private static String formatMoneyStatic(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.##", new java.text.DecimalFormatSymbols(Locale.US));
        return "TSh " + format.format(safe);
    }

    private static String formatMoneyPlainStatic(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.##", new java.text.DecimalFormatSymbols(Locale.US));
        return format.format(safe);
    }

    private static final class StaffReviewPdfRenderer {
        private static final float MARGIN = 34f;
        private static final float TOP_MARGIN = 42f;
        private static final float BOTTOM_MARGIN = 52f;
        private static final float TITLE_SIZE = 18f;
        private static final float SUBTITLE_SIZE = 11f;
        private static final float META_SIZE = 8.6f;
        private static final float HEADER_SIZE = 7.2f;
        private static final float BODY_SIZE = 7.6f;
        private static final float CELL_PADDING_X = 6f;
        private static final float CELL_PADDING_Y = 6f;
        private static final Color TEXT_COLOR = new Color(17, 24, 39);
        private static final Color MUTED_COLOR = new Color(71, 85, 105);
        private static final Color BORDER_COLOR = new Color(218, 225, 232);
        private static final Color HEADER_FILL = new Color(248, 250, 252);
        private static final Color RULE_COLOR = new Color(31, 41, 55);

        private final PDDocument document;
        private final String saccoName;
        private final String title;
        private final String subtitle;
        private final String periodLine;
        private final String summaryLine;
        private final String generatedDate;
        private final String footerTitle;
        private final String[] headers;
        private final float[] widths;
        private final List<String[]> rows;
        private final byte[] watermarkLogo;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private StaffReviewPdfRenderer(PDDocument document,
                                       String saccoName,
                                       String title,
                                       String subtitle,
                                       String periodLine,
                                       String summaryLine,
                                       String generatedDate,
                                       String footerTitle,
                                       String[] headers,
                                       float[] widths,
                                       List<String[]> rows,
                                       byte[] watermarkLogo) {
            this.document = document;
            this.saccoName = sanitizePdfText(saccoName).toUpperCase(Locale.ROOT);
            this.title = sanitizePdfText(title);
            this.subtitle = sanitizePdfText(subtitle);
            this.periodLine = sanitizePdfText(periodLine);
            this.summaryLine = sanitizePdfText(summaryLine);
            this.generatedDate = sanitizePdfText(generatedDate);
            this.footerTitle = sanitizePdfText(footerTitle);
            this.headers = headers;
            this.widths = scaleWidths(widths, PDRectangle.A4.getWidth() - (MARGIN * 2f));
            this.rows = rows;
            this.watermarkLogo = watermarkLogo;
        }

        private void render() throws IOException {
            startPage();
            drawTableHeader();
            for (String[] row : rows) {
                drawBodyRow(row);
            }
            closeContent();
            drawFooters();
        }

        private void startPage() throws IOException {
            closeContent();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            PdfWatermarkRenderer.draw(document, stream, page, watermarkLogo);
            y = page.getMediaBox().getHeight() - TOP_MARGIN;
            drawHeader();
        }

        private void closeContent() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private void drawHeader() throws IOException {
            writeText(saccoName, MARGIN, y, bold, TITLE_SIZE, TEXT_COLOR);
            writeRightAligned("Generated: " + generatedDate, page.getMediaBox().getWidth() - MARGIN, y - 2f, bold, META_SIZE, TEXT_COLOR);
            y -= 19f;
            writeText(title, MARGIN, y, bold, TITLE_SIZE - 2f, TEXT_COLOR);
            y -= 18f;
            writeText(subtitle, MARGIN, y, regular, SUBTITLE_SIZE, MUTED_COLOR);
            y -= 17f;
            writeText(periodLine, MARGIN, y, regular, META_SIZE, MUTED_COLOR);
            y -= 12f;
            writeText(summaryLine, MARGIN, y, regular, META_SIZE, MUTED_COLOR);
            y -= 16f;
            stream.setStrokingColor(RULE_COLOR);
            stream.moveTo(MARGIN, y);
            stream.lineTo(page.getMediaBox().getWidth() - MARGIN, y);
            stream.stroke();
            y -= 18f;
        }

        private void drawTableHeader() throws IOException {
            drawRow(headers, bold, HEADER_SIZE, true);
        }

        private void drawBodyRow(String[] row) throws IOException {
            float rowHeight = measureRow(row, regular, BODY_SIZE);
            if (y - rowHeight < BOTTOM_MARGIN) {
                startPage();
                drawTableHeader();
            }
            drawRow(row, regular, BODY_SIZE, false);
        }

        private void drawRow(String[] cells, PDType1Font font, float fontSize, boolean header) throws IOException {
            float rowHeight = measureRow(cells, font, fontSize);
            float x = MARGIN;
            float lineHeight = fontSize + 2.2f;
            for (int i = 0; i < widths.length; i++) {
                stream.setNonStrokingColor(header ? HEADER_FILL : Color.WHITE);
                stream.addRect(x, y - rowHeight, widths[i], rowHeight);
                stream.fill();
                stream.setStrokingColor(BORDER_COLOR);
                stream.addRect(x, y - rowHeight, widths[i], rowHeight);
                stream.stroke();

                List<String> lines = wrap(cell(cells, i), font, fontSize, widths[i] - (CELL_PADDING_X * 2f));
                float textY = y - CELL_PADDING_Y - fontSize;
                for (String line : lines) {
                    writeText(line, x + CELL_PADDING_X, textY, font, fontSize, TEXT_COLOR);
                    textY -= lineHeight;
                }
                x += widths[i];
            }
            y -= rowHeight;
        }

        private float measureRow(String[] cells, PDType1Font font, float fontSize) throws IOException {
            int maxLines = 1;
            for (int i = 0; i < widths.length; i++) {
                maxLines = Math.max(maxLines, wrap(cell(cells, i), font, fontSize, widths[i] - (CELL_PADDING_X * 2f)).size());
            }
            return Math.max(26f, (CELL_PADDING_Y * 2f) + (maxLines * (fontSize + 2.2f)));
        }

        private List<String> wrap(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
            String safe = sanitizePdfText(text);
            List<String> lines = new ArrayList<>();
            for (String paragraph : safe.split("\\n", -1)) {
                StringBuilder current = new StringBuilder();
                for (String token : paragraph.split("\\s+")) {
                    String candidate = current.isEmpty() ? token : current + " " + token;
                    if (stringWidth(candidate, font, fontSize) > maxWidth && !current.isEmpty()) {
                        lines.add(current.toString());
                        current = new StringBuilder(token);
                    } else {
                        current = new StringBuilder(candidate);
                    }
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                }
            }
            return lines.isEmpty() ? List.of("-") : lines;
        }

        private void drawFooters() throws IOException {
            int total = document.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage footerPage = document.getPage(i);
                try (PDPageContentStream footerStream = new PDPageContentStream(document, footerPage, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    float footerY = BOTTOM_MARGIN - 20f;
                    footerStream.setStrokingColor(RULE_COLOR);
                    footerStream.moveTo(MARGIN, footerY + 16f);
                    footerStream.lineTo(footerPage.getMediaBox().getWidth() - MARGIN, footerY + 16f);
                    footerStream.stroke();
                    writeText(footerStream, saccoName + " | " + footerTitle, MARGIN, footerY, regular, META_SIZE, TEXT_COLOR);
                    writeRightAligned(footerStream, "Page " + (i + 1) + " of " + total, footerPage.getMediaBox().getWidth() - MARGIN, footerY, bold, META_SIZE, TEXT_COLOR);
                }
            }
        }

        private void writeText(String text, float x, float y, PDType1Font font, float size, Color color) throws IOException {
            writeText(stream, text, x, y, font, size, color);
        }

        private void writeRightAligned(String text, float rightX, float y, PDType1Font font, float size, Color color) throws IOException {
            writeRightAligned(stream, text, rightX, y, font, size, color);
        }

        private static void writeText(PDPageContentStream target, String text, float x, float y, PDType1Font font, float size, Color color) throws IOException {
            target.beginText();
            target.setNonStrokingColor(color);
            target.setFont(font, size);
            target.newLineAtOffset(x, y);
            target.showText(sanitizePdfLineText(text));
            target.endText();
        }

        private static void writeRightAligned(PDPageContentStream target, String text, float rightX, float y, PDType1Font font, float size, Color color) throws IOException {
            float width = stringWidth(sanitizePdfLineText(text), font, size);
            writeText(target, text, rightX - width, y, font, size, color);
        }

        private static String cell(String[] cells, int index) {
            return cells != null && index < cells.length ? cells[index] : "";
        }

        private static float stringWidth(String text, PDType1Font font, float fontSize) throws IOException {
            return font.getStringWidth(sanitizePdfLineText(text)) / 1000f * fontSize;
        }

        private static float[] scaleWidths(float[] rawWidths, float targetWidth) {
            float total = 0f;
            for (float width : rawWidths) {
                total += width;
            }
            float scale = total == 0f ? 1f : targetWidth / total;
            float[] scaled = new float[rawWidths.length];
            for (int i = 0; i < rawWidths.length; i++) {
                scaled[i] = rawWidths[i] * scale;
            }
            return scaled;
        }
    }

    private static final class PDFCursor {
        private static final float MARGIN = 48f;
        private static final float FONT_SIZE = 11f;
        private static final float LEADING = 16f;

        private final PDDocument document;
        private final byte[] watermarkLogo;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private PDFCursor(PDDocument document, byte[] watermarkLogo) {
            this.document = document;
            this.watermarkLogo = watermarkLogo;
        }

        private void openPage() throws IOException {
            close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            PdfWatermarkRenderer.draw(document, stream, page, watermarkLogo);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void writeWrapped(String text) throws IOException {
            List<String> wrapped = wrap(text == null ? "" : text, text != null && !text.isBlank() && !text.startsWith(" ") && !text.contains("|") && !text.endsWith(":") ? regular : regular);
            if (text != null && (text.equals("Member Loan Report") || text.equals("Manager Disbursed Loans Report") || text.equals("Summary") || text.equals("Loan List"))) {
                wrapped = wrap(text, bold);
                writeLines(wrapped, bold);
                y -= 2f;
                return;
            }
            writeLines(wrapped, regular);
        }

        private void writeLines(List<String> lines, PDType1Font font) throws IOException {
            for (String line : lines) {
                if (y <= MARGIN) {
                    openPage();
                }
                stream.beginText();
                stream.setFont(font, FONT_SIZE);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(line);
                stream.endText();
                y -= LEADING;
            }
        }

        private List<String> wrap(String text, PDType1Font font) throws IOException {
            List<String> lines = new ArrayList<>();
            if (text.isBlank()) {
                lines.add("");
                return lines;
            }
            float maxWidth = PDRectangle.A4.getWidth() - (MARGIN * 2);
            String[] words = text.split("\\s+");
            StringBuilder current = new StringBuilder();
            for (String word : words) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                float width = font.getStringWidth(candidate) / 1000f * FONT_SIZE;
                if (width > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }

    private static final class StationPdfRenderer {
        private static final float MARGIN = 28f;
        private static final float TOP_MARGIN = 34f;
        private static final float BOTTOM_MARGIN = 34f;
        private static final float FOOTER_GAP = 18f;
        private static final float TITLE_SIZE = 17f;
        private static final float SECTION_SIZE = 10.2f;
        private static final float BODY_SIZE = 7.4f;
        private static final float SMALL_SIZE = 6.3f;
        private static final float CELL_PADDING_X = 4f;
        private static final float CELL_PADDING_Y = 3.2f;
        private static final Color TEXT_COLOR = new Color(17, 24, 39);
        private static final Color BORDER_COLOR = new Color(32, 32, 32);
        private static final Color GRID_COLOR = new Color(210, 216, 224);
        private static final Color MUTED_COLOR = new Color(85, 99, 116);

        private final PDDocument document;
        private final StationAnalyticsExportReport report;
        private final byte[] watermarkLogo;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPage page;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        private StationPdfRenderer(PDDocument document, StationAnalyticsExportReport report, byte[] watermarkLogo) {
            this.document = document;
            this.report = report;
            this.watermarkLogo = watermarkLogo;
        }

        private void render() throws IOException {
            startNewPage();
            drawHeader();
            drawSection("1. MEMBER INFORMATION", new String[]{"Field", "Value"}, new float[]{128f, 210f}, List.of(
                new String[]{"Station", report.stationId()},
                new String[]{"Name", report.preparedBy()},
                new String[]{"Role", report.preparedByRole()}
            ), BODY_SIZE);
            drawSection("2. REPORT DETAILS", new String[]{"Field", "Value"}, new float[]{128f, 300f}, List.of(
                new String[]{"Reporting Period", report.periodLabel()},
                new String[]{"Generated On", formatDateStatic(report.generatedOn())},
                new String[]{"Name", report.preparedBy()},
                new String[]{"Role", report.preparedByRole()},
                new String[]{"Report Type", "Station Loan Summary"},
                new String[]{"Loan Product", report.loanProductLabel()}
            ), BODY_SIZE);
            drawStatusSection();
            drawProductSection();
            drawParticipationSection();
            drawPortfolioSection();
            drawYearlySection();
            drawYearlyGraphSection();
            drawSignOffSection();
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
            PdfWatermarkRenderer.draw(document, stream, page, watermarkLogo);
            pageNumber++;
            y = page.getMediaBox().getHeight() - TOP_MARGIN;
        }

        private void closePage() throws IOException {
            if (stream != null) {
                drawFooter();
                stream.close();
                stream = null;
            }
        }

        private void drawHeader() throws IOException {
            writeCentered(report.saccoName(), y, bold, TITLE_SIZE);
            writeRight("Generated: " + formatDateStatic(report.generatedOn()), page.getMediaBox().getWidth() - MARGIN, y + 2f, regular, BODY_SIZE);
            y -= 18f;
            writeCentered("STATION LOAN STATUS REPORT", y, bold, 13f);
            y -= 18f;
            rule();
            y -= 18f;
        }

        private void drawStatusSection() throws IOException {
            List<String[]> rows = report.statusRows().stream()
                .map(row -> new String[]{row.metric(), String.valueOf(row.count()), String.valueOf(row.applicantCount())})
                .toList();
            drawSection("3. LOAN STATUS SUMMARY", new String[]{"Metric", "Count", "Number of Applicants"},
                new float[]{250f, 90f, 150f}, rows, BODY_SIZE);
        }

        private void drawProductSection() throws IOException {
            List<String[]> rows = report.productRows().stream()
                .map(row -> new String[]{
                    row.loanProduct(),
                    String.valueOf(row.applications()),
                    String.valueOf(row.approved()),
                    String.valueOf(row.active()),
                    String.valueOf(row.disbursed()),
                    String.valueOf(row.paid()),
                    String.valueOf(row.defaulted()),
                    String.valueOf(row.rejected()),
                    row.totalPaidInterestLabel(),
                    row.fullyPaidLoanInterestLabel()
                })
                .toList();
            drawSection("4. LOAN PRODUCT PERFORMANCE",
                new String[]{"Loan Product", "Applications", "Approved", "Active", "Disbursed", "Paid", "Defaulted", "Rejected", "Total Paid Interest Accumulated", "Total Interest of Fully Paid Loan"},
                new float[]{92f, 45f, 43f, 38f, 45f, 32f, 45f, 42f, 84f, 64f},
                rows,
                SMALL_SIZE);
        }

        private void drawParticipationSection() throws IOException {
            StationParticipationSummary summary = report.participation();
            drawSection("5. MEMBER / APPLICANT PARTICIPATION SUMMARY",
                new String[]{"Indicator", "Value"},
                new float[]{275f, 160f},
                List.of(
                    new String[]{"Active Station Members", String.valueOf(summary.activeStationMembers())},
                    new String[]{"Unique Applicants", String.valueOf(summary.uniqueApplicants())},
                    new String[]{"Participation Rate", summary.participationRateLabel()},
                    new String[]{"Applications per Applicant", summary.applicationsPerApplicantLabel()},
                    new String[]{"Repeat Applicants", String.valueOf(summary.repeatApplicants())}
                ),
                BODY_SIZE);
        }

        private void drawPortfolioSection() throws IOException {
            long handled = report.statusRows().isEmpty() ? 0 : report.statusRows().get(0).count();
            long paid = report.statusRows().stream().filter(row -> row.metric().startsWith("Paid")).mapToLong(StationStatusRow::count).findFirst().orElse(0L);
            long defaulted = report.statusRows().stream().filter(row -> row.metric().startsWith("Defaulted")).mapToLong(StationStatusRow::count).findFirst().orElse(0L);
            String risk = defaulted >= 1 ? "Review" : "Low";
            drawSection("6. PORTFOLIO SUMMARY",
                new String[]{"Indicator", "Value"},
                new float[]{275f, 160f},
                List.of(
                    new String[]{"Loans Handled", String.valueOf(handled)},
                    new String[]{"Paid Loans", String.valueOf(paid)},
                    new String[]{"Defaulted", String.valueOf(defaulted)},
                    new String[]{"Portfolio Risk Rating", risk}
                ),
                BODY_SIZE);
        }

        private void drawYearlySection() throws IOException {
            List<String[]> rows = report.yearlyRows().stream()
                .map(row -> new String[]{
                    String.valueOf(row.year()),
                    String.valueOf(row.totalLoanApplications()),
                    String.valueOf(row.uniqueApplicants()),
                    String.valueOf(row.defaultedLoans()),
                    row.defaultRateLabel(),
                    String.valueOf(row.totalPaidLoans()),
                    row.totalPaidInterestAccumulatedLabel(),
                    row.fullyPaidLoanInterestLabel()
                })
                .toList();
            drawSection("7. YEARLY LOAN AND INTEREST SUMMARY",
                new String[]{"Year", "Total Loan Applications", "Unique Applicants", "Defaulted Loans", "Default Rate", "Total Paid Loans", "Total Paid Interest Accumulated", "Total Interest of Fully Paid Loan"},
                new float[]{38f, 78f, 66f, 58f, 52f, 58f, 98f, 86f},
                rows,
                SMALL_SIZE);
        }

        private void drawSignOffSection() throws IOException {
            drawSection("9. SIGN-OFF",
                new String[]{"Field", "Name", "Date"},
                new float[]{120f, 250f, 120f},
                List.of(
                    new String[]{"Prepared By", report.preparedBy(), formatDateStatic(report.generatedOn())},
                    new String[]{"Reviewed By", "____________________________", "____________"},
                    new String[]{"Approved By", "____________________________", "____________"}
                ),
                BODY_SIZE);
        }

        private void drawYearlyGraphSection() throws IOException {
            ensureSpace(166f);
            write("8. YEARLY LOAN TREND GRAPH", MARGIN, y, bold, SECTION_SIZE);
            y -= 12f;
            float width = page.getMediaBox().getWidth() - (MARGIN * 2);
            float height = 132f;
            float bottom = y - height;
            stream.setStrokingColor(BORDER_COLOR);
            stream.addRect(MARGIN, bottom, width, height);
            stream.stroke();
            float chartX = MARGIN + 38f;
            float chartY = bottom + 34f;
            float chartWidth = width - 68f;
            float chartHeight = height - 66f;
            List<StationYearlySummaryRow> rows = report.yearlyRows();
            long rawMax = rows.stream()
                .mapToLong(row -> Math.max(row.totalLoanApplications(), Math.max(row.totalPaidLoans(), row.defaultedLoans())))
                .max()
                .orElse(1L);
            long axisMax = niceAxisMax(rawMax);
            drawYearAxis(chartX, chartY, chartWidth, chartHeight, axisMax, rows);
            if (rawMax == 0L) {
                stream.setNonStrokingColor(MUTED_COLOR);
                writeCenteredAt("No loan activity recorded for the selected years.", chartX + (chartWidth / 2f), chartY + (chartHeight / 2f), regular, BODY_SIZE);
                stream.setNonStrokingColor(TEXT_COLOR);
            } else {
                drawYearSeries(chartX, chartY, chartWidth, chartHeight, axisMax, 0, "Applications", new Color(37, 99, 235));
                drawYearSeries(chartX, chartY, chartWidth, chartHeight, axisMax, 1, "Paid", new Color(5, 150, 105));
                drawYearSeries(chartX, chartY, chartWidth, chartHeight, axisMax, 2, "Defaulted", new Color(220, 38, 38));
            }
            y = bottom - 14f;
        }

        private void drawYearAxis(float chartX,
                                  float chartY,
                                  float chartWidth,
                                  float chartHeight,
                                  long axisMax,
                                  List<StationYearlySummaryRow> rows) throws IOException {
            int tickCount = axisMax <= 4 ? (int) axisMax : 4;
            tickCount = Math.max(tickCount, 1);
            stream.setLineWidth(0.45f);
            for (int i = 0; i <= tickCount; i++) {
                long tickValue = Math.round(axisMax * (i / (double) tickCount));
                float tickY = chartY + chartHeight * tickValue / axisMax;
                stream.setStrokingColor(i == 0 ? BORDER_COLOR : GRID_COLOR);
                drawLine(chartX, tickY, chartX + chartWidth, tickY);
                stream.setNonStrokingColor(MUTED_COLOR);
                writeRight(String.valueOf(tickValue), chartX - 6f, tickY - 2f, regular, SMALL_SIZE);
            }
            stream.setStrokingColor(BORDER_COLOR);
            stream.setLineWidth(0.8f);
            drawLine(chartX, chartY, chartX + chartWidth, chartY);
            drawLine(chartX, chartY, chartX, chartY + chartHeight);
            for (int i = 0; i < rows.size(); i++) {
                if (!shouldShowYearLabel(i, rows.size())) {
                    continue;
                }
                float x = yearX(chartX, chartWidth, i, rows.size());
                stream.setNonStrokingColor(MUTED_COLOR);
                writeCenteredAt(String.valueOf(rows.get(i).year()), x, chartY - 14f, regular, SMALL_SIZE);
            }
            stream.setNonStrokingColor(TEXT_COLOR);
        }

        private void drawYearSeries(float chartX,
                                    float chartY,
                                    float chartWidth,
                                    float chartHeight,
                                    long max,
                                    int valueIndex,
                                    String label,
                                    Color color) throws IOException {
            List<StationYearlySummaryRow> rows = report.yearlyRows();
            if (rows.isEmpty()) {
                return;
            }
            stream.setStrokingColor(color);
            stream.setLineWidth(valueIndex == 0 ? 1.2f : 0.9f);
            for (int i = 1; i < rows.size(); i++) {
                float x1 = yearX(chartX, chartWidth, i - 1, rows.size());
                float y1 = chartY + chartHeight * yearValue(rows.get(i - 1), valueIndex) / max;
                float x2 = yearX(chartX, chartWidth, i, rows.size());
                float y2 = chartY + chartHeight * yearValue(rows.get(i), valueIndex) / max;
                drawLine(x1, y1, x2, y2);
            }
            for (int i = 0; i < rows.size(); i++) {
                long value = yearValue(rows.get(i), valueIndex);
                float x = yearX(chartX, chartWidth, i, rows.size());
                float pointY = chartY + chartHeight * value / max;
                drawPoint(x, pointY, color);
                if (rows.size() <= 8 || i == 0 || i == rows.size() - 1) {
                    stream.setNonStrokingColor(color);
                    writeCenteredAt(String.valueOf(value), x, pointY + 6f + (valueIndex * 6f), regular, SMALL_SIZE);
                }
            }
            stream.setNonStrokingColor(color);
            float legendX = chartX + 24f + (valueIndex * 118f);
            stream.setStrokingColor(color);
            drawLine(legendX - 14f, chartY + chartHeight + 19f, legendX - 4f, chartY + chartHeight + 19f);
            write(label, legendX, chartY + chartHeight + 17f, regular, SMALL_SIZE);
            stream.setNonStrokingColor(TEXT_COLOR);
            stream.setStrokingColor(BORDER_COLOR);
        }

        private long niceAxisMax(long rawMax) {
            if (rawMax <= 1L) {
                return 1L;
            }
            if (rawMax <= 4L) {
                return rawMax;
            }
            long magnitude = 1L;
            while (magnitude * 10L < rawMax) {
                magnitude *= 10L;
            }
            long[] steps = {1L, 2L, 5L, 10L};
            for (long step : steps) {
                long candidate = step * magnitude;
                if (candidate >= rawMax) {
                    return candidate;
                }
            }
            return 10L * magnitude;
        }

        private boolean shouldShowYearLabel(int index, int count) {
            return count <= 8 || index == 0 || index == count - 1 || index % 2 == 0;
        }

        private float yearX(float chartX, float chartWidth, int index, int count) {
            return chartX + chartWidth * index / Math.max(count - 1, 1);
        }

        private void drawPoint(float x, float pointY, Color color) throws IOException {
            stream.setNonStrokingColor(color);
            stream.addRect(x - 1.7f, pointY - 1.7f, 3.4f, 3.4f);
            stream.fill();
        }

        private long yearValue(StationYearlySummaryRow row, int index) {
            return switch (index) {
                case 1 -> row.totalPaidLoans();
                case 2 -> row.defaultedLoans();
                default -> row.totalLoanApplications();
            };
        }

        private void drawLine(float x1, float y1, float x2, float y2) throws IOException {
            stream.moveTo(x1, y1);
            stream.lineTo(x2, y2);
            stream.stroke();
        }

        private void drawSection(String title, String[] headers, float[] widths, List<String[]> rows, float fontSize) throws IOException {
            ensureSpace(34f);
            write(title, MARGIN, y, bold, SECTION_SIZE);
            y -= 9f;
            drawTable(headers, widths, rows, fontSize);
            y -= 13f;
        }

        private void drawTable(String[] headers, float[] widths, List<String[]> rows, float fontSize) throws IOException {
            drawRow(headers, widths, bold, fontSize);
            for (String[] row : rows) {
                float needed = rowHeight(row, widths, regular, fontSize);
                if (y - needed < (BOTTOM_MARGIN + FOOTER_GAP)) {
                    startNewPage();
                    drawRow(headers, widths, bold, fontSize);
                }
                drawRow(row, widths, regular, fontSize);
            }
        }

        private void drawRow(String[] cells, float[] widths, PDType1Font font, float fontSize) throws IOException {
            float rowHeight = rowHeight(cells, widths, font, fontSize);
            ensureSpace(rowHeight);
            float x = MARGIN;
            for (int i = 0; i < cells.length; i++) {
                stream.setStrokingColor(BORDER_COLOR);
                stream.addRect(x, y - rowHeight, widths[i], rowHeight);
                stream.stroke();
                float textY = y - CELL_PADDING_Y - fontSize;
                for (String line : wrapText(cells[i], font, fontSize, widths[i] - (CELL_PADDING_X * 2f))) {
                    write(line, x + CELL_PADDING_X, textY, font, fontSize);
                    textY -= fontSize + 1.6f;
                }
                x += widths[i];
            }
            y -= rowHeight;
        }

        private float rowHeight(String[] cells, float[] widths, PDType1Font font, float fontSize) throws IOException {
            int maxLines = 1;
            for (int i = 0; i < cells.length; i++) {
                maxLines = Math.max(maxLines, wrapText(cells[i], font, fontSize, widths[i] - (CELL_PADDING_X * 2f)).size());
            }
            return (CELL_PADDING_Y * 2f) + (maxLines * (fontSize + 1.6f));
        }

        private List<String> wrapText(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
            String safe = sanitizePdfText(text);
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            for (String word : safe.split("\\s+")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (font.getStringWidth(candidate) / 1000f * fontSize > maxWidth && !current.isEmpty()) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines.isEmpty() ? List.of("-") : lines;
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (y - requiredHeight < (BOTTOM_MARGIN + FOOTER_GAP)) {
                startNewPage();
            }
        }

        private void drawFooter() throws IOException {
            float footerY = BOTTOM_MARGIN;
            ruleAt(footerY + 12f);
            write(report.saccoName() + " | Station Loan Status Report", MARGIN, footerY, regular, 7f);
            writeRight("Page " + pageNumber, page.getMediaBox().getWidth() - MARGIN, footerY, regular, 7f);
        }

        private void rule() throws IOException {
            ruleAt(y);
        }

        private void ruleAt(float lineY) throws IOException {
            stream.setStrokingColor(BORDER_COLOR);
            stream.moveTo(MARGIN, lineY);
            stream.lineTo(page.getMediaBox().getWidth() - MARGIN, lineY);
            stream.stroke();
        }

        private void writeCentered(String text, float baselineY, PDType1Font font, float fontSize) throws IOException {
            float width = font.getStringWidth(sanitizePdfLineText(text)) / 1000f * fontSize;
            write(text, (page.getMediaBox().getWidth() - width) / 2f, baselineY, font, fontSize);
        }

        private void writeRight(String text, float rightX, float baselineY, PDType1Font font, float fontSize) throws IOException {
            float width = font.getStringWidth(sanitizePdfLineText(text)) / 1000f * fontSize;
            write(text, rightX - width, baselineY, font, fontSize);
        }

        private void writeCenteredAt(String text, float centerX, float baselineY, PDType1Font font, float fontSize) throws IOException {
            float width = font.getStringWidth(sanitizePdfLineText(text)) / 1000f * fontSize;
            write(text, centerX - (width / 2f), baselineY, font, fontSize);
        }

        private void write(String text, float x, float baselineY, PDType1Font font, float fontSize) throws IOException {
            stream.beginText();
            stream.setNonStrokingColor(TEXT_COLOR);
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(x, baselineY);
            stream.showText(sanitizePdfLineText(text));
            stream.endText();
        }

        private static String formatDateStatic(LocalDate date) {
            return date == null ? "-" : DATE_FORMATTER.format(date);
        }
    }

    private static final class MemberPdfRenderer {
        private static final float MARGIN = 38f;
        private static final float TOP_MARGIN = 40f;
        private static final float BOTTOM_MARGIN = 34f;
        private static final float FOOTER_GAP = 22f;
        private static final float TITLE_SIZE = 18f;
        private static final float META_SIZE = 9f;
        private static final float SECTION_SIZE = 10.5f;
        private static final float BODY_SIZE = 8.2f;
        private static final float SMALL_SIZE = 7.1f;
        private static final float LINE_GAP = 3f;
        private static final float CELL_PADDING_X = 6f;
        private static final float CELL_PADDING_Y = 5f;
        private static final Color TEXT_COLOR = new Color(41, 55, 71);
        private static final Color MUTED_COLOR = new Color(87, 106, 126);
        private static final Color BORDER_COLOR = new Color(225, 232, 238);
        private static final Color HEADER_FILL = new Color(246, 248, 251);
        private static final Color RULE_COLOR = new Color(60, 79, 97);

        private final PDDocument document;
        private final MemberLoanReport report;
        private final byte[] watermarkLogo;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final String generatedDate;
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private MemberPdfRenderer(PDDocument document, MemberLoanReport report, String generatedDate, byte[] watermarkLogo) {
            this.document = document;
            this.report = report;
            this.generatedDate = generatedDate;
            this.watermarkLogo = watermarkLogo;
        }

        private void render() throws IOException {
            startNewPage();
            drawHeader();
            drawSummaryTable();
            drawAnalyticsTable();
            drawDetailedLoansSection();
            drawApprovalSection();
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
            PdfWatermarkRenderer.draw(document, stream, page, watermarkLogo);
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
            ensureSpace(56f);
            writeText(sanitizePdfText("Member Loan Report"), MARGIN, y, bold, TITLE_SIZE, TEXT_COLOR);
            writeRightAligned(
                "Generated: " + generatedDate,
                page.getMediaBox().getWidth() - MARGIN,
                y + 1f,
                regular,
                META_SIZE,
                MUTED_COLOR
            );
            y -= 19f;
            String memberLabel = sanitizePdfText(report.member().getFullName()).toUpperCase(Locale.ROOT)
                + " (" + sanitizePdfText(report.member().getMemberNo()) + ")";
            writeText(memberLabel, MARGIN, y, bold, META_SIZE + 0.5f, MUTED_COLOR);
            y -= 16f;
            drawRule();
            y -= 14f;
        }

        private void drawSummaryTable() throws IOException {
            List<String[]> rows = List.of(
                new String[]{"Disbursed Loans", String.valueOf(report.summary().disbursedCount())},
                new String[]{"Returned / Paid", String.valueOf(report.summary().paidCount())},
                new String[]{"Ongoing Loans", String.valueOf(report.summary().ongoingCount())},
                new String[]{"Total Disbursed Amount", sanitizePdfText(report.summary().getDisbursedAmountLabel())},
                new String[]{"Total Returned Amount", sanitizePdfText(report.summary().getPaidAmountLabel())}
            );
            float width = contentWidth();
            drawTable(
                new String[]{"Metric", "Value"},
                new float[]{width * 0.58f, width * 0.42f},
                rows,
                BODY_SIZE,
                BODY_SIZE,
                14f
            );
            y -= 16f;
        }

        private void drawAnalyticsTable() throws IOException {
            MemberLoanAnalyticsSummary analytics = report.analytics();
            if (analytics == null) {
                return;
            }
            drawSectionHeading("Loan Status Analytics");
            List<String[]> rows = List.of(
                new String[]{"Applied Loans", String.valueOf(analytics.appliedCount())},
                new String[]{"Active Loans", String.valueOf(analytics.activeCount())},
                new String[]{"Disbursed Loans", String.valueOf(analytics.disbursedCount())},
                new String[]{"Paid Loans", String.valueOf(analytics.paidCount())},
                new String[]{"Defaulted Loans", String.valueOf(analytics.defaultedCount())},
                new String[]{"Rejected Loans", String.valueOf(analytics.rejectedCount())},
                new String[]{"Active Loan Amount", sanitizePdfText(analytics.getActiveAmountLabel())}
            );
            float width = contentWidth();
            drawTable(
                new String[]{"Metric", "Value"},
                new float[]{width * 0.58f, width * 0.42f},
                rows,
                BODY_SIZE,
                BODY_SIZE,
                14f
            );
            y -= 6f;
        }

        private void drawDetailedLoansSection() throws IOException {
            drawSectionHeading("Detailed Loan Applications");
            drawParagraph("This layout uses structured tables for better clarity and PDF readability.", regular, META_SIZE, MUTED_COLOR);
            y -= 6f;

            List<String[]> rows = new ArrayList<>();
            List<MemberLoanDetail> details = report.details() == null ? List.of() : report.details();
            if (details.isEmpty()) {
                rows.add(new String[]{"-", "No records", "-", "-", "-", "-", "-", "-", "-"});
            } else {
                int index = 1;
                for (MemberLoanDetail detail : details) {
                    rows.add(new String[]{
                        String.valueOf(index++),
                        sanitizePdfText(detail.loanApplicationIdLabel()),
                        sanitizePdfText(detail.loanIdLabel()),
                        sanitizePdfText(detail.approvedAmountLabel()),
                        sanitizePdfText(detail.tenureLabel()),
                        "App: " + sanitizePdfText(detail.applicationFeeLabel())
                            + "\nIns: " + sanitizePdfText(detail.insuranceFeeLabel())
                            + "\nProc: " + sanitizePdfText(detail.processingFeeLabel()),
                        sanitizePdfText(detail.totalDeductionsLabel()),
                        sanitizePdfText(detail.statusLabel()),
                        sanitizePdfText(detail.disbursementDateLabel()) + " / " + sanitizePdfText(detail.finalDueDateLabel())
                    });
                }
            }

            drawTable(
                new String[]{"Loan #", "Application ID", "Loan ID", "Amount", "Tenure", "Fees", "Deductions", "Status", "Disbursed / Due"},
                new float[]{28f, 54f, 42f, 56f, 46f, 66f, 64f, 48f, contentWidth() - 404f},
                rows,
                SMALL_SIZE,
                SMALL_SIZE,
                13f
            );
            y -= 16f;
        }

        private void drawApprovalSection() throws IOException {
            drawSectionHeading("Approval & Member Details");
            List<MemberLoanDetail> details = report.details() == null ? List.of() : report.details();
            if (details.isEmpty()) {
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Field", "No loan applications found for this member yet."});
                float width = contentWidth();
                drawTable(
                    new String[]{"Field", "Details"},
                    new float[]{width * 0.32f, width * 0.68f},
                    rows,
                    BODY_SIZE,
                    BODY_SIZE,
                    14f
                );
                return;
            }

            int index = 1;
            for (MemberLoanDetail detail : details) {
                drawLoanDetailTitle(detail, index++);
                List<String[]> rows = new ArrayList<>();
                rows.add(new String[]{"Member Contact", stackText(detail.memberDetailsLabel(), " | ")});
                rows.add(new String[]{"Guarantor", stackText(detail.guarantorDetailsLabel(), "; ")});
                rows.add(new String[]{"Committee Decision", stackText(detail.approvalDecisionSummaryLabel(), " | ", "; ")});
                rows.add(new String[]{"Prepared By", sanitizePdfText(detail.preparedByLabel()) + " / " + sanitizePdfText(detail.preparedDateLabel())});

                float width = contentWidth();
                drawTable(
                    new String[]{"Field", "Details"},
                    new float[]{width * 0.32f, width * 0.68f},
                    rows,
                    BODY_SIZE,
                    BODY_SIZE,
                    12f
                );
            }
        }

        private void drawLoanDetailTitle(MemberLoanDetail detail, int index) throws IOException {
            ensureSpace(18f);
            String loanId = "-".equals(detail.loanIdLabel())
                ? ""
                : " | Loan ID " + sanitizePdfText(detail.loanIdLabel());
            String title = "Loan " + index
                + ": " + sanitizePdfText(detail.approvedProductLabel())
                + " | Application " + sanitizePdfText(detail.loanApplicationIdLabel())
                + loanId;
            writeText(title, MARGIN, y, bold, BODY_SIZE + 0.6f, TEXT_COLOR);
            y -= 12f;
        }

        private void drawSectionHeading(String text) throws IOException {
            ensureSpace(18f);
            writeText(sanitizePdfText(text), MARGIN, y, bold, SECTION_SIZE, TEXT_COLOR);
            y -= 16f;
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
            drawRow(headers, widths, bold, headerFontSize, true);
            for (String[] row : rows) {
                if (row == null) {
                    continue;
                }
                float rowHeight = measureRowHeight(row, widths, regular, bodyFontSize);
                if (y - rowHeight < (BOTTOM_MARGIN + FOOTER_GAP)) {
                    startNewPage();
                    drawRow(headers, widths, bold, headerFontSize, true);
                }
                drawRow(row, widths, regular, bodyFontSize, false);
            }
            y -= gapAfter;
        }

        private void drawRow(String[] cells,
                             float[] widths,
                             PDType1Font font,
                             float fontSize,
                             boolean header) throws IOException {
            float rowHeight = measureRowHeight(cells, widths, font, fontSize);
            ensureSpace(rowHeight);

            float x = MARGIN;
            float lineHeight = fontSize + 2f;
            List<List<String>> wrappedCells = new ArrayList<>();
            for (int i = 0; i < cells.length; i++) {
                wrappedCells.add(wrapText(cells[i], font, fontSize, widths[i] - (CELL_PADDING_X * 2f)));
            }

            for (int i = 0; i < cells.length; i++) {
                stream.setNonStrokingColor(header ? HEADER_FILL : Color.WHITE);
                stream.addRect(x, y - rowHeight, widths[i], rowHeight);
                stream.fill();

                stream.setStrokingColor(BORDER_COLOR);
                stream.addRect(x, y - rowHeight, widths[i], rowHeight);
                stream.stroke();

                float textY = y - CELL_PADDING_Y - fontSize;
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
            float lineHeight = fontSize + 2f;
            int maxLines = 1;
            for (int i = 0; i < cells.length; i++) {
                List<String> lines = wrapText(cells[i], font, fontSize, widths[i] - (CELL_PADDING_X * 2f));
                maxLines = Math.max(maxLines, lines.size());
            }
            return (CELL_PADDING_Y * 2f) + (maxLines * lineHeight);
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
                List<String> paragraphLines = wrapParagraph(paragraph, font, fontSize, maxWidth);
                lines.addAll(paragraphLines);
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
            String preparedBy = sanitizePdfText(report.member().getFullName());
            String preparedDate = generatedDate;
            writeText("Prepared by: " + preparedBy, MARGIN, footerY, regular, 7.4f, MUTED_COLOR);
            writeRightAligned("Date: " + preparedDate, page.getMediaBox().getWidth() - MARGIN, footerY, regular, 7.4f, MUTED_COLOR);
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

        private float contentWidth() {
            return page.getMediaBox().getWidth() - (MARGIN * 2f);
        }

        private static String stackText(String text, String... delimiters) {
            String stacked = sanitizePdfText(text);
            for (String delimiter : delimiters) {
                stacked = stacked.replace(delimiter, "\n");
            }
            return stacked;
        }
    }
}
