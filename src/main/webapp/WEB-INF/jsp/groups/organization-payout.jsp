<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %><%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../fragments/header.jspf" %><%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="orgPayout.title" /></h1></div>
<section class="erp-resource-section"><h2><spring:message code="group.organizationLoan" /></h2><div class="erp-panel-body"><dl class="erp-detail-facts">
<div><dt><spring:message code="group.name" /></dt><dd><c:out value="${packet.organization}" /></dd></div>
<div><dt><spring:message code="group.loan" /></dt><dd><c:out value="${packet.number}" /></dd></div>
<div><dt><spring:message code="group.status" /></dt><dd><spring:message code="loan.status.${packet.loanStatus}" /></dd></div>
<div><dt><spring:message code="orgAgreement.signatures" /></dt><dd><c:out value="${packet.signedCount}" /> / <c:out value="${packet.requiredSignatures}" /></dd></div>
</dl><p><spring:message code="orgPayout.instructionOnly" /></p></div></section>
<section class="erp-resource-section"><h2><spring:message code="orgAgreement.payout" /></h2><div class="erp-panel-body"><dl class="erp-detail-facts">
<div><dt><spring:message code="group.principal" /></dt><dd><fmt:formatNumber value="${packet.principal}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
<div><dt><spring:message code="orgAgreement.recordedChargeDeductions" /></dt><dd><fmt:formatNumber value="${packet.deductions}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
<div><dt><spring:message code="orgAgreement.recordedNetCashEstimate" /></dt><dd><fmt:formatNumber value="${packet.net}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
<div><dt><spring:message code="group.provider" /></dt><dd><c:out value="${packet.destination.provider}" /></dd></div>
<div><dt><spring:message code="orgAgreement.holder" /></dt><dd><c:out value="${packet.destination.accountHolderName}" /></dd></div>
<div><dt><spring:message code="orgAgreement.account" /></dt><dd><c:out value="${packet.destination.accountIdentifier}" /></dd></div>
</dl></div></section>
<c:if test="${not empty payoutError}"><p role="alert" class="coa-error"><spring:message code="${payoutError}" /></p></c:if>
<c:if test="${payoutCodeSent}"><p role="status"><spring:message code="group.otpSent" /></p></c:if>
<sec:authorize access="@access.has(principal,'MEMBER_LOANS_UPDATE')" var="canConfirmPayout" />
<c:choose><c:when test="${not empty packet.confirmation}"><section class="erp-resource-section"><h2><spring:message code="orgPayout.confirmed" /></h2><div class="erp-panel-body"><dl class="erp-detail-facts"><div><dt><spring:message code="orgPayout.confirmedBy" /></dt><dd><c:out value="${packet.confirmation.name}" /> (<c:out value="${packet.confirmation.number}" />)</dd></div><div><dt><spring:message code="orgPayout.confirmedAt" /></dt><dd><c:out value="${appTime:display(packet.confirmation.at)}" /></dd></div></dl></div></section></c:when>
<c:when test="${not packet.canConfirm()}"><p role="status"><spring:message code="group.error.payoutClosed" /></p></c:when><c:otherwise>
<c:if test="${not packet.signatureReady}"><p role="alert" class="coa-error"><spring:message code="group.error.signature" /></p></c:if>
<c:if test="${not canConfirmPayout}"><p role="alert" class="coa-error"><spring:message code="group.error.signingPermission" /></p></c:if>
<c:if test="${packet.otpRequired}"><c:url value="/app/group-loans/${packet.cycle}/organization-payout/code" var="payoutCodeUrl" /><form action="${payoutCodeUrl}" method="post"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="packetHash" value="<c:out value='${packet.hash}' />" /><div class="accounting-actions"><button type="submit" class="app-btn btn-neutral" ${not packet.signatureReady or not canConfirmPayout?'disabled':''}><spring:message code="group.requestCode" /></button></div></form></c:if>
<c:url value="/app/group-loans/${packet.cycle}/organization-payout/confirm" var="payoutConfirmUrl" /><form action="${payoutConfirmUrl}" method="post" class="accounting-create-form"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="packetHash" value="<c:out value='${packet.hash}' />" /><section class="accounting-form accounting-create-card"><h2><spring:message code="orgPayout.confirm" /></h2><div class="erp-panel-body"><c:if test="${packet.otpRequired}"><label><spring:message code="group.otpCode" /><input name="otpCode" class="aws-control" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" required /></label></c:if><label class="aws-settings-checkbox-row"><input name="accepted" type="checkbox" value="true" required class="aws-settings-checkbox" /><span class="aws-settings-checkbox-copy"><spring:message code="orgPayout.acceptance" /></span></label></div></section><div class="accounting-actions accounting-form-actions"><button class="app-btn btn-launch" type="submit" ${not packet.signatureReady or not canConfirmPayout?'disabled':''}><spring:message code="orgPayout.confirm" /></button></div></form>
</c:otherwise></c:choose>
<%@ include file="../fragments/footer.jspf" %>
