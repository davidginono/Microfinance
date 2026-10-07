<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/library' />"><spring:message code="library.navigation" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><a class="erp-page-path__item erp-page-path__link" href="<c:url value="${empty activity?'/finance/library':'/finance/library/transactions'}" />"><spring:message code="${empty activity?'library.activities':'library.transactions'}" /></a><span class="erp-page-path__separator" aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><spring:message code="${empty activity?'library.newActivity':'library.newTransaction'}" /></span></nav>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="${empty activity?'library.newActivity':'library.newTransaction'}" /></h1></div>
<div class="accounting-workspace accounting-create-workspace">
    <c:if test="${not empty activity}"><p class="coa-location"><spring:message code="library.activity" />: <strong><c:out value="${activity.code}" /> · <c:out value="${pageContext.response.locale.language=='sw' and not empty activity.nameSw?activity.nameSw:activity.name}" /></strong></p></c:if>
    <section class="accounting-form accounting-create-card"><h2><spring:message code="${empty activity?'library.activityDetails':'library.transactionDetails'}" /></h2>
        <c:url value="${empty activity?'/finance/library/activities':'/finance/library/activities/'}${empty activity?'':activity.id}${empty activity?'':'/transactions'}" var="saveUrl" />
        <form:form method="post" modelAttribute="codeForm" action="${saveUrl}" cssClass="accounting-create-form">
            <div class="accounting-error-summary" role="alert" tabindex="-1"><form:errors path="*" cssClass="coa-error" element="p" /></div>
            <div class="accounting-grid">
                <label><spring:message code="library.code" /><form:input path="code" cssClass="aws-control" maxlength="40" pattern="[A-Za-z0-9][A-Za-z0-9_.-]{0,39}" required="required" /><small><spring:message code="library.codeHelp" /></small><form:errors path="code" cssClass="coa-error" /></label>
                <label><spring:message code="library.name" /><form:input path="name" cssClass="aws-control" maxlength="160" required="required" /><form:errors path="name" cssClass="coa-error" /></label>
                <c:if test="${not empty activity}"><label><spring:message code="library.sourceEvent" /><form:select path="sourceEvent" cssClass="aws-control"><c:forEach items="${sourceEvents}" var="event"><form:option value="${event}"><spring:message code="accounting.policy.event.${event}" /></form:option></c:forEach></form:select><small><spring:message code="library.eventHelp" /></small><form:errors path="sourceEvent" cssClass="coa-error" /></label></c:if>

            </div>
            <details class="accounting-additional" ${not empty codeForm.description?'open':''}><summary><spring:message code="coa.additionalSettings" /></summary><label class="coa-wide"><spring:message code="coa.description" /><form:textarea path="description" cssClass="aws-control" maxlength="500" rows="2" /><form:errors path="description" cssClass="coa-error" /></label></details>
            <div class="accounting-actions accounting-form-actions"><c:url value="${empty activity?'/finance/library':'/finance/library/activities/'}${empty activity?'':activity.id}" var="cancelUrl" /><a class="app-btn btn-neutral" href="<c:out value='${cancelUrl}' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-primary" ${not empty activity and not activity.active?'disabled':''}><spring:message code="${empty activity?'library.createActivity':'library.createTransaction'}" /></button></div>
        </form:form>
    </section>
</div>
<%@ include file="accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
