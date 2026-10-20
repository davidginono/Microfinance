<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="manager.reports.breadcrumb" text="Manager Panel / Loan Reports" /></p>
    <h1 class="erp-page-title" id="manager-reports-title"><spring:message code="manager.reports.title" text="Manager Review Reports" /></h1>
</div>

<form method="get" action="/documents/reports/manager-loans.pdf" data-page-preloader="false" class="erp-form-wrap erp-filter-form erp-table-toolbar mb-4 aws-filter-toolbar" data-aws-filter-toolbar>
    <div class="erp-table-toolbar__control">
        <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="common.fromDate" text="From Date" /></label>
        <input type="date"
               name="fromDate"
               value="${fromDateValue}"
               class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" />
    </div>
    <div class="erp-table-toolbar__control">
        <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="common.toDate" text="To Date" /></label>
        <input type="date"
               name="toDate"
               value="${toDateValue}"
               class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" />
    </div>
    <div class="erp-table-toolbar__control">
        <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="review.decision" text="Decision" /></label>
        <select name="decisionFilter" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
            <option value="ALL" ${decisionFilterValue eq 'ALL' ? 'selected' : ''}><spring:message code="review.allDecisions" text="All decisions" /></option>
            <option value="APPROVED" ${decisionFilterValue eq 'APPROVED' ? 'selected' : ''}><spring:message code="review.approved" text="Approved" /></option>
            <option value="REJECTED" ${decisionFilterValue eq 'REJECTED' ? 'selected' : ''}><spring:message code="review.rejected" text="Rejected" /></option>
        </select>
    </div>
    <div class="erp-table-toolbar__actions pt-6">
        <button type="submit" class="app-btn btn-primary"><spring:message code="reports.generatePdf" text="Generate PDF Report" /></button>
        <button type="submit" class="app-btn btn-neutral" data-print-action="true"><spring:message code="common.print" text="Print" /></button>
    </div>
</form>

<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results..." data-resource-table="manager-reports-1" data-resource-split data-table-title-id="manager-reports-title">
    <div class="erp-table-scroll">
    <table class="erp-table" data-table-sort-mode="local">
        <thead>
        <tr>
            <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
            <th><spring:message code="loan.loanId" text="Loan ID" /></th>
            <th><spring:message code="common.applicant" text="Applicant" /></th>
            <th><spring:message code="loan.type" text="Loan Type" /></th>
            <th><spring:message code="common.amount" text="Amount" /></th>
            <th><spring:message code="review.managerDecision" text="Manager Decision" /></th>
            <th><spring:message code="review.reviewedAt" text="Reviewed At" /></th>
            <th><spring:message code="review.currentStatus" text="Current Status" /></th>
            <th data-table-actions-header></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${reportRows}" var="row">
            <tr>
                <td><a class="resource-table-id-link" href="<c:url value='/manager/loan-applications/${row.id}' />">${row.shortId}</a></td>
                <td><c:choose><c:when test="${not empty row.loanId and row.loanId != '-'}"><a class="resource-table-id-link" href="<c:url value='/manager/loan-applications/${row.id}' />"><c:out value="${row.loanId}" /></a></c:when><c:otherwise><c:out value="${empty row.loanId ? '-' : row.loanId}" /></c:otherwise></c:choose></td>
                <td>${row.applicantName}</td>
                <td>${row.loanTypeLabel}</td>
                <td><fmt:formatNumber value="${row.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                <td><span data-resource-status='<c:out value="${row.managerDecisionLabel}" />'>${row.managerDecisionLabel}</span></td>
                <td>${appTime:display(row.reviewedAt)}</td>
                <td><span data-resource-status='<c:out value="${row.currentStatusLabel}" />'>${row.currentStatusLabel}</span></td>
                <td><a href="/manager/loan-applications/${row.id}" class="app-btn btn-primary"><spring:message code="common.open" text="Open" /></a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty reportRows}">
            <tr>
                <td colspan="9" class="px-3 py-8 text-center text-slate-500">
                    <spring:message code="manager.reports.empty" text="No manager-reviewed loans matched the selected period." />
                </td>
            </tr>
        </c:if>
        </tbody>
    </table>
</div>
</div>

<%@ include file="../fragments/footer.jspf" %>
