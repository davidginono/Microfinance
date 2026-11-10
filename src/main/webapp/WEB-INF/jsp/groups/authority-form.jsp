<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="../fragments/header.jspf" %><%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/group-forms.css' />" />
<template data-resource-form-page></template>
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__link" href="<c:url value='/finance/groups/${group.id}/authority' />"><spring:message code="authority.title" /></a><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="authority.record" /></span></nav>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="authority.record" /></h1></div>
<div class="accounting-workspace accounting-create-workspace group-editor"><c:url value="/finance/groups/${group.id}/authority" var="saveUrl" /><c:url value="/finance/groups/${group.id}/authority/clients" var="clientsUrl" />
<form:form modelAttribute="authorityForm" method="post" action="${saveUrl}" cssClass="accounting-create-form" data-preserve-submit-command="true" data-unsaved-financial-form="${pageContext.request.method=='POST'}"><form:hidden path="requestKey" /><form:hidden path="expectedPreviousId" />
<%-- Enter uses the controller's default record command, rather than a signer row command. --%>
<button type="submit" hidden></button>
<c:set var="groupFormNotice" value="authority.notLoanApproval" />
<%@ include file="form-group-details.jspf" %>
<section class="erp-resource-section"><h2><spring:message code="authority.limits" /></h2><div class="accounting-grid group-form-fields">
<c:forEach items="${['validFrom','validThrough']}" var="field"><label><spring:message code="authority.${field}" /><form:input path="${field}" type="date" cssClass="aws-control" required="required" /><form:errors path="${field}" cssClass="coa-error" /></label></c:forEach>
<label><spring:message code="authority.principal" /> (TZS)<form:input path="maxPrincipal" type="number" data-amount-input="true" min="0.01" step="0.01" cssClass="aws-control" required="required" /><form:errors path="maxPrincipal" cssClass="coa-error" /></label>
<label><spring:message code="authority.requiredSignatures" /><form:input path="requiredSignatures" type="number" min="1" max="50" cssClass="aws-control" required="required" /><form:errors path="requiredSignatures" cssClass="coa-error" /></label>
</div></section>
<section class="erp-resource-section"><h2><spring:message code="authority.documents" /></h2><div class="accounting-grid group-form-fields"><c:forEach items="${['capacityEvidence','resolutionEvidence','signatureEvidence']}" var="field"><label><spring:message code="authority.${field}" /><form:input path="${field}" cssClass="aws-control" maxlength="500" required="required" /><form:errors path="${field}" cssClass="coa-error" /></label></c:forEach></div></section>
<section class="erp-resource-section">
    <h2><spring:message code="authority.signers" /></h2>
    <div class="group-attribute-list">
        <c:forEach items="${authorityForm.signers}" var="row" varStatus="s">
            <div class="group-signer-row group-form-fields" role="group" aria-labelledby="authority-label-${s.index}">
                <div class="group-signer-choice" data-loan-choice data-choice-url="${clientsUrl}" data-group-new-choice="${s.last and param.action=='addSigner'}">
                    <label id="authority-label-${s.index}" for="authority-person-${s.index}"><spring:message code="authority.signer" /> ${s.count}</label>
                    <form:select id="authority-person-${s.index}" path="signers[${s.index}].clientId" cssClass="aws-control" data-choice-select="true" data-searchable="true">
                        <option value=""><spring:message code="voucher.choose" /></option>
                        <c:if test="${not empty selectedSigners[row.clientId]}"><option value="${row.clientId}" selected><c:out value="${selectedSigners[row.clientId].number}" /> · <c:out value="${selectedSigners[row.clientId].name}" /></option></c:if>
                        <c:if test="${not empty row.clientId and empty selectedSigners[row.clientId]}"><option value="${row.clientId}" selected><spring:message code="common.notAvailable" /></option></c:if>
                    </form:select>
                    <form:errors path="signers[${s.index}].clientId" cssClass="coa-error" />
                    <%@ include file="../accounting/loans/choice-search.jspf" %>
                </div>
                <div class="group-signer-powers">
                    <c:forEach items="${['mayApply','maySign','mayReceive','mayViewAccount']}" var="power">
                        <div>
                            <label class="aws-settings-checkbox-row"><form:checkbox path="signers[${s.index}].${power}" cssClass="aws-settings-checkbox" /><span class="aws-settings-checkbox-copy"><spring:message code="authority.${power}" /></span></label>
                            <form:errors path="signers[${s.index}].${power}" cssClass="coa-error" />
                        </div>
                    </c:forEach>
                </div>
                <button class="app-btn btn-neutral" type="submit" name="action" value="removeSigner:${s.index}" formnovalidate><spring:message code="group.remove" /><span class="sr-only"> <spring:message code="authority.signer" /> ${s.count}</span></button>
            </div>
        </c:forEach>
    </div>
    <div class="accounting-actions group-attribute-actions"><button class="app-btn btn-neutral" type="submit" name="action" value="addSigner" formnovalidate ${authorityForm.signers.size()>=50?'disabled':''}><spring:message code="authority.addSigner" /></button></div>
</section>
<section class="erp-resource-section"><h2><spring:message code="authority.payout" /></h2><div class="accounting-grid group-form-fields">
<label><spring:message code="common.type" /><form:select path="payoutType" cssClass="aws-control"><option value=""><spring:message code="voucher.choose" /></option><c:forEach items="${['BANK_ACCOUNT','MOBILE_MONEY','OTHER']}" var="type"><form:option value="${type}"><spring:message code="authority.payoutType.${type}" /></form:option></c:forEach></form:select><form:errors path="payoutType" cssClass="coa-error" /></label>
<c:forEach items="${['payoutProvider','payoutHolder','payoutIdentifier','payoutEvidence']}" var="field"><label><spring:message code="authority.${field}" /><form:input path="${field}" cssClass="aws-control" maxlength="${field=='payoutEvidence'?500:field=='payoutProvider'?120:160}" required="required" /><form:errors path="${field}" cssClass="coa-error" /></label></c:forEach>

<div><label class="aws-settings-checkbox-row"><form:checkbox path="verified" cssClass="aws-settings-checkbox" required="required" /><span class="aws-settings-checkbox-copy"><spring:message code="authority.verified" /></span></label><form:errors path="verified" cssClass="coa-error" /></div>
</div></section>
<div class="accounting-error-summary" role="alert" tabindex="-1"><form:errors element="p" cssClass="coa-error" /><form:errors path="requestKey" element="p" cssClass="coa-error" /><form:errors path="expectedPreviousId" element="p" cssClass="coa-error" /></div>
<div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/groups/${group.id}/authority' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch" type="submit" name="action" value="record"><spring:message code="authority.record" /></button></div>
</form:form></div><script src="<c:url value='/js/loan-recording.js' />" defer></script><script src="<c:url value='/js/group-forms.js' />" defer></script><%@ include file="../accounting/accounting-form-support.jspf" %><%@ include file="../fragments/footer.jspf" %>
