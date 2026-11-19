<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<template data-resource-details-page></template>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library' />"><spring:message code="library.navigation" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library/transactions' />"><spring:message code="library.transactions" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><spring:message code="library.transactionTemplates" /></span></nav>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="library.transactionTemplates" /></h1></div>
<div class="accounting-workspace accounting-resource-workspace">
    <c:if test="${not empty transaction.description}"><p><c:out value="${transaction.description}" /></p></c:if>
    <c:if test="${not empty librarySuccess}"><p class="coa-success" role="status"><spring:message code="${librarySuccess}" /></p></c:if>
    <c:if test="${transaction.systemManaged()}"><p class="accounting-definition-note"><spring:message code="batch.systemHelp" /></p></c:if>
    <c:if test="${not transaction.systemManaged()}">
    <section data-resource-table="accounting-library-transaction-2" class="erp-table-wrap" data-aws-table-region>
        <div class="app-table-titlebar coa-titlebar"><h2><spring:message code="library.template" /></h2><sec:authorize access="@access.has(principal, 'ACCOUNTING_ACCOUNTS_UPDATE')"><c:if test="${transaction.active}"><a class="app-btn ${empty template?'btn-launch':'btn-neutral'}" href="<c:url value='/finance/library/transactions/${transaction.id}/template' />"><spring:message code="${empty template?'library.addTemplate':'library.editTemplate'}" /></a></c:if></sec:authorize></div>
        <div class="erp-table-scroll erp-table-scroll-sm" data-view-position-key="library-template"><table class="erp-table" data-table-sort-mode="local">
            <thead><tr><th><spring:message code="library.component" /></th><th><spring:message code="library.debitAccount" /></th><th><spring:message code="library.creditAccount" /></th></tr></thead>
            <tbody><c:forEach items="${template.rules}" var="rule"><tr><td><spring:message code="library.component.${rule.component}" /></td><td><c:out value="${rule.debitCode}" /> · <c:out value="${rule.debitName}" /></td><td><c:out value="${rule.creditCode}" /> · <c:out value="${rule.creditName}" /></td></tr></c:forEach><c:if test="${empty template}"><tr><td colspan="3"><spring:message code="library.noTemplate" /></td></tr></c:if></tbody>
        </table></div>
    </section>
    </c:if>
</div>
<%@ include file="../fragments/footer.jspf" %>
