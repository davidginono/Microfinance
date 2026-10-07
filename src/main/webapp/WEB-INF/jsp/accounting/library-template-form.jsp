<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library' />"><spring:message code="library.navigation" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library/transactions' />"><spring:message code="library.transactions" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library/transactions/${transaction.id}' />"><c:out value="${transaction.code}" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><spring:message code="library.newVersion" /></span></nav>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="library.newVersion" /></h1></div>
<div class="accounting-workspace accounting-create-workspace">
    <dl class="accounting-context accounting-form"><div><dt><spring:message code="library.transaction" /></dt><dd><c:out value="${transaction.code}" /> · <c:out value="${pageContext.response.locale.language=='sw' and not empty transaction.nameSw?transaction.nameSw:transaction.name}" /></dd></div><div><dt><spring:message code="library.sourceEvent" /></dt><dd><spring:message code="accounting.policy.event.${transaction.sourceEvent}" /></dd></div></dl>
    <p><spring:message code="library.templateHelp" /></p>
    <details class="coa-parent-picker"><summary><spring:message code="library.findAccounts" /></summary>
        <section class="erp-table-wrap" data-aws-table-region>
            <form method="get" class="erp-table-toolbar coa-filters" action="<c:url value='/finance/library/transactions/${transaction.id}/template' />"><label class="erp-table-toolbar__search"><spring:message code="coa.search" /><input class="aws-control" name="accountSearch" value="<c:out value='${accountSearch}' />" maxlength="100" /></label><button class="app-btn btn-neutral"><spring:message code="coa.filter" /></button></form>
            <div class="erp-table-scroll erp-table-scroll-sm" data-view-position-key="library-account-codes"><table class="erp-table"><thead><tr><th><spring:message code="accounting.code" /></th><th><spring:message code="accounting.name" /></th><th><spring:message code="accounting.kind" /></th></tr></thead><tbody><c:forEach items="${accountChoices.rows}" var="a"><tr><td><c:out value="${a.code}" /></td><td><c:out value="${pageContext.response.locale.language=='sw' and not empty a.nameSw?a.nameSw:a.name}" /></td><td><spring:message code="accounting.option.${a.kind}" /></td></tr></c:forEach><c:if test="${empty accountChoices.rows}"><tr><td colspan="3"><spring:message code="coa.empty" /></td></tr></c:if></tbody></table></div>
            <div class="erp-table-footer accounting-actions"><c:if test="${accountChoices.page>0}"><c:url value="/finance/library/transactions/${transaction.id}/template" var="previous"><c:param name="accountPage" value="${accountChoices.page-1}" /><c:param name="accountSearch" value="${accountSearch}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${previous}' />"><spring:message code="common.previous" /></a></c:if><span><spring:message code="coa.page" arguments="${accountChoices.page+1}" /></span><c:if test="${accountChoices.hasNext}"><c:url value="/finance/library/transactions/${transaction.id}/template" var="next"><c:param name="accountPage" value="${accountChoices.page+1}" /><c:param name="accountSearch" value="${accountSearch}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${next}' />"><spring:message code="common.next" /></a></c:if></div>
        </section>
    </details>
    <datalist id="library-account-codes"><c:forEach items="${accountChoices.rows}" var="a"><option value="<c:out value='${a.code}' />"><c:out value="${a.name}" /></option></c:forEach></datalist>
    <section class="accounting-form accounting-create-card"><h2><spring:message code="library.involvedAccounts" /></h2>
        <c:url value="/finance/library/transactions/${transaction.id}/templates" var="saveUrl" />
        <form:form method="post" modelAttribute="templateForm" action="${saveUrl}" id="library-template-form" cssClass="accounting-create-form">
            <form:hidden path="requestKey" /><form:hidden path="expectedRevision" />
            <div class="accounting-error-summary" role="alert" tabindex="-1"><form:errors path="*" cssClass="coa-error" element="p" /></div>
            <c:url value="/finance/library/lookup/accounts" var="accountLookupUrl" />
            <c:set var="ruleForms" value="${templateForm.rules}" /><c:set var="rulePrefix" value="" />
            <%@ include file="library-rule-fields.jspf" %>
            <p class="library-lookup-status" role="status" data-lookup-failure="<spring:message code='library.lookupUnavailable' />"></p>
            <label><spring:message code="library.reason" /><form:textarea path="reason" cssClass="aws-control" maxlength="500" rows="2" required="required" /><form:errors path="reason" cssClass="coa-error" /></label>
            <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/library/transactions/${transaction.id}' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-primary" ${not transaction.active?'disabled':''}><spring:message code="library.saveVersion" /></button></div>
        </form:form>
    </section>
</div>
<script src="<c:url value='/js/accounting-library.js' />" defer></script>
<%@ include file="accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
