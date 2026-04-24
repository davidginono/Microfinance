<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / SACCOs / Detail</p>
    <h1 class="erp-page-title">${saccoDetail.summary.saccoName}</h1>
    <p class="erp-page-subtitle">Read-only platform view for ${saccoDetail.summary.saccoId}.</p>
</div>

<section class="erp-panel">
    <div class="erp-panel-body space-y-4">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div class="flex min-w-0 items-center gap-4">
                <c:choose>
                    <c:when test="${saccoDetail.summary.hasLogo}">
                        <img src="${saccoDetail.summary.logoUrl}"
                             alt="${saccoDetail.summary.saccoName} logo"
                             class="h-16 w-16 rounded-md border border-slate-200 bg-white object-contain p-2 shadow-sm" />
                    </c:when>
                    <c:otherwise>
                        <span class="inline-flex h-16 w-16 items-center justify-center rounded-md border border-dashed border-slate-300 bg-slate-50 text-lg font-semibold uppercase tracking-[0.16em] text-slate-500">
                            ${saccoDetail.summary.logoFallbackText}
                        </span>
                    </c:otherwise>
                </c:choose>
                <div class="min-w-0">
                    <p class="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">${saccoDetail.summary.saccoId}</p>
                    <h2 class="mt-1 text-2xl font-semibold text-slate-900">${saccoDetail.summary.saccoName}</h2>
                    <p class="mt-2 text-sm text-slate-600">Stations: ${saccoDetail.summary.stationListLabel}</p>
                </div>
            </div>
            <span class="inline-flex items-center gap-2 self-start rounded-full border px-3 py-1 text-sm font-semibold ${saccoDetail.summary.toneBadgeClass}">
                <span class="h-2.5 w-2.5 rounded-full ${saccoDetail.summary.toneDotClass}"></span>
                ${saccoDetail.summary.healthStatus}
            </span>
        </div>

        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Members</p>
                <p class="mt-2 text-2xl font-semibold text-slate-900">${saccoDetail.summary.totalMembers}</p>
                <p class="mt-1 text-sm text-slate-500">Active: ${saccoDetail.summary.activeMembers} | Inactive: ${saccoDetail.summary.inactiveMembers}</p>
            </div>
            <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Loans</p>
                <p class="mt-2 text-2xl font-semibold text-slate-900">${saccoDetail.summary.totalDisbursedPrincipalLabel}</p>
                <p class="mt-1 text-sm text-slate-500">Active loans: ${saccoDetail.summary.activeLoanCount}</p>
            </div>
            <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Repayment / Default</p>
                <p class="mt-2 text-2xl font-semibold text-slate-900">${saccoDetail.summary.repaymentPercentLabel}</p>
                <p class="mt-1 text-sm text-slate-500">Default: ${saccoDetail.summary.defaultPercentLabel}</p>
            </div>
            <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Liquidity</p>
                <p class="mt-2 text-2xl font-semibold text-slate-900">${saccoDetail.summary.liquidityRatioLabel}</p>
                <p class="mt-1 text-sm text-slate-500">${saccoDetail.summary.healthNote}</p>
            </div>
        </div>
    </div>
</section>

<div class="mt-4 flex flex-wrap gap-2">
    <a href="/admin/saccos/${saccoDetail.saccoId}?section=overview" class="erp-filter-tab ${selectedSection eq 'overview' ? 'is-active' : ''}">Overview</a>
    <a href="/admin/saccos/${saccoDetail.saccoId}?section=loans" class="erp-filter-tab ${selectedSection eq 'loans' ? 'is-active' : ''}">Loans</a>
    <a href="/admin/saccos/${saccoDetail.saccoId}?section=members" class="erp-filter-tab ${selectedSection eq 'members' ? 'is-active' : ''}">Members</a>
    <a href="/admin/saccos/${saccoDetail.saccoId}?section=financials" class="erp-filter-tab ${selectedSection eq 'financials' ? 'is-active' : ''}">Financials</a>
    <a href="/admin/saccos/${saccoDetail.saccoId}?section=audit" class="erp-filter-tab ${selectedSection eq 'audit' ? 'is-active' : ''}">Audit</a>
</div>

<c:choose>
    <c:when test="${selectedSection eq 'loans'}">
        <section class="erp-panel mt-4">
            <div class="erp-panel-header">
                <p class="erp-panel-title">Loan Portfolio</p>
            </div>
            <div class="erp-panel-body space-y-4">
                <div class="grid gap-3 sm:grid-cols-3">
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Disbursed Principal</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.summary.totalDisbursedPrincipalFullLabel}</p>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Paid Loans</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.paidLoanCount}</p>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Overdue Unpaid</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.overdueLoanCount}</p>
                    </div>
                </div>
                <div class="erp-table-wrap overflow-x-auto">
                    <table class="erp-table min-w-[760px]">
                        <thead>
                        <tr>
                            <th>Loan ID</th>
                            <th>Applicant</th>
                            <th>Status</th>
                            <th>Amount</th>
                            <th>Final Due Date</th>
                            <th>Updated</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${saccoDetail.recentLoans}" var="loan">
                            <tr>
                                <td class="font-semibold text-slate-900">${loan.loanReference}</td>
                                <td>${loan.applicantName}</td>
                                <td>${loan.statusLabel}</td>
                                <td>${loan.amountLabel}</td>
                                <td>${loan.finalDueDateLabel}</td>
                                <td>${loan.updatedAtLabel}</td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty saccoDetail.recentLoans}">
                            <tr><td colspan="6" class="text-slate-500">No loan activity found for this SACCO.</td></tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>
        </section>
    </c:when>
    <c:when test="${selectedSection eq 'members'}">
        <section class="erp-panel mt-4">
            <div class="erp-panel-header">
                <p class="erp-panel-title">Member Summary</p>
            </div>
            <div class="erp-panel-body">
                <div class="grid gap-3 sm:grid-cols-3">
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Total Members</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.summary.totalMembers}</p>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Active Members</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.summary.activeMembers}</p>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Inactive Members</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.summary.inactiveMembers}</p>
                    </div>
                </div>
            </div>
        </section>
    </c:when>
    <c:when test="${selectedSection eq 'financials'}">
        <section class="erp-panel mt-4">
            <div class="erp-panel-header">
                <p class="erp-panel-title">Financial Summary</p>
            </div>
            <div class="erp-panel-body">
                <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Savings Total</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.summary.totalSavingsFullLabel}</p>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Active Exposure</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.summary.activeExposureFullLabel}</p>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Repayment Proxy</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.summary.repaymentPercentLabel}</p>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Default / Liquidity</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.summary.defaultPercentLabel}</p>
                        <p class="mt-1 text-sm text-slate-500">Liquidity: ${saccoDetail.summary.liquidityRatioLabel}</p>
                    </div>
                </div>
            </div>
        </section>
    </c:when>
    <c:when test="${selectedSection eq 'audit'}">
        <section class="erp-panel mt-4">
            <div class="erp-panel-header">
                <p class="erp-panel-title">Recent Audit Activity</p>
            </div>
            <div class="erp-panel-body">
                <div class="space-y-3">
                    <c:forEach items="${saccoDetail.recentAuditEntries}" var="entry">
                        <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                            <div class="flex flex-wrap items-start justify-between gap-3">
                                <div>
                                    <p class="font-semibold text-slate-900">${entry.actionLabel}</p>
                                    <p class="mt-1 text-sm text-slate-600">${entry.sourceLabel} | ${entry.actorLabel}</p>
                                </div>
                                <span class="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">${entry.createdAtLabel}</span>
                            </div>
                        </div>
                    </c:forEach>
                    <c:if test="${empty saccoDetail.recentAuditEntries}">
                        <p class="text-sm text-slate-500">No recent audit entries were matched to this SACCO.</p>
                    </c:if>
                </div>
            </div>
        </section>
    </c:when>
    <c:otherwise>
        <section class="erp-panel mt-4">
            <div class="erp-panel-header">
                <p class="erp-panel-title">Overview</p>
            </div>
            <div class="erp-panel-body">
                <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Stations</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.summary.stationCount}</p>
                        <p class="mt-1 text-sm text-slate-500">${saccoDetail.summary.stationListLabel}</p>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Savings</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.summary.totalSavingsLabel}</p>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Active Exposure</p>
                        <p class="mt-2 text-xl font-semibold text-slate-900">${saccoDetail.summary.activeExposureLabel}</p>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                        <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Health Note</p>
                        <p class="mt-2 text-sm font-medium text-slate-700">${saccoDetail.summary.healthNote}</p>
                    </div>
                </div>
            </div>
        </section>
    </c:otherwise>
</c:choose>

<%@ include file="../fragments/footer.jspf" %>
