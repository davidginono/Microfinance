<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Manager Panel / Loan Reports</p>
    <h1 class="erp-page-title">Disbursed Loan Reports</h1>
    <p class="erp-page-subtitle">Filter disbursed loans by year, review which ones have been returned, and export the report to PDF.</p>
</div>

<form method="get" action="/manager/reports" class="erp-form-wrap mb-4 grid gap-4 md:grid-cols-3">
    <div>
        <label class="mb-1 block text-sm font-medium text-slate-700">Year</label>
        <select name="year" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
            <c:forEach items="${reportYears}" var="reportYear">
                <option value="${reportYear}" ${yearValue == reportYear.toString() ? 'selected' : ''}>${reportYear}</option>
            </c:forEach>
        </select>
    </div>
    <div class="flex items-end">
        <label class="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700">
            <input type="checkbox" name="returnedOnly" value="true" ${returnedOnlyChecked} />
            Returned loans only
        </label>
    </div>
    <div class="flex items-end gap-2">
        <button type="submit" class="app-btn btn-primary">Apply Filter</button>
        <a href="/documents/reports/manager-loans.pdf?year=${yearValue}&returnedOnly=${returnedOnlyValue}" class="app-btn btn-neutral">Download PDF</a>
    </div>
</form>

<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr>
            <th>Loan Id</th>
            <th>Applicant</th>
            <th>Loan Type</th>
            <th>Amount</th>
            <th>Disbursed</th>
            <th>Final Due Date</th>
            <th>Status</th>
            <th>Paid At</th>
            <th></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${reportRows}" var="row">
            <tr>
                <td>${row.shortId}</td>
                <td>${row.applicantName}</td>
                <td>${row.loanTypeLabel}</td>
                <td>${row.amount}</td>
                <td>${row.disbursed}</td>
                <td>${row.finalDueDate}</td>
                <td>${row.statusLabel}</td>
                <td>${row.paidAt}</td>
                <td><a href="/manager/loan-applications/${row.id}" class="app-btn btn-primary">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty reportRows}">
            <tr><td colspan="9">No disbursed loans matched the selected year.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
