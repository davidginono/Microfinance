<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Accountant Panel / Reports</p>
    <h1 class="erp-page-title">Accountant Review Reports</h1>
    <p class="erp-page-subtitle">Filter the loans you reviewed by date range and decision, then export the report when needed.</p>
</div>

<form method="get" action="/accountant/reports" class="erp-form-wrap erp-filter-form mb-4 grid gap-4 lg:grid-cols-4">
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
    <div>
        <label class="mb-1 block text-sm font-medium text-slate-700">Decision</label>
        <select name="decisionFilter" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
            <option value="ALL" ${decisionFilterValue eq 'ALL' ? 'selected' : ''}>All decisions</option>
            <option value="APPROVED" ${decisionFilterValue eq 'APPROVED' ? 'selected' : ''}>Approved for disbursement</option>
            <option value="REJECTED" ${decisionFilterValue eq 'REJECTED' ? 'selected' : ''}>Rejected</option>
        </select>
    </div>
    <div class="flex items-end gap-2">
        <button type="submit" class="app-btn btn-primary">Generate Report</button>
        <a href="/documents/reports/accountant-loans.pdf?fromDate=${fromDateValue}&toDate=${toDateValue}&decisionFilter=${decisionFilterValue}" class="app-btn btn-neutral">Download PDF</a>
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
            <th>Decision</th>
            <th>Reviewed At</th>
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
                <td>${row.decisionLabel}</td>
                <td>${row.reviewedAt}</td>
                <td>${row.currentStatusLabel}</td>
                <td><a href="/accountant/loan-applications/${row.id}" class="app-btn btn-primary">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty reportRows}">
            <tr>
                <td colspan="9" class="px-3 py-8 text-center text-slate-500">
                    No accountant-reviewed loans matched the selected period.
                </td>
            </tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
