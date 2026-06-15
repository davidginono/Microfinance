<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / SACCOs / Detail</p>
    <h1 class="erp-page-title">${saccoDetail.summary.saccoName}</h1>
    <p class="erp-page-subtitle">Read-only platform view for ${saccoDetail.summary.saccoId}.</p>
</div>

<section class="erp-panel mb-4 overflow-hidden">
    <div class="erp-panel-header">
        <p class="erp-panel-title">Station Scope</p>
    </div>
    <form action="/admin/saccos/${saccoDetail.saccoId}" method="get" class="erp-panel-body flex flex-col gap-4 lg:flex-row lg:items-end">
        <input type="hidden" name="section" value="${selectedSection}" />
        <label class="block min-w-0 flex-1 text-xs font-semibold uppercase tracking-wide text-slate-500">
            Station
            <select name="stationId" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800" style="height:3rem;min-height:3rem;">
                <option value="" ${empty saccoDetail.selectedStationId ? 'selected' : ''}>All stations</option>
                <c:forEach items="${saccoDetail.stationOptions}" var="station">
                    <option value="${station}" ${saccoDetail.selectedStationId eq station ? 'selected' : ''}>${station}</option>
                </c:forEach>
            </select>
        </label>
        <div class="flex flex-wrap gap-2 lg:justify-end">
            <a href="/admin/saccos/${saccoDetail.saccoId}?section=${selectedSection}" class="app-btn btn-neutral px-5" style="height:3rem;min-height:3rem;">Reset</a>
            <button type="submit" class="app-btn btn-primary px-5" style="height:3rem;min-height:3rem;">Apply Filter</button>
        </div>
    </form>
</section>

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

        <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
            <div class="grid gap-3 lg:grid-cols-[minmax(12rem,1fr)_minmax(32rem,2fr)] lg:items-start">
                <div>
                    <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Station Access</p>
                    <div class="mt-2 flex flex-wrap items-center gap-2">
                        <span class="inline-flex rounded-full border px-3 py-1 text-xs font-semibold ${saccoDetail.summary.accessBadgeClass}">
                            ${saccoDetail.summary.accessStatusLabel}
                        </span>
                        <span class="text-sm text-slate-500">Payment due: ${saccoDetail.summary.paymentDueDateLabel}</span>
                    </div>
                    <c:if test="${not empty saccoDetail.summary.accessRestrictionReason}">
                        <p class="mt-2 text-sm text-slate-600">${saccoDetail.summary.accessRestrictionReason}</p>
                    </c:if>
                    <c:if test="${saccoDetail.summary.accessSuspended}">
                        <p class="mt-1 text-xs font-semibold uppercase tracking-[0.14em] text-rose-600">Suspended ${saccoDetail.summary.accessSuspendedAtLabel}</p>
                    </c:if>
                </div>
                <div class="flex min-w-0 flex-col gap-2">
                    <c:choose>
                        <c:when test="${not saccoDetail.stationScoped}">
                            <div class="rounded border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-500">
                                Select one station above to suspend or restore access.
                            </div>
                        </c:when>
                        <c:when test="${saccoDetail.summary.accessSuspended}">
                            <form action="/admin/saccos/${saccoDetail.saccoId}/access/restore" method="post" class="flex flex-wrap justify-end gap-2">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                <input type="hidden" name="stationId" value="${saccoDetail.selectedStationId}" />
                                <button type="submit" class="app-btn btn-primary justify-center px-5 sm:min-w-[12rem]" style="height:3rem;min-height:3rem;">Restore Access</button>
                            </form>
                        </c:when>
                        <c:otherwise>
                            <form action="/admin/saccos/${saccoDetail.saccoId}/access/suspend"
                                  method="post"
                                  data-confirm-eyebrow="Confirm Suspension"
                                  data-confirm-title="Suspend Station Access"
                                  data-confirm-message="Suspend access for station ${saccoDetail.selectedStationId}? Users at this station will lose workspace access until it is restored."
                                  data-confirm-proceed="Suspend Access">
                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                <input type="hidden" name="stationId" value="${saccoDetail.selectedStationId}" />
                                <div class="relative flex flex-row items-center gap-2 flex-wrap">
                                    <label class="block min-w-[12rem] flex-[1_1_16rem] text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                        Reason
                                        <input name="reason" required maxlength="500" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 text-sm font-medium normal-case tracking-normal text-slate-800" style="height:3rem;min-height:3rem;" placeholder="Suspension reason" />
                                    </label>
                                    <label class="block min-w-[11rem] flex-[0_1_12rem] text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">
                                        Payment Due
                                        <input name="paymentDueDate" type="date" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 text-sm font-medium normal-case tracking-normal text-slate-800" style="height:3rem;min-height:3rem;" />
                                    </label>
                                    <div class="flex shrink-0 items-center pt-5">
                                        <button type="submit" class="app-btn btn-reject min-w-[12rem] justify-center px-5" style="height:3rem;min-height:3rem;">Suspend Access</button>
                                    </div>
                                </div>
                            </form>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
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

<c:set var="stationTabQuery" value="${empty saccoDetail.selectedStationId ? '' : '&stationId='.concat(saccoDetail.selectedStationId)}" />
<div class="mt-4 flex flex-wrap gap-2">
    <a href="/admin/saccos/${saccoDetail.saccoId}?section=overview${stationTabQuery}" class="erp-filter-tab ${selectedSection eq 'overview' ? 'is-active' : ''}">Overview</a>
    <a href="/admin/saccos/${saccoDetail.saccoId}?section=loans${stationTabQuery}" class="erp-filter-tab ${selectedSection eq 'loans' ? 'is-active' : ''}">Loans</a>
    <a href="/admin/saccos/${saccoDetail.saccoId}?section=members${stationTabQuery}" class="erp-filter-tab ${selectedSection eq 'members' ? 'is-active' : ''}">Members</a>
    <a href="/admin/saccos/${saccoDetail.saccoId}?section=financials${stationTabQuery}" class="erp-filter-tab ${selectedSection eq 'financials' ? 'is-active' : ''}">Financials</a>
    <a href="/admin/saccos/${saccoDetail.saccoId}?section=sms${stationTabQuery}" class="erp-filter-tab ${selectedSection eq 'sms' ? 'is-active' : ''}">SMS Units</a>
    <a href="/admin/saccos/${saccoDetail.saccoId}?section=audit${stationTabQuery}" class="erp-filter-tab ${selectedSection eq 'audit' ? 'is-active' : ''}">Audit</a>
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
    <c:when test="${selectedSection eq 'sms'}">
        <c:choose>
            <c:when test="${saccoDetail.stationScoped}">
                <section class="erp-panel mt-4">
                    <div class="erp-panel-header">
                        <div>
                            <p class="erp-panel-title">SMS Units: ${saccoDetail.selectedStationId}</p>
                            <p class="mt-1 text-sm text-slate-500">Current station balance and usage status.</p>
                        </div>
                        <a href="/admin/sms-usage?saccoId=${saccoDetail.saccoId}&stationId=${saccoDetail.selectedStationId}" class="app-btn btn-neutral">Manage SMS Units</a>
                    </div>
                    <div class="erp-panel-body">
                        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                            <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Available Units</p>
                                <p class="mt-2 text-xl font-semibold text-slate-900"><fmt:formatNumber value="${smsAccount.availableUnits}" /></p>
                            </div>
                            <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Alert Reserve</p>
                                <p class="mt-2 text-xl font-semibold text-slate-900"><fmt:formatNumber value="${smsAccount.alertReservedUnits}" /> / 3</p>
                            </div>
                            <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Warning Baseline</p>
                                <p class="mt-2 text-xl font-semibold text-slate-900"><fmt:formatNumber value="${smsAccount.warningBaseline}" /></p>
                            </div>
                            <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3">
                                <p class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">SMS Status</p>
                                <p class="mt-3">
                                    <span class="rounded-md border px-2 py-1 text-xs font-bold ${smsAccount.status eq 'DEPLETED' ? 'border-rose-200 bg-rose-50 text-rose-700' : smsAccount.status eq 'CRITICAL' ? 'border-orange-200 bg-orange-50 text-orange-700' : smsAccount.status eq 'LOW' ? 'border-amber-200 bg-amber-50 text-amber-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}">${smsAccount.status}</span>
                                </p>
                            </div>
                        </div>
                    </div>
                </section>

                <section class="erp-panel mt-4">
                    <div class="erp-panel-header">
                        <p class="erp-panel-title">Recent SMS Usage</p>
                    </div>
                    <div class="erp-panel-body overflow-x-auto">
                        <table class="erp-table min-w-[760px]">
                            <thead>
                            <tr>
                                <th>Time</th>
                                <th>Event</th>
                                <th>Outcome</th>
                                <th>Unit Change</th>
                                <th>Provider Reference</th>
                                <th>Note</th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach items="${smsHistory.content}" var="entry">
                                <tr>
                                    <td class="whitespace-nowrap">${entry.createdAt}</td>
                                    <td>${empty entry.eventType ? '-' : entry.eventType}</td>
                                    <td class="font-semibold text-slate-900">${entry.outcome}</td>
                                    <td>${entry.unitChange}</td>
                                    <td>${empty entry.providerReference ? '-' : entry.providerReference}</td>
                                    <td>${empty entry.note ? '-' : entry.note}</td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty smsHistory.content}">
                                <tr><td colspan="6" class="text-slate-500">No SMS usage has been recorded for this station.</td></tr>
                            </c:if>
                            </tbody>
                        </table>
                        <c:if test="${smsHistory.totalPages gt 1}">
                            <div class="mt-4 flex justify-end gap-2">
                                <c:if test="${not smsHistory.first}"><a class="app-btn btn-neutral" href="/admin/saccos/${saccoDetail.saccoId}?section=sms&stationId=${saccoDetail.selectedStationId}&smsHistoryPage=${smsHistory.number - 1}">Previous</a></c:if>
                                <c:if test="${not smsHistory.last}"><a class="app-btn btn-neutral" href="/admin/saccos/${saccoDetail.saccoId}?section=sms&stationId=${saccoDetail.selectedStationId}&smsHistoryPage=${smsHistory.number + 1}">Next</a></c:if>
                            </div>
                        </c:if>
                    </div>
                </section>
            </c:when>
            <c:otherwise>
                <section class="erp-panel mt-4">
                    <div class="erp-panel-header">
                        <div>
                            <p class="erp-panel-title">Station SMS Balances</p>
                            <p class="mt-1 text-sm text-slate-500">Select a station above to view its usage history.</p>
                        </div>
                        <a href="/admin/sms-usage?saccoId=${saccoDetail.saccoId}" class="app-btn btn-neutral">Manage SMS Units</a>
                    </div>
                    <div class="erp-panel-body overflow-x-auto">
                        <table class="erp-table min-w-[680px]">
                            <thead>
                            <tr>
                                <th>Station</th>
                                <th>Available Units</th>
                                <th>Alert Reserve</th>
                                <th>Warning Baseline</th>
                                <th>Status</th>
                                <th></th>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach items="${smsAccounts.content}" var="account">
                                <tr>
                                    <td class="font-semibold text-slate-900">${account.stationId}</td>
                                    <td><fmt:formatNumber value="${account.availableUnits}" /></td>
                                    <td><fmt:formatNumber value="${account.alertReservedUnits}" /> / 3</td>
                                    <td><fmt:formatNumber value="${account.warningBaseline}" /></td>
                                    <td><span class="rounded-md border px-2 py-1 text-xs font-bold ${account.status eq 'DEPLETED' ? 'border-rose-200 bg-rose-50 text-rose-700' : account.status eq 'CRITICAL' ? 'border-orange-200 bg-orange-50 text-orange-700' : account.status eq 'LOW' ? 'border-amber-200 bg-amber-50 text-amber-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}">${account.status}</span></td>
                                    <td class="text-right"><a href="/admin/saccos/${saccoDetail.saccoId}?section=sms&stationId=${account.stationId}" class="app-btn btn-neutral">View Details</a></td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty smsAccounts.content}">
                                <tr><td colspan="6" class="text-slate-500">No station SMS accounts were found for this SACCO.</td></tr>
                            </c:if>
                            </tbody>
                        </table>
                        <c:if test="${smsAccounts.totalPages gt 1}">
                            <div class="mt-4 flex justify-end gap-2">
                                <c:if test="${not smsAccounts.first}"><a class="app-btn btn-neutral" href="/admin/saccos/${saccoDetail.saccoId}?section=sms&smsPage=${smsAccounts.number - 1}">Previous</a></c:if>
                                <c:if test="${not smsAccounts.last}"><a class="app-btn btn-neutral" href="/admin/saccos/${saccoDetail.saccoId}?section=sms&smsPage=${smsAccounts.number + 1}">Next</a></c:if>
                            </div>
                        </c:if>
                    </div>
                </section>
            </c:otherwise>
        </c:choose>
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

<%@ include file="../fragments/confirm-modal.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
