<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />">
    <a href="<c:url value='${payoffLoanUrl}' />"><spring:message code="recording.loanDetails" /></a>
    <span aria-hidden="true">›</span>
    <span aria-current="page"><spring:message code="prepayment.payoffQuote" /></span>
</nav>
<div class="accounting-workspace accounting-servicing-workspace" data-resource-form-page>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="prepayment.payoffQuote" /></h1></div>
    <c:if test="${payoffSaved or payoffReversed}"><p class="console-message" data-message-type="success" role="status"><spring:message code="${payoffReversed?'prepayment.payoffReversed':'prepayment.payoffSaved'}" /></p></c:if>
    <c:if test="${empty payoffPosting or not empty payoffPosting.reversedBy}">
    <section class="erp-resource-section erp-form-wrap">
        <h2><spring:message code="voucher.date" /></h2>
        <form method="get" class="waiver-date-form">
            <label><span class="sr-only"><spring:message code="voucher.date" /></span>
                <input class="aws-control" name="date" type="date" value="${payoffDate}" max="${payoffLatest}" required />
            </label>
            <button class="app-btn btn-neutral" type="submit"><spring:message code="fs.apply" /></button>
        </form>
    </section>
    </c:if>
    <c:if test="${not empty payoffError}">
        <p class="console-message" data-message-type="error" role="alert"><spring:message code="${payoffError}" /></p>
    </c:if>
    <c:if test="${not empty payoff}">
        <section class="accounting-section erp-detail-card">
            <h2><spring:message code="prepayment.payoffSummary" /></h2>
            <dl class="erp-detail-facts">
                <div><dt><spring:message code="loan.loanId" /></dt><dd><c:out value="${payoff.loanNumber}" /></dd></div>
                <div><dt><spring:message code="recording.principal" /> (TZS)</dt><dd><fmt:formatNumber value="${payoff.principal}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
                <div><dt><spring:message code="prepayment.payoffInterestDue" /> (TZS)</dt><dd><fmt:formatNumber value="${payoff.interest}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
                <div><dt><spring:message code="prepayment.payoffCharges" /> (TZS)</dt><dd><fmt:formatNumber value="${payoff.charges}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
                <div><dt><spring:message code="prepayment.payoffInterestCancelled" /> (TZS)</dt><dd><fmt:formatNumber value="${payoff.futureInterest}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
                <div><dt><spring:message code="prepayment.payoffCredit" /> (TZS)</dt><dd><fmt:formatNumber value="${payoff.interestCredit}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
                <div><dt><spring:message code="prepayment.payoffCashDue" /> (TZS)</dt><dd><fmt:formatNumber value="${payoff.total}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
                <c:if test="${payoff.refundDue gt 0}"><div><dt><spring:message code="prepayment.payoffRefundDue" /> (TZS)</dt><dd><fmt:formatNumber value="${payoff.refundDue}" minFractionDigits="2" maxFractionDigits="2" /></dd></div></c:if>
            </dl>
        </section>
        <sec:authorize access="@access.has(principal,'LOAN_RECORDING_POST') and @access.has(principal,'ACCOUNTING_BUSINESS_CREATE')">
            <c:if test="${payoff.monthly and not payoffDate.isAfter(payoffToday)}">
                <c:url value="${payoffLoanUrl}/full-payoff" var="postPayoffUrl" />
                <c:if test="${payoff.total gt 0 or payoff.refundDue gt 0}">
                    <form method="get" action="${postPayoffUrl}" class="waiver-date-form">
                        <input type="hidden" name="date" value="${payoffDate}" />
                        <label><spring:message code="voucher.chooseAccount" />
                            <input class="aws-control" name="${payoff.total gt 0?'moneySearch':'payableSearch'}" type="search" maxlength="100" />
                        </label>
                        <button class="app-btn btn-neutral" type="submit"><spring:message code="common.search" /></button>
                    </form>
                </c:if>
                <form method="post" action="${postPayoffUrl}">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="requestKey" value="${payoffRequestKey}" />
                    <input type="hidden" name="date" value="${payoffDate}" />
                    <input type="hidden" name="sequence" value="${payoff.sequence}" />
                    <input type="hidden" name="cash" value="${payoff.total.toPlainString()}" />
                    <input type="hidden" name="refund" value="${payoff.refundDue.toPlainString()}" />
                    <section class="erp-resource-section erp-form-wrap">
                        <h2><spring:message code="prepayment.payoffPost" /></h2>
                        <div class="waiver-fields">
                            <c:if test="${payoff.total gt 0}">
                                <label><spring:message code="voucher.moneyAccount" />
                                    <select name="moneyAccount" class="aws-control" required>
                                        <option value=""><spring:message code="voucher.choose" /></option>
                                        <c:forEach items="${payoffMoneyAccounts}" var="account"><option value="${account.id}" ${account.id eq selectedPayoffMoney?'selected':''}><c:out value="${account.label}" /></option></c:forEach>
                                    </select>
                                </label>
                                <label><spring:message code="voucher.evidence" />
                                    <input name="reference" class="aws-control" maxlength="100" required value="<c:out value='${payoffReference}' />" />
                                </label>
                            </c:if>
                            <c:if test="${payoff.refundDue gt 0}">
                                <label><spring:message code="prepayment.payoffPayable" />
                                    <select name="payableAccount" class="aws-control" required>
                                        <option value=""><spring:message code="voucher.choose" /></option>
                                        <c:forEach items="${payoffPayableAccounts}" var="account"><option value="${account.id}" ${account.id eq selectedPayoffPayable?'selected':''}><c:out value="${account.label}" /></option></c:forEach>
                                    </select>
                                </label>
                            </c:if>
                            <label class="accounting-servicing-confirm"><input type="checkbox" name="confirmed" value="true" required /> <span><spring:message code="prepayment.payoffConfirm" /></span></label>
                        </div>
                    </section>
                    <div class="accounting-actions waiver-actions"><button class="app-btn btn-launch" type="submit"><spring:message code="prepayment.payoffPost" /></button></div>
                </form>
            </c:if>
        </sec:authorize>
    </c:if>
    <c:if test="${not empty payoffPosting}">
        <section class="accounting-section erp-detail-card">
            <h2><spring:message code="prepayment.payoffRecorded" /></h2>
            <dl class="erp-detail-facts">
                <div><dt><spring:message code="voucher.date" /></dt><dd><c:out value="${payoffPosting.date}" /></dd></div>
                <div><dt><spring:message code="prepayment.payoffCashReceived" /> (TZS)</dt><dd><fmt:formatNumber value="${payoffPosting.cash}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
                <div><dt><spring:message code="prepayment.payoffReturned" /> (TZS)</dt><dd><fmt:formatNumber value="${payoffPosting.returned}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
                <c:if test="${payoffPosting.refund gt 0}"><div><dt><spring:message code="prepayment.payoffRefund" /> (TZS)</dt><dd><fmt:formatNumber value="${payoffPosting.refund}" minFractionDigits="2" maxFractionDigits="2" /></dd></div></c:if>
            </dl>
            <div class="accounting-actions">
                <c:if test="${not empty payoffPosting.journal}">
                    <c:url value="/finance/vouchers/${payoffPosting.cash gt 0?'receipts':'journals'}/${payoffPosting.journal}" var="payoffVoucherUrl" />
                    <a class="app-btn btn-neutral" href="${payoffVoucherUrl}"><spring:message code="prepayment.payoffVoucher" /></a>
                </c:if>
                <sec:authorize access="@access.has(principal,'ACCOUNTING_BUSINESS_CREATE')">
                    <c:if test="${empty payoffPosting.reversedBy and payoffPosting.refund gt 0 and empty payoffPosting.refundVoucher}">
                        <c:url value="/finance/vouchers/payments/new" var="refundUrl"><c:param name="payoffRefundId" value="${payoffPosting.id}" /></c:url>
                        <a class="app-btn btn-neutral" href="${refundUrl}"><spring:message code="prepayment.payoffRefundRecord" /></a>
                    </c:if>
                </sec:authorize>
                <c:if test="${not empty payoffPosting.refundVoucher}"><a class="app-btn btn-neutral" href="<c:url value='/finance/vouchers/payments/${payoffPosting.refundVoucher}' />"><spring:message code="prepayment.payoffRefundVoucher" /></a></c:if>
            </div>
        </section>
        <sec:authorize access="@access.has(principal,'LOAN_RECORDING_REVERSE') and @access.has(principal,'ACCOUNTING_BUSINESS_REVERSE')">
            <c:if test="${empty payoffPosting.reversedBy and empty payoffPosting.refundVoucher}">
                <form action="<c:url value='${payoffLoanUrl}/full-payoff/reverse' />" method="post">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="original" value="${payoffPosting.id}" />
                    <input type="hidden" name="requestKey" value="${payoffRequestKey}" />
                    <section class="erp-resource-section erp-form-wrap">
                        <h2><spring:message code="prepayment.payoffReverse" /></h2>
                        <div class="waiver-fields">
                            <label><spring:message code="voucher.date" /><input name="date" type="date" class="aws-control" value="${payoffToday}" min="${payoffPosting.date}" max="${payoffToday}" required /></label>
                            <label><spring:message code="prepayment.reason" /><textarea name="reason" class="aws-control" maxlength="500" required></textarea></label>
                        </div>
                    </section>
                    <div class="accounting-actions waiver-actions"><button class="app-btn btn-neutral" type="submit"><spring:message code="prepayment.payoffReverse" /></button></div>
                </form>
            </c:if>
        </sec:authorize>
    </c:if>
</div>
<%@ include file="../fragments/footer.jspf" %>
