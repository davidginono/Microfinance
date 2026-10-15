<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-policy.css' />" />
<div class="policy-workspace erp-detail-workspace">
    <spring:message code="accounting.settings.versionTitle" arguments="${policy.version}" text="Accounting settings (Version {0})" var="policyDetailTitle" />
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/policies' />"><spring:message code="accounting.settings.title" /></a><span aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><c:out value="${policyDetailTitle}" /></span></nav>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><c:out value="${policyDetailTitle}" /></h1></div>
    <c:if test="${not empty policyError}"><p class="policy-error" role="alert"><spring:message code="${policyError}" /></p></c:if>
    <section class="erp-detail-card policy-overview"><h2><spring:message code="accounting.settings.general" /></h2><dl class="erp-detail-facts">
        <div><dt><spring:message code="accounting.policy.status" /></dt><dd><span class="policy-status ${policy.approvalDecision=='APPROVED'?'policy-status-approved':policy.approvalDecision=='REJECTED'?'policy-status-rejected':''}"><c:choose><c:when test="${empty policy.approvalDecision}"><spring:message code="accounting.policy.pending" /></c:when><c:otherwise><spring:message code="accounting.policy.status.${policy.approvalDecision}" /></c:otherwise></c:choose></span></dd></div>
        <div><dt><spring:message code="accounting.settings.books" /></dt><dd><spring:message code="accounting.settings.books.${policy.authoritativeLedger}" /></dd></div>
        <div><dt><spring:message code="accounting.settings.opening" /></dt><dd><c:out value="${policy.openingDate}" /></dd></div><div><dt><spring:message code="accounting.settings.effective" /></dt><dd><c:out value="${policy.effectiveFrom}" /></dd></div>
    </dl></section>
    <section class="erp-detail-card policy-content-section">
        <div class="policy-settings-heading"><h2><spring:message code="accounting.settings.general" /></h2><sec:authorize access="@access.has(principal, 'ACCOUNTING_POLICIES_CREATE')"><a class="app-btn btn-neutral" href="<c:url value='/finance/policies/${policy.id}/change' />"><spring:message code="accounting.settings.change" /></a></sec:authorize></div>
        <dl class="policy-setting-list"><div><dt><spring:message code="accounting.settings.interest" /></dt><dd><c:choose><c:when test="${policy.decisions['INTEREST_RECOGNITION']=='CASH_DUE_INTEREST_V1' or policy.decisions['INTEREST_RECOGNITION']=='ACCRUAL_DUE_INTEREST_V1'}"><spring:message code="accounting.settings.interest.${policy.decisions['INTEREST_RECOGNITION']}" /></c:when><c:otherwise><spring:message code="accounting.settings.notSet" /></c:otherwise></c:choose></dd></div></dl>
    </section>
    <section class="erp-detail-card policy-content-section"><h2><spring:message code="accounting.settings.posting" /></h2><p class="policy-scope"><spring:message code="accounting.settings.scope" /></p><dl class="policy-setting-list">
        <c:forEach items="${postingEvents}" var="event"><div><dt><spring:message code="accounting.settings.event.${event}" /></dt><dd><span class="policy-status ${policy.postingMatrix[event].permission=='ALLOWED'?'policy-status-approved':''}"><c:choose><c:when test="${policy.postingMatrix[event].permission=='ALLOWED'}"><spring:message code="accounting.settings.on" /></c:when><c:otherwise><spring:message code="accounting.settings.off" /></c:otherwise></c:choose></span></dd></div></c:forEach>
    </dl></section>
    <section class="erp-detail-card policy-content-section"><h2><spring:message code="accounting.settings.accounts" /></h2><dl class="policy-setting-list">
        <c:forEach items="${accountKeys}" var="key"><div><dt><spring:message code="accounting.settings.account.${key}" /></dt><dd><c:choose><c:when test="${not empty accountLabels[policy.accountMappings[key]]}"><c:out value="${accountLabels[policy.accountMappings[key]]}" /></c:when><c:when test="${not empty policy.accountMappings[key]}"><spring:message code="accounting.settings.accountUnavailable" /></c:when><c:otherwise><spring:message code="accounting.settings.notSet" /></c:otherwise></c:choose></dd></div></c:forEach>
    </dl></section>
    <details class="erp-detail-card"><summary><spring:message code="common.review" /></summary><dl class="policy-detail-list"><div><dt><spring:message code="accounting.policy.evidence" /></dt><dd><c:out value="${policy.evidenceReference}" /></dd></div>
        <c:if test="${not empty policy.approvalDecision}"><div><dt><spring:message code="accounting.reviewEvidence" /></dt><dd><c:out value="${policy.approvalEvidence}" /></dd></div><div><dt><spring:message code="accounting.policy.reason" /></dt><dd><c:out value="${policy.approvalReason}" /></dd></div><div><dt><spring:message code="accounting.date" /></dt><dd><c:out value="${policy.approvedAt}" /></dd></div></c:if>
    </dl></details>
    <sec:authorize access="@access.has(principal, 'ACCOUNTING_POLICIES_APPROVE')"><c:if test="${empty policy.approvalDecision and policy.makerId ne pageContext.request.userPrincipal.principal.memberId}">
        <c:url var="policyDecisionUrl" value="/finance/policies/${policy.id}/decision" />
        <form class="policy-form" method="post" action="${policyDecisionUrl}"><h2><spring:message code="accounting.policy.decision" /></h2>
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="policy-field"><label for="decision"><spring:message code="accounting.policy.decision" /></label><select id="decision" name="decision" class="aws-control" required><option value=""><spring:message code="accounting.policy.select" /></option><option value="APPROVED"><spring:message code="accounting.settings.approve" /></option><option value="REJECTED"><spring:message code="accounting.settings.reject" /></option></select></div>
            <div class="policy-field"><label for="decisionEvidence"><spring:message code="accounting.policy.evidence" /></label><input id="decisionEvidence" name="evidenceReference" maxlength="1000" class="aws-control" required value="<c:out value='${decisionEvidence}' />" /></div>
            <div class="policy-field"><label for="decisionReason"><spring:message code="accounting.policy.reason" /></label><textarea id="decisionReason" name="reason" maxlength="2000" class="aws-control" required><c:out value="${decisionReason}" /></textarea></div>
            <label><input type="checkbox" name="confirmed" value="true" required /> <spring:message code="accounting.settings.reviewConfirm" /></label>
            <div class="policy-actions"><button type="submit" class="app-btn btn-neutral"><spring:message code="accounting.policy.recordDecision" /></button></div>
        </form>
    </c:if></sec:authorize>
</div>
<%@ include file="../../fragments/footer.jspf" %>
