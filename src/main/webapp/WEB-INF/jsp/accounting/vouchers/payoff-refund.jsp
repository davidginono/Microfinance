<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<c:url value="/finance/vouchers/payments" var="paymentUrl" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />">
    <a href="${paymentUrl}"><spring:message code="voucher.register.PAYMENT" /></a><span aria-hidden="true">›</span>
    <span aria-current="page"><spring:message code="prepayment.payoffRefundRecord" /></span>
</nav>
<div class="accounting-workspace accounting-servicing-workspace" data-resource-form-page>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="prepayment.payoffRefundRecord" /></h1></div>
    <form:form modelAttribute="voucherForm" method="post" action="${paymentUrl}">
        <form:hidden path="requestKey" /><form:hidden path="payoffRefundId" /><form:hidden path="party" />
        <form:hidden path="rows[0].amount" /><form:hidden path="rows[0].debitAccountId" /><form:hidden path="rows[0].description" />
        <section class="erp-resource-section erp-form-wrap">
            <h2><spring:message code="prepayment.payoffRefund" /></h2>
            <div class="waiver-fields">
                <form:errors path="*" element="p" cssClass="field-error-text" />
                <dl class="erp-detail-facts">
                    <div><dt><spring:message code="voucher.paidTo" /></dt><dd><c:out value="${voucherForm.party}" /></dd></div>
                    <div><dt><spring:message code="voucher.amount" /> (TZS)</dt><dd><fmt:formatNumber value="${voucherForm.rows[0].amount}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
                </dl>
                <label><spring:message code="voucher.date" /><form:input path="effectiveDate" type="date" cssClass="aws-control" required="required" /></label>
                <label><spring:message code="voucher.moneyAccount" />
                    <form:select path="moneyAccountId" cssClass="aws-control" required="required">
                        <form:option value=""><spring:message code="voucher.choose" /></form:option>
                        <c:forEach items="${moneyAccounts}" var="account"><form:option value="${account.id}"><c:out value="${account.code} · ${account.name}" /></form:option></c:forEach>
                    </form:select>
                </label>
                <label><spring:message code="voucher.evidence" /><form:input path="evidence" cssClass="aws-control" maxlength="500" required="required" /></label>
            </div>
        </section>
        <div class="accounting-actions waiver-actions"><a class="app-btn btn-neutral" href="${paymentUrl}"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch" type="submit"><spring:message code="prepayment.payoffRefundPost" /></button></div>
    </form:form>
</div>
<%@ include file="../../fragments/footer.jspf" %>
