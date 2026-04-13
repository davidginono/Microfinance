<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Chairperson Panel / Manager Decisions</p>
    <h1 class="erp-page-title">Chairperson Review Window</h1>
    <p class="erp-page-subtitle">Observe manager decisions, reasons, and current application status without changing the underlying workflow.</p>
</div>
<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr>
            <th class="px-3 py-2 text-left">Date</th>
            <th class="px-3 py-2 text-left">Reference</th>
            <th class="px-3 py-2 text-left">Applicant</th>
            <th class="px-3 py-2 text-left">Amount</th>
            <th class="px-3 py-2 text-left">Manager Decision</th>
            <th class="px-3 py-2 text-left">Reason</th>
            <th class="px-3 py-2 text-left">Current Status</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${reviews}" var="review">
            <c:set var="app" value="${applications[review.loanApplicationId]}" />
            <tr>
                <td class="px-3 py-2">${fn:replace(fn:substring(review.createdAt, 0, 16), 'T', ' ')}</td>
                <td class="px-3 py-2">${fn:substring(review.loanApplicationId, 0, 8)}</td>
                <td class="px-3 py-2">
                    <c:if test="${not empty app}">${applicantNames[app.applicantMemberId]}</c:if>
                </td>
                <td class="px-3 py-2">
                    <c:if test="${not empty app}">${app.amount}</c:if>
                </td>
                <td class="px-3 py-2">${review.decision}</td>
                <td class="px-3 py-2">${empty review.reasons ? '-' : review.reasons}</td>
                <td class="px-3 py-2">
                    <c:if test="${not empty app}">${app.status}</c:if>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty reviews}">
            <tr><td colspan="7" class="px-3 py-4 text-slate-500">No manager decisions recorded yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
