<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="manager.queue.breadcrumb" text="Manager Panel / Queue" /></p>
    <h1 class="erp-page-title"><spring:message code="manager.queue.title" text="Manager Queue" /></h1>
    <p class="erp-page-subtitle"><spring:message code="manager.queue.subtitle" text="Move across workflow stages, inspect applicant details, and finalize the records waiting on manager action." /></p>
</div>
<div class="erp-toolbar manager-queue-filter-toolbar">
    <div class="manager-queue-filter-stack">
        <div>
            <p class="erp-widget-title"><spring:message code="common.currentFilter" text="Current Filter" /></p>
            <h2 class="erp-widget-heading">${currentFilterLabel}</h2>
        </div>
<form action="/manager/loan-applications" method="get" class="erp-filter-form manager-queue-search-form aws-filter-toolbar" data-aws-filter-toolbar>
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                ${queueSearchLabel}
                <input type="search"
                       name="searchId"
                       value="${fn:escapeXml(queueSearchValue)}"
                       placeholder="${queueSearchPlaceholder}"
                       inputmode="numeric"
                       class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <div class="manager-queue-search-actions">
                <c:if test="${not empty queueSearchValue}">
                    <a href="/manager/loan-applications?filter=${currentFilterKey}" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
                </c:if>
                <button type="submit" class="app-btn btn-primary"><spring:message code="common.search" text="Search" /></button>
            </div>
        </form>
    </div>
</div>
<div class="erp-table-wrap erp-table-scroll" data-aws-table-region data-loading-label="Loading results...">
<table class="erp-table">
    <thead>
    <tr>
        <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
        <th><spring:message code="reports.loanProduct" text="Loan Product" /></th>
        <th><spring:message code="common.applicant" text="Applicant" /></th>
        <th><spring:message code="common.amount" text="Amount" /></th>
        <th><spring:message code="common.status" text="Status" /></th>
        <th><spring:message code="loan.date" text="Date" /></th>
        <th><spring:message code="common.actions" text="Actions" /></th>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${apps}" var="app">
        <tr>
            <td class="px-3 py-2">${app.applicationNumber}</td>
            <td class="px-3 py-2">
                <c:choose>
                    <c:when test="${not empty loanProductNames[app.loanType]}"><c:out value="${loanProductNames[app.loanType]}" /></c:when>
                    <c:otherwise><spring:message code="loan.type.${app.loanType}" text="${app.loanType}" /></c:otherwise>
                </c:choose>
            </td>
            <td class="px-3 py-2">
                <c:choose>
                    <c:when test="${not empty applicantNames[app.applicantMemberId]}">${applicantNames[app.applicantMemberId]}</c:when>
                    <c:otherwise>#${fn:substring(app.applicantMemberId, 0, 8)}</c:otherwise>
                </c:choose>
            </td>
            <td class="px-3 py-2"><fmt:formatNumber value="${app.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
            <td class="px-3 py-2"><spring:message code="loan.status.${app.status}" text="${app.status}" /></td>
            <td class="px-3 py-2">
                <c:choose>
                    <c:when test="${not empty app.disbursementDate}">${app.disbursementDate}</c:when>
                    <c:otherwise>${fn:replace(fn:substring(app.createdAt, 0, 16), 'T', ' ')}</c:otherwise>
                </c:choose>
            </td>
            <td class="px-3 py-2">
                <div class="flex flex-wrap gap-2">
                    <a href="/manager/loan-applications/${app.id}" class="app-btn btn-primary"><spring:message code="common.open" text="Open" /></a>
                </div>
            </td>
        </tr>
    </c:forEach>
    <c:if test="${empty apps}">
        <tr>
            <td colspan="7" class="px-3 py-8 text-center text-slate-500">
                <c:choose>
                    <c:when test="${not empty queueSearchValue}">
                        No loan applications found for <span class="font-semibold text-slate-700">${fn:toLowerCase(queueSearchLabel)}</span>
                        <span class="font-semibold text-slate-700"><c:out value="${queueSearchValue}" /></span>
                        in the ${fn:toLowerCase(currentFilterLabel)} filter.
                    </c:when>
                    <c:otherwise>
                        No loan applications found for the ${fn:toLowerCase(currentFilterLabel)} filter.
                    </c:otherwise>
                </c:choose>
            </td>
        </tr>
    </c:if>
    </tbody>
</table>
</div>

<%@ include file="../fragments/confirm-modal.jspf" %>

<%@ include file="../fragments/footer.jspf" %>
