package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ExternalAccountStatusService {
    private final LoanAnalyticsService loanAnalyticsService;

    public ExternalAccountStatusView resolve(Member member) {
        if (member == null) {
            return ExternalAccountStatusView.unavailable("Client details are not available.");
        }
        BigDecimal activeExposure = loanAnalyticsService.activeLoanAmount(
            member.getId(),
            member.getSaccoId(),
            member.getStationId()
        );
        long defaultRiskCount = loanAnalyticsService.defaultedRiskLoanCount(
            member.getId(),
            member.getSaccoId(),
            member.getStationId()
        );
        String riskLabel = defaultRiskCount <= 0 ? "No PAR/default history" : defaultRiskCount + " PAR/default record(s)";
        return ExternalAccountStatusView.available(
            formatMoney(activeExposure),
            riskLabel,
            "Local credit status loaded."
        );
    }

    public ExternalAccountStatusView loading(String statusMessage) {
        return ExternalAccountStatusView.loading(statusMessage);
    }

    private String formatMoney(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.DOWN);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat format = new DecimalFormat("#,##0.##", symbols);
        return "TZS " + format.format(safeAmount);
    }

    public static final class ExternalAccountStatusView {
        private final boolean available;
        private final boolean pending;
        private final String activeExposureLabel;
        private final String riskHistoryLabel;
        private final String statusMessage;

        private ExternalAccountStatusView(boolean available, boolean pending, String activeExposureLabel, String riskHistoryLabel, String statusMessage) {
            this.available = available;
            this.pending = pending;
            this.activeExposureLabel = activeExposureLabel;
            this.riskHistoryLabel = riskHistoryLabel;
            this.statusMessage = statusMessage;
        }

        public static ExternalAccountStatusView available(String activeExposureLabel, String riskHistoryLabel, String statusMessage) {
            return new ExternalAccountStatusView(true, false, activeExposureLabel, riskHistoryLabel, statusMessage);
        }

        public static ExternalAccountStatusView loading(String statusMessage) {
            return new ExternalAccountStatusView(false, true, "Loading...", "Loading...", statusMessage);
        }

        public static ExternalAccountStatusView unavailable(String statusMessage) {
            return new ExternalAccountStatusView(false, false, "-", "-", statusMessage);
        }

        public boolean isAvailable() {
            return available;
        }

        public boolean isPending() {
            return pending;
        }

        public String getActiveExposureLabel() {
            return activeExposureLabel;
        }

        public String getRiskHistoryLabel() {
            return riskHistoryLabel;
        }

        public String getStatusMessage() {
            return statusMessage;
        }
    }
}
