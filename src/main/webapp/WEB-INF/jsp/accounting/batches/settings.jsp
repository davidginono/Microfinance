<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" /><link rel="stylesheet" href="<c:url value='/css/receipt-batches.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/batches/receipts' />"><spring:message code="batch.receipts" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><spring:message code="batch.settings" /></span></nav>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="batch.settings" /></h1></div>
<div class="accounting-workspace">
<p><spring:message code="batch.settingsHelp" /></p>
<c:if test="${not empty errorKey}"><p class="coa-error" role="alert"><spring:message code="${errorKey}" /></p></c:if><c:if test="${param.saved=='true'}"><p role="status"><spring:message code="batch.savedStatus" /></p></c:if>
<c:if test="${not empty policy}"><section class="batch-settings-summary"><h2><spring:message code="batch.currentPolicy" /> ${policy.version}</h2><p><spring:message code="batch.order.${policy.allocationOrder}" /> · <c:out value="${policy.effectiveFrom}" /></p><p><c:out value="${policy.interestAccount}" /></p><p><c:out value="${policy.reason}" /></p></section></c:if>
<sec:authorize access="@access.has(principal,'ACCOUNTING_ACCOUNTS_UPDATE')">
<form method="post" action="<c:url value='/finance/batches/receipts/settings' />" class="accounting-create-form batch-settings" data-batch-settings>
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="${empty policyForm?requestKey:policyForm.requestKey}" /><input type="hidden" name="expectedVersion" value="${empty policyForm?policy.id:policyForm.expectedVersion}" />
<label><spring:message code="batch.effectiveFrom" /><input type="date" name="effectiveFrom" class="aws-control" required value="${empty policyForm?today:policyForm.effectiveFrom}" /></label>
<div class="batch-policy"><span class="batch-policy-label" id="allocation-policy-label"><spring:message code="batch.allocationPolicy" /></span><input type="hidden" name="allocationOrder" value="INTEREST_FIRST" /><p class="batch-policy-value" aria-labelledby="allocation-policy-label"><spring:message code="batch.order.INTEREST_FIRST" /></p><p class="batch-hint"><spring:message code="batch.statutoryOrder" /></p></div>
<label><spring:message code="batch.interestAccount" /><select name="interestAccountId" class="aws-control" required data-batch-choice="settings-interest" data-searchable="true"><option value=""><spring:message code="batch.choose" /></option><c:if test="${not empty policy}"><option value="${policy.interestAccountId}" selected><c:out value="${policy.interestAccount}" /></option></c:if></select></label>
<label><spring:message code="batch.reason" /><textarea name="reason" class="aws-control" maxlength="500" required><c:out value="${policyForm.reason}" /></textarea></label>
<div class="accounting-actions batch-settings-actions"><button class="app-btn btn-launch"><spring:message code="batch.saveSettings" /></button></div></form></sec:authorize>
<%@ include file="messages.jspf" %></div><script src="<c:url value='/js/receipt-batches.js' />" defer></script><%@ include file="../../fragments/footer.jspf" %>
