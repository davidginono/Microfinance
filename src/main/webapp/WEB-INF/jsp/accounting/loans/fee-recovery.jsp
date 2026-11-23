<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %><%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/loan-recording.css' />" />
<c:url value="/finance/loan-records/${loanId}" var="loanUrl" />
<c:url value="/finance/loan-records/${loanId}/fee-recovery" var="recoveryUrl" />
<spring:message code="charges.recoveryReview" var="title" />
<nav class="erp-page-path"><a href="${loanUrl}"><spring:message code="recording.loanDetails" /></a><span>›</span><span aria-current="page"><c:out value="${title}" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><c:out value="${title}" /></h1></div>
<c:if test="${not empty recoveryError}"><div class="console-message" data-message-type="error" role="alert"><spring:message code="${recoveryError}" /></div></c:if>
<c:if test="${not empty recoveryReview}">
<section class="erp-resource-section"><h2><spring:message code="${recoveryReview.approved()?'charges.recoveryApproved':'charges.recoveryPending'}" /></h2>
<div class="erp-panel-body">
<dl class="loan-topup-facts">
    <c:forEach items="${recoveryReview.amounts}" var="fee"><div><dt><spring:message code="charges.kind.${fee.key}" /> (TZS)</dt><dd><fmt:formatNumber value="${fee.value}" minFractionDigits="2" maxFractionDigits="2" /></dd></div></c:forEach>
    <div><dt><spring:message code="voucher.evidence" /></dt><dd><c:out value="${recoveryReview.evidence}" /></dd></div>
</dl>
</div></section>
<sec:authorize access="@access.has(principal,'LOAN_FEE_RECOVERY_APPROVE')">
<c:if test="${not recoveryReview.approved() and recoveryReview.makerId ne recoveryActor}">
<form action="${recoveryUrl}/${recoveryReview.id}/approve" method="post" class="accounting-create-form" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
<section class="erp-resource-section"><h2><spring:message code="charges.recoveryApprove" /></h2><div class="loan-form-grid erp-panel-body">
<label><spring:message code="voucher.date" /><input class="aws-control" name="date" type="date" value="<c:out value='${recoveryDate}' />" required /></label>
<label><spring:message code="charges.reviewReason" /><input class="aws-control" name="reason" maxlength="500" value="<c:out value='${recoveryReason}' />" required /></label>
</div></section>
<div class="accounting-actions accounting-form-actions"><button class="app-btn btn-launch" type="submit"><spring:message code="charges.recoveryApprove" /></button></div>
</form></c:if></sec:authorize></c:if>
<sec:authorize access="@access.has(principal,'LOAN_FEE_RECOVERY_SUBMIT')">
<c:if test="${empty recoveryReview or not recoveryReview.approved()}">
<form action="${recoveryUrl}" method="post" class="accounting-create-form" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="<c:out value='${recoveryForm.requestKey}' />" />
<section class="erp-resource-section"><h2><spring:message code="charges.recoverySubmit" /></h2><div class="loan-form-grid erp-panel-body">
    <div data-loan-choice data-category="MANAGEMENT" data-choice-url="${recoveryUrl}/accounts">
        <label><spring:message code="charges.recoveryHolding" /><select class="aws-control" name="holdingAccountId" data-choice-select data-searchable="true" required>
            <option value=""><spring:message code="voucher.choose" /></option>
            <c:forEach items="${holdingAccounts}" var="account"><option value="${account.id}" ${recoveryForm.holdingAccountId eq account.id?'selected':''}><c:out value="${account.label}" /></option></c:forEach>
            <c:if test="${not empty selectedHolding}"><option value="${selectedHolding.id}" selected><c:out value="${selectedHolding.label}" /></option></c:if>
        </select></label>
        <%@ include file="choice-search.jspf" %>
    </div>
    <label><spring:message code="charges.kind.APPLICATION" /> (TZS)<input class="aws-control" name="application" type="number" min="0" step="0.01" data-amount-input="true" value="<c:out value='${recoveryForm.application}' />" /></label>
    <label><spring:message code="charges.kind.PROCESSING" /> (TZS)<input class="aws-control" name="processing" type="number" min="0" step="0.01" data-amount-input="true" value="<c:out value='${recoveryForm.processing}' />" /></label>
    <label><spring:message code="charges.recoveryEvidence" /><input class="aws-control" name="evidence" maxlength="500" required value="<c:out value='${recoveryForm.evidence}' />" /></label>
</div></section>
<div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="${loanUrl}"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch" type="submit"><spring:message code="charges.recoverySubmit" /></button></div>
</form></c:if></sec:authorize>
<script src="<c:url value='/js/loan-recording.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
