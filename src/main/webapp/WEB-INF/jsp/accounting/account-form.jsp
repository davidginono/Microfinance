<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<c:set var="formBase" value="${groupMode?'/finance/accounts/groups':'/finance/accounts/posting'}" />
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/accounts' />"><spring:message code="finance.nav.accounts" /></a><span aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><spring:message code="${groupMode?'coa.newGroup':'coa.newAccount'}" /></span></nav>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="${groupMode?'coa.newGroup':'coa.newAccount'}" /></h1></div>
<div class="accounting-workspace accounting-create-workspace">
    <p><spring:message code="${groupMode?'coa.groupHelp':'coa.accountHelp'}" /></p>
    <c:if test="${not empty parentError}"><p class="coa-error" role="alert"><spring:message code="${parentError}" /></p></c:if>
    <details class="coa-parent-picker" ${empty selectedParent?'open':''}>
        <summary><spring:message code="coa.chooseParent" /></summary>
        <section class="erp-table-wrap" data-aws-table-region>
            <form method="get" action="<c:url value='${formBase}/new' />" class="erp-table-toolbar coa-filters">
                <label class="erp-table-toolbar__search"><spring:message code="coa.searchGroups" /><input class="aws-control" name="parentSearch" value="<c:out value='${parentSearch}' />" maxlength="100" /></label>
                <button class="app-btn btn-neutral"><spring:message code="coa.filter" /></button>
            </form>
            <div class="erp-table-scroll erp-table-scroll-sm" data-view-position-key="coa-parent-picker"><table class="erp-table">
                <thead><tr><th><spring:message code="accounting.code" /></th><th><spring:message code="accounting.name" /></th><th><spring:message code="common.actions" /></th></tr></thead>
                <tbody><c:forEach items="${parents.rows}" var="p"><tr><td><c:out value="${p.code}" /></td><td><c:out value="${pageContext.response.locale.language=='sw' and not empty p.nameSw?p.nameSw:p.name}" /></td><td><c:url value="${formBase}/new" var="selectParent"><c:param name="parentId" value="${p.id}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${selectParent}' />"><spring:message code="coa.select" /></a></td></tr></c:forEach><c:if test="${empty parents.rows}"><tr><td colspan="3"><spring:message code="${groupMode?'coa.noGroupParents':'coa.noAccountParents'}" /></td></tr></c:if></tbody>
            </table></div>
            <div class="erp-table-footer accounting-actions">
                <c:if test="${parents.page>0}"><c:url value="${formBase}/new" var="previous"><c:param name="parentPage" value="${parents.page-1}" /><c:param name="parentSearch" value="${parentSearch}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${previous}' />"><spring:message code="common.previous" /></a></c:if>
                <span><spring:message code="coa.page" arguments="${parents.page+1}" /></span>
                <c:if test="${parents.hasNext}"><c:url value="${formBase}/new" var="next"><c:param name="parentPage" value="${parents.page+1}" /><c:param name="parentSearch" value="${parentSearch}" /></c:url><a class="app-btn btn-neutral" href="<c:out value='${next}' />"><spring:message code="common.next" /></a></c:if>
            </div>
        </section>
    </details>
    <section class="accounting-form accounting-create-card">
        <h2><spring:message code="${groupMode?'coa.groupDetails':'coa.accountDetails'}" /></h2>
        <c:if test="${not empty selectedParent}"><dl class="accounting-context"><div><dt><spring:message code="coa.parentGroup" /></dt><dd><c:out value="${selectedParent.code}" /> · <c:out value="${pageContext.response.locale.language=='sw' and not empty selectedParent.nameSw?selectedParent.nameSw:selectedParent.name}" /></dd></div><div><dt><spring:message code="accounting.type" /></dt><dd><spring:message code="accounting.option.${selectedParent.type}" /></dd></div></dl></c:if>
        <c:url value="${formBase}" var="saveUrl" />
        <form:form method="post" action="${saveUrl}" modelAttribute="accountForm" cssClass="accounting-create-form">
            <form:hidden path="parentId" />
            <div class="accounting-error-summary" role="alert" tabindex="-1"><form:errors path="*" cssClass="coa-error" element="p" /></div>
            <form:errors path="parentId" cssClass="coa-error" element="p" />
            <div class="accounting-grid">
                <label><spring:message code="accounting.code" /><form:input path="code" cssClass="aws-control" maxlength="6" pattern="[1-5][0-9]{5}" required="required" /><small><spring:message code="coa.codeHelp" /></small><form:errors path="code" cssClass="coa-error" /></label>
                <label><spring:message code="library.name" /><form:input path="name" cssClass="aws-control" maxlength="160" required="required" /><form:errors path="name" cssClass="coa-error" /></label>
            </div>
                <c:if test="${not groupMode}">
                    <fieldset class="accounting-form-section"><legend><spring:message code="coa.accountSettings" /></legend><div class="accounting-grid">
                    <label><spring:message code="accounting.normalBalance" /><form:select path="normalBalance" cssClass="aws-control"><form:option value="DEBIT"><spring:message code="accounting.debit" /></form:option><form:option value="CREDIT"><spring:message code="accounting.credit" /></form:option></form:select><small><spring:message code="coa.balanceHelp" /></small></label>
                    <label><spring:message code="coa.postingAccess" /><form:select path="kind" cssClass="aws-control"><form:option value="POSTING"><spring:message code="accounting.option.POSTING" /></form:option><form:option value="CONTROL"><spring:message code="coa.control" /></form:option></form:select><form:errors path="kind" cssClass="coa-error" /></label>
                    <label><spring:message code="accounting.purpose" /><form:select path="purpose" cssClass="aws-control"><c:forEach items="${accountPurposes}" var="p"><form:option value="${p}"><spring:message code="accounting.option.${p}" /></form:option></c:forEach></form:select></label>
                    </div></fieldset>
                </c:if>
            <details class="accounting-additional" ${not empty accountForm.description?'open':''}><summary><spring:message code="coa.additionalSettings" /></summary><label><spring:message code="coa.description" /><form:textarea path="description" cssClass="aws-control" maxlength="500" rows="2" /><form:errors path="description" cssClass="coa-error" /></label></details>
            <c:url value="/finance/accounts" var="cancelChart"><c:param name="parentId" value="${selectedParent.id}" /></c:url><div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:out value='${cancelChart}' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-primary"><spring:message code="${groupMode?'coa.createGroup':'coa.createAccount'}" /></button></div>
        </form:form>
    </section>
</div>
<%@ include file="accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
