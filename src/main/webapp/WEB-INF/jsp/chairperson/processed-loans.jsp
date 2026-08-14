<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="chairperson.office" text="Chairperson Office" /> / <spring:message code="chairperson.processedLoans" text="Processed Loans" /></p>
    <h1 class="erp-page-title"><spring:message code="chairperson.processedLoans" text="Processed Loans" /></h1>
</div>

<section class="erp-panel overflow-hidden">
    <form method="get" action="${pageContext.request.contextPath}/chairperson/processed-loans" class="erp-filter-form aws-filter-toolbar" data-aws-filter-toolbar>
        <label class="erp-table-toolbar__search text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="common.search" text="Search" />
            <input type="search" name="search" value="${fn:escapeXml(processedLoans.search)}"
                   placeholder="<spring:message code='chairperson.processedLoans.searchPlaceholder' text='Loan ID or application ID' />"
                   class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" />
        </label>
        <label class="erp-table-toolbar__control text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="reports.startDate" text="Start Date" />
            <input type="date" name="fromDate" value="${processedLoans.fromDate}" class="fcms-control" />
        </label>
        <label class="erp-table-toolbar__control text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="reports.endDate" text="End Date" />
            <input type="date" name="toDate" value="${processedLoans.toDate}" class="fcms-control" />
        </label>
        <label class="erp-table-toolbar__control text-xs font-semibold uppercase tracking-wide text-slate-500">
            <spring:message code="common.status" text="Status" />
            <select name="status" class="fcms-control">
                <option value=""><spring:message code="common.allStatuses" text="All statuses" /></option>
                <c:forEach items="${processedLoans.availableStatuses}" var="statusOption">
                    <option value="${statusOption}" ${processedLoans.status eq statusOption ? 'selected' : ''}>
                        <spring:message code="loan.status.${statusOption}" text="${fn:replace(statusOption, '_', ' ')}" />
                    </option>
                </c:forEach>
            </select>
        </label>
        <div class="erp-table-toolbar__actions">
            <button class="app-btn btn-primary" type="submit"><spring:message code="common.applyFilters" text="Apply Filter" /></button>
            <c:if test="${not empty processedLoans.search or not empty processedLoans.fromDate or not empty processedLoans.toDate or not empty processedLoans.status}">
                <a class="app-btn btn-neutral" href="${pageContext.request.contextPath}/chairperson/processed-loans"><spring:message code="common.reset" text="Reset" /></a>
            </c:if>
        </div>
    </form>
    <div class="erp-table-wrap border-0 shadow-none" data-aws-table-region>
        <div class="erp-table-scroll">
            <table class="erp-table">
                <thead><tr>
                    <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
                    <th><spring:message code="common.applicant" text="Applicant" /></th>
                    <th><spring:message code="reports.loanProduct" text="Loan Product" /></th>
                    <th><spring:message code="common.amount" text="Amount" /></th>
                    <th><spring:message code="common.status" text="Status" /></th>
                    <th><spring:message code="chairperson.latestDecision" text="Latest Staff Decision" /></th>
                    <th><spring:message code="chairperson.decisionCount" text="Decisions" /></th>
                    <th></th>
                </tr></thead>
                <tbody>
                <c:forEach items="${processedLoans.rows}" var="row">
                    <tr>
                        <td><span class="font-semibold text-slate-800"><c:out value="${row.applicationId}" /></span><c:if test="${not empty row.loanId}"><div class="text-xs text-slate-500"><c:out value="${row.loanId}" /></div></c:if></td>
                        <td><c:out value="${row.applicantName}" /><div class="text-xs text-slate-500"><c:out value="${row.memberNo}" /></div></td>
                        <td><c:out value="${row.productName}" /></td>
                        <td>TSh <fmt:formatNumber value="${row.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                        <td><span class="erp-status-badge"><spring:message code="loan.status.${row.status}" text="${fn:replace(row.status, '_', ' ')}" /></span></td>
                        <td>
                            <c:choose><c:when test="${not empty row.latestDecision}">
                                <span class="font-semibold"><spring:message code="chairperson.decision.${row.latestDecision.decision}" text="${row.latestDecision.decision}" /></span>
                                <div class="text-xs text-slate-500"><c:out value="${row.latestDecision.stageLabel}" /> &middot; <c:out value="${row.latestDecision.reviewerName}" /></div>
                                <div class="text-xs text-slate-500">${fn:replace(fn:substring(row.latestDecision.decidedAt, 0, 16), 'T', ' ')}</div>
                            </c:when><c:otherwise><span class="text-slate-500"><spring:message code="chairperson.awaitingDecision" text="Awaiting staff decision" /></span></c:otherwise></c:choose>
                        </td>
                        <td><c:out value="${row.decisionCount}" /></td>
                        <td><a class="app-btn btn-neutral" href="${pageContext.request.contextPath}/chairperson/processed-loans/${row.id}"><spring:message code="common.view" text="View" /></a></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty processedLoans.rows}"><tr><td colspan="8" class="px-4 py-8 text-center text-sm text-slate-500"><spring:message code="chairperson.processedLoans.empty" text="No reviewed loan applications were found." /></td></tr></c:if>
                </tbody>
            </table>
        </div>
    </div>
</section>

<c:if test="${processedLoans.totalPages gt 1}">
    <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
        <span class="text-sm text-slate-500"><spring:message code="common.page" text="Page" /> ${processedLoans.page + 1} / ${processedLoans.totalPages}</span>
        <div class="flex gap-2">
            <c:if test="${not processedLoans.first}"><c:url var="prevUrl" value="/chairperson/processed-loans"><c:param name="search" value="${processedLoans.search}"/><c:param name="fromDate" value="${processedLoans.fromDate}"/><c:param name="toDate" value="${processedLoans.toDate}"/><c:param name="status" value="${processedLoans.status}"/><c:param name="page" value="${processedLoans.page - 1}"/></c:url><a class="app-btn btn-neutral" href="${prevUrl}"><spring:message code="common.previous" text="Previous" /></a></c:if>
            <c:if test="${not processedLoans.last}"><c:url var="nextUrl" value="/chairperson/processed-loans"><c:param name="search" value="${processedLoans.search}"/><c:param name="fromDate" value="${processedLoans.fromDate}"/><c:param name="toDate" value="${processedLoans.toDate}"/><c:param name="status" value="${processedLoans.status}"/><c:param name="page" value="${processedLoans.page + 1}"/></c:url><a class="app-btn btn-primary" href="${nextUrl}"><spring:message code="common.next" text="Next" /></a></c:if>
        </div>
    </div>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
