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

    public List<Map<String, Object>> buildDashboardStatusChartRows(List<ManagerService.StatusCount> statusBreakdown) {
        return buildRows(statusBreakdown.stream()
            .map(entry -> new DashboardSlice(entry.status().name(), dashboardStatusLabel(entry.status()), dashboardStatusColor(entry.status()), List.of(entry.status())))
            .toList(), statusBreakdown);
    }

    public List<Map<String, Object>> buildManagerDashboardChartRows(List<ManagerService.StatusCount> statusBreakdown) {
        return buildRows(List.of(
            slice("READY_FOR_MANAGER", "Waiting for Manager Review", "#14B8A6", LoanStatus.READY_FOR_MANAGER),
            slice("MANAGER_ACCEPTED", "Approved by Manager", "#0EA5E9", LoanStatus.MANAGER_ACCEPTED),
            slice("MANAGER_REJECTED", "Rejected by Manager", "#F43F5E", LoanStatus.MANAGER_REJECTED),
            slice("DISBURSED_PORTFOLIO", "Disbursed Loans", "#22C55E", LoanStatus.FINAL_APPROVED),
            slice("PAID", "Paid Loans", "#16A34A", LoanStatus.PAID),
            slice("DEFAULTED", "Defaulted Loans", "#DC2626", LoanStatus.DEFAULTED)
        ), statusBreakdown);
    }

    public List<Map<String, Object>> buildBoardDashboardChartRows(List<ManagerService.StatusCount> statusBreakdown,
                                                                  boolean includeDisbursementPortfolio) {
        List<DashboardSlice> slices = new java.util.ArrayList<>(List.of(
            slice("AWAITING_BOARD", "Waiting for Board Review", "#6366F1", LoanStatus.AWAITING_BOARD),
            slice("BOARD_APPROVED", "Approved by Board", "#22C55E", LoanStatus.BOARD_APPROVED),
            slice("BOARD_REJECTED", "Rejected by Board", "#F43F5E", LoanStatus.BOARD_REJECTED)
        ));
        addDisbursementPortfolioSlices(slices, includeDisbursementPortfolio);
        return buildRows(slices, statusBreakdown);
    }

    public List<Map<String, Object>> buildLoanOfficerDashboardChartRows(List<ManagerService.StatusCount> statusBreakdown,
                                                                        boolean includeDisbursementPortfolio) {
        List<DashboardSlice> slices = new java.util.ArrayList<>(List.of(
            slice("AWAITING_LOAN_OFFICER", "Waiting for Loan Officer Review", "#7C3AED", LoanStatus.AWAITING_LOAN_OFFICER),
            slice("LOAN_OFFICER_APPROVED", "Approved by Loan Officer", "#22C55E", LoanStatus.LOAN_OFFICER_APPROVED),
            slice("LOAN_OFFICER_REJECTED", "Rejected by Loan Officer", "#F43F5E", LoanStatus.LOAN_OFFICER_REJECTED)
        ));
        addDisbursementPortfolioSlices(slices, includeDisbursementPortfolio);
        return buildRows(slices, statusBreakdown);
    }

    public List<Map<String, Object>> buildAccountantDashboardChartRows(List<ManagerService.StatusCount> statusBreakdown,
                                                                       boolean includeDisbursementPortfolio) {
        List<DashboardSlice> slices = new java.util.ArrayList<>(List.of(
            slice("AWAITING_ACCOUNTANT", "Waiting for Accountant Review", "#0F766E", LoanStatus.AWAITING_ACCOUNTANT),
            slice("ACCOUNTANT_APPROVED", "Approved by Accountant", "#22C55E", LoanStatus.ACCOUNTANT_APPROVED),
            slice("ACCOUNTANT_REJECTED", "Rejected by Accountant", "#F43F5E", LoanStatus.ACCOUNTANT_REJECTED),
            slice("READY_FOR_DISBURSEMENT", "Ready for Disbursement", "#0EA5E9", LoanStatus.READY_FOR_DISBURSEMENT)
        ));
        addDisbursementPortfolioSlices(slices, includeDisbursementPortfolio);
        return buildRows(slices, statusBreakdown);
    }

    public List<Map<String, Object>> buildDisbursementDashboardChartRows(List<ManagerService.StatusCount> statusBreakdown) {
        return buildRows(List.of(
            slice("READY_FOR_DISBURSEMENT", "Ready for Disbursement", "#0EA5E9", LoanStatus.READY_FOR_DISBURSEMENT),
            slice("DISBURSED_PORTFOLIO", "Disbursed Loans", "#22C55E", LoanStatus.FINAL_APPROVED),
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

    private void addDisbursementPortfolioSlices(List<DashboardSlice> slices, boolean includeDisbursementPortfolio) {
        if (!includeDisbursementPortfolio) {
            return;
        }
        slices.add(slice("DISBURSED_PORTFOLIO", "Disbursed Loans", "#22C55E", LoanStatus.FINAL_APPROVED));
        slices.add(slice("PAID", "Paid Loans", "#16A34A", LoanStatus.PAID));
        slices.add(slice("DEFAULTED", "Defaulted Loans", "#DC2626", LoanStatus.DEFAULTED));
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
