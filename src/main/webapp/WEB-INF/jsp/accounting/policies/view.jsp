<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-policy.css' />" />
<div class="policy-workspace">
    <div class="erp-page-header policy-heading" data-aws-page-header><h1 class="erp-page-title"><spring:message code="policy.title" /> <c:out value="${policy.version()}" /></h1><span><spring:message code="policy.state.${policy.state()}" /></span></div>
    <c:if test="${not empty policyError}"><p class="policy-error" role="alert"><spring:message code="${policyError}" /></p></c:if>
    <c:if test="${not empty policySuccess}"><p class="policy-success" role="status"><spring:message code="${policySuccess}" /></p></c:if>
    <p class="policy-copy"><spring:message code="policy.immutableHelp" /></p>
    <sec:authorize access="@access.has(principal,'ACCOUNTING_POLICY_CREATE')"><a class="app-btn btn-neutral" href="<c:url value='/accounting/policies/new?source=${policy.id()}' />"><spring:message code="policy.new" /></a></sec:authorize>
    <div class="policy-form">
        <section class="policy-section"><h2><spring:message code="policy.books" /></h2><div class="policy-fields">
            <div><strong><spring:message code="policy.authority" /></strong><p><spring:message code="policy.authority.${policy.content().authority()}" /></p></div>
            <div><strong><spring:message code="policy.authorityEvidence" /></strong><p class="policy-value"><c:out value="${policy.content().authorityEvidence()}" /></p></div>
            <div><strong><spring:message code="policy.effectiveFrom" /></strong><p><c:out value="${policy.content().effectiveFrom()}" /></p></div>
            <div><strong><spring:message code="policy.openingDate" /></strong><p><c:out value="${policy.content().openingDate()}" /></p></div>
            <div><strong><spring:message code="policy.recordedAt" /></strong><p><c:out value="${policy.createdAt()}" /></p></div>
            <div><strong><spring:message code="policy.contentHash" /></strong><p class="policy-hash"><c:out value="${policy.contentHash()}" /></p></div>
        </div></section>
        <section class="policy-section"><h2><spring:message code="policy.decisions" /></h2><div class="policy-fields">
            <c:forEach items="${policy.content().decisions()}" var="entry"><div><strong><spring:message code="policy.decision.${entry.key}" /></strong><p class="policy-value"><c:out value="${entry.value}" /></p></div></c:forEach>
        </div></section>
        <section class="policy-section"><h2><spring:message code="policy.postingMatrix" /></h2>
            <c:forEach items="${policy.content().postingRules()}" var="entry"><details class="policy-rule"><summary><spring:message code="policy.event.${entry.key}" /> · <spring:message code="${entry.value.enabled() ? 'policy.enabled' : 'policy.disabled'}" /></summary>
                <p class="policy-value"><c:out value="${entry.value.treatment()}" /></p><p class="policy-value"><spring:message code="policy.evidence" />: <c:out value="${entry.value.evidenceReference()}" /></p>
                <div class="policy-fields"><c:forEach items="${entry.value.accountCodes()}" var="mapping"><div><strong><spring:message code="policy.role.${mapping.key}" /></strong><p><c:out value="${mapping.value}" /></p></div></c:forEach></div>
            </details></c:forEach>
        </section>
        <c:if test="${policy.state() == 'DRAFT'}"><section class="policy-section"><h2><spring:message code="policy.review" /></h2><p class="policy-copy"><spring:message code="policy.reviewHelp" /></p>
            <form method="post" action="<c:url value='/accounting/policies/${policy.id()}/approve' />" class="policy-form">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="expectedHash" value="<c:out value='${policy.contentHash()}' />" />
                <label class="policy-field" for="reviewEvidence"><spring:message code="policy.evidence" /><input class="aws-control" id="reviewEvidence" name="evidence" maxlength="500" required value="<c:out value='${reviewEvidence}' />" /></label>
                <label class="policy-field" for="reviewReason"><spring:message code="policy.reviewReason" /><textarea class="aws-control" id="reviewReason" name="reason" rows="3" maxlength="2000" required><c:out value="${reviewReason}" /></textarea></label>
                <div class="policy-actions">
                    <sec:authorize access="@access.has(principal,'ACCOUNTING_POLICY_APPROVE')"><button class="app-btn btn-primary" type="submit"><spring:message code="policy.approve" /></button></sec:authorize>
                    <sec:authorize access="@access.has(principal,'ACCOUNTING_POLICY_REJECT')"><button class="app-btn btn-neutral" type="submit" formaction="<c:url value='/accounting/policies/${policy.id()}/reject' />"><spring:message code="policy.reject" /></button></sec:authorize>
                </div>
            </form>
        </section></c:if>
        <c:if test="${policy.state() != 'DRAFT'}"><section class="policy-section"><h2><spring:message code="policy.review" /></h2><p class="policy-value"><c:out value="${policy.reviewEvidence()}" /></p><p class="policy-value"><c:out value="${policy.reviewReason()}" /></p><p><c:out value="${policy.checkedAt()}" /></p></section></c:if>
    </div>
</div>
<%@ include file="../../fragments/footer.jspf" %>
