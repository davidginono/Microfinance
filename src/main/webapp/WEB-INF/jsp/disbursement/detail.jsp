<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %><%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %><%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../fragments/header.jspf" %><%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" /><link rel="stylesheet" href="<c:url value='/css/loan-recording.css' />" />
<nav class="erp-page-path"><a href="<c:url value='/disbursement/queue' />"><spring:message code="disbursement.queue.title" /></a><span>›</span><span aria-current="page"><spring:message code="simpleGroup.disburse" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="simpleGroup.disburse" /></h1></div>
<%@ include file="../fragments/staff-loan-review-summary.jspf" %>
<%@ include file="../accounting/loans/simple-group-details.jspf" %>
<c:if test="${retiredGroupApplication}"><p class="coa-error" role="alert"><spring:message code="simpleGroup.error.retired" /></p></c:if>
<c:if test="${showDisbursementPermissionMessage}"><p role="alert"><spring:message code="simpleGroup.noDisbursePermission" /></p></c:if>
<c:if test="${showDisbursementForm}">
<c:url value="/disbursement/loan-applications/${app.id}/finalize" var="postUrl" />
<form id="disbursement-post" action="${postUrl}" method="post" enctype="multipart/form-data" class="accounting-create-form" data-resource-form-page="true" data-workspace-submit="recover" data-workspace-return="disbursement-post">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="<c:out value='${disbursementRequestKey}' />" />
<section class="erp-resource-section"><h2><spring:message code="simpleGroup.payment" /></h2><div class="loan-form-grid erp-panel-body">
<label><spring:message code="simpleGroup.paymentDate" text="Payment date" /><input class="aws-control" name="disbursementDate" type="date" required value="<c:out value='${empty postingDisbursementDate?disbursementToday:postingDisbursementDate}' />" /></label>
<label><spring:message code="recording.firstPaymentDate" text="First payment date" /><input class="aws-control" name="firstRepaymentDate" type="date" required ${not empty simpleGroup?'readonly':''} value="<c:out value='${not empty simpleGroup?app.firstRepaymentDate:empty postingFirstPaymentDate?(empty app.firstRepaymentDate?firstRepaymentDateDefault:app.firstRepaymentDate):postingFirstPaymentDate}' />" /></label>
<label><spring:message code="simpleGroup.amountToPay" /> (TZS)<input class="aws-control" name="depositAmount" readonly value="<c:out value='${disbursementBaseAmount}' />" /></label>
<c:choose><c:when test="${recordedLoanApplication}"><%@ include file="../fragments/staff-disbursement-posting-fields.jspf" %><c:if test="${disbursementProofRequired}"><p><spring:message code="loan.disbursement.proofRequiredHelp" /></p></c:if></c:when><c:otherwise>
<label><spring:message code="recording.paymentReference" /><input name="disbursementReference" class="aws-control" required maxlength="100" value="${fn:escapeXml(postingDisbursementReference)}" /></label>
<label><spring:message code="simpleGroup.paymentProof" /><input type="file" name="disbursementProofFile" accept=".pdf,.png,.jpg,.jpeg" class="aws-control" ${disbursementProofRequired?"required":""} /></label>
</c:otherwise></c:choose>
<label><spring:message code="simpleGroup.notes" text="Notes" /><textarea class="aws-control" name="disbursementNotes" maxlength="500"><c:out value="${postingDisbursementNotes}" /></textarea></label>
<c:if test="${disbursementOtpEnabled}"><div data-staff-funding-code data-url="<c:url value='/disbursement/loan-applications/${app.id}/request-disbursement-otp' />" data-error="<spring:message code='simpleGroup.codeError' htmlEscape='true' />" data-sent="<spring:message code='simpleGroup.codeSent' htmlEscape='true' />"><label><spring:message code="simpleGroup.staffCode" /><input name="disbursementOtpCode" class="aws-control" inputmode="numeric" autocomplete="one-time-code" maxlength="10" required /></label><button type="button" class="app-btn btn-neutral" data-send-staff-code><spring:message code="simpleGroup.sendCode" /></button><p role="status" data-code-status></p></div></c:if>
</div></section>
<label class="aws-settings-checkbox-row"><input type="checkbox" required class="aws-settings-checkbox" /><span><spring:message code="simpleGroup.disburseConfirm" /></span></label>
<c:if test="${not empty error}"><p class="coa-error" role="alert"><c:out value="${error}" /></p></c:if>
<div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/disbursement/queue' />"><spring:message code="common.cancel" /></a><button type="submit" class="app-btn btn-launch"><spring:message code="simpleGroup.disburse" /></button></div>
</form></c:if>
<script src="<c:url value='/js/loan-recording.js' />" defer></script><script src="<c:url value='/js/staff-funding-code.js' />" defer></script>
<%@ include file="../accounting/accounting-form-support.jspf" %><%@ include file="../fragments/footer.jspf" %>
