<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/loan-repayments.css' />" />
<div class="repayment-workspace">
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="repayment.title" /></h1></div>
    <c:if test="${not empty repaymentError}"><p class="repayment-error" role="alert"><spring:message code="${repaymentError}" /></p></c:if>
    <section class="erp-table-wrap" data-aws-table-region>
        <div class="app-table-titlebar"><h2><spring:message code="repayment.ledgerLoans" /></h2><span class="app-table-count">(<c:out value="${loans.totalElements}" />)</span></div>
        <c:url var="repaymentListUrl" value="/repayments" />
        <form method="get" action="${repaymentListUrl}" class="aws-filter-toolbar" data-aws-filter-toolbar>
            <label for="loanNumber"><spring:message code="loan.loanId" /></label>
            <input id="loanNumber" name="loanNumber" class="aws-control" maxlength="20" pattern="[0-9]{4,20}" value="<c:out value='${loanNumber}' />" />
            <button type="submit" class="erp-icon-btn" aria-label="<spring:message code='common.search' />" title="<spring:message code='common.search' />"><i data-lucide="search" aria-hidden="true"></i></button>
        </form>
        <div class="erp-table-scroll" data-view-position-key="repayment-loans">
            <table class="erp-table">
                <thead><tr><th><spring:message code="loan.loanId" /></th><th><spring:message code="repayment.disbursed" /></th><th class="money"><spring:message code="repayment.principalOutstanding" /> (TZS)</th><th class="money"><spring:message code="repayment.principalPaid" /> (TZS)</th><th class="money"><spring:message code="repayment.interestPaid" /> (TZS)</th><th><spring:message code="common.actions" /></th></tr></thead>
                <tbody>
                <c:forEach items="${loans.content}" var="row"><tr>
                    <td><c:out value="${row.loanId}" /></td><td><c:out value="${row.disbursementDate}" /></td>
                    <td class="money"><fmt:formatNumber value="${row.outstandingPrincipal}" minFractionDigits="2" maxFractionDigits="2" /></td>
                    <td class="money"><fmt:formatNumber value="${row.principalPaid}" minFractionDigits="2" maxFractionDigits="2" /></td>
                    <td class="money"><fmt:formatNumber value="${row.interestPaid}" minFractionDigits="2" maxFractionDigits="2" /></td>
                    <td><a class="app-btn btn-neutral" href="<c:url value='/repayments/loans/${row.id}' />"><spring:message code="common.view" /></a></td>
                </tr></c:forEach>
                <c:if test="${empty loans.content}"><tr><td colspan="6" class="erp-table-empty"><spring:message code="repayment.empty" /></td></tr></c:if>
                </tbody>
            </table>
        </div>
        <div class="repayment-pagination">
            <c:if test="${loans.hasPrevious()}"><c:url var="previous" value="/repayments"><c:param name="page" value="${loans.number - 1}" /><c:param name="loanNumber" value="${loanNumber}" /></c:url><a class="app-btn btn-neutral" href="${previous}"><spring:message code="common.previous" /></a></c:if>
            <span><spring:message code="repayment.page" /> <c:out value="${loans.number + 1}" /></span>
            <c:if test="${loans.hasNext()}"><c:url var="next" value="/repayments"><c:param name="page" value="${loans.number + 1}" /><c:param name="loanNumber" value="${loanNumber}" /></c:url><a class="app-btn btn-neutral" href="${next}"><spring:message code="common.next" /></a></c:if>
        </div>
    </section>
</div>
<%@ include file="../fragments/footer.jspf" %>
