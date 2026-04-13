<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Manager Panel / Dashboard</p>
    <h1 class="erp-page-title">Manager Dashboard</h1>
    <p class="erp-page-subtitle">Track disbursed loans quickly and keep an eye on how applications are distributed across workflow statuses.</p>
</div>

<section class="erp-stat-grid">
    <div class="erp-stat-card erp-stat-green">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">Total Loans Disbursed</p>
                <p class="erp-stat-value">${dashboardTotalDisbursedLoans}</p>
                <p class="erp-stat-meta">Disbursed in ${dashboardDisbursementYear}.</p>
            </div>
            <span class="erp-stat-icon">D</span>
        </div>
        <div class="erp-stat-footer"><span>${dashboardDisbursementYear}</span><span>${dashboardTotalDisbursedLoans}</span></div>
    </div>
    <div class="erp-stat-card erp-stat-blue">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">Active Disbursed</p>
                <p class="erp-stat-value">${dashboardActiveDisbursedLoans}</p>
                <p class="erp-stat-meta">Still waiting for repayment confirmation.</p>
            </div>
            <span class="erp-stat-icon">A</span>
        </div>
        <div class="erp-stat-footer"><span>Currently ongoing</span><span>${dashboardActiveDisbursedLoans}</span></div>
    </div>
    <div class="erp-stat-card erp-stat-amber">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">On Review By Manager</p>
                <p class="erp-stat-value">${dashboardOnReviewByManagerLoans}</p>
                <p class="erp-stat-meta">Applications currently waiting for your action.</p>
            </div>
            <span class="erp-stat-icon">M</span>
        </div>
        <div class="erp-stat-footer"><span>Queue waiting</span><span>${dashboardOnReviewByManagerLoans}</span></div>
    </div>
    <div class="erp-stat-card erp-stat-red">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">Returned / Paid</p>
                <p class="erp-stat-value">${dashboardPaidLoans}</p>
                <p class="erp-stat-meta">Marked paid by a manager.</p>
            </div>
            <span class="erp-stat-icon">P</span>
        </div>
        <div class="erp-stat-footer"><span>Closed out loans</span><span>${dashboardPaidLoans}</span></div>
    </div>
</section>

<section class="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
    <div class="erp-panel">
        <div class="erp-panel-header">
            <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <p class="erp-panel-title">Loans Per Status</p>
                    <p class="mt-1 text-sm text-slate-500">A quick view of where the SACCO loan pipeline currently stands.</p>
                </div>
                <a href="/manager/loan-applications?status=READY_FOR_MANAGER" class="app-btn btn-primary">Open Manager Queue</a>
            </div>
        </div>
        <div class="erp-panel-body">
            <div class="erp-table-wrap overflow-x-auto">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th>Status</th>
                        <th>Loans</th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${dashboardStatusRows}" var="entry">
                        <tr>
                            <td>${entry.statusLabel}</td>
                            <td>${entry.count}</td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </div>
    </div>

    <div class="erp-panel">
        <div class="erp-panel-header">
            <div>
                <p class="erp-panel-title">Recent Disbursements</p>
                <p class="mt-1 text-sm text-slate-500">Loans disbursed within the last ${dashboardRecentDisbursementDays} days.</p>
            </div>
        </div>
        <div class="erp-panel-body">
            <div class="space-y-3">
                <c:forEach items="${dashboardDisbursementRows}" var="loan">
                    <a href="/manager/loan-applications/${loan.id}" class="block rounded border border-slate-200 bg-white p-3 transition hover:border-slate-300 hover:bg-slate-50">
                        <div class="flex items-start justify-between gap-3">
                            <div>
                                <p class="font-semibold text-slate-900">${loan.applicantName}</p>
                                <p class="mt-1 text-sm text-slate-600">${loan.loanTypeLabel}</p>
                            </div>
                            <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                                ${loan.statusLabel}
                            </span>
                        </div>
                        <div class="mt-3 flex flex-wrap gap-3 text-sm text-slate-600">
                            <span>Loan ID: ${loan.shortId}</span>
                            <span>Amount: ${loan.amount}</span>
                            <span>Disbursed: ${loan.disbursementDate}</span>
                        </div>
                    </a>
                </c:forEach>
                <c:if test="${empty dashboardDisbursementRows}">
                    <div class="erp-section-muted">
                        <p class="text-slate-600">No loans were disbursed in the last ${dashboardRecentDisbursementDays} days.</p>
                    </div>
                </c:if>
            </div>
        </div>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
