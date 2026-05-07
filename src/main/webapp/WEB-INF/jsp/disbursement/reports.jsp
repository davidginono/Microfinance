<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Disbursement Panel / Reports</p>
    <h1 class="erp-page-title">Disbursement Reports</h1>
    <p class="erp-page-subtitle">Filter the loans you disbursed by date range and export the report when needed.</p>
</div>

<form method="get" action="/disbursement/reports" class="erp-form-wrap erp-filter-form mb-4 grid gap-4 lg:grid-cols-3">
    <div>
        <label class="mb-1 block text-sm font-medium text-slate-700">From Date</label>
        <input type="date"
               name="fromDate"
               value="${fromDateValue}"
               class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" />
    </div>
    <div>
        <label class="mb-1 block text-sm font-medium text-slate-700">To Date</label>
        <input type="date"
               name="toDate"
               value="${toDateValue}"
               class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" />
    </div>
    <div class="flex items-end gap-2">
        <button type="submit" class="app-btn btn-primary">Generate Report</button>
        <a href="/documents/reports/disbursement-loans.pdf?fromDate=${fromDateValue}&toDate=${toDateValue}" class="app-btn btn-neutral">Download PDF</a>
    </div>
</form>

<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr>
            <th>Loan Application ID</th>
            <th>Loan ID</th>
            <th>Applicant</th>
            <th>Loan Type</th>
            <th>Amount</th>
            <th>Disbursed At</th>
            <th>Current Status</th>
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
                <td>${row.amount}</td>
                <td>${row.disbursedAt}</td>
                <td>${row.currentStatusLabel}</td>
                <td><a href="/disbursement/loan-applications/${row.id}" class="app-btn btn-primary">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty reportRows}">
            <tr>
                <td colspan="8" class="px-3 py-8 text-center text-slate-500">
                    No disbursed loans matched the selected period.
                </td>
            </tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
