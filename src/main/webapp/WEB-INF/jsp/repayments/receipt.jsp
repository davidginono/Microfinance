<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/loan-repayments.css' />" />
<article class="repayment-workspace repayment-receipt">
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="repayment.receipt" /></h1><p class="repayment-reference"><c:out value="${receipt.reference}" /></p></div>
    <c:if test="${not empty receipt.reversedBy}"><div class="repayment-error" role="status"><spring:message code="repayment.reversed" /> <a href="<c:url value='/repayments/loans/${receipt.loanApplicationId}/receipts/${receipt.reversedBy}' />"><spring:message code="repayment.correction" /></a></div></c:if>
    <dl class="repayment-facts">
        <div><dt><spring:message code="loan.loanId" /></dt><dd><c:out value="${receipt.loanId}" /></dd></div>
        <div><dt><spring:message code="repayment.paymentDate" /></dt><dd><c:out value="${receipt.paymentDate}" /></dd></div>
        <div><dt><spring:message code="common.status" /></dt><dd><c:choose><c:when test="${not empty receipt.reversedBy}"><spring:message code="repayment.reversed" /></c:when><c:otherwise><spring:message code="repayment.kind.${receipt.kind}" /></c:otherwise></c:choose></dd></div>
        <div><dt><spring:message code="common.amount" /></dt><dd>TZS <fmt:formatNumber value="${receipt.amount}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
        <div><dt><spring:message code="repayment.principal" /></dt><dd>TZS <fmt:formatNumber value="${receipt.principal}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
        <div><dt><spring:message code="repayment.interest" /></dt><dd>TZS <fmt:formatNumber value="${receipt.interest}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
        <div><dt><spring:message code="repayment.channel" /></dt><dd><spring:message code="repayment.channel.${receipt.channel}" /></dd></div>
        <div><dt><spring:message code="repayment.channelReference" /></dt><dd><c:out value="${receipt.channelReference}" /></dd></div>
        <div><dt><spring:message code="repayment.postedAt" /></dt><dd><c:out value="${appTime:format(receipt.postedAt)}" /></dd></div>
        <c:if test="${not empty receipt.reason}"><div><dt><spring:message code="repayment.reason" /></dt><dd><c:out value="${receipt.reason}" /></dd></div></c:if>
    </dl>
    <div class="repayment-pagination repayment-no-print">
        <a class="app-btn btn-neutral" href="<c:url value='/repayments/loans/${receipt.loanApplicationId}' />"><spring:message code="repayment.loanRecord" /></a>
        <button class="erp-icon-btn" type="button" data-repayment-print aria-label="<spring:message code='repayment.print' />" title="<spring:message code='repayment.print' />"><i data-lucide="printer" aria-hidden="true"></i></button>
        <c:if test="${not empty receipt.reverses}"><a class="app-btn btn-neutral" href="<c:url value='/repayments/loans/${receipt.loanApplicationId}/receipts/${receipt.reverses}' />"><spring:message code="repayment.original" /></a></c:if>
    </div>
</article>
<script src="<c:url value='/js/loan-repayments.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
