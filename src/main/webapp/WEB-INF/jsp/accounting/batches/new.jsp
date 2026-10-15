<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="accounting-workspace"><nav class="erp-page-path"><a href="<c:url value='/finance/batches/receipts' />"><spring:message code="batch.receipts" /></a></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="batch.create" /></h1></div>
<form method="post" action="<c:url value='/finance/batches/receipts/new' />" class="accounting-create-form">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
<label><spring:message code="batch.title" /><input class="aws-control" name="title" maxlength="120" required autofocus /></label>
<div class="accounting-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/batches/receipts' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-neutral"><spring:message code="batch.create" /></button></div>
</form></div><%@ include file="../../fragments/footer.jspf" %>
