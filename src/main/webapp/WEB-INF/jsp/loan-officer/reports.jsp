<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="loanOfficer.reports.breadcrumb" text="Loan Officer Panel / Loan Reports" /></p>
    <h1 class="erp-page-title" id="loan-officer-reports-title"><spring:message code="loanOfficer.reports.title" text="Loan Officer Review Reports" /></h1>
</div>

<form method="get" action="/documents/reports/loan-officer-loans.pdf" data-page-preloader="false" data-table-toolbar-for="loan-officer-reports-1" data-table-export-for="loan-officer-reports-1" class="app-table-toolbar">
    <input type="hidden" name="fromDate" value="${fromDateValue}" />
    <input type="hidden" name="toDate" value="${toDateValue}" />
    <input type="hidden" name="decisionFilter" value="${decisionFilterValue}" />
    <button type="submit" class="app-btn btn-neutral"><spring:message code="reports.downloadPdf" /></button>
    <button type="submit" class="app-btn btn-neutral" data-print-action="true"><spring:message code="common.print" /></button>
</form>

<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results..." data-table-sync-query="true" data-resource-table="loan-officer-reports-1" data-resource-split data-table-title-id="loan-officer-reports-title">
<form method="get" action="/loan-officer/reports" class="erp-filter-form erp-table-toolbar aws-filter-toolbar" data-aws-filter-toolbar data-table-filter-for="loan-officer-reports-1">
    <div class="erp-table-toolbar__control">
        <label for="loan-officer-reports-1-fromDate" class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="common.fromDate" text="From Date" /></label>
        <input type="date"
               id="loan-officer-reports-1-fromDate" name="fromDate"
               value="${fromDateValue}"
               class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" />
    </div>
    <div class="erp-table-toolbar__control">
        <label for="loan-officer-reports-1-toDate" class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="common.toDate" text="To Date" /></label>
        <input type="date"
               id="loan-officer-reports-1-toDate" name="toDate"
               value="${toDateValue}"
               class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" />
    </div>
    <div class="erp-table-toolbar__control">
        <label for="loan-officer-reports-1-decisionFilter" class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="review.decision" text="Decision" /></label>
        <select id="loan-officer-reports-1-decisionFilter" name="decisionFilter" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
            <option value="ALL" ${decisionFilterValue eq 'ALL' ? 'selected' : ''}><spring:message code="review.allDecisions" text="All decisions" /></option>
            <option value="APPROVED" ${decisionFilterValue eq 'APPROVED' ? 'selected' : ''}><spring:message code="review.approved" text="Approved" /></option>
            <option value="REJECTED" ${decisionFilterValue eq 'REJECTED' ? 'selected' : ''}><spring:message code="review.rejected" text="Rejected" /></option>
        </select>
    </div>
    <div class="resource-table-filter-actions">
        <button type="submit" class="app-btn btn-neutral"><spring:message code="common.applyFilters" /></button>
    </div>
</form>

    <div class="erp-table-scroll">
    <table class="erp-table" data-table-sort-mode="local">
        <thead>
        <tr>
            <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
            <th><spring:message code="loan.loanId" text="Loan ID" /></th>
            <th><spring:message code="common.applicant" text="Applicant" /></th>
            <th><spring:message code="loan.type" text="Loan Type" /></th>
            <th><spring:message code="common.amount" text="Amount" /></th>
            <th><spring:message code="review.loanOfficerDecision" text="Loan Officer Decision" /></th>
            <th><spring:message code="review.reviewedAt" text="Reviewed At" /></th>
            <th><spring:message code="review.currentStatus" text="Current Status" /></th>
            <th data-table-actions-header></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${reportRows}" var="row">
            <tr>
                <td><a class="resource-table-id-link" href="<c:url value='/loan-officer/loan-applications/${row.id}' />">${row.shortId}</a></td>
                <td><c:choose><c:when test="${not empty row.loanId and row.loanId != '-'}"><a class="resource-table-id-link" href="<c:url value='/loan-officer/loan-applications/${row.id}' />"><c:out value="${row.loanId}" /></a></c:when><c:otherwise><c:out value="${empty row.loanId ? '-' : row.loanId}" /></c:otherwise></c:choose></td>
                <td>${row.applicantName}</td>
                <td>${row.loanTypeLabel}</td>
                <td><fmt:formatNumber value="${row.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                <td><span data-resource-status='<c:out value="${row.decisionLabel}" />'>${row.decisionLabel}</span></td>
                <td>${appTime:display(row.reviewedAt)}</td>
                <td><span data-resource-status='<c:out value="${row.currentStatusLabel}" />'>${row.currentStatusLabel}</span></td>
                <td><a href="/loan-officer/loan-applications/${row.id}" class="app-btn btn-primary"><spring:message code="common.open" text="Open" /></a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty reportRows}">
            <tr>
                <td colspan="9" class="px-3 py-8 text-center text-slate-500">
                    <spring:message code="loanOfficer.reports.empty" text="No loan officer-reviewed loans matched the selected period." />
                </td>
            </tr>
        </c:if>
        </tbody>
    </table>
</div>
</div>

<%@ include file="../fragments/footer.jspf" %>
