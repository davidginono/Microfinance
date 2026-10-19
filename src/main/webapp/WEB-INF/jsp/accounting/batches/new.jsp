<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/batches/receipts' />"><spring:message code="batch.receipts" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><spring:message code="batch.create" /></span></nav>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="batch.create" /></h1></div>
<div class="accounting-workspace accounting-create-workspace">
<form method="post" action="<c:url value='/finance/batches/receipts/new' />" class="accounting-create-form">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
<section class="erp-resource-section"><h2><spring:message code="batch.create" /></h2><label class="grid gap-2"><spring:message code="batch.title" /><input class="aws-control" name="title" maxlength="120" required autofocus /></label></section>
<div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/batches/receipts' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch"><spring:message code="batch.create" /></button></div>
</form></div><%@ include file="../../fragments/footer.jspf" %>
