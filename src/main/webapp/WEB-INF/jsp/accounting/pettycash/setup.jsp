<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" /><link rel="stylesheet" href="<c:url value='/css/accounting-vouchers.css' />" />
<%@ include file="../../fragments/alerts.jspf" %>
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/accounts' />"><spring:message code="finance.nav.accounts" /></a><span aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><spring:message code="petty.setup" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="petty.setup" /></h1></div>
<div class="accounting-workspace accounting-create-workspace" data-resource-form-page>
<c:choose><c:when test="${not empty fund}"><section class="erp-resource-section"><h2><spring:message code="petty.account" /></h2><dl class="erp-detail-facts"><div><dt><spring:message code="petty.account" /></dt><dd><c:out value="${fund.name}" /></dd></div><div><dt><spring:message code="petty.starts" /></dt><dd><c:out value="${appTime:display(fund.startsOn)}" /></dd></div><div><dt><spring:message code="voucher.evidence" /></dt><dd><c:out value="${fund.evidence}" /></dd></div></dl></section></c:when><c:otherwise>
<sec:authorize access="@access.has(principal,'ACCOUNTING_ACCOUNTS_UPDATE')">
<form method="post" action="<c:url value='/finance/petty-cash/setup' />" class="accounting-create-form" data-petty-setup>
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
<section class="accounting-form accounting-create-card"><h2><spring:message code="petty.account" /></h2><div class="accounting-grid">
<div class="voucher-field"><label for="petty-account"><spring:message code="petty.account" /></label><div class="voucher-account-choice"><input type="hidden" name="accountId" value="<c:out value='${accountId}' />" /><span data-account-label hidden><c:out value="${selectedAccountName}" /></span><select id="petty-account" class="aws-control" data-searchable="true" data-petty-account-choice data-url="<c:url value='/finance/petty-cash/accounts' />" data-error="<spring:message code='voucher.loadError' />"><option value=""><spring:message code="voucher.chooseAccount" /></option></select></div></div>
<label><spring:message code="voucher.evidence" /><input name="evidence" class="aws-control" value="<c:out value='${evidence}' />" required maxlength="500" /></label>
</div><p><spring:message code="petty.setupRequirement" /></p></section>
<c:if test="${not empty setupError}"><div class="console-message" data-message-type="error" role="alert"><spring:message code="${setupError}" /></div></c:if>
<div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/accounts' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch"><spring:message code="petty.save" /></button></div>
</form></sec:authorize></c:otherwise></c:choose></div>
<script src="<c:url value='/js/voucher-selects.js' />" defer></script><script src="<c:url value='/js/petty-cash.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
