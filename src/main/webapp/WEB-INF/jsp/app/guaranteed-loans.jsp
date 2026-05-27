<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Member Workspace / Guarantees</p>
    <h1 class="erp-page-title">Loans I Guarantee</h1>
    <p class="erp-page-subtitle">Current status of active loans linked to your approved guarantees.</p>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title">Guaranteed Loans</p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink">Active Guarantee Position</h2>
    </div>
    <div class="overflow-x-auto">
        <table class="min-w-full divide-y divide-slate-200 text-sm">
            <thead class="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
            <tr>
                <th class="px-5 py-3">Loan</th>
                <th class="px-5 py-3">Status</th>
                <th class="px-5 py-3">Amount</th>
                <th class="px-5 py-3">Time Left</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 bg-white">
            <c:forEach items="${guaranteedLoans}" var="row">
                <tr>
                    <td class="px-5 py-4 font-semibold text-sacco-ink">
                        <c:choose>
                            <c:when test="${row.loan ne null and not empty row.loan.loanId}">${row.loan.loanId}</c:when>
                            <c:when test="${row.loan ne null}">${row.loan.applicationNumber}</c:when>
                            <c:otherwise>${row.request.loanApplicationId}</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-5 py-4 text-slate-700">${row.statusLabel}</td>
                    <td class="px-5 py-4 text-slate-700">
                        <c:if test="${row.loan ne null}"><fmt:formatNumber value="${row.loan.amount}" minFractionDigits="2" maxFractionDigits="2" /></c:if>
                    </td>
                    <td class="px-5 py-4 text-slate-700">
                        <c:choose>
                            <c:when test="${row.daysLeft ne null}">${row.daysLeft} day(s)</c:when>
                            <c:otherwise>Not scheduled</c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty guaranteedLoans}">
                <tr>
                    <td colspan="4" class="px-5 py-8 text-center text-slate-500">No active guaranteed loans yet.</td>
                </tr>
            </c:if>
            </tbody>
        </table>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
