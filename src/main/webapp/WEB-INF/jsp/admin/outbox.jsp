<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Outbox Monitor</p>
    <h1 class="erp-page-title">Outbox Monitor</h1>
    <p class="erp-page-subtitle">Monitor event publication, identify failures, and retry the items that need operational recovery.</p>
</div>
<div class="erp-panel mb-4 overflow-hidden">
    <div class="erp-panel-header">
        <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
                <p class="erp-panel-title">Filter And View Options</p>
                <p class="mt-1 text-sm text-slate-500">Focus on the period and status you need before paging through outbox activity.</p>
            </div>
            <div class="inline-flex flex-wrap items-center gap-2 rounded-md border border-sky-100 bg-sky-50 px-3 py-2 text-sm text-slate-600">
                <span class="rounded border border-sky-200 bg-white px-2 py-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-sky-700">Current Slice</span>
                <span>
                    Showing
                    <span class="font-semibold text-slate-800">
                        <c:choose>
                            <c:when test="${eventsPage.totalElements eq 0}">0</c:when>
                            <c:otherwise>${eventsPage.number * eventsPage.size + 1}-${eventsPage.number * eventsPage.size + fn:length(events)}</c:otherwise>
                        </c:choose>
                    </span>
                    of <span class="font-semibold text-slate-800">${eventsPage.totalElements}</span> outbox events
                </span>
            </div>
        </div>
    </div>
    <form action="/admin/outbox" method="get" class="erp-panel-body grid gap-4 xl:grid-cols-[minmax(0,1.1fr)_minmax(0,1.1fr)_minmax(0,1fr)_minmax(0,1fr)_minmax(0,0.9fr)_auto] xl:items-end">
        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Filter From
            <input type="date" name="dateFrom" value="${selectedDateFrom}" class="mt-1 w-full rounded border px-3 py-2.5 text-sm text-slate-800 ${not empty dateFromError ? 'border-rose-300 bg-rose-50' : 'border-slate-300 bg-white'}" />
            <c:if test="${not empty dateFromError}">
                <span class="mt-1 block text-[0.78rem] font-normal normal-case tracking-normal text-rose-600">${dateFromError}</span>
            </c:if>
        </label>
        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Filter To
            <input type="date" name="dateTo" value="${selectedDateTo}" class="mt-1 w-full rounded border px-3 py-2.5 text-sm text-slate-800 ${not empty dateToError ? 'border-rose-300 bg-rose-50' : 'border-slate-300 bg-white'}" />
            <c:if test="${not empty dateToError}">
                <span class="mt-1 block text-[0.78rem] font-normal normal-case tracking-normal text-rose-600">${dateToError}</span>
            </c:if>
        </label>
        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Status
            <select name="status" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800">
                <option value="" ${empty selectedOutboxStatus ? 'selected' : ''}>All statuses</option>
                <c:forEach items="${outboxStatuses}" var="outboxStatus">
                    <option value="${outboxStatus}" ${selectedOutboxStatus eq outboxStatus.name() ? 'selected' : ''}>${outboxStatus}</option>
                </c:forEach>
            </select>
        </label>
        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Loan ID
            <input type="text" name="loanId" value="${selectedLoanId}" placeholder="Search loan UUID or prefix" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
        </label>
        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
            Rows Per Page
            <select name="size" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800">
                <option value="25" ${selectedPageSize eq 25 ? 'selected' : ''}>25 rows</option>
                <option value="50" ${selectedPageSize eq 50 ? 'selected' : ''}>50 rows</option>
                <option value="100" ${selectedPageSize eq 100 ? 'selected' : ''}>100 rows</option>
            </select>
        </label>
        <div class="flex flex-wrap items-center gap-2 xl:justify-end">
            <a href="/admin/outbox" class="app-btn btn-neutral">Reset</a>
            <button type="submit" class="app-btn btn-primary">Apply Filters</button>
        </div>
    </form>
</div>
<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr><th>Event Type</th><th>Aggregate</th><th>Status</th><th>Created</th><th>Action</th></tr>
        </thead>
        <tbody>
        <c:forEach items="${events}" var="event">
            <tr>
                <td class="px-3 py-2 font-semibold text-slate-900">${event.eventType}</td>
                <td class="px-3 py-2">${event.aggregateType}<div class="text-xs text-slate-500">${event.aggregateId}</div></td>
                <td class="px-3 py-2">${event.status}</td>
                <td class="px-3 py-2">${event.createdAt}</td>
                <td class="px-3 py-2">
                    <c:if test="${event.status eq 'FAILED'}">
                        <form action="/admin/outbox/${event.id}/retry" method="post">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                            <input type="hidden" name="status" value="${selectedOutboxStatus}" />
                            <input type="hidden" name="dateFrom" value="${selectedDateFrom}" />
                            <input type="hidden" name="dateTo" value="${selectedDateTo}" />
                            <input type="hidden" name="loanId" value="${selectedLoanId}" />
                            <input type="hidden" name="size" value="${selectedPageSize}" />
                            <input type="hidden" name="page" value="${eventsPage.number}" />
                            <button type="submit" class="app-btn btn-primary">Retry</button>
                        </form>
                    </c:if>
                    <c:if test="${event.status ne 'FAILED'}">-</c:if>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty events}">
            <tr><td colspan="5" class="px-3 py-3 text-slate-500">No outbox events found for this view.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>
<c:if test="${eventsPage.totalPages gt 1}">
    <div class="mt-4 flex flex-col gap-3 rounded border border-slate-200 bg-white px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <p class="text-sm text-slate-600">Page <span class="font-semibold text-slate-800">${eventsPage.number + 1}</span> of <span class="font-semibold text-slate-800">${eventsPage.totalPages}</span></p>
        <div class="flex flex-wrap items-center gap-2">
            <c:choose>
                <c:when test="${eventsPage.first}">
                    <span class="app-btn btn-neutral opacity-50">Previous</span>
                </c:when>
                <c:otherwise>
                    <a href="/admin/outbox?page=${eventsPage.number - 1}${outboxPaginationQuery}" class="app-btn btn-neutral">Previous</a>
                </c:otherwise>
            </c:choose>
            <c:choose>
                <c:when test="${eventsPage.last}">
                    <span class="app-btn btn-neutral opacity-50">Next</span>
                </c:when>
                <c:otherwise>
                    <a href="/admin/outbox?page=${eventsPage.number + 1}${outboxPaginationQuery}" class="app-btn btn-primary">Next</a>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
