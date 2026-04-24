<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Member Workspace / Loan Reports</p>
    <h1 class="erp-page-title">My Loan Report</h1>
    <p class="erp-page-subtitle">Download a PDF summary of your disbursed loans, the ones you have returned, and the loans still ongoing.</p>
</div>

<div class="erp-toolbar">
    <div>
        <p class="erp-widget-title">Report Owner</p>
        <h2 class="erp-widget-heading">${reportOwnerName}</h2>
    </div>
    <a href="/documents/reports/member-loans.pdf" class="app-btn btn-primary">Download PDF Report</a>
</div>

<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr>
            <th>Loan Application ID</th>
            <th>Loan ID</th>
            <th>Loan Type</th>
            <th>Amount</th>
            <th>Disbursed</th>
            <th>Final Due Date</th>
            <th>Status</th>
            <th>Paid At</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${reportRows}" var="loan">
            <tr>
                <td>${loan.shortId}</td>
                <td><c:out value="${empty loan.loanId ? '-' : loan.loanId}" /></td>
                <td>${loan.loanTypeLabel}</td>
                <td>${loan.amount}</td>
                <td>${loan.disbursed}</td>
                <td>${loan.finalDueDate}</td>
                <td>${loan.statusLabel}</td>
                <td>${loan.paidAt}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty reportRows}">
            <tr><td colspan="7">No disbursed loans found for this member yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
