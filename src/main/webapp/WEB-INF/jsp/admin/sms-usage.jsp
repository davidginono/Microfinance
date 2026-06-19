<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<style>
    .sms-unit-control .neo-select-button,
    .sms-filter-control .neo-select-button {
        height: 3rem;
        min-height: 3rem;
        max-height: 3rem;
        padding-top: 0.5rem;
        padding-bottom: 0.5rem;
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / SMS Usage</p>
    <h1 class="erp-page-title">SMS Usage</h1>
    <p class="erp-page-subtitle">Track prepaid SMS units by SACCO-station.</p>
</div>

<c:choose>
    <c:when test="${superAdmin}">
        <section class="grid gap-4 xl:grid-cols-2">
            <div class="erp-panel">
                <div class="erp-panel-header">
                    <div><p class="erp-panel-title">Add SMS Units</p></div>
                </div>
                <div class="erp-panel-body">
                    <form method="post" action="/admin/sms-usage/allocations" class="grid gap-4 sm:grid-cols-2">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                        <label class="sms-unit-control block text-sm font-semibold text-slate-700">
                            SACCO
                            <select id="smsAllocationSacco" name="saccoId" required class="mt-1 w-full rounded-md border border-slate-300 bg-white px-3" style="height:3rem;min-height:3rem;max-height:3rem;">
                                <option value="">Select SACCO</option>
                                <c:forEach items="${registeredSaccos}" var="sacco">
                                    <option value="${sacco.saccoId}" ${selectedSaccoId eq sacco.saccoId ? 'selected' : ''}>${sacco.saccoName}</option>
                                </c:forEach>
                            </select>
                        </label>
                        <label class="sms-unit-control block text-sm font-semibold text-slate-700">
                            Station ID
                            <select id="smsAllocationStation" name="stationId" data-selected-station="${selectedStationId}" required class="mt-1 w-full rounded-md border border-slate-300 bg-white px-3" style="height:3rem;min-height:3rem;max-height:3rem;">
                                <option value="">Select a SACCO first</option>
                            </select>
                        </label>
                        <label class="block text-sm font-semibold text-slate-700">
                            Units
                            <input type="number" name="units" min="1" required class="mt-1 w-full rounded-md border border-slate-300 px-3" style="height:3rem;min-height:3rem;max-height:3rem;" />
                        </label>
                        <label class="block text-sm font-semibold text-slate-700">
                            Allocation Note
                            <input name="note" maxlength="500" required class="mt-1 w-full rounded-md border border-slate-300 px-3" style="height:3rem;min-height:3rem;max-height:3rem;" />
                        </label>
                        <div class="sm:col-span-2 flex justify-end">
                            <button type="submit" class="app-btn btn-primary" style="height:3rem;min-height:3rem;max-height:3rem;">Add Units</button>
                        </div>
                    </form>
                </div>
            </div>

            <div class="erp-panel">
                <div class="erp-panel-header">
                    <div><p class="erp-panel-title">Warning Thresholds</p></div>
                </div>
                <div class="erp-panel-body">
                    <form method="post" action="/admin/sms-usage/thresholds" class="grid gap-4 sm:grid-cols-2">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                        <label class="block text-sm font-semibold text-slate-700">
                            Low Balance %
                            <input type="number" name="lowPercent" min="1" max="99" value="${smsSettings.lowPercent}" required class="mt-1 h-12 w-full rounded-md border border-slate-300 px-3" />
                        </label>
                        <label class="block text-sm font-semibold text-slate-700">
                            Critical %
                            <input type="number" name="criticalPercent" min="1" max="98" value="${smsSettings.criticalPercent}" required class="mt-1 h-12 w-full rounded-md border border-slate-300 px-3" />
                        </label>
                        <div class="sm:col-span-2 flex justify-end">
                            <button type="submit" class="app-btn btn-primary h-12">Save Thresholds</button>
                        </div>
                    </form>
                </div>
            </div>
        </section>

        <section class="erp-panel mt-5">
            <div class="erp-panel-header"><div><p class="erp-panel-title">Station SMS Balances</p></div></div>
            <div class="erp-panel-body">
                <form method="get" action="/admin/sms-usage" class="relative mb-4 flex flex-row items-center gap-2 flex-wrap">
                    <div class="sms-filter-control min-w-[12rem] flex-1">
                        <select id="smsBalanceSacco" name="saccoId" class="w-full rounded-md border border-slate-300 bg-white px-3" style="height:3rem;min-height:3rem;max-height:3rem;">
                            <option value="">All SACCOs</option>
                            <c:forEach items="${registeredSaccos}" var="sacco">
                                <option value="${sacco.saccoId}" ${selectedSaccoId eq sacco.saccoId ? 'selected' : ''}>${sacco.saccoName}</option>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="sms-filter-control min-w-[10rem] flex-1">
                        <select id="smsBalanceStation" name="stationId" data-selected-station="${selectedStationId}" class="w-full rounded-md border border-slate-300 bg-white px-3" style="height:3rem;min-height:3rem;max-height:3rem;">
                            <option value="">Select a SACCO first</option>
                        </select>
                    </div>
                    <div class="sms-filter-control min-w-[10rem] flex-1">
                        <select name="status" class="w-full rounded-md border border-slate-300 bg-white px-3" style="height:3rem;min-height:3rem;max-height:3rem;">
                            <option value="">All statuses</option>
                            <c:forEach items="${smsStatuses}" var="item">
                                <option value="${item}" ${selectedSmsStatus eq item.toString() ? 'selected' : ''}>${item}</option>
                            </c:forEach>
                        </select>
                    </div>
                    <button type="submit" class="app-btn btn-neutral min-w-[9rem] justify-center" style="height:3rem;min-height:3rem;max-height:3rem;">Apply Filters</button>
                </form>
                <div class="erp-table-wrap erp-table-scroll">
                    <table class="min-w-full divide-y divide-slate-200 text-sm">
                        <thead class="bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500">
                        <tr><th class="px-4 py-3">SACCO</th><th class="px-4 py-3">Station</th><th class="px-4 py-3">Available</th><th class="px-4 py-3">Alert Reserve</th><th class="px-4 py-3">Baseline</th><th class="px-4 py-3">Depleted Alerts Sent</th><th class="px-4 py-3">Status</th><th class="px-4 py-3"></th></tr>
                        </thead>
                        <tbody class="divide-y divide-slate-100 bg-white">
                        <c:forEach items="${accounts.content}" var="account">
                            <tr>
                                <td class="px-4 py-3 font-semibold text-slate-900">${account.saccoId}</td>
                                <td class="px-4 py-3">${account.stationId}</td>
                                <td class="px-4 py-3"><fmt:formatNumber value="${account.availableUnits}" /></td>
                                <td class="px-4 py-3"><fmt:formatNumber value="${account.alertReservedUnits}" /> / 3</td>
                                <td class="px-4 py-3"><fmt:formatNumber value="${account.warningBaseline}" /></td>
                                <td class="px-4 py-3"><fmt:formatNumber value="${account.depletedAlertSmsSentCount}" /></td>
                                <td class="px-4 py-3"><span class="rounded-md border px-2 py-1 text-xs font-bold ${account.status eq 'DEPLETED' ? 'border-rose-200 bg-rose-50 text-rose-700' : account.status eq 'CRITICAL' ? 'border-orange-200 bg-orange-50 text-orange-700' : account.status eq 'LOW' ? 'border-amber-200 bg-amber-50 text-amber-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}">${account.status}</span></td>
                                <td class="px-4 py-3 text-right"><a class="app-btn btn-neutral" href="/admin/sms-usage?accountId=${account.id}">View History</a></td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty accounts.content}"><tr><td colspan="8" class="px-4 py-8 text-center text-slate-500">No station SMS accounts match the filters.</td></tr></c:if>
                        </tbody>
                    </table>
                </div>
                <c:if test="${accounts.totalPages gt 1}">
                    <div class="mt-4 flex justify-end gap-2">
                        <c:if test="${not accounts.first}"><a class="app-btn btn-neutral" href="/admin/sms-usage?page=${accounts.number - 1}&saccoId=${selectedSaccoId}&stationId=${selectedStationId}&status=${selectedSmsStatus}">Previous</a></c:if>
                        <c:if test="${not accounts.last}"><a class="app-btn btn-neutral" href="/admin/sms-usage?page=${accounts.number + 1}&saccoId=${selectedSaccoId}&stationId=${selectedStationId}&status=${selectedSmsStatus}">Next</a></c:if>
                    </div>
                </c:if>
            </div>
        </section>

        <div id="smsStationTemplate" hidden>
            <c:forEach items="${registeredSaccos}" var="sacco">
                <c:forEach items="${sacco.stationIds}" var="station">
                    <span data-station-option data-sacco="${sacco.saccoId}" data-station="${station}"></span>
                </c:forEach>
            </c:forEach>
        </div>
    </c:when>
    <c:otherwise>
        <section class="erp-stat-grid">
            <div class="erp-stat-card erp-stat-blue"><div class="erp-stat-main"><div><p class="erp-stat-label">Available Units</p><p class="erp-stat-value">${selectedAccount.availableUnits}</p><p class="erp-stat-meta">${selectedAccount.saccoId} / ${selectedAccount.stationId}</p></div><span class="erp-stat-icon">U</span></div></div>
            <div class="erp-stat-card erp-stat-amber"><div class="erp-stat-main"><div><p class="erp-stat-label">Alert Reserve</p><p class="erp-stat-value">${selectedAccount.alertReservedUnits} / 3</p><p class="erp-stat-meta">Reserved for LOW, CRITICAL, and DEPLETED alerts</p></div><span class="erp-stat-icon">A</span></div></div>
            <div class="erp-stat-card erp-stat-blue"><div class="erp-stat-main"><div><p class="erp-stat-label">Depleted Alerts Sent</p><p class="erp-stat-value">${selectedAccount.depletedAlertSmsSentCount}</p><p class="erp-stat-meta">SMS alerts sent to verified Minor Admins</p></div><span class="erp-stat-icon">D</span></div></div>
            <div class="erp-stat-card ${selectedAccount.status eq 'DEPLETED' ? 'erp-stat-red' : selectedAccount.status eq 'HEALTHY' ? 'erp-stat-green' : 'erp-stat-amber'}"><div class="erp-stat-main"><div><p class="erp-stat-label">SMS Status</p><p class="erp-stat-value text-2xl">${selectedAccount.status}</p><p class="erp-stat-meta">Warning baseline: ${selectedAccount.warningBaseline}</p></div><span class="erp-stat-icon">S</span></div></div>
        </section>
    </c:otherwise>
</c:choose>

<c:if test="${not empty selectedAccount}">
    <section class="erp-panel mt-5">
        <div class="erp-panel-header">
            <div>
                <p class="erp-panel-title">OTP Delivery Policy</p>
                <p class="mt-1 text-sm text-slate-500">${selectedAccount.saccoId} / ${selectedAccount.stationId}</p>
            </div>
        </div>
        <div class="erp-panel-body">
            <p class="font-semibold text-slate-900">${selectedOtpDeliveryChannel}</p>
            <p class="mt-1 text-sm text-slate-500">Minor Admins configure this policy in Settings & Controls. OTP SMS uses available units, not alert-reserve units.</p>
        </div>
    </section>
</c:if>

<section class="erp-panel mt-5">
    <div class="erp-panel-header">
        <div>
            <p class="erp-panel-title">Usage History<c:if test="${not empty selectedAccount}">: ${selectedAccount.saccoId} / ${selectedAccount.stationId}</c:if></p>
            <p class="mt-1 text-sm text-slate-500">Showing ${usageHistory.numberOfElements} of ${usageHistory.totalElements} history rows. Repeated depleted blocks are grouped by day.</p>
        </div>
    </div>
    <div class="erp-panel-body">
        <div class="erp-table-wrap erp-table-scroll">
        <table class="min-w-full divide-y divide-slate-200 text-sm">
            <thead class="sticky top-0 z-10 bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500"><tr><th class="px-4 py-3">First Seen</th><th class="px-4 py-3">Last Seen</th><th class="px-4 py-3">Event</th><th class="px-4 py-3">Outcome</th><th class="px-4 py-3">Count</th><th class="px-4 py-3">Unit Change</th><th class="px-4 py-3">Provider Reference</th><th class="px-4 py-3">Note</th></tr></thead>
            <tbody class="divide-y divide-slate-100 bg-white">
            <c:forEach items="${usageHistory.content}" var="entry">
                <tr><td class="whitespace-nowrap px-4 py-3">${entry.createdAt}</td><td class="whitespace-nowrap px-4 py-3">${empty entry.lastOccurredAt ? entry.createdAt : entry.lastOccurredAt}</td><td class="px-4 py-3">${empty entry.eventType ? '-' : entry.eventType}</td><td class="px-4 py-3 font-semibold">${entry.outcome}</td><td class="px-4 py-3"><fmt:formatNumber value="${entry.eventCount}" /></td><td class="px-4 py-3">${entry.unitChange}</td><td class="px-4 py-3">${empty entry.providerReference ? '-' : entry.providerReference}</td><td class="px-4 py-3">${empty entry.note ? '-' : entry.note}</td></tr>
            </c:forEach>
            <c:if test="${empty usageHistory.content}"><tr><td colspan="8" class="px-4 py-8 text-center text-slate-500">No SMS usage has been recorded for this station.</td></tr></c:if>
            </tbody>
        </table>
        </div>
        <c:if test="${usageHistory.totalPages gt 1 and not empty selectedAccount}">
            <div class="mt-4 flex justify-end gap-2">
                <span class="inline-flex items-center px-2 text-sm text-slate-500">Page ${usageHistory.number + 1} of ${usageHistory.totalPages}</span>
                <c:if test="${not usageHistory.first}"><a class="app-btn btn-neutral" href="/admin/sms-usage?accountId=${selectedAccount.id}&historyPage=${usageHistory.number - 1}">Previous</a></c:if>
                <c:if test="${not usageHistory.last}"><a class="app-btn btn-neutral" href="/admin/sms-usage?accountId=${selectedAccount.id}&historyPage=${usageHistory.number + 1}">Next</a></c:if>
            </div>
        </c:if>
    </div>
</section>

<c:if test="${superAdmin}">
    <script>
        (function () {
            const stationTemplate = document.getElementById('smsStationTemplate');
            if (!stationTemplate) {
                return;
            }

            function bindStationSelect(saccoSelectId, stationSelectId, allowAllStations) {
                const saccoSelect = document.getElementById(saccoSelectId);
                const stationSelect = document.getElementById(stationSelectId);
                if (!saccoSelect || !stationSelect) {
                    return;
                }

                function refreshStations() {
                    const selectedSacco = saccoSelect.value;
                    const selectedStation = stationSelect.dataset.selectedStation || '';
                    stationSelect.innerHTML = '';

                    const placeholder = document.createElement('option');
                    placeholder.value = '';
                    placeholder.textContent = selectedSacco
                        ? (allowAllStations ? 'All stations' : 'Select station')
                        : 'Select a SACCO first';
                    stationSelect.appendChild(placeholder);

                    stationTemplate.querySelectorAll('[data-station-option]').forEach(function (option) {
                        if (option.dataset.sacco !== selectedSacco) {
                            return;
                        }
                        const next = document.createElement('option');
                        next.value = option.dataset.station;
                        next.textContent = option.dataset.station;
                        next.selected = option.dataset.station === selectedStation;
                        stationSelect.appendChild(next);
                    });
                    stationSelect.disabled = !selectedSacco;
                    stationSelect.dispatchEvent(new Event('change', { bubbles: true }));
                }

                saccoSelect.addEventListener('change', function () {
                    stationSelect.dataset.selectedStation = '';
                    refreshStations();
                });
                refreshStations();
            }

            bindStationSelect('smsAllocationSacco', 'smsAllocationStation', false);
            bindStationSelect('smsBalanceSacco', 'smsBalanceStation', true);
        })();
    </script>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
