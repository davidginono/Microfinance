<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-policy.css' />" />
<div class="policy-workspace erp-detail-workspace">
    <spring:message code="voucher.select" var="policySelect" />
    <c:set var="policyDetailTitle"><spring:message code="accounting.policy.title" /> <c:out value="${policy.version}" /></c:set>
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/policies' />"><spring:message code="accounting.policy.title" /></a><span aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><c:out value="${policyDetailTitle}" /></span></nav>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><c:out value="${policyDetailTitle}" /></h1></div>
    <c:if test="${not empty policyError}"><p class="policy-error" role="alert"><spring:message code="${policyError}" /></p></c:if>
    <section class="erp-detail-card policy-overview"><h2><spring:message code="accounting.policy.title" /></h2><dl class="erp-detail-facts">
        <div><dt><spring:message code="accounting.policy.status" /></dt><dd><span class="policy-status ${policy.approvalDecision=='APPROVED'?'policy-status-approved':policy.approvalDecision=='REJECTED'?'policy-status-rejected':''}"><c:choose><c:when test="${empty policy.approvalDecision}"><spring:message code="accounting.policy.pending" /></c:when><c:otherwise><spring:message code="accounting.policy.status.${policy.approvalDecision}" /></c:otherwise></c:choose></span></dd></div>
        <div><dt><spring:message code="accounting.policy.books" /></dt><dd><spring:message code="accounting.policy.books.${policy.authoritativeLedger}" /></dd></div>
        <div><dt><spring:message code="accounting.policy.opening" /></dt><dd><c:out value="${policy.openingDate}" /></dd></div><div><dt><spring:message code="accounting.policy.effective" /></dt><dd><c:out value="${policy.effectiveFrom}" /></dd></div>
    </dl></section>
    <section data-resource-table="policy-decisions" data-table-selection="single" data-resource-split class="erp-table-wrap policy-record-table" data-aws-table-region>
        <div class="app-table-titlebar"><h2><spring:message code="accounting.policy.decisions" /></h2></div>
        <div class="erp-table-scroll erp-table-scroll-sm" data-view-position-key="policy-decisions"><table class="erp-table"><thead><tr><th><span class="sr-only"><c:out value="${policySelect}" /></span></th><th><spring:message code="accounting.policy.decisions" /></th><th><spring:message code="table.details" /></th></tr></thead><tbody>
            <c:forEach items="${decisionFields}" var="field"><spring:message code="accounting.policy.decision.${field}" var="decisionLabel" /><tr><td><input type="checkbox" aria-label="<c:out value='${policySelect} ${decisionLabel}' />" /></td><td><c:out value="${decisionLabel}" /></td><td><span class="policy-row-summary"><c:out value="${policy.decisions[field]}" /></span></td></tr></c:forEach>
        </tbody></table></div>
    </section>
    <section data-resource-table="policy-posting-rules" data-table-selection="single" data-resource-split class="erp-table-wrap policy-record-table" data-aws-table-region>
        <div class="app-table-titlebar"><h2><spring:message code="accounting.policy.matrix" /></h2></div>
        <div class="erp-table-scroll erp-table-scroll-sm" data-view-position-key="policy-posting-rules"><table class="erp-table"><thead><tr><th><span class="sr-only"><c:out value="${policySelect}" /></span></th><th><spring:message code="accounting.policy.matrix" /></th><th><spring:message code="accounting.policy.status" /></th><th><spring:message code="accounting.policy.treatment" /></th></tr></thead><tbody>
            <c:forEach items="${postingEvents}" var="event"><spring:message code="accounting.policy.event.${event}" var="eventLabel" /><tr><td><input type="checkbox" aria-label="<c:out value='${policySelect} ${eventLabel}' />" /></td><td><c:out value="${eventLabel}" /></td><td><span class="policy-status ${policy.postingMatrix[event].permission=='ALLOWED'?'policy-status-approved':''}"><spring:message code="accounting.policy.permission.${policy.postingMatrix[event].permission}" /></span></td><td><span class="policy-row-summary"><c:out value="${policy.postingMatrix[event].treatment}" /></span></td></tr></c:forEach>
        </tbody></table></div>
    </section>
    <details class="erp-detail-card"><summary><spring:message code="accounting.policy.mappings" /></summary><dl class="policy-detail-list"><c:forEach items="${policy.accountMappings}" var="mapping"><div><dt><c:out value="${mapping.key}" /></dt><dd><c:out value="${mapping.value}" /></dd></div></c:forEach></dl></details>
    <details class="erp-detail-card"><summary><spring:message code="common.review" /></summary><dl class="policy-detail-list"><div><dt><spring:message code="accounting.policy.evidence" /></dt><dd><c:out value="${policy.evidenceReference}" /></dd></div>
        <c:if test="${not empty policy.approvalDecision}"><div><dt><spring:message code="accounting.reviewEvidence" /></dt><dd><c:out value="${policy.approvalEvidence}" /></dd></div><div><dt><spring:message code="accounting.policy.reason" /></dt><dd><c:out value="${policy.approvalReason}" /></dd></div><div><dt><spring:message code="accounting.date" /></dt><dd><c:out value="${policy.approvedAt}" /></dd></div></c:if>
    </dl></details>
    <sec:authorize access="@access.has(principal, 'ACCOUNTING_POLICIES_APPROVE')"><c:if test="${empty policy.approvalDecision and policy.makerId ne pageContext.request.userPrincipal.principal.memberId}">
        <c:url var="policyDecisionUrl" value="/finance/policies/${policy.id}/decision" />
        <form class="policy-form" method="post" action="${policyDecisionUrl}"><h2><spring:message code="accounting.policy.decision" /></h2>
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="policy-field"><label for="decision"><spring:message code="accounting.policy.decision" /></label><select id="decision" name="decision" class="aws-control" required><option value=""><spring:message code="accounting.policy.select" /></option><option value="APPROVED"><spring:message code="accounting.policy.approve" /></option><option value="REJECTED"><spring:message code="accounting.policy.reject" /></option></select></div>
            <div class="policy-field"><label for="decisionEvidence"><spring:message code="accounting.policy.evidence" /></label><input id="decisionEvidence" name="evidenceReference" maxlength="1000" class="aws-control" required value="<c:out value='${decisionEvidence}' />" /></div>
            <div class="policy-field"><label for="decisionReason"><spring:message code="accounting.policy.reason" /></label><textarea id="decisionReason" name="reason" maxlength="2000" class="aws-control" required><c:out value="${decisionReason}" /></textarea></div>
            <label><input type="checkbox" name="confirmed" value="true" required /> <spring:message code="accounting.policy.confirm" /></label>
            <div class="policy-actions"><button type="submit" class="app-btn btn-neutral"><spring:message code="accounting.policy.recordDecision" /></button></div>
        </form>
    </c:if></sec:authorize>
</div>
<script src="<c:url value='/js/accounting-policy-details.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>
