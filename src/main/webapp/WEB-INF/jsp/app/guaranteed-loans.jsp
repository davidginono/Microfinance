<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="guaranteedLoans.breadcrumb" text="Member Workspace / Guarantees" /></p>
    <h1 class="erp-page-title"><spring:message code="guaranteedLoans.title" text="Loans I Guarantee" /></h1>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title"><spring:message code="guaranteedLoans.panel" text="Guaranteed Loans" /></p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="guaranteedLoans.activePosition" text="Active Guarantee Position" /></h2>
    </div>
<div class="erp-table-wrap erp-table-scroll border-0 shadow-none" data-aws-table-region data-loading-label="Loading results...">
        <table class="min-w-full divide-y divide-slate-200 text-sm">
            <thead class="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
            <tr>
                <th class="px-5 py-3"><spring:message code="loan.single" text="Loan" /></th>
                <th class="px-5 py-3"><spring:message code="common.status" text="Status" /></th>
                <th class="px-5 py-3"><spring:message code="common.amount" text="Amount" /></th>
                <th class="px-5 py-3"><spring:message code="loan.repayment.timeLeft" text="Time Left" /></th>
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
                            <c:when test="${row.daysLeft ne null}">${row.daysLeft} <spring:message code="common.days" text="day(s)" /></c:when>
                            <c:otherwise><spring:message code="loan.repayment.notScheduled" text="Not scheduled" /></c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty guaranteedLoans}">
                <tr>
                    <td colspan="4" class="px-5 py-8 text-center text-slate-500"><spring:message code="guaranteedLoans.empty" text="No active guaranteed loans yet." /></td>
                </tr>
            </c:if>
            </tbody>
        </table>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
