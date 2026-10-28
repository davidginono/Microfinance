<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<template data-resource-details-page></template>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/loan-repayments.css' />" /><link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="repayment-workspace">
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title" id="repayments-loan-title"><spring:message code="repayment.title" /></h1><p><spring:message code="repayment.asOf" /> <c:out value="${ledger.asOf}" /></p></div>
    <c:if test="${not empty repaymentError}"><div class="repayment-error" role="alert"><spring:message code="${repaymentError}" /></div></c:if>
    <c:if test="${ledger.financialState=='WRITTEN_OFF' or ledger.financialState=='DISBURSEMENT_REVERSED'}"><p role="status"><span data-resource-status="${ledger.financialState}"><spring:message code="recording.status.${ledger.financialState}" /></span>. <spring:message code="adjustment.historicalSchedule" /></p></c:if>
    <section class="repayment-section"><h2><spring:message code="recording.loanDetails" /></h2><dl class="repayment-facts erp-detail-facts">
        <div><dt><spring:message code="loan.loanId" /></dt><dd><c:out value="${ledger.loanId}" /></dd></div>
        <div><dt><spring:message code="repayment.principalOutstanding" /></dt><dd>TZS <fmt:formatNumber value="${ledger.outstandingPrincipal}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
        <div><dt><spring:message code="repayment.principalDue" /></dt><dd>TZS <fmt:formatNumber value="${ledger.duePrincipal}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
        <div><dt><spring:message code="repayment.interestDue" /></dt><dd>TZS <fmt:formatNumber value="${ledger.dueInterest}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
        <div><dt><spring:message code="repayment.principalPaid" /></dt><dd>TZS <fmt:formatNumber value="${ledger.principalPaid}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
        <div><dt><spring:message code="repayment.interestPaid" /></dt><dd>TZS <fmt:formatNumber value="${ledger.interestPaid}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
        <div><dt><spring:message code="repayment.futureInterest" /></dt><dd>TZS <fmt:formatNumber value="${ledger.futureInterest}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
    </dl></section>
    <sec:authorize access="@access.has(principal, 'LOAN_REPAYMENTS_CREATE')">
        <c:if test="${ledger.outstandingPrincipal + ledger.dueInterest > 0 and ledger.financialState!='WRITTEN_OFF' and ledger.financialState!='DISBURSEMENT_REVERSED'}">
            <section class="repayment-section"><h2><spring:message code="repayment.post" /></h2>
                <c:url var="postUrl" value="/repayments/loans/${ledger.id}/payments" />
                <form:form method="post" action="${postUrl}" modelAttribute="paymentForm" cssClass="repayment-form" data-repayment-form="true">
                    <form:hidden path="requestKey" />
                    <label for="amount"><spring:message code="common.amount" /> (TZS)<form:input path="amount" id="amount" type="number" data-amount-input="true" min="0.01" step="0.01" required="required" cssClass="aws-control" /></label>
                    <label for="paymentDate"><spring:message code="repayment.paymentDate" /><form:input path="paymentDate" id="paymentDate" type="date" max="${today}" required="required" cssClass="aws-control" /></label>
                    <label for="channel"><spring:message code="repayment.channel" /><form:select path="channel" id="channel" required="required" cssClass="aws-control"><form:option value=""><spring:message code="repayment.selectChannel" /></form:option><form:option value="CASH"><spring:message code="repayment.channel.CASH" /></form:option><form:option value="BANK"><spring:message code="repayment.channel.BANK" /></form:option><form:option value="MOBILE_MONEY"><spring:message code="repayment.channel.MOBILE_MONEY" /></form:option></form:select></label>
                    <label for="reference"><spring:message code="repayment.channelReference" /><form:input path="reference" id="reference" maxlength="100" required="required" cssClass="aws-control" /></label>
                    <label class="repayment-confirm"><form:checkbox path="confirmed" required="required" /><spring:message code="repayment.confirmReceived" /></label>
                    <button type="submit" class="app-btn btn-launch" data-repayment-submit><i data-lucide="check" aria-hidden="true"></i><spring:message code="repayment.post" /></button>
                </form:form>
            </section>
        </c:if>
    </sec:authorize>
    <c:set var="latestReceipt" value="${ledger.latestReceipt}" />
    <section class="erp-table-wrap" data-aws-table-region data-resource-table="repayments-loan-1" data-resource-split>
        <div class="app-table-titlebar"><h2><spring:message code="repayment.history" /></h2></div>
        <div class="erp-table-scroll" data-view-position-key="repayment-history-${ledger.id}">
            <table class="erp-table"><thead><tr><th data-table-order="reference"><spring:message code="repayment.receipt" /></th><th data-table-order="date"><spring:message code="repayment.paymentDate" /></th><th data-table-order="status"><spring:message code="common.status" /></th><th class="money" data-table-order="amount"><spring:message code="common.amount" /> (TZS)</th><th class="money" data-table-order="principal"><spring:message code="repayment.principal" /> (TZS)</th><th class="money" data-table-order="interest"><spring:message code="repayment.interest" /> (TZS)</th><th data-table-actions-header><spring:message code="common.actions" /></th></tr></thead><tbody>
                <c:forEach items="${ledger.history.content}" var="row"><tr>
                    <td class="repayment-reference"><c:out value="${row.reference}" /></td><td><c:out value="${appTime:display(row.paymentDate)}" /></td>
                    <td><c:choose><c:when test="${not empty row.reversedBy}"><span data-resource-status="REVERSED"><spring:message code="repayment.reversed" /></span></c:when><c:otherwise><span data-resource-status="${row.kind=='PAYMENT'?'POSTED':row.kind}"><spring:message code="repayment.kind.${row.kind}" /></span></c:otherwise></c:choose></td>
                    <td class="money"><fmt:formatNumber value="${row.amount}" minFractionDigits="2" maxFractionDigits="2" /></td><td class="money"><fmt:formatNumber value="${row.principal}" minFractionDigits="2" maxFractionDigits="2" /></td><td class="money"><fmt:formatNumber value="${row.interest}" minFractionDigits="2" maxFractionDigits="2" /></td>
                    <td><a class="app-btn btn-neutral" href="<c:url value='/repayments/loans/${ledger.id}/receipts/${row.id}' />"><spring:message code="repayment.receipt" /></a></td>
                </tr></c:forEach>
                <c:if test="${empty ledger.history.content}"><tr><td colspan="7" class="erp-table-empty"><spring:message code="repayment.noPayments" /></td></tr></c:if>
            </tbody></table>
        </div>
        <div class="repayment-pagination" data-table-pagination data-table-for="repayments-loan-1" data-page="${ledger.history.number}">
            <c:if test="${ledger.history.hasPrevious()}"><c:url var="previous" value="/repayments/loans/${ledger.id}"><c:param name="page" value="${ledger.history.number - 1}" /><c:param name="schedulePage" value="${ledger.installments.number}" /></c:url><a class="app-btn btn-neutral" href="${previous}" data-page-direction="previous"><spring:message code="common.previous" /></a></c:if>
            <span><spring:message code="repayment.page" /> <c:out value="${ledger.history.number + 1}" /></span>
            <c:if test="${ledger.history.hasNext()}"><c:url var="next" value="/repayments/loans/${ledger.id}"><c:param name="page" value="${ledger.history.number + 1}" /><c:param name="schedulePage" value="${ledger.installments.number}" /></c:url><a class="app-btn btn-neutral" href="${next}" data-page-direction="next"><spring:message code="common.next" /></a></c:if>
        </div>
    </section>
    <sec:authorize access="@access.has(principal, 'LOAN_REPAYMENTS_REVERSE')">
        <c:if test="${not empty latestReceipt and ledger.financialState!='WRITTEN_OFF' and ledger.financialState!='DISBURSEMENT_REVERSED'}"><section class="repayment-section"><h2><spring:message code="repayment.reverseLatest" /></h2>
            <p class="repayment-reference"><c:out value="${latestReceipt.reference}" /> | TZS <fmt:formatNumber value="${latestReceipt.amount}" minFractionDigits="2" maxFractionDigits="2" /> | <c:out value="${appTime:display(latestReceipt.paymentDate)}" /></p>
            <form method="post" action="<c:url value='/repayments/loans/${ledger.id}/payments/${ledger.latestPaymentId}/reverse' />" class="repayment-form" data-repayment-form>
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="${reversalRequestKey}" />
                <label for="reason"><spring:message code="repayment.reason" /><input id="reason" name="reason" class="aws-control" maxlength="500" required value="<c:out value='${reversalReason}' />" /></label>
                <label class="repayment-confirm"><input type="checkbox" name="confirmed" value="true" required /><spring:message code="repayment.confirmReverse" /></label>
                <button class="app-btn btn-neutral" type="submit" data-repayment-submit><i data-lucide="undo-2" aria-hidden="true"></i><spring:message code="repayment.reverse" /></button>
            </form>
        </section></c:if>
    </sec:authorize>
    <section class="erp-table-wrap" data-aws-table-region data-resource-table="repayments-loan-2" data-table-page-param="schedulePage">
        <div class="app-table-titlebar"><h2><spring:message code="repayment.schedule" /></h2></div>
        <div class="erp-table-scroll" data-view-position-key="repayment-installments-${ledger.id}"><table class="erp-table"><thead><tr><th data-table-order="number">#</th><th data-table-order="due"><spring:message code="repayment.dueDate" /></th><th class="money" data-table-order="beginning"><spring:message code="repayment.beginningBalance" /> (TZS)</th><th class="money" data-table-order="payment"><spring:message code="repayment.scheduledPayment" /> (TZS)</th><th class="money" data-table-order="principal"><spring:message code="repayment.principal" /> (TZS)</th><th class="money" data-table-order="interest"><spring:message code="repayment.interest" /> (TZS)</th><th class="money" data-table-order="ending"><spring:message code="repayment.endingBalance" /> (TZS)</th><th class="money" data-table-order="principalPaid"><spring:message code="repayment.principalPaid" /> (TZS)</th><th class="money" data-table-order="interestPaid"><spring:message code="repayment.interestPaid" /> (TZS)</th></tr></thead><tbody>
            <c:forEach items="${ledger.installments.content}" var="row"><tr><td><c:out value="${row.number}" /></td><td><c:out value="${appTime:display(row.dueDate)}" /></td><td class="money"><fmt:formatNumber value="${row.beginningBalance}" minFractionDigits="2" maxFractionDigits="2" /></td><td class="money"><fmt:formatNumber value="${row.scheduledPayment}" minFractionDigits="2" maxFractionDigits="2" /></td><td class="money"><fmt:formatNumber value="${row.principal}" minFractionDigits="2" maxFractionDigits="2" /></td><td class="money"><fmt:formatNumber value="${row.interest}" minFractionDigits="2" maxFractionDigits="2" /></td><td class="money"><fmt:formatNumber value="${row.endingBalance}" minFractionDigits="2" maxFractionDigits="2" /></td><td class="money"><fmt:formatNumber value="${row.principalPaid}" minFractionDigits="2" maxFractionDigits="2" /></td><td class="money"><fmt:formatNumber value="${row.interestPaid}" minFractionDigits="2" maxFractionDigits="2" /></td></tr></c:forEach>
        </tbody></table></div>
        <div class="repayment-pagination" data-table-pagination data-table-for="repayments-loan-2" data-page="${ledger.installments.number}">
            <c:if test="${ledger.installments.hasPrevious()}"><c:url var="previousSchedule" value="/repayments/loans/${ledger.id}"><c:param name="schedulePage" value="${ledger.installments.number - 1}" /><c:param name="page" value="${ledger.history.number}" /></c:url><a class="app-btn btn-neutral" href="${previousSchedule}" data-page-direction="previous"><spring:message code="common.previous" /></a></c:if>
            <span><spring:message code="repayment.page" /> <c:out value="${ledger.installments.number + 1}" /></span>
            <c:if test="${ledger.installments.hasNext()}"><c:url var="nextSchedule" value="/repayments/loans/${ledger.id}"><c:param name="schedulePage" value="${ledger.installments.number + 1}" /><c:param name="page" value="${ledger.history.number}" /></c:url><a class="app-btn btn-neutral" href="${nextSchedule}" data-page-direction="next"><spring:message code="common.next" /></a></c:if>
        </div>
    </section>
    <c:if test="${not empty ledger.journal}"><section class="erp-table-wrap" data-aws-table-region data-resource-table="repayments-loan-3">
        <div class="app-table-titlebar"><h2><spring:message code="repayment.journal" /></h2></div>
        <div class="erp-table-scroll erp-table-scroll-sm"><table class="erp-table"><thead><tr><th data-table-order="date"><spring:message code="repayment.paymentDate" /></th><th data-table-order="account"><spring:message code="repayment.account" /></th><th class="money" data-table-order="debit"><spring:message code="repayment.debit" /> (TZS)</th><th class="money" data-table-order="credit"><spring:message code="repayment.credit" /> (TZS)</th></tr></thead><tbody>
            <c:forEach items="${ledger.journal}" var="line"><tr><td><c:out value="${appTime:display(line.date)}" /></td><td><spring:message code="repayment.account.${line.account}" /></td><td class="money"><fmt:formatNumber value="${line.debit}" minFractionDigits="2" maxFractionDigits="2" /></td><td class="money"><fmt:formatNumber value="${line.credit}" minFractionDigits="2" maxFractionDigits="2" /></td></tr></c:forEach>
        </tbody></table></div>
    </section></c:if>
</div>
<script src="<c:url value='/js/loan-repayments.js' />" defer></script>
<%@ include file="interest-waivers.jspf" %><%@ include file="../fragments/footer.jspf" %>
