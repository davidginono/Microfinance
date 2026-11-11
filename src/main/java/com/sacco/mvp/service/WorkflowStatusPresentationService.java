package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.EnumMap;

@Service
public class WorkflowStatusPresentationService {











    public List<Map<String, Object>> buildDisbursementDashboardChartRows(List<ManagerService.StatusCount> statusBreakdown) {
        return buildRows(List.of(
            slice("READY_FOR_DISBURSEMENT", "Ready for Disbursement", "#0EA5E9", LoanStatus.READY_FOR_DISBURSEMENT),
            slice("DISBURSED_PORTFOLIO", "Disbursed Loans", "#22C55E", LoanStatus.DISBURSED),
            slice("PAR", "Portfolio At Risk", "#F59E0B", LoanStatus.PAR),
            slice("PAID", "Paid Loans", "#16A34A", LoanStatus.PAID),
            slice("DEFAULTED", "Defaulted Loans", "#DC2626", LoanStatus.DEFAULTED)
        ), statusBreakdown);
    }

    public long countFor(List<ManagerService.StatusCount> statusBreakdown, LoanStatus status) {
        if (statusBreakdown == null || status == null) {
            return 0L;
        }
        return statusBreakdown.stream()
            .filter(entry -> entry.status() == status)
            .mapToLong(ManagerService.StatusCount::count)
            .findFirst()
            .orElse(0L);
    }

    public String dashboardStatusLabel(LoanStatus status) {
        return switch (status) {
            case WITHDRAWN, WRITTEN_OFF, DISBURSEMENT_REVERSED -> java.util.ResourceBundle.getBundle("messages", org.springframework.context.i18n.LocaleContextHolder.getLocale()).getString("loan.status."+status.name());
            case RECORDED -> java.util.ResourceBundle.getBundle("messages", org.springframework.context.i18n.LocaleContextHolder.getLocale()).getString("recording.status.RECORDED");
            case SUBMITTED -> "Submitted";
            case READY_FOR_MANAGER -> "On Review By Manager";
            case AWAITING_LOAN_OFFICER -> "On Review By Loan Officer";
            case AWAITING_CHAIRPERSON -> "On Review By Chairperson";
            case AWAITING_BOARD -> "On Review By Board";
            case AWAITING_CREDIT_COMMITTEE -> "On Review By Credit Committee";
            case AWAITING_ACCOUNTANT -> "On Review By Accountant";
            case READY_FOR_DISBURSEMENT, MANAGER_ACCEPTED -> "Ready for Disbursement";
            case DISBURSED -> "Disbursed";
            case PAR -> "Portfolio At Risk";
            case DEFAULTED -> "Defaulted / Not Paid";
            case PAID -> "Paid";
            case MANAGER_REJECTED -> "Manager Rejected";
            case LOAN_OFFICER_REJECTED -> "Loan Officer Rejected";
            case CHAIRPERSON_REJECTED -> "Chairperson Rejected";
            case BOARD_REJECTED -> "Board Rejected";
            case CREDIT_COMMITTEE_REJECTED -> "Credit Committee Rejected";
            case ACCOUNTANT_REJECTED -> "Accountant Rejected";
            case REJECTED -> "Rejected";
            case ALL_GUARANTORS_APPROVED -> "All Guarantors Approved";
            case AWAITING_GUARANTORS -> "Awaiting Guarantors";
            case BOARD_APPROVED -> "Reviewed By Board";
            case CREDIT_COMMITTEE_APPROVED -> "Reviewed By Credit Committee";
            case CHAIRPERSON_APPROVED -> "Reviewed By Chairperson";
            case LOAN_OFFICER_APPROVED -> "Reviewed By Loan Officer";
            case ACCOUNTANT_APPROVED -> "Reviewed By Accountant";
            case DRAFT -> "Draft";
        };
    }





    private List<Map<String, Object>> buildRows(List<DashboardSlice> slices,
                                                List<ManagerService.StatusCount> statusBreakdown) {
        Map<LoanStatus, Long> counts = new EnumMap<>(LoanStatus.class);
        if (statusBreakdown != null) {
            statusBreakdown.forEach(entry -> counts.put(entry.status(), entry.count()));
        }
        long maxCount = slices.stream()
            .map(slice -> countFor(counts, slice.statuses()))
            .max(Long::compareTo)
            .orElse(1L);
        long safeMax = Math.max(1L, maxCount);

        return slices.stream()
            .map(slice -> {
                long count = countFor(counts, slice.statuses());
                long widthPercent = count <= 0 ? 0L : Math.max(8L, Math.round((count * 100.0d) / safeMax));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", slice.key());
                row.put("label", slice.label());
                row.put("count", count);
                row.put("widthPercent", widthPercent);
                row.put("color", slice.color());
                return row;
            })
            .toList();
    }

    private long countFor(Map<LoanStatus, Long> counts, Collection<LoanStatus> statuses) {
        return statuses.stream()
            .mapToLong(status -> counts.getOrDefault(status, 0L))
            .sum();
    }

    private DashboardSlice slice(String key, String label, String color, LoanStatus... statuses) {
        return new DashboardSlice(key, label, color, List.of(statuses));
    }

    private record DashboardSlice(String key, String label, String color, List<LoanStatus> statuses) {}
}
