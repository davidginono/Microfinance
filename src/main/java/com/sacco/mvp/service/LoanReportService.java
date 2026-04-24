package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanApplication;
import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.Member;
import com.sacco.mvp.repository.LoanApplicationRepository;
import com.sacco.mvp.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoanReportService {
    private static final List<LoanStatus> DISBURSED_STATUSES = List.of(
        LoanStatus.FINAL_APPROVED,
        LoanStatus.DEFAULTED,
        LoanStatus.PAID
    );
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LoanApplicationRepository loanApplicationRepository;
    private final MemberRepository memberRepository;

    public MemberLoanReport memberReport(UUID memberId) {
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        List<LoanApplication> loans = loanApplicationRepository.findByApplicantMemberIdAndStatusInOrderByCreatedAtDesc(
            memberId, DISBURSED_STATUSES).stream()
            .sorted(Comparator.comparing(LoanApplication::getDisbursementDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LoanApplication::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        return new MemberLoanReport(member, summarize(loans), loans);
    }

    public ManagerLoanReport managerReport(String saccoId, Integer year, boolean returnedOnly) {
        int effectiveYear = year == null ? LocalDate.now().getYear() : year;
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
        int currentYear = LocalDate.now().getYear();
        if (!years.contains(currentYear)) {
            years.add(0, currentYear);
        }
        return years;
    }

    public byte[] buildMemberPdf(MemberLoanReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Member Loan Report");
        lines.add("Member: " + report.member().getFullName() + " (" + report.member().getMemberNo() + ")");
        lines.add("Generated: " + DATE_FORMATTER.format(LocalDate.now()));
        lines.add("");
        lines.add("Summary");
        lines.add("Disbursed loans: " + report.summary().disbursedCount());
        lines.add("Returned / paid: " + report.summary().paidCount());
        lines.add("Ongoing loans: " + report.summary().ongoingCount());
        lines.add("Total disbursed amount: " + formatMoney(report.summary().disbursedAmount()));
        lines.add("Total returned amount: " + formatMoney(report.summary().paidAmount()));
        lines.add("");
        lines.add("Loan List");
        for (LoanApplication loan : report.loans()) {
            lines.add(shortLoanRow(loan, null));
        }
        return renderPdf(lines);
    }

    public byte[] buildManagerPdf(ManagerLoanReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Manager Disbursed Loans Report");
        lines.add("SACCO: " + report.saccoId());
        lines.add("Generated: " + DATE_FORMATTER.format(LocalDate.now()));
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
        return renderPdf(lines);
    }

    private LoanSummary summarize(Collection<LoanApplication> loans) {
        long paidCount = loans.stream().filter(loan -> loan.getStatus() == LoanStatus.PAID).count();
        long ongoingCount = loans.stream()
            .filter(loan -> loan.getStatus() == LoanStatus.FINAL_APPROVED || loan.getStatus() == LoanStatus.DEFAULTED)
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

    private byte[] renderPdf(List<String> lines) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDFCursor cursor = new PDFCursor(document);
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

    private String shortLoanRow(LoanApplication loan, Member applicant) {
        StringBuilder line = new StringBuilder();
        line.append(shortId(loan.getId())).append(" | ");
        if (applicant != null) {
            line.append(applicant.getFullName()).append(" (").append(applicant.getMemberNo()).append(") | ");
        }
        line.append(humanizeLoanType(loan.getLoanType().name())).append(" | ");
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

    private String formatMoney(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.00", new java.text.DecimalFormatSymbols(Locale.US));
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
        return dateTime == null ? "-" : dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
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
        List<LoanApplication> loans
    ) {}

    public record ManagerLoanReport(
        String saccoId,
        int year,
        boolean returnedOnly,
        LoanSummary summary,
        List<LoanApplication> loans,
        Map<UUID, Member> applicantMap
    ) {}

    private static String formatMoneyStatic(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        java.text.DecimalFormat format = new java.text.DecimalFormat("#,##0.00", new java.text.DecimalFormatSymbols(Locale.US));
        return "TSh " + format.format(safe);
    }

    private static final class PDFCursor {
        private static final float MARGIN = 48f;
        private static final float FONT_SIZE = 11f;
        private static final float LEADING = 16f;

        private final PDDocument document;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPage page;
        private PDPageContentStream stream;
        private float y;

        private PDFCursor(PDDocument document) {
            this.document = document;
        }

        private void openPage() throws IOException {
            close();
            page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
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
}
