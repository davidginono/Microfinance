<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-policy.css' />" />
<div class="policy-workspace">
<nav class="erp-page-path"><a href="<c:url value='/finance/policies' />"><spring:message code="accounting.settings.title" /></a><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="accounting.settings.change" /></span></nav>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="accounting.settings.change" /></h1></div>
<c:url var="createPolicyUrl" value="/finance/policies" />
<c:url var="accountChoicesUrl" value="/finance/policies/accounts" />
<spring:message code="accounting.settings.chooseAccount" var="chooseAccount" />
<form:form modelAttribute="policyForm" method="post" action="${createPolicyUrl}" cssClass="policy-form accounting-create-form">
    <form:hidden path="requestKey" /><form:hidden path="sourcePolicyId" />
    <c:if test="${not empty policyError}"><div class="loan-error-summary policy-error" role="alert" tabindex="-1"><spring:message code="${policyError}" /></div></c:if>
    <section class="policy-section"><h2><spring:message code="accounting.settings.general" /></h2><div class="policy-fields">
        <div class="policy-field"><label for="authoritativeLedger"><spring:message code="accounting.settings.books" /></label><c:choose><c:when test="${not empty policyForm.sourcePolicyId}"><form:hidden path="authoritativeLedger" id="authoritativeLedgerValue" /><spring:message code="accounting.settings.books.${policyForm.authoritativeLedger}" var="selectedBooks" /><input id="authoritativeLedger" class="aws-control" value="<c:out value='${selectedBooks}' />" readonly /></c:when><c:otherwise><form:select path="authoritativeLedger" id="authoritativeLedger" cssClass="aws-control" required="required"><form:option value=""><spring:message code="accounting.settings.choose" /></form:option><form:option value="LOCAL_GL"><spring:message code="accounting.settings.books.LOCAL_GL" /></form:option><form:option value="EXTERNAL_GL"><spring:message code="accounting.settings.books.EXTERNAL_GL" /></form:option></form:select></c:otherwise></c:choose></div>
        <div class="policy-field"><label for="openingDate"><spring:message code="accounting.settings.opening" /></label><form:input path="openingDate" id="openingDate" type="date" cssClass="aws-control" required="required" readonly="${not empty policyForm.sourcePolicyId}" /></div>
        <div class="policy-field"><label for="effectiveFrom"><spring:message code="accounting.settings.effective" /></label><form:input path="effectiveFrom" id="effectiveFrom" type="date" cssClass="aws-control" required="required" /></div>
        <div class="policy-field"><label for="interestRecording"><spring:message code="accounting.settings.interest" /></label><form:select path="interestRecording" id="interestRecording" cssClass="aws-control" required="required"><form:option value=""><spring:message code="accounting.settings.choose" /></form:option><c:forEach items="${interestModes}" var="mode"><form:option value="${mode}"><spring:message code="accounting.settings.interest.${mode}" /></form:option></c:forEach></form:select></div>
    </div></section>
    <section class="policy-section"><h2><spring:message code="accounting.settings.posting" /></h2><p class="policy-scope"><spring:message code="accounting.settings.scope" /></p><div class="policy-fields">
        <c:forEach items="${postingEvents}" var="event"><div class="policy-field"><label for="permission-${event}"><spring:message code="accounting.settings.event.${event}" /></label><form:select path="permissions[${event}]" id="permission-${event}" cssClass="aws-control" required="required"><form:option value="DISABLED"><spring:message code="accounting.settings.off" /></form:option><form:option value="ALLOWED"><spring:message code="accounting.settings.on" /></form:option></form:select></div></c:forEach>
    </div></section>
    <section class="policy-section"><h2><spring:message code="accounting.settings.accounts" /></h2><div class="policy-fields">
        <c:forEach items="${accountKeys}" var="key"><div class="policy-field" data-loan-choice data-choice-lazy data-choice-url="${accountChoicesUrl}"><label for="account-${key}"><spring:message code="accounting.settings.account.${key}" /></label><form:select path="accounts[${key}]" id="account-${key}" cssClass="aws-control" data-choice-select="true"><form:option value="" label="${chooseAccount}" /><c:if test="${not empty policyForm.accounts[key]}"><form:option value="${policyForm.accounts[key]}"><c:choose><c:when test="${not empty accountLabels[policyForm.accounts[key]]}"><c:out value="${accountLabels[policyForm.accounts[key]]}" /></c:when><c:otherwise><spring:message code="accounting.settings.accountUnavailable" /></c:otherwise></c:choose></form:option></c:if></form:select><span data-choice-error role="status" data-error="<spring:message code='recording.lookupError' />" data-search-label="<spring:message code='voucher.search' />"></span></div></c:forEach>
    </div></section>
    <section class="policy-section"><div class="policy-field"><label for="evidenceReference"><spring:message code="accounting.settings.reference" /></label><form:input path="evidenceReference" id="evidenceReference" maxlength="1000" cssClass="aws-control" required="required" /></div></section>
    <div class="policy-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/policies' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-primary" type="submit"><spring:message code="accounting.settings.save" /></button></div>
</form:form>
</div>
<%@ include file="../accounting-form-support.jspf" %>
<script src="<c:url value='/js/loan-recording.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
