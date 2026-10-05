package com.sacco.mvp.reporting.operational.service;

import com.sacco.mvp.domain.LoanStatus;
import com.sacco.mvp.domain.LoanRepaymentTransaction.Channel;
import com.sacco.mvp.reporting.operational.dto.ReportDefinition;
import com.sacco.mvp.reporting.operational.dto.ReportDefinition.*;
import com.sacco.mvp.service.ApplicationClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor
public class ReportDefinitionValidator {
    private final OperationalReportCatalog catalog;
    private final ApplicationClock clock;
    public ReportDefinition validate(ReportDefinition d) {
        require(d != null && d.schemaVersion() == 1 && d.dataset() != null && d.direction() != null
            && d.language() != null && d.orientation() != null && d.paper() != null);
        require(d.metricVersion() == catalog.dataset(d.dataset()).metricVersion());
        require(d.dateFrom() != null && d.dateTo() != null && !d.dateFrom().isAfter(d.dateTo())
            && !d.dateTo().isAfter(clock.today()) && ChronoUnit.DAYS.between(d.dateFrom(), d.dateTo()) <= 366);
        require(d.dataset() != Dataset.LOAN_PORTFOLIO || d.dateFrom().equals(d.dateTo()));
        require(d.columns() != null && !d.columns().isEmpty() && d.columns().size() <= 12);
        Set<Field> selected = new HashSet<>();
        for (var c : d.columns()) {
            require(c != null && c.field() != null && selected.add(c.field()) && c.width() >= 70 && c.width() <= 400);
            var spec = catalog.field(d.dataset(), c.field());
            require(c.format() == spec.format()); // Do not remove monetary units or date semantics through presentation.
            safeText(c.label(), 60);
        }
        require(d.sortBy() != null); catalog.field(d.dataset(), d.sortBy());
        require(d.groupBy() != null && d.groupBy().size() <= 2 && new HashSet<>(d.groupBy()).size() == d.groupBy().size());
        d.groupBy().forEach(f -> require(catalog.field(d.dataset(), f).groupable()));
        require(d.totals() != null && d.totals().size() <= 5 && new HashSet<>(d.totals()).size() == d.totals().size());
        d.totals().forEach(f -> require(catalog.field(d.dataset(), f).summable()));
        require(d.loanId() != null && (d.loanId().isEmpty() || d.loanId().matches("[0-9]{4,20}")));
        require(d.channel() != null && d.status() != null);
        if (!d.channel().isEmpty()) { require(d.dataset() == Dataset.COLLECTIONS); require(Arrays.stream(Channel.values()).anyMatch(c -> c.name().equals(d.channel()))); }
        if (!d.status().isEmpty()) { require(d.dataset() == Dataset.LOAN_PORTFOLIO); require(Arrays.stream(LoanStatus.values()).anyMatch(s -> s.name().equals(d.status()))); }
        safeText(d.title(), 120); safeText(d.footer(), 300);
        return new ReportDefinition(d.schemaVersion(), d.metricVersion(), d.dataset(), List.copyOf(d.columns()), d.dateFrom(), d.dateTo(),
            d.loanId(), d.channel(), d.status(), List.copyOf(d.groupBy()), List.copyOf(d.totals()), d.sortBy(), d.direction(),
            d.title(), d.footer(), d.language(), d.orientation(), d.paper(), d.showInstitutionBranding());
    }
    public static void safeText(String text, int maximum) {
        require(text != null && text.length() <= maximum && text.codePoints().noneMatch(c -> Character.isISOControl(c)
            || c == '<' || c == '>' || c == '`' || c == '\u202e' || c == '\u202d'));
    }
    private static void require(boolean condition) { if (!condition) throw new IllegalArgumentException("opreport.error.definition"); }
}
