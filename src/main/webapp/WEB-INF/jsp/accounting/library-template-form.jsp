<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
    <c:set var="templateTitle" value="${empty templateForm.expectedRequestKey?'library.addTemplate':'library.editTemplate'}" />
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library' />"><spring:message code="library.navigation" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library/transactions' />"><spring:message code="library.transactions" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library/transactions/${transaction.id}' />"><c:out value="${transaction.code}" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><spring:message code="${templateTitle}" /></span></nav>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="${templateTitle}" /></h1></div>
<div class="accounting-workspace accounting-create-workspace">
    <dl class="accounting-context accounting-form"><div><dt><spring:message code="library.transaction" /></dt><dd><c:out value="${transaction.code}" /> · <c:out value="${pageContext.response.locale.language=='sw' and not empty transaction.nameSw?transaction.nameSw:transaction.name}" /></dd></div><div><dt><spring:message code="library.sourceEvent" /></dt><dd><spring:message code="library.event.${transaction.sourceEvent}" /></dd></div></dl>
    <section class="accounting-form accounting-create-card"><h2><spring:message code="library.involvedAccounts" /></h2>
        <c:url value="/finance/library/transactions/${transaction.id}/template" var="saveUrl" />
        <form:form method="post" modelAttribute="templateForm" action="${saveUrl}" id="library-template-form" cssClass="accounting-create-form">
            <form:hidden path="requestKey" /><form:hidden path="expectedRequestKey" />
            <div class="accounting-error-summary" role="alert" tabindex="-1"><form:errors path="*" cssClass="coa-error" element="p" /></div>
            <c:url value="/finance/library/lookup/accounts" var="accountLookupUrl" />
            <c:set var="ruleForms" value="${templateForm.rules}" /><c:set var="rulePrefix" value="" />
            <%@ include file="library-rule-fields.jspf" %>
            <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/library/transactions/${transaction.id}' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch" ${not transaction.active?'disabled':''}><spring:message code="library.saveTemplate" /></button></div>
        </form:form>
    </section>
</div>
<script src="<c:url value='/js/voucher-selects.js' />" defer></script>
<script src="<c:url value='/js/accounting-library.js' />" defer></script>
<%@ include file="accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
