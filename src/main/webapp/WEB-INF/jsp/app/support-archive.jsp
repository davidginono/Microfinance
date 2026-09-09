<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="support.archive.breadcrumb" text="Member Workspace / Support / Sent Archive" /></p>
    <h1 class="erp-page-title"><spring:message code="support.archive.title" text="Sent Support Archive" /></h1>
</div>

<section class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
    <form method="get" action="/app/support/archive" class="erp-filter-form aws-filter-toolbar" data-aws-filter-toolbar>
        <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="common.fromDate" text="From Date" />
            <input type="date" name="fromDate" value="${fromDate}" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
        </label>
        <label class="text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="common.toDate" text="To Date" />
            <input type="date" name="toDate" value="${toDate}" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
        </label>
        <div class="support-archive-filter-actions">
            <c:if test="${not empty fromDate or not empty toDate}"><a href="/app/support/archive" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a></c:if>
            <button type="submit" class="app-btn btn-primary"><spring:message code="common.applyFilters" text="Apply Filters" /></button>
        </div>
    </form>
    <div class="erp-table-scroll">
    <table class="erp-table">
        <thead>
        <tr><th><spring:message code="common.subject" text="Subject" /></th><th><spring:message code="common.message" text="Message" /></th><th><spring:message code="common.status" text="Status" /></th><th><spring:message code="support.archive.stationAdminRead" text="Station Admin Read" /></th><th><spring:message code="support.archive.sent" text="Sent" /></th></tr>
        </thead>
        <tbody>
        <c:forEach items="${supportArchive}" var="item">
            <tr>
                <td class="px-3 py-2 font-semibold text-slate-900"><c:out value="${item.subject}" /></td>
                <td class="px-3 py-2 text-slate-700"><c:out value="${item.message}" /></td>
                <td class="px-3 py-2">${item.status}</td>
                <td class="px-3 py-2">
                    <span class="${item.readBySuperAdmin ? 'rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700' : 'rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600'}">
                        <c:choose>
                            <c:when test="${item.readBySuperAdmin}"><spring:message code="support.archive.readByStationAdmin" text="Read by Station Admin" /></c:when>
                            <c:otherwise><spring:message code="support.archive.notReadYet" text="Not read yet" /></c:otherwise>
                        </c:choose>
                    </span>
                </td>
                <td class="px-3 py-2 whitespace-nowrap"><c:out value="${appTime:format(item.createdAt)}" /></td>
            </tr>
        </c:forEach>
        <c:if test="${empty supportArchive}">
            <tr><td colspan="5" class="px-3 py-3 text-slate-500"><spring:message code="support.archive.empty" text="No support messages sent yet." /></td></tr>
        </c:if>
        </tbody>
    </table>
</div>
    <c:if test="${archivePage.totalPages gt 1}">
        <nav class="aws-table-pagination-footer" aria-label="Support archive pages">
            <span>Page ${archivePage.number + 1} of ${archivePage.totalPages}</span>
            <div class="flex gap-2">
                <c:if test="${not archivePage.first}"><c:url var="supportPreviousUrl" value="/app/support/archive"><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /><c:param name="page" value="${archivePage.number - 1}" /></c:url><a class="app-btn btn-neutral" href="${supportPreviousUrl}"><spring:message code="common.previous" text="Previous" /></a></c:if>
                <c:if test="${not archivePage.last}"><c:url var="supportNextUrl" value="/app/support/archive"><c:param name="fromDate" value="${fromDate}" /><c:param name="toDate" value="${toDate}" /><c:param name="page" value="${archivePage.number + 1}" /></c:url><a class="app-btn btn-primary" href="${supportNextUrl}"><spring:message code="common.next" text="Next" /></a></c:if>
            </div>
        </nav>
    </c:if>
</section>

<%@ include file="../fragments/footer.jspf" %>
