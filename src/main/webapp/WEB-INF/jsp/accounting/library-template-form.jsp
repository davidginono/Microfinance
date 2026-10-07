<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="accounting-workspace">
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="library.newVersion" /></h1></div>
    <p class="coa-location"><strong><c:out value="${transaction.code}" /> · <c:out value="${pageContext.response.locale.language=='sw' and not empty transaction.nameSw?transaction.nameSw:transaction.name}" /></strong> · <spring:message code="accounting.policy.event.${transaction.sourceEvent}" /></p>
    <p><spring:message code="library.templateHelp" /></p>
    <details class="coa-parent-picker"><summary><spring:message code="library.findAccounts" /></summary>
        <section class="erp-table-wrap" data-aws-table-region>
            <form method="get" class="erp-table-toolbar coa-filters" action="<c:url value='/finance/library/transactions/${transaction.id}/template' />"><label class="erp-table-toolbar__search"><spring:message code="coa.search" /><input class="aws-control" name="accountSearch" value="<c:out value='${accountSearch}' />" maxlength="100" /></label><button class="app-btn btn-neutral"><spring:message code="coa.filter" /></button></form>
            <div class="erp-table-scroll erp-table-scroll-sm" data-view-position-key="library-account-codes"><table class="erp-table"><thead><tr><th><spring:message code="accounting.code" /></th><th><spring:message code="accounting.name" /></th><th><spring:message code="accounting.kind" /></th></tr></thead><tbody><c:forEach items="${accountChoices.rows}" var="a"><tr><td><c:out value="${a.code}" /></td><td><c:out value="${pageContext.response.locale.language=='sw' and not empty a.nameSw?a.nameSw:a.name}" /></td><td><spring:message code="accounting.option.${a.kind}" /></td></tr></c:forEach><c:if test="${empty accountChoices.rows}"><tr><td colspan="3"><spring:message code="coa.empty" /></td></tr></c:if></tbody></table></div>
            <div class="erp-table-footer accounting-actions"><c:if test="${accountChoices.page>0}"><c:url value="/finance/library/transactions/${transaction.id}/template" var="previous"><c:param name="accountPage" value="${accountChoices.page-1}" /><c:param name="accountSearch" value="${accountSearch}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${previous}' />"><spring:message code="common.previous" /></a></c:if><span><spring:message code="coa.page" arguments="${accountChoices.page+1}" /></span><c:if test="${accountChoices.hasNext}"><c:url value="/finance/library/transactions/${transaction.id}/template" var="next"><c:param name="accountPage" value="${accountChoices.page+1}" /><c:param name="accountSearch" value="${accountSearch}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${next}' />"><spring:message code="common.next" /></a></c:if></div>
        </section>
    </details>
    <datalist id="library-account-codes"><c:forEach items="${accountChoices.rows}" var="a"><option value="<c:out value='${a.code}' />"><c:out value="${a.name}" /></option></c:forEach></datalist>
    <section class="accounting-form">
        <c:url value="/finance/library/transactions/${transaction.id}/templates" var="saveUrl" />
        <form:form method="post" modelAttribute="templateForm" action="${saveUrl}" id="library-template-form">
            <form:hidden path="requestKey" /><form:hidden path="expectedRevision" />
            <div role="alert"><form:errors path="*" cssClass="coa-error" element="p" /></div>
            <div id="library-rule-list"><c:forEach items="${templateForm.rules}" var="rule" varStatus="n"><div class="library-rule accounting-grid">
                <label><spring:message code="library.component" /><form:select path="rules[${n.index}].component" cssClass="aws-control"><c:forEach items="${components}" var="component"><form:option value="${component}"><spring:message code="library.component.${component}" /></form:option></c:forEach></form:select></label>
                <label><spring:message code="library.debitCode" /><form:input path="rules[${n.index}].debitCode" cssClass="aws-control" maxlength="40" list="library-account-codes" required="required" /></label>
                <label><spring:message code="library.creditCode" /><form:input path="rules[${n.index}].creditCode" cssClass="aws-control" maxlength="40" list="library-account-codes" required="required" /></label>
                <button type="button" class="app-btn btn-neutral library-remove"><spring:message code="library.removeRule" /></button>
            </div></c:forEach></div>
            <button type="button" class="app-btn btn-neutral" id="library-add-rule"><spring:message code="library.addRule" /></button>
            <label><spring:message code="library.reason" /><form:textarea path="reason" cssClass="aws-control" maxlength="500" rows="2" required="required" /></label>
            <div class="accounting-actions"><button class="app-btn btn-primary" ${not transaction.active?'disabled':''}><spring:message code="library.saveVersion" /></button><a class="app-btn btn-neutral" href="<c:url value='/finance/library/transactions/${transaction.id}' />"><spring:message code="common.cancel" /></a></div>
        </form:form>
    </section>
</div>
<script src="<c:url value='/js/accounting-library.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
