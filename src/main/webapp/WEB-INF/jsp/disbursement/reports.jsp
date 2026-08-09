<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="disbursement.reports.breadcrumb" text="Disbursement Panel / Reports" /></p>
    <h1 class="erp-page-title"><spring:message code="disbursement.reports.title" text="Disbursement Reports" /></h1>
</div>

<form method="get" action="/documents/reports/disbursement-loans.pdf" data-page-preloader="false" class="erp-form-wrap erp-filter-form erp-table-toolbar mb-4 aws-filter-toolbar" data-aws-filter-toolbar>
    <div class="erp-table-toolbar__control">
        <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="common.fromDate" text="From Date" /></label>
        <input type="date"
               name="fromDate"
               value="${fromDateValue}"
               class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" />
    </div>
    <div class="erp-table-toolbar__control">
        <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="common.toDate" text="To Date" /></label>
        <input type="date"
               name="toDate"
               value="${toDateValue}"
               class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" />
    </div>
    <div class="erp-table-toolbar__actions pt-6">
        <button type="submit" class="app-btn btn-primary"><spring:message code="reports.generatePdf" text="Generate PDF Report" /></button>
    </div>
</form>

<div class="erp-table-wrap erp-table-scroll" data-aws-table-region data-loading-label="Loading results...">
    <table class="erp-table">
        <thead>
        <tr>
            <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
            <th><spring:message code="loan.loanId" text="Loan ID" /></th>
            <th><spring:message code="common.applicant" text="Applicant" /></th>
            <th><spring:message code="loan.type" text="Loan Type" /></th>
            <th><spring:message code="common.amount" text="Amount" /></th>
            <th><spring:message code="review.disbursedAt" text="Disbursed At" /></th>
            <th><spring:message code="review.currentStatus" text="Current Status" /></th>
            <th></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${reportRows}" var="row">
            <tr>
                <td>${row.shortId}</td>
                <td><c:out value="${empty row.loanId ? '-' : row.loanId}" /></td>
                <td>${row.applicantName}</td>
                <td>${row.loanTypeLabel}</td>
                <td><fmt:formatNumber value="${row.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                <td>${row.disbursedAt}</td>
                <td>${row.currentStatusLabel}</td>
                <td><a href="/disbursement/loan-applications/${row.id}" class="app-btn btn-primary"><spring:message code="common.open" text="Open" /></a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty reportRows}">
            <tr>
                <td colspan="8" class="px-3 py-8 text-center text-slate-500">
                    <spring:message code="disbursement.reports.empty" text="No disbursed loans matched the selected period." />
                </td>
            </tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
