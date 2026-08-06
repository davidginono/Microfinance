<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.outbox.breadcrumb" text="Admin Tools / Outbox Monitor" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.outbox.title" text="Outbox Monitor" /></h1>
    <p class="erp-page-subtitle"><spring:message code="admin.outbox.subtitle" text="Monitor event delivery and retries." /></p>
</div>
<section class="erp-table-wrap admin-register-shell" aria-label="Outbox monitor results">
    <div class="app-table-titlebar">
        <div class="app-table-heading"><h2>Outbox Monitor results</h2></div>
        <div class="app-table-toolbar">
            <span class="admin-register-count"><spring:message code="common.showing" text="Showing" />
                <c:choose><c:when test="${eventsPage.totalElements eq 0}">0</c:when><c:otherwise>${eventsPage.number * eventsPage.size + 1}-${eventsPage.number * eventsPage.size + fn:length(events)}</c:otherwise></c:choose>
                <spring:message code="common.of" text="of" /> ${eventsPage.totalElements}
            </span>
            <a href="/admin/outbox" class="app-icon-button" aria-label="Refresh outbox monitor" title="Refresh outbox monitor">&#8635;</a>
        </div>
    </div>
    <form action="/admin/outbox" method="get" class="admin-filter-form admin-filter-bar aws-filter-toolbar" data-aws-filter-toolbar>
        <c:if test="${superAdminScopeFilters}">
            <label class="admin-filter-field block min-w-0 text-sm font-semibold text-slate-700">
                SACCO
                <select name="saccoId" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none">
                    <option value="">All SACCOs</option>
                    <c:forEach items="${registeredSaccos}" var="sacco">
                        <option value="${sacco.saccoId}" ${selectedSaccoId eq sacco.saccoId ? 'selected' : ''}>${sacco.saccoName}</option>
                    </c:forEach>
                </select>
            </label>
            <label class="admin-filter-field block min-w-0 text-sm font-semibold text-slate-700">
                Station
                <select name="stationId" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none">
                    <option value="">All stations</option>
                    <c:forEach items="${selectedStationOptions}" var="station">
                        <option value="${station}" ${selectedStationId eq station ? 'selected' : ''}>${station}</option>
                    </c:forEach>
                </select>
            </label>
        </c:if>
        <label class="admin-filter-field admin-filter-field--date block min-w-0 text-sm font-semibold text-slate-700">
            Filter From
            <input type="date" name="dateFrom" value="${selectedDateFrom}" class="mt-1 w-full border px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none ${not empty dateFromError ? 'border-rose-300 bg-rose-50' : 'border-slate-300 bg-white'}" />
            <c:if test="${not empty dateFromError}">
                <span class="mt-1 block text-[0.78rem] font-normal normal-case tracking-normal text-rose-600">${dateFromError}</span>
            </c:if>
        </label>
        <label class="admin-filter-field admin-filter-field--date block min-w-0 text-sm font-semibold text-slate-700">
            Filter To
            <input type="date" name="dateTo" value="${selectedDateTo}" class="mt-1 w-full border px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none ${not empty dateToError ? 'border-rose-300 bg-rose-50' : 'border-slate-300 bg-white'}" />
            <c:if test="${not empty dateToError}">
                <span class="mt-1 block text-[0.78rem] font-normal normal-case tracking-normal text-rose-600">${dateToError}</span>
            </c:if>
        </label>
        <label class="admin-filter-field admin-filter-field--loan block min-w-0 text-sm font-semibold text-slate-700">
            Loan Application ID
            <input type="text" name="loanApplicationId" value="${selectedLoanApplicationId}" placeholder="Search application UUID or prefix" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none" />
        </label>
        <label class="admin-filter-field admin-filter-field--rows block min-w-0 text-sm font-semibold text-slate-700">
            <span class="admin-filter-field--rows-label">Rows Per Page</span>
            <select name="size" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none">
                <option value="25" ${selectedPageSize eq 25 ? 'selected' : ''}>25 rows</option>
                <option value="50" ${selectedPageSize eq 50 ? 'selected' : ''}>50 rows</option>
                <option value="100" ${selectedPageSize eq 100 ? 'selected' : ''}>100 rows</option>
            </select>
        </label>
        <div class="admin-filter-actions flex flex-wrap items-end gap-2">
            <button type="submit" class="app-btn btn-primary">Apply Filters</button>
            <a href="/admin/outbox" class="app-btn btn-neutral">Reset</a>
        </div>
    </form>
    <div class="erp-table-scroll" data-aws-table-region data-loading-label="Loading results...">
    <table class="erp-table">
        <thead>
        <tr><th>Event Type</th><th>Aggregate</th><th>Status</th><th>Created</th><th>Actor ID</th><th>Name</th></tr>
        </thead>
        <tbody>
        <c:forEach items="${events}" var="event">
            <tr>
                <td class="px-3 py-2 font-semibold text-slate-900">${event.eventType}</td>
                <td class="px-3 py-2">${event.aggregateType}<div class="text-xs text-slate-500">${event.aggregateId}</div></td>
                <td class="px-3 py-2">${event.status}</td>
                <td class="px-3 py-2">${event.createdAt}</td>
                <td class="px-3 py-2 font-mono text-xs text-slate-700">
                    <c:set var="actorPrefix" value="${outboxActorPrefixes[event.id]}" />
                    <c:choose>
                        <c:when test="${not empty actorPrefix}">${actorPrefix}</c:when>
                        <c:otherwise>-</c:otherwise>
                    </c:choose>
                </td>
                <c:set var="outboxActorName" value="${outboxActorNames[event.id]}" />
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty outboxActorName}"><c:out value="${outboxActorName}" /></c:when>
                        <c:otherwise>-</c:otherwise>
                    </c:choose>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty events}">
            <tr><td colspan="6" class="px-3 py-3 text-slate-500">No outbox events found for this view.</td></tr>
        </c:if>
        </tbody>
    </table>
    </div>
</section>
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
