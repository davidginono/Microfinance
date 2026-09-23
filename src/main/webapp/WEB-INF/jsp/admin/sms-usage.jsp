<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">Admin Tools / SMS Usage</p>
    <h1 class="erp-page-title">SMS Usage</h1>
</div>

<c:url var="smsRefreshUrl" value="/admin/sms-usage">
    <c:param name="saccoId" value="${selectedSaccoId}" />
    <c:param name="stationId" value="${selectedStationId}" />
    <c:param name="status" value="${selectedSmsStatus}" />
    <c:param name="page" value="${superAdmin ? accounts.number : 0}" />
    <c:param name="loanUsagePage" value="${loanSmsUsage.number}" />
    <c:param name="fromDate" value="${selectedLoanFromDate}" />
    <c:param name="toDate" value="${selectedLoanToDate}" />
    <c:param name="loanStatus" value="${selectedLoanStatus}" />
    <c:forEach items="${selectedApplicantIds}" var="applicantId"><c:param name="applicantIds" value="${applicantId}" /></c:forEach>
</c:url>

<c:if test="${superAdmin}">
    <section class="grid gap-3 xl:grid-cols-2" aria-label="SMS usage controls">
        <div class="erp-panel">
            <div class="erp-panel-header"><p class="erp-panel-title">Add SMS Units</p></div>
            <form method="post" action="/admin/sms-usage/allocations" class="erp-panel-body relative flex flex-row items-end gap-2 flex-wrap">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <label class="min-w-[12rem] flex-[1_1_14rem] text-xs font-semibold text-slate-700">
                    SACCO
                    <select id="smsAllocationSacco" name="saccoId" required class="fcms-control mt-1 w-full">
                        <option value="">Select SACCO</option>
                        <c:forEach items="${registeredSaccos}" var="sacco"><option value="${sacco.saccoId}" ${selectedSaccoId eq sacco.saccoId ? 'selected' : ''}>${sacco.saccoName}</option></c:forEach>
                    </select>
                </label>
                <label class="min-w-[10rem] flex-[1_1_12rem] text-xs font-semibold text-slate-700">
                    Station
                    <select id="smsAllocationStation" name="stationId" data-selected-station="${selectedStationId}" required class="fcms-control mt-1 w-full">
                        <option value="">Select a SACCO first</option>
                    </select>
                </label>
                <label class="min-w-[8rem] flex-[0_1_9rem] text-xs font-semibold text-slate-700">
                    Units
                    <input type="number" name="units" min="1" required class="fcms-control mt-1 w-full" />
                </label>
                <label class="min-w-[14rem] flex-[1_1_18rem] text-xs font-semibold text-slate-700">
                    Allocation Note
                    <input name="note" maxlength="500" required class="fcms-control mt-1 w-full" />
                </label>
                <button type="submit" class="app-btn btn-launch shrink-0" data-aws-action-pin="true">Add Units</button>
            </form>
        </div>

        <div class="erp-panel">
            <div class="erp-panel-header"><p class="erp-panel-title">Warning Thresholds</p></div>
            <form method="post" action="/admin/sms-usage/thresholds" class="erp-panel-body relative flex flex-row items-end gap-2 flex-wrap">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <label class="min-w-[10rem] flex-[1_1_12rem] text-xs font-semibold text-slate-700">
                    Low Balance %
                    <input type="number" name="lowPercent" min="1" max="99" value="${smsSettings.lowPercent}" required class="fcms-control mt-1 w-full" />
                </label>
                <label class="min-w-[10rem] flex-[1_1_12rem] text-xs font-semibold text-slate-700">
                    Critical %
                    <input type="number" name="criticalPercent" min="1" max="98" value="${smsSettings.criticalPercent}" required class="fcms-control mt-1 w-full" />
                </label>
                <button type="submit" class="app-btn btn-primary shrink-0" data-aws-action-pin="true">Save Thresholds</button>
            </form>
        </div>
    </section>

    <c:set var="accountTotal" value="${accounts.totalElements}" />
    <c:set var="accountStart" value="${accountTotal == 0 ? 0 : (accounts.number * accounts.size) + 1}" />
    <c:set var="accountEnd" value="${accountTotal == 0 ? 0 : (accounts.number * accounts.size) + accounts.numberOfElements}" />
    <c:url var="accountPreviousUrl" value="/admin/sms-usage">
        <c:param name="saccoId" value="${selectedSaccoId}" /><c:param name="stationId" value="${selectedStationId}" /><c:param name="status" value="${selectedSmsStatus}" />
        <c:param name="page" value="${accounts.number - 1}" /><c:param name="loanUsagePage" value="${loanSmsUsage.number}" />
        <c:param name="fromDate" value="${selectedLoanFromDate}" /><c:param name="toDate" value="${selectedLoanToDate}" /><c:param name="loanStatus" value="${selectedLoanStatus}" /><c:forEach items="${selectedApplicantIds}" var="applicantId"><c:param name="applicantIds" value="${applicantId}" /></c:forEach>
    </c:url>
    <c:url var="accountNextUrl" value="/admin/sms-usage">
        <c:param name="saccoId" value="${selectedSaccoId}" /><c:param name="stationId" value="${selectedStationId}" /><c:param name="status" value="${selectedSmsStatus}" />
        <c:param name="page" value="${accounts.number + 1}" /><c:param name="loanUsagePage" value="${loanSmsUsage.number}" />
        <c:param name="fromDate" value="${selectedLoanFromDate}" /><c:param name="toDate" value="${selectedLoanToDate}" /><c:param name="loanStatus" value="${selectedLoanStatus}" /><c:forEach items="${selectedApplicantIds}" var="applicantId"><c:param name="applicantIds" value="${applicantId}" /></c:forEach>
    </c:url>

    <section class="erp-table-wrap mt-3" aria-label="Station SMS balances">
        <div class="app-table-titlebar">
            <div class="app-table-heading"><h2>Station SMS Balances</h2><span>Showing ${accountStart}-${accountEnd} of ${accountTotal}</span></div>
            <div class="app-table-toolbar">
                <a class="app-icon-button" href="${smsRefreshUrl}" aria-label="Refresh station SMS balances">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M20 11a8 8 0 1 0 2 5.3"/><path d="M20 4v7h-7"/></svg>
                </a>
                <span class="admin-register-count">Page ${accounts.number + 1} of ${accounts.totalPages gt 0 ? accounts.totalPages : 1}</span>
                <c:choose><c:when test="${accounts.first}"><span class="app-icon-button opacity-50" aria-disabled="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg></span></c:when><c:otherwise><a class="app-icon-button aws-pagination-chevron" href="${accountPreviousUrl}" aria-label="Previous station SMS balances page"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg></a></c:otherwise></c:choose>
                <c:choose><c:when test="${accounts.last}"><span class="app-icon-button opacity-50" aria-disabled="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg></span></c:when><c:otherwise><a class="app-icon-button aws-pagination-chevron" href="${accountNextUrl}" aria-label="Next station SMS balances page"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg></a></c:otherwise></c:choose>
            </div>
        </div>
        <form method="get" action="/admin/sms-usage" class="aws-filter-toolbar" data-aws-filter-toolbar>
            <label class="min-w-[12rem] flex-[1_1_14rem]"><span class="sr-only">Filter by SACCO</span><select id="smsBalanceSacco" name="saccoId" class="fcms-control w-full"><option value="">All SACCOs</option><c:forEach items="${registeredSaccos}" var="sacco"><option value="${sacco.saccoId}" ${selectedSaccoId eq sacco.saccoId ? 'selected' : ''}>${sacco.saccoName}</option></c:forEach></select></label>
            <label class="min-w-[10rem] flex-[1_1_12rem]"><span class="sr-only">Filter by station</span><select id="smsBalanceStation" name="stationId" data-selected-station="${selectedStationId}" class="fcms-control w-full"><option value="">Select a SACCO first</option></select></label>
            <label class="min-w-[10rem] flex-[0_1_12rem]"><span class="sr-only">Filter by status</span><select name="status" class="fcms-control w-full"><option value="">All statuses</option><c:forEach items="${smsStatuses}" var="item"><option value="${item}" ${selectedSmsStatus eq item.toString() ? 'selected' : ''}>${item}</option></c:forEach></select></label>
            <button type="submit" class="app-btn btn-primary shrink-0">Apply</button>
            <a href="/admin/sms-usage" class="app-btn btn-neutral shrink-0">Reset</a>
        </form>
        <div class="erp-table-scroll" data-aws-table-region data-loading-label="Loading station SMS balances...">
            <table class="erp-table">
                <thead><tr><th>SACCO</th><th>Station</th><th>Available</th><th>Alert Reserve</th><th>Baseline</th><th>Depleted Alerts</th><th>Status</th></tr></thead>
                <tbody>
                <c:forEach items="${accounts.content}" var="account">
                    <tr>
                        <td class="font-semibold text-sacco-ink"><c:out value="${account.saccoId}" /></td>
                        <td class="whitespace-nowrap"><c:out value="${account.stationId}" /></td>
                        <td class="whitespace-nowrap"><fmt:formatNumber value="${account.availableUnits}" /></td>
                        <td class="whitespace-nowrap"><fmt:formatNumber value="${account.alertReservedUnits}" /> / 3</td>
                        <td class="whitespace-nowrap"><fmt:formatNumber value="${account.warningBaseline}" /></td>
                        <td class="whitespace-nowrap"><fmt:formatNumber value="${account.depletedAlertSmsSentCount}" /></td>
                        <td><span class="rounded-sm border px-2 py-1 text-xs font-bold ${account.status eq 'DEPLETED' ? 'border-rose-200 bg-rose-50 text-rose-700' : account.status eq 'CRITICAL' ? 'border-orange-200 bg-orange-50 text-orange-700' : account.status eq 'LOW' ? 'border-amber-200 bg-amber-50 text-amber-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}">${account.status}</span></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty accounts.content}"><tr><td colspan="7" class="text-center text-slate-500">No station SMS accounts match the filters.</td></tr></c:if>
                </tbody>
            </table>
        </div>
    </section>

    <div id="smsStationTemplate" hidden>
        <c:forEach items="${registeredSaccos}" var="sacco"><c:forEach items="${sacco.stationIds}" var="station"><span data-station-option data-sacco="${sacco.saccoId}" data-station="${station}"></span></c:forEach></c:forEach>
    </div>
</c:if>

<c:if test="${not superAdmin and not empty selectedAccount}">
    <section class="erp-stat-grid">
        <div class="erp-stat-card erp-stat-blue"><div class="erp-stat-main"><div><p class="erp-stat-label">Available Units</p><p class="erp-stat-value">${selectedAccount.availableUnits}</p><p class="erp-stat-meta">${selectedAccount.saccoId} / ${selectedAccount.stationId}</p></div><span class="erp-stat-icon">U</span></div></div>
        <div class="erp-stat-card erp-stat-amber"><div class="erp-stat-main"><div><p class="erp-stat-label">Alert Reserve</p><p class="erp-stat-value">${selectedAccount.alertReservedUnits} / 3</p></div><span class="erp-stat-icon">A</span></div></div>
        <div class="erp-stat-card erp-stat-blue"><div class="erp-stat-main"><div><p class="erp-stat-label">Depleted Alerts Sent</p><p class="erp-stat-value">${selectedAccount.depletedAlertSmsSentCount}</p></div><span class="erp-stat-icon">D</span></div></div>
        <div class="erp-stat-card ${selectedAccount.status eq 'DEPLETED' ? 'erp-stat-red' : selectedAccount.status eq 'HEALTHY' ? 'erp-stat-green' : 'erp-stat-amber'}"><div class="erp-stat-main"><div><p class="erp-stat-label">SMS Status</p><p class="erp-stat-value text-2xl">${selectedAccount.status}</p><p class="erp-stat-meta">Baseline: ${selectedAccount.warningBaseline}</p></div><span class="erp-stat-icon">S</span></div></div>
    </section>
</c:if>

<c:if test="${not empty selectedAccount}">
    <section class="erp-panel mt-3">
        <div class="erp-panel-header"><p class="erp-panel-title">OTP Delivery Policy</p></div>
        <div class="erp-panel-body relative flex flex-row items-center justify-between gap-2 flex-wrap">
            <span class="text-sm text-slate-600">${selectedAccount.saccoId} / ${selectedAccount.stationId}</span>
            <strong class="text-sm text-sacco-ink">${selectedOtpDeliveryChannel}</strong>
        </div>
    </section>
</c:if>

<c:url var="loanUsagePreviousUrl" value="/admin/sms-usage">
    <c:param name="saccoId" value="${selectedSaccoId}" /><c:param name="stationId" value="${selectedStationId}" /><c:param name="status" value="${selectedSmsStatus}" /><c:param name="page" value="${superAdmin ? accounts.number : 0}" /><c:param name="loanUsagePage" value="${loanSmsUsage.number - 1}" />
    <c:param name="fromDate" value="${selectedLoanFromDate}" /><c:param name="toDate" value="${selectedLoanToDate}" /><c:param name="loanStatus" value="${selectedLoanStatus}" /><c:forEach items="${selectedApplicantIds}" var="applicantId"><c:param name="applicantIds" value="${applicantId}" /></c:forEach>
</c:url>
<c:url var="loanUsageNextUrl" value="/admin/sms-usage">
    <c:param name="saccoId" value="${selectedSaccoId}" /><c:param name="stationId" value="${selectedStationId}" /><c:param name="status" value="${selectedSmsStatus}" /><c:param name="page" value="${superAdmin ? accounts.number : 0}" /><c:param name="loanUsagePage" value="${loanSmsUsage.number + 1}" />
    <c:param name="fromDate" value="${selectedLoanFromDate}" /><c:param name="toDate" value="${selectedLoanToDate}" /><c:param name="loanStatus" value="${selectedLoanStatus}" /><c:forEach items="${selectedApplicantIds}" var="applicantId"><c:param name="applicantIds" value="${applicantId}" /></c:forEach>
</c:url>

<c:url var="loanUsagePdfUrl" value="/documents/reports/sms-usage.pdf">
    <c:param name="saccoId" value="${selectedSaccoId}" /><c:param name="stationId" value="${selectedStationId}" />
    <c:param name="fromDate" value="${selectedLoanFromDate}" /><c:param name="toDate" value="${selectedLoanToDate}" /><c:param name="loanStatus" value="${selectedLoanStatus}" />
    <c:forEach items="${selectedApplicantIds}" var="applicantId"><c:param name="applicantIds" value="${applicantId}" /></c:forEach>
</c:url>
<c:url var="loanUsageExcelUrl" value="/documents/reports/sms-usage.xlsx">
    <c:param name="saccoId" value="${selectedSaccoId}" /><c:param name="stationId" value="${selectedStationId}" />
    <c:param name="fromDate" value="${selectedLoanFromDate}" /><c:param name="toDate" value="${selectedLoanToDate}" /><c:param name="loanStatus" value="${selectedLoanStatus}" />
    <c:forEach items="${selectedApplicantIds}" var="applicantId"><c:param name="applicantIds" value="${applicantId}" /></c:forEach>
</c:url>

<section class="erp-table-wrap mt-3" aria-label="Loan application SMS usage">
    <div class="app-table-titlebar">
        <div class="app-table-heading">
            <h2>Loan Application SMS Usage</h2>
            <span>${loanSmsUsage.numberOfElements} of ${loanSmsUsage.totalElements}</span>
        </div>
        <div class="app-table-toolbar">
            <a class="app-icon-button" href="${smsRefreshUrl}" aria-label="Refresh loan application SMS usage"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M20 11a8 8 0 1 0 2 5.3"/><path d="M20 4v7h-7"/></svg></a>
            <a class="app-btn btn-neutral" href="${loanUsagePdfUrl}" data-download-action="true">PDF</a>
            <a class="app-btn btn-neutral" href="${loanUsageExcelUrl}" data-download-action="true">Excel</a>
            <span class="admin-register-count">Page ${loanSmsUsage.number + 1} of ${loanSmsUsage.totalPages gt 0 ? loanSmsUsage.totalPages : 1}</span>
            <c:choose><c:when test="${loanSmsUsage.first}"><span class="app-icon-button opacity-50" aria-disabled="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg></span></c:when><c:otherwise><a class="app-icon-button aws-pagination-chevron" href="${loanUsagePreviousUrl}" aria-label="Previous loan application SMS usage page"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m15 18-6-6 6-6"/></svg></a></c:otherwise></c:choose>
            <c:choose><c:when test="${loanSmsUsage.last}"><span class="app-icon-button opacity-50" aria-disabled="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg></span></c:when><c:otherwise><a class="app-icon-button aws-pagination-chevron" href="${loanUsageNextUrl}" aria-label="Next loan application SMS usage page"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m9 18 6-6-6-6"/></svg></a></c:otherwise></c:choose>
        </div>
    </div>
    <form method="get" action="/admin/sms-usage" class="aws-filter-toolbar sms-usage-filter-toolbar" data-aws-filter-toolbar data-sms-applicant-filter>
        <input type="hidden" name="saccoId" value="${selectedSaccoId}" data-sms-filter-sacco />
        <input type="hidden" name="stationId" value="${selectedStationId}" data-sms-filter-station />
        <input type="hidden" name="status" value="${selectedSmsStatus}" />
        <input type="hidden" name="page" value="${superAdmin ? accounts.number : 0}" />
        <input type="hidden" name="loanUsagePage" value="0" />
        <label class="min-w-[9rem] flex-[0_1_10rem] text-xs font-semibold text-slate-700">
            From
            <input type="date" name="fromDate" value="${selectedLoanFromDate}" class="fcms-control mt-1 w-full" />
        </label>
        <label class="min-w-[9rem] flex-[0_1_10rem] text-xs font-semibold text-slate-700">
            To
            <input type="date" name="toDate" value="${selectedLoanToDate}" class="fcms-control mt-1 w-full" />
        </label>
        <label class="min-w-[13rem] flex-[1_1_14rem] text-xs font-semibold text-slate-700">
            Loan Status
            <select name="loanStatus" class="fcms-control mt-1 w-full">
                <option value="">All loan statuses</option>
                <c:forEach items="${loanStatuses}" var="loanStatusOption">
                    <option value="${loanStatusOption.value}" ${selectedLoanStatus eq loanStatusOption.value ? 'selected' : ''}><c:out value="${loanStatusOption.label}" /></option>
                </c:forEach>
            </select>
        </label>
        <div class="sms-applicant-picker min-w-[18rem] flex-[2_1_26rem]" data-search-url="/admin/sms-usage/applicants/search">
            <label class="text-xs font-semibold text-slate-700" for="smsApplicantSearch">Applicants</label>
            <input id="smsApplicantSearch" type="search" autocomplete="off" class="fcms-control mt-1 w-full" placeholder="Search name or member no." data-sms-applicant-search />
            <div class="sms-applicant-results" data-sms-applicant-results hidden></div>
            <div class="sms-applicant-chips" data-sms-applicant-selected>
                <c:forEach items="${selectedApplicantFilters}" var="applicant">
                    <span class="sms-applicant-chip" data-sms-selected-applicant="${applicant.id}">
                        <span><c:out value="${applicant.label}" /></span>
                        <button type="button" aria-label="Remove applicant" data-sms-remove-applicant>&times;</button>
                    </span>
                    <input type="hidden" name="applicantIds" value="${applicant.id}" data-sms-applicant-input />
                </c:forEach>
            </div>
        </div>
        <button type="submit" class="app-btn btn-primary shrink-0">Apply</button>
        <a href="/admin/sms-usage" class="app-btn btn-neutral shrink-0">Reset</a>
    </form>
    <div class="erp-table-scroll" data-aws-table-region data-loading-label="Loading loan application SMS usage...">
        <table class="erp-table">
            <thead><tr><th>Applicant</th><th>Member No.</th><th>Loan ID</th><th>Application No.</th><th>Status</th><th>SACCO</th><th>Station</th><th>SMS Events</th><th>Units Used</th><th>Last SMS</th></tr></thead>
            <tbody>
            <c:forEach items="${loanSmsUsage.content}" var="entry">
                <tr>
                    <td class="font-semibold text-sacco-ink"><c:out value="${entry.applicantName}" /></td>
                    <td class="whitespace-nowrap"><c:out value="${entry.applicantMemberNo}" /></td>
                    <td class="whitespace-nowrap"><c:out value="${entry.loanId}" /></td>
                    <td class="whitespace-nowrap"><c:out value="${empty entry.applicationNumber ? '-' : entry.applicationNumber}" /></td>
                    <td><span class="rounded-sm border border-slate-200 bg-slate-50 px-2 py-1 text-xs font-bold text-slate-700"><c:out value="${entry.loanStatusLabel}" /></span></td>
                    <td class="whitespace-nowrap"><c:out value="${entry.saccoId}" /></td>
                    <td class="whitespace-nowrap"><c:out value="${entry.stationId}" /></td>
                    <td><fmt:formatNumber value="${entry.smsEventCount}" /></td>
                    <td class="font-semibold"><fmt:formatNumber value="${entry.unitsUsed}" /></td>
                    <td class="whitespace-nowrap"><c:out value="${entry.lastSmsAtLabel}" /></td>
                </tr>
            </c:forEach>
            <c:if test="${empty loanSmsUsage.content}"><tr><td colspan="10" class="text-center text-slate-500">No loan application SMS usage has been recorded for this scope.</td></tr></c:if>
            </tbody>
        </table>
    </div>
</section>

<script>
    (function () {
        const form = document.querySelector('[data-sms-applicant-filter]');
        if (!form) {
            return;
        }
        const search = form.querySelector('[data-sms-applicant-search]');
        const results = form.querySelector('[data-sms-applicant-results]');
        const selected = form.querySelector('[data-sms-applicant-selected]');
        const saccoField = form.querySelector('[data-sms-filter-sacco]');
        const stationField = form.querySelector('[data-sms-filter-station]');
        const searchUrl = form.querySelector('[data-search-url]') ? form.querySelector('[data-search-url]').dataset.searchUrl : form.dataset.searchUrl;
        let timer = null;

        function selectedIds() {
            return new Set(Array.from(form.querySelectorAll('[data-sms-applicant-input]')).map((input) => input.value));
        }

        function hideResults() {
            if (results) {
                results.hidden = true;
                results.innerHTML = '';
            }
        }

        function addApplicant(id, label) {
            if (!id || selectedIds().has(id)) {
                return;
            }
            const chip = document.createElement('span');
            chip.className = 'sms-applicant-chip';
            chip.dataset.smsSelectedApplicant = id;

            const text = document.createElement('span');
            text.textContent = label || id;
            chip.appendChild(text);

            const remove = document.createElement('button');
            remove.type = 'button';
            remove.setAttribute('aria-label', 'Remove applicant');
            remove.dataset.smsRemoveApplicant = 'true';
            remove.innerHTML = '&times;';
            chip.appendChild(remove);

            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'applicantIds';
            input.value = id;
            input.dataset.smsApplicantInput = 'true';

            selected.appendChild(chip);
            selected.appendChild(input);
            hideResults();
            search.value = '';
        }

        function removeApplicant(id) {
            form.querySelectorAll('[data-sms-selected-applicant="' + id + '"]').forEach((chip) => chip.remove());
            form.querySelectorAll('[data-sms-applicant-input]').forEach((input) => {
                if (input.value === id) {
                    input.remove();
                }
            });
        }

        selected.addEventListener('click', function (event) {
            const button = event.target.closest('[data-sms-remove-applicant]');
            if (!button) {
                return;
            }
            const chip = button.closest('[data-sms-selected-applicant]');
            if (chip) {
                removeApplicant(chip.dataset.smsSelectedApplicant);
            }
        });

        function renderResults(items) {
            results.innerHTML = '';
            if (!items.length) {
                const empty = document.createElement('div');
                empty.className = 'sms-applicant-result-empty';
                empty.textContent = 'No applicants found';
                results.appendChild(empty);
                results.hidden = false;
                return;
            }
            items.forEach(function (item) {
                if (!item || !item.id || selectedIds().has(item.id)) {
                    return;
                }
                const option = document.createElement('button');
                option.type = 'button';
                option.className = 'sms-applicant-result';
                option.dataset.applicantId = item.id;
                option.dataset.applicantLabel = item.label || item.id;
                const label = document.createElement('span');
                label.textContent = item.label || item.id;
                const scope = document.createElement('small');
                scope.textContent = (item.saccoId || '-') + ' / ' + (item.stationId || '-');
                option.appendChild(label);
                option.appendChild(scope);
                results.appendChild(option);
            });
            results.hidden = results.children.length === 0;
        }

        results.addEventListener('click', function (event) {
            const option = event.target.closest('[data-applicant-id]');
            if (!option) {
                return;
            }
            addApplicant(option.dataset.applicantId, option.dataset.applicantLabel);
        });

        search.addEventListener('input', function () {
            window.clearTimeout(timer);
            const term = search.value.trim();
            if (term.length < 2) {
                hideResults();
                return;
            }
            timer = window.setTimeout(function () {
                const params = new URLSearchParams();
                params.set('q', term);
                if (saccoField && saccoField.value) {
                    params.set('saccoId', saccoField.value);
                }
                if (stationField && stationField.value) {
                    params.set('stationId', stationField.value);
                }
                fetch(searchUrl + '?' + params.toString(), {headers: {'Accept': 'application/json'}})
                    .then((response) => response.ok ? response.json() : [])
                    .then((items) => renderResults(Array.isArray(items) ? items : []))
                    .catch(hideResults);
            }, 220);
        });

        document.addEventListener('click', function (event) {
            if (!form.contains(event.target)) {
                hideResults();
            }
        });
    })();
</script>

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
                    placeholder.textContent = selectedSacco ? (allowAllStations ? 'All stations' : 'Select station') : 'Select a SACCO first';
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
