<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-policy.css' />" />
<div class="policy-workspace">
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="accounting.policy.propose" /></h1></div>
<p><spring:message code="accounting.policy.gate" /></p>
<c:if test="${not empty policyError}"><p class="policy-error" role="alert"><spring:message code="${policyError}" /></p></c:if>
<c:url var="createPolicyUrl" value="/finance/policies" />
<form:form modelAttribute="policyForm" method="post" action="${createPolicyUrl}" cssClass="policy-form">
    <form:hidden path="requestKey" />
    <div class="policy-fields">
        <div class="policy-field"><label for="authoritativeLedger"><spring:message code="accounting.policy.books" /></label><form:select path="authoritativeLedger" id="authoritativeLedger" cssClass="aws-control" required="required"><form:option value=""><spring:message code="accounting.policy.select" /></form:option><form:option value="LOCAL_GL"><spring:message code="accounting.policy.books.LOCAL_GL" /></form:option><form:option value="EXTERNAL_GL"><spring:message code="accounting.policy.books.EXTERNAL_GL" /></form:option></form:select></div>
        <div class="policy-field"><label for="openingDate"><spring:message code="accounting.policy.opening" /></label><form:input path="openingDate" id="openingDate" type="date" cssClass="aws-control" required="required" /></div>
        <div class="policy-field"><label for="effectiveFrom"><spring:message code="accounting.policy.effective" /></label><form:input path="effectiveFrom" id="effectiveFrom" type="date" cssClass="aws-control" required="required" /></div>
        <div class="policy-field"><label for="evidenceReference"><spring:message code="accounting.policy.evidence" /></label><form:input path="evidenceReference" id="evidenceReference" maxlength="1000" cssClass="aws-control" required="required" /></div>
    </div>
    <section class="policy-section"><h2><spring:message code="accounting.policy.decisions" /></h2><div class="policy-fields">
        <c:forEach items="${decisionFields}" var="field"><div class="policy-field"><label for="decision-${field}"><spring:message code="accounting.policy.decision.${field}" /></label><form:textarea path="decisions[${field}]" id="decision-${field}" maxlength="6000" cssClass="aws-control" required="required" /></div></c:forEach>
    </div></section>
    <section class="policy-section"><h2><spring:message code="accounting.policy.matrix" /></h2><p><spring:message code="accounting.policy.matrixHelp" /></p>
        <div class="policy-fields"><c:forEach items="${postingEvents}" var="event"><div class="policy-field"><label for="permission-${event}"><spring:message code="accounting.policy.event.${event}" /></label>
            <form:select path="permissions[${event}]" id="permission-${event}" cssClass="aws-control" required="required"><form:option value=""><spring:message code="accounting.policy.select" /></form:option><form:option value="DISABLED"><spring:message code="accounting.policy.disabled" /></form:option><form:option value="ALLOWED"><spring:message code="accounting.policy.allowed" /></form:option></form:select>
            <label for="treatment-${event}"><spring:message code="accounting.policy.treatment" /></label><form:textarea path="treatments[${event}]" id="treatment-${event}" cssClass="aws-control" maxlength="6000" required="required" /></div></c:forEach></div>
    </section>
    <section class="policy-section"><div class="policy-field"><label for="accountMappings"><spring:message code="accounting.policy.mappings" /></label><p><spring:message code="accounting.policy.mappingsHelp" /></p><form:textarea path="accountMappings" id="accountMappings" cssClass="aws-control" maxlength="12000" /></div></section>
    <div class="policy-actions"><button class="app-btn btn-primary" type="submit"><spring:message code="accounting.policy.submit" /></button></div>
</form:form>
</div>
<%@ include file="../../fragments/footer.jspf" %>
