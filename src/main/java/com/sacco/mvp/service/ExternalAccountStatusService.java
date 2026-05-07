package com.sacco.mvp.service;

import com.sacco.mvp.domain.Member;
import com.sacco.mvp.integration.foresight.ForesightAccountSummary;
import com.sacco.mvp.integration.foresight.ForesightDirectoryService;
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
    private final ForesightDirectoryService foresightDirectoryService;

    public ExternalAccountStatusView resolve(Member member) {
        if (member == null) {
            return ExternalAccountStatusView.unavailable("Member account details are not available.");
        }
        if (member.getMemberNo() == null || member.getMemberNo().isBlank()) {
            return ExternalAccountStatusView.unavailable("Member number is not available for live balance lookup.");
        }
        if (member.getStationId() == null || member.getStationId().isBlank()) {
            return ExternalAccountStatusView.unavailable("Station ID is not available for live balance lookup.");
        }
        try {
            ForesightAccountSummary summary = foresightDirectoryService.fetchAccountSummary(
                member.getMemberNo(),
                member.getStationId()
            );
            BigDecimal savings = summary == null || summary.savingsBalance() == null
                ? BigDecimal.ZERO
                : summary.savingsBalance();
            BigDecimal shares = summary == null || summary.sharesBalance() == null
                ? BigDecimal.ZERO
                : summary.sharesBalance();
            return ExternalAccountStatusView.available(
                formatMoney(savings),
                formatMoney(shares),
                "Financial statuses loaded."
            );
        } catch (IllegalStateException ex) {
            return ExternalAccountStatusView.unavailable("Live balances are unavailable right now. Try again later.");
        }
    }

    public ExternalAccountStatusView loading(String statusMessage) {
        return ExternalAccountStatusView.loading(statusMessage);
    }

    private String formatMoney(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.DOWN);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        return "TSh " + format.format(safeAmount);
    }

    public static final class ExternalAccountStatusView {
        private final boolean available;
        private final boolean pending;
        private final String savingsLabel;
        private final String sharesLabel;
        private final String statusMessage;

        private ExternalAccountStatusView(boolean available, boolean pending, String savingsLabel, String sharesLabel, String statusMessage) {
            this.available = available;
            this.pending = pending;
            this.savingsLabel = savingsLabel;
            this.sharesLabel = sharesLabel;
            this.statusMessage = statusMessage;
        }

        public static ExternalAccountStatusView available(String savingsLabel, String sharesLabel, String statusMessage) {
            return new ExternalAccountStatusView(true, false, savingsLabel, sharesLabel, statusMessage);
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

        public String getSavingsLabel() {
            return savingsLabel;
        }

        public String getSharesLabel() {
            return sharesLabel;
        }

        public String getStatusMessage() {
            return statusMessage;
        }
    }
}
