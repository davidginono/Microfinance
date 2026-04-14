<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Event Log</p>
    <h1 class="erp-page-title">Event Log</h1>
    <p class="erp-page-subtitle">Review the recorded actions taken across the system for visibility, troubleshooting, and operational follow-up.</p>
</div>
<div class="mb-3">
    <a href="/admin/dashboard" class="app-btn btn-neutral">Back To Dashboard</a>
</div>
<div class="erp-panel mb-4 overflow-hidden">
    <div class="erp-panel-header">
        <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
                <p class="erp-panel-title">Filter And View Options</p>
                <p class="mt-1 text-sm text-slate-500">Trim the audit trail to the period you want, then page through the matching system activity.</p>
            </div>
            <div class="inline-flex flex-wrap items-center gap-2 rounded-md border border-sky-100 bg-sky-50 px-3 py-2 text-sm text-slate-600">
                <span class="rounded border border-sky-200 bg-white px-2 py-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-sky-700">Current Slice</span>
                <span>
                    Showing
                    <span class="font-semibold text-slate-800">
                        <c:choose>
                            <c:when test="${entriesPage.totalElements eq 0}">0</c:when>
                            <c:otherwise>${entriesPage.number * entriesPage.size + 1}-${entriesPage.number * entriesPage.size + fn:length(entries)}</c:otherwise>
                        </c:choose>
                    </span>
                    of <span class="font-semibold text-slate-800">${entriesPage.totalElements}</span> audit events
                </span>
            </div>
        </div>
    </div>
    <form action="/admin/events" method="get" class="erp-panel-body grid gap-4 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,1.15fr)_minmax(0,1fr)_minmax(0,0.9fr)_auto] xl:items-end">
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
            Actor / User ID
            <input type="text" name="actorId" value="${selectedActorId}" placeholder="Search actor UUID or prefix" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
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
            <a href="/admin/events" class="app-btn btn-neutral">Reset</a>
            <button type="submit" class="app-btn btn-primary">Apply Filters</button>
        </div>
    </form>
</div>
<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr>
            <th>Action</th>
            <th>Entity</th>
            <th>Reference</th>
            <th>Actor</th>
            <th>Date</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${entries}" var="entry">
            <tr>
                <td class="px-3 py-2 font-semibold text-slate-900">${entry.displayAction}</td>
                <td class="px-3 py-2">${entry.displayEntityType}</td>
                <td class="px-3 py-2">${entry.shortEntityReference}</td>
                <td class="px-3 py-2">${entry.actorReferenceLabel}</td>
                <td class="px-3 py-2 whitespace-nowrap">${entry.createdAtLabel}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty entries}">
            <tr><td colspan="5" class="px-3 py-3 text-slate-500">No events recorded yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>
<c:if test="${entriesPage.totalPages gt 1}">
    <div class="mt-4 flex flex-col gap-3 rounded border border-slate-200 bg-white px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <p class="text-sm text-slate-600">Page <span class="font-semibold text-slate-800">${entriesPage.number + 1}</span> of <span class="font-semibold text-slate-800">${entriesPage.totalPages}</span></p>
        <div class="flex flex-wrap items-center gap-2">
            <c:choose>
                <c:when test="${entriesPage.first}">
                    <span class="app-btn btn-neutral opacity-50">Previous</span>
                </c:when>
                <c:otherwise>
                    <a href="/admin/events?page=${entriesPage.number - 1}${eventsPaginationQuery}" class="app-btn btn-neutral">Previous</a>
                </c:otherwise>
            </c:choose>
            <c:choose>
                <c:when test="${entriesPage.last}">
                    <span class="app-btn btn-neutral opacity-50">Next</span>
                </c:when>
                <c:otherwise>
                    <a href="/admin/events?page=${entriesPage.number + 1}${eventsPaginationQuery}" class="app-btn btn-primary">Next</a>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
