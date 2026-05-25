package com.sacco.mvp.service;

import com.sacco.mvp.domain.LoanStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowStatusPresentationService {

    public List<Map<String, Object>> buildDashboardStatusChartRows(List<ManagerService.StatusCount> statusBreakdown) {
        long maxCount = statusBreakdown.stream()
            .map(ManagerService.StatusCount::count)
            .max(Long::compareTo)
            .orElse(1L);
        long safeMax = Math.max(1L, maxCount);

        return statusBreakdown.stream()
            .map(entry -> {
                long count = entry.count();
                long widthPercent = count <= 0 ? 0L : Math.max(8L, Math.round((count * 100.0d) / safeMax));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("status", entry.status().name());
                row.put("label", dashboardStatusLabel(entry.status()));
                row.put("count", count);
                row.put("widthPercent", widthPercent);
                row.put("color", dashboardStatusColor(entry.status()));
                return row;
            })
            .toList();
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
            case SUBMITTED -> "Submitted";
            case READY_FOR_MANAGER -> "On Review By Manager";
            case AWAITING_LOAN_OFFICER -> "On Review By Loan Officer";
            case AWAITING_BOARD -> "On Review By Board";
            case AWAITING_ACCOUNTANT -> "On Review By Accountant";
            case READY_FOR_DISBURSEMENT, MANAGER_ACCEPTED -> "Ready for Disbursement";
            case FINAL_APPROVED -> "Disbursed Loan";
            case DEFAULTED -> "Defaulted / Not Paid";
            case PAID -> "Paid";
            case MANAGER_REJECTED -> "Manager Rejected";
            case LOAN_OFFICER_REJECTED -> "Loan Officer Rejected";
            case BOARD_REJECTED -> "Board Rejected";
            case ACCOUNTANT_REJECTED -> "Accountant Rejected";
            case FORFEITED -> "Forfeited";
            case FINAL_REJECTED -> "Final Rejected";
            case ALL_GUARANTORS_APPROVED -> "All Guarantors Approved";
            case AWAITING_GUARANTORS -> "Awaiting Guarantors";
            case BOARD_APPROVED -> "Reviewed By Board";
            case LOAN_OFFICER_APPROVED -> "Reviewed By Loan Officer";
            case ACCOUNTANT_APPROVED -> "Reviewed By Accountant";
            case DRAFT -> "Draft";
        };
    }

    public String dashboardStatusColor(LoanStatus status) {
        return switch (status) {
            case DRAFT -> "#60A5FA";
            case SUBMITTED -> "#94A3B8";
            case AWAITING_GUARANTORS -> "#F59E0B";
            case ALL_GUARANTORS_APPROVED -> "#0F766E";
            case READY_FOR_MANAGER -> "#14B8A6";
            case AWAITING_LOAN_OFFICER -> "#7C3AED";
            case AWAITING_BOARD -> "#6366F1";
            case AWAITING_ACCOUNTANT -> "#0F766E";
            case BOARD_APPROVED -> "#2F348D";
            case LOAN_OFFICER_APPROVED, ACCOUNTANT_APPROVED, READY_FOR_DISBURSEMENT, MANAGER_ACCEPTED -> "#0EA5E9";
            case FINAL_APPROVED -> "#22C55E";
            case DEFAULTED -> "#DC2626";
            case PAID -> "#16A34A";
            case MANAGER_REJECTED, LOAN_OFFICER_REJECTED, BOARD_REJECTED, ACCOUNTANT_REJECTED, FORFEITED, FINAL_REJECTED -> "#F43F5E";
        };
    }
}
