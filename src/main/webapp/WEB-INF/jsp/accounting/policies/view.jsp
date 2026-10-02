<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-policy.css' />" />
<div class="policy-workspace">
    <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="accounting.policy.title" /> <c:out value="${policy.version}" /></h1></div>
    <c:if test="${not empty policyError}"><p class="policy-error" role="alert"><spring:message code="${policyError}" /></p></c:if>
    <dl class="policy-detail-list"><dt><spring:message code="accounting.policy.books" /></dt><dd><spring:message code="accounting.policy.books.${policy.authoritativeLedger}" /></dd>
        <dt><spring:message code="accounting.policy.opening" /></dt><dd><c:out value="${policy.openingDate}" /></dd><dt><spring:message code="accounting.policy.effective" /></dt><dd><c:out value="${policy.effectiveFrom}" /></dd>
        <dt><spring:message code="accounting.policy.evidence" /></dt><dd><c:out value="${policy.evidenceReference}" /></dd><dt><spring:message code="accounting.policy.status" /></dt><dd><c:choose><c:when test="${empty policy.approvalDecision}"><spring:message code="accounting.policy.pending" /></c:when><c:otherwise><spring:message code="accounting.policy.status.${policy.approvalDecision}" /></c:otherwise></c:choose></dd>
    </dl>
    <details class="policy-section" open><summary><spring:message code="accounting.policy.decisions" /></summary><c:forEach items="${decisionFields}" var="field"><h3><spring:message code="accounting.policy.decision.${field}" /></h3><p class="policy-decision"><c:out value="${policy.decisions[field]}" /></p></c:forEach></details>
    <details class="policy-section" open><summary><spring:message code="accounting.policy.matrix" /></summary><c:forEach items="${postingEvents}" var="event"><h3><spring:message code="accounting.policy.event.${event}" />: <spring:message code="accounting.policy.permission.${policy.postingMatrix[event].permission}" /></h3><p class="policy-decision"><c:out value="${policy.postingMatrix[event].treatment}" /></p></c:forEach></details>
    <details class="policy-section"><summary><spring:message code="accounting.policy.mappings" /></summary><c:forEach items="${policy.accountMappings}" var="mapping"><p class="policy-decision"><c:out value="${mapping.key}" /> = <c:out value="${mapping.value}" /></p></c:forEach></details>
    <c:if test="${not empty policy.approvalDecision}"><p class="policy-decision"><c:out value="${policy.approvalEvidence}" /><br /><c:out value="${policy.approvalReason}" /><br /><c:out value="${policy.approvedAt}" /></p></c:if>
    <sec:authorize access="@access.has(principal, 'ACCOUNTING_POLICIES_APPROVE')"><c:if test="${empty policy.approvalDecision and policy.makerId ne pageContext.request.userPrincipal.principal.memberId}">
        <c:url var="policyDecisionUrl" value="/finance/policies/${policy.id}/decision" />
        <form class="policy-form" method="post" action="${policyDecisionUrl}">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="policy-field"><label for="decision"><spring:message code="accounting.policy.decision" /></label><select id="decision" name="decision" class="aws-control" required><option value=""><spring:message code="accounting.policy.select" /></option><option value="APPROVED"><spring:message code="accounting.policy.approve" /></option><option value="REJECTED"><spring:message code="accounting.policy.reject" /></option></select></div>
            <div class="policy-field"><label for="decisionEvidence"><spring:message code="accounting.policy.evidence" /></label><input id="decisionEvidence" name="evidenceReference" maxlength="1000" class="aws-control" required value="<c:out value='${decisionEvidence}' />" /></div>
            <div class="policy-field"><label for="decisionReason"><spring:message code="accounting.policy.reason" /></label><textarea id="decisionReason" name="reason" maxlength="2000" class="aws-control" required><c:out value="${decisionReason}" /></textarea></div>
            <label><input type="checkbox" name="confirmed" value="true" required /> <spring:message code="accounting.policy.confirm" /></label>
            <div class="policy-actions"><button type="submit" class="app-btn btn-primary"><spring:message code="accounting.policy.recordDecision" /></button></div>
        </form>
    </c:if></sec:authorize>
</div>
<%@ include file="../../fragments/footer.jspf" %>
