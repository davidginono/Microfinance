<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %><%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %><%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/group-forms.css' />" />
<template data-resource-form-page></template>
<c:set var="cycleFormTitle" value="${empty cycle ? 'group.createCycle' : 'group.prepareCycle'}" />
<sec:authorize access="@access.has(principal,'GROUP_LENDING_SUBMIT')" var="canSendCycle" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__link" href="<c:url value='/finance/groups/${group.id}/cycles' />"><spring:message code="group.cycles" /></a><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="${cycleFormTitle}" /></span></nav>
<div class="erp-page-header erp-detail-header" data-aws-page-header>
    <h1 class="erp-page-title"><spring:message code="${cycleFormTitle}" /></h1>
    <c:if test="${not empty cycle and canSendCycle}">
        <div class="accounting-actions" data-aws-action-pin="true"><a class="app-btn btn-neutral" href="<c:url value='/finance/groups/cycles/${cycle.id}/withdraw' />"><spring:message code="group.withdraw" /></a></div>
    </c:if>
</div>
<div class="accounting-workspace accounting-create-workspace group-editor">
<c:choose><c:when test="${empty cycle}"><c:url value="/finance/groups/${group.id}/cycles" var="saveUrl" /></c:when><c:otherwise><c:url value="/finance/groups/cycles/${cycle.id}" var="saveUrl" /></c:otherwise></c:choose><c:url value="/finance/groups/cycle-policies" var="policiesUrl" /><c:url value="/finance/groups/${group.id}/member-choices" var="membersUrl" />
<form:form modelAttribute="cycleForm" action="${saveUrl}" method="post" cssClass="accounting-create-form" data-preserve-submit-command="true" data-unsaved-financial-form="${pageContext.request.method=='POST'}"><form:hidden path="requestKey" /><form:hidden path="expectedVersion" />
<%-- Enter uses the controller's default save command, rather than a participant row command. --%>
<button type="submit" hidden></button>
<%@ include file="form-group-details.jspf" %>
<section class="erp-resource-section"><h2><spring:message code="group.cycle" /></h2><div class="accounting-grid group-form-fields">
<c:choose>
    <c:when test="${empty cycle}">
        <div data-loan-choice data-choice-url="${policiesUrl}"><label for="cycle-policy"><spring:message code="group.productRules" /></label><form:select id="cycle-policy" path="policyId" cssClass="aws-control" data-choice-select="true" data-searchable="true"><option value=""><spring:message code="voucher.choose" /></option><c:if test="${not empty selectedPolicy}"><option value="${selectedPolicy.id}" selected><c:out value="${selectedPolicy.product}" /> · <spring:message code="group.model.${selectedPolicy.model}" /></option></c:if></form:select><form:errors path="policyId" cssClass="coa-error" /><%@ include file="../accounting/loans/choice-search.jspf" %></div>
    </c:when>
    <c:otherwise>
        <div class="group-fixed-field"><dl><dt><spring:message code="group.productRules" /></dt><dd><c:if test="${not empty selectedPolicy}"><c:out value="${selectedPolicy.product}" /> · </c:if><spring:message code="group.model.${cycle.model}" /></dd></dl><form:hidden path="policyId" /></div>
    </c:otherwise>
</c:choose>
<label><spring:message code="group.applicationDate" /><form:input path="applicationDate" type="date" cssClass="aws-control" /><form:errors path="applicationDate" cssClass="coa-error" /></label>
<label><spring:message code="group.firstPaymentDate" /><form:input path="firstPaymentDate" type="date" cssClass="aws-control" /><form:errors path="firstPaymentDate" cssClass="coa-error" /></label>
<label><spring:message code="group.tenureUnit" /><form:select path="tenureUnit" cssClass="aws-control"><c:forEach items="${['DAYS','MONTHS','YEARS']}" var="unit"><form:option value="${unit}"><spring:message code="group.unit.${unit}" /></form:option></c:forEach></form:select><form:errors path="tenureUnit" cssClass="coa-error" /></label>
<label><spring:message code="group.tenure" /><form:input path="tenureValue" type="number" min="1" cssClass="aws-control" /><form:errors path="tenureValue" cssClass="coa-error" /></label><label><spring:message code="group.purpose" /><form:input path="purpose" maxlength="500" cssClass="aws-control" /><form:errors path="purpose" cssClass="coa-error" /></label>
</div></section>
<c:if test="${not empty cycle}">
<section class="erp-resource-section">
<h2><spring:message code="group.participants" /></h2>
<div class="group-attribute-list">
<c:forEach items="${cycleForm.participants}" var="row" varStatus="s">
<c:set var="newParticipant" value="${s.last and param.action=='addRow'}" />
<details class="group-participant" ${s.first or newParticipant?'open':''}>
<summary><spring:message code="group.participant" /> ${s.count}<c:if test="${not empty memberLabels[row.clientId]}"> · <c:out value="${memberLabels[row.clientId]}" /></c:if></summary>
<div class="accounting-grid group-form-fields">
<div data-loan-choice data-choice-url="${membersUrl}" data-group-new-choice="${newParticipant}"><label for="participant-${s.index}"><spring:message code="group.client" /></label><form:select id="participant-${s.index}" path="participants[${s.index}].clientId" cssClass="aws-control" data-choice-select="true" data-searchable="true"><option value=""><spring:message code="voucher.choose" /></option><c:if test="${not empty memberLabels[row.clientId]}"><option value="${row.clientId}" selected><c:out value="${memberLabels[row.clientId]}" /></option></c:if></form:select><form:errors path="participants[${s.index}].clientId" cssClass="coa-error" /><%@ include file="../accounting/loans/choice-search.jspf" %></div>
<c:forEach items="${cycle.model=='SHARED'?['principal','income','expenses','otherDebt']:['principal','income','expenses','otherDebt','guaranteeCapacity']}" var="field"><label><spring:message code="group.${field}" /><form:input path="participants[${s.index}].${field}" type="number" data-amount-input="true" min="0" step="0.01" cssClass="aws-control" /><form:errors path="participants[${s.index}].${field}" cssClass="coa-error" /></label></c:forEach>
<c:forEach items="${cycle.model=='SHARED'?['assessmentEvidence','collateral']:['assessmentEvidence','capacityEvidence','collateral']}" var="field"><label><spring:message code="group.${field}" /><form:input path="participants[${s.index}].${field}" maxlength="500" cssClass="aws-control" /><form:errors path="participants[${s.index}].${field}" cssClass="coa-error" /></label></c:forEach>
<label><spring:message code="group.collateralValue" /><form:input path="participants[${s.index}].collateralValue" type="number" data-amount-input="true" min="0" step="0.01" cssClass="aws-control" /><form:errors path="participants[${s.index}].collateralValue" cssClass="coa-error" /></label>
<label><spring:message code="group.insurancePremium" /><form:input path="participants[${s.index}].insurancePremium" type="number" data-amount-input="true" min="0" step="0.01" cssClass="aws-control" /><form:errors path="participants[${s.index}].insurancePremium" cssClass="coa-error" /></label><label><spring:message code="group.insuranceEvidence" /><form:input path="participants[${s.index}].insuranceEvidence" maxlength="500" cssClass="aws-control" /><form:errors path="participants[${s.index}].insuranceEvidence" cssClass="coa-error" /></label>
<c:if test="${cycle.model=='INDIVIDUAL' and not empty selectedPolicy.releaseRules}"><c:set var="disclosureRules" value="${selectedPolicy.releaseRules}" /><c:set var="disclosurePath" value="participants[${s.index}].disclosure" /><%@ include file="disclosure-entry.jspf" %></c:if>
</div>
<div class="accounting-actions group-attribute-actions"><button class="app-btn btn-neutral" type="submit" name="action" value="removeRow:${s.index}" formnovalidate data-confirm-message="<spring:message code='group.removeParticipantConfirm' htmlEscape='true' />" data-confirm-proceed="<spring:message code='group.remove' htmlEscape='true' />"><spring:message code="group.remove" /><span class="sr-only"> <spring:message code="group.participant" /> ${s.count}</span></button></div>
</details>
</c:forEach>
</div>
<div class="accounting-actions group-attribute-actions"><button class="app-btn btn-neutral" type="submit" name="action" value="addRow" formnovalidate ${cycleForm.participants.size()>=50?'disabled':''}><spring:message code="group.addParticipant" /></button></div>
</section>
</c:if>
<c:if test="${not empty selectedPolicy.releaseRules}"><section class="erp-resource-section"><h2><spring:message code="groupDisclosure.title" /></h2><div><p><spring:message code="groupDisclosure.methodology" />: <c:out value="${selectedPolicy.releaseRules.methodologyReference}" /></p><c:if test="${cycle.model=='SHARED'}"><div class="accounting-grid group-form-fields"><c:set var="disclosureRules" value="${selectedPolicy.releaseRules}" /><c:set var="disclosurePath" value="disclosure" /><%@ include file="disclosure-entry.jspf" %></div></c:if></div></section></c:if>
<c:if test="${not empty sharedQuote}"><section class="erp-resource-section"><h2><spring:message code="group.sharedQuote" /></h2><p><spring:message code="group.sharedEstimate" /></p><dl class="accounting-grid"><c:forEach items="${['principal','interest','charges','deductions','net','totalPayable']}" var="field"><div><dt><spring:message code="group.shared.${field}" /> (TZS)</dt><dd><fmt:formatNumber value="${sharedQuote[field]}" minFractionDigits="2" maxFractionDigits="2" /></dd></div></c:forEach></dl><div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="editor"><thead><tr><th><spring:message code="group.client" /></th><c:forEach items="${['principal','interest','charges','deductions','net','monthlyPayment']}" var="field"><th><spring:message code="group.shared.${field}" /> (TZS)</th></c:forEach></tr></thead><tbody><c:forEach items="${sharedQuote.shares}" var="share"><tr><td><c:out value="${memberLabels[share.clientId]}" /></td><c:forEach items="${['principal','interest','charges','deductions','net','monthlyPayment']}" var="field"><td><fmt:formatNumber value="${share[field]}" minFractionDigits="2" maxFractionDigits="2" /></td></c:forEach></tr></c:forEach></tbody></table></div></section></c:if>
<div class="accounting-error-summary" role="alert" tabindex="-1"><form:errors element="p" cssClass="coa-error" /><form:errors path="requestKey" element="p" cssClass="coa-error" /><form:errors path="expectedVersion" element="p" cssClass="coa-error" /><c:if test="${not empty cycle}"><form:errors path="policyId" element="p" cssClass="coa-error" /></c:if></div>
<div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/groups/${group.id}/cycles' />"><spring:message code="common.cancel" /></a><button class="app-btn ${empty cycle or not canSendCycle ? 'btn-launch' : 'btn-neutral'}" type="submit" name="action" value="save"><spring:message code="group.saveDraft" /></button><c:if test="${cycle.model=='SHARED'}"><sec:authorize access="@access.has(principal,'GROUP_LENDING_UPDATE') and @access.has(principal,'LOAN_RECORDING_CREATE')"><button class="app-btn btn-neutral" type="submit" name="action" value="previewShared"><spring:message code="group.sharedQuote" /></button></sec:authorize></c:if><c:if test="${not empty cycle and canSendCycle}"><button class="app-btn btn-launch" type="submit" name="action" value="submit"><spring:message code="group.send" /></button></c:if></div>
</form:form></div><script src="<c:url value='/js/loan-recording.js' />" defer></script><script src="<c:url value='/js/group-forms.js' />" defer></script><%@ include file="../fragments/confirm-modal.jspf" %><%@ include file="../accounting/accounting-form-support.jspf" %><%@ include file="../fragments/footer.jspf" %>
