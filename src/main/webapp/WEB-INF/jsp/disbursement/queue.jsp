<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="disbursement.queue.breadcrumb" text="Disbursement Panel / Queue" /></p>
    <h1 class="erp-page-title" id="disbursement-queue-title"><spring:message code="disbursement.queue.title" text="Disbursement Queue" /></h1>
</div>

<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results..." data-table-sync-query="true" data-resource-table="disbursement-queue-1" data-resource-split data-table-title-id="disbursement-queue-title">
<form action="/disbursement/loan-applications" method="get" class="erp-filter-form disbursement-queue-search-form aws-filter-toolbar" data-aws-filter-toolbar data-table-filter-for="disbursement-queue-1">
            <input type="hidden" name="filter" value="${currentFilterKey}" />
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                ${queueSearchLabel}
                <input type="search"
                       name="searchId"
                       value="${fn:escapeXml(queueSearchValue)}"
                       placeholder="${queueSearchPlaceholder}"
                       inputmode="numeric"
                       class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
            </label>
            <div class="disbursement-queue-search-actions">
                <c:if test="${not empty queueSearchValue}">
                    <a href="/disbursement/loan-applications?filter=${currentFilterKey}" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
                </c:if>
                <button type="submit" class="app-btn btn-primary"><spring:message code="common.search" text="Search" /></button>
            </div>
        </form>

    <div class="erp-table-scroll">
    <table class="erp-table">
        <thead>
        <tr>
            <th data-table-order="number"><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
            <th data-table-order="product"><spring:message code="reports.loanProduct" text="Loan Product" /></th>
            <th data-table-order="applicant"><spring:message code="common.applicant" text="Applicant" /></th>
            <th data-table-order="amount"><spring:message code="common.amount" text="Amount" /></th>
            <th data-table-order="status"><spring:message code="common.status" text="Status" /></th>
            <th data-table-order="date"><spring:message code="loan.date" text="Date" /></th>
            <th data-table-actions-header><spring:message code="common.action" text="Action" /></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${apps}" var="app">
            <tr>
                <td class="px-3 py-2"><a class="resource-table-id-link" href="<c:url value='/disbursement/loan-applications/${app.id}' />">${app.applicationNumber}</a></td>
                <td class="px-3 py-2">
                    <c:out value="${applicationProductNames[app.id]}" />
                </td>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty applicantNames[app.applicantMemberId]}">${applicantNames[app.applicantMemberId]}</c:when>
                        <c:otherwise>#${fn:substring(app.applicantMemberId, 0, 8)}</c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2"><fmt:formatNumber value="${app.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                <td class="px-3 py-2"><span data-resource-status="${app.status}"><spring:message code="loan.status.${app.status}" text="${app.status}" /></span></td>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty app.disbursementDate}">${appTime:display(app.disbursementDate)}</c:when>
                        <c:otherwise><c:out value="${appTime:format(app.createdAt)}" /></c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2">
                    <a href="/disbursement/loan-applications/${app.id}" class="app-btn btn-primary"><spring:message code="common.open" text="Open" /></a>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty apps}">
            <tr>
                <td colspan="7" class="px-3 py-8 text-center text-slate-500">
                    No loan applications found for the ${fn:toLowerCase(currentFilterLabel)} filter.
                </td>
            </tr>
        </c:if>
        </tbody>
    </table>
</div>
</div>

<%@ include file="../fragments/footer.jspf" %>
