<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-policy.css' />" />
<div class="policy-workspace erp-detail-workspace">
    <spring:message code="accounting.policy.detailTitle" arguments="${policy.version}" text="Accounting policy (Version {0})" var="policyDetailTitle" />
    <nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/policies' />"><spring:message code="accounting.policy.title" /></a><span aria-hidden="true">›</span><span class="erp-page-path__item" aria-current="page"><c:out value="${policyDetailTitle}" /></span></nav>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><c:out value="${policyDetailTitle}" /></h1></div>
    <c:if test="${not empty policyError}"><p class="policy-error" role="alert"><spring:message code="${policyError}" /></p></c:if>
    <section class="erp-detail-card policy-overview"><h2><spring:message code="accounting.policy.overview" text="Policy details" /></h2><dl class="erp-detail-facts">
        <div><dt><spring:message code="accounting.policy.status" /></dt><dd><span class="policy-status ${policy.approvalDecision=='APPROVED'?'policy-status-approved':policy.approvalDecision=='REJECTED'?'policy-status-rejected':''}"><c:choose><c:when test="${empty policy.approvalDecision}"><spring:message code="accounting.policy.pending" /></c:when><c:otherwise><spring:message code="accounting.policy.status.${policy.approvalDecision}" /></c:otherwise></c:choose></span></dd></div>
        <div><dt><spring:message code="accounting.policy.books" /></dt><dd><spring:message code="accounting.policy.books.${policy.authoritativeLedger}" /></dd></div>
        <div><dt><spring:message code="accounting.policy.opening" /></dt><dd><c:out value="${policy.openingDate}" /></dd></div><div><dt><spring:message code="accounting.policy.effective" /></dt><dd><c:out value="${policy.effectiveFrom}" /></dd></div>
    </dl></section>
    <details class="erp-detail-card policy-explanation">
        <summary><spring:message code="accounting.policy.effects" text="How this policy affects workflows" /></summary>
        <dl class="policy-detail-list">
            <div><dt><spring:message code="accounting.policy.effects.scope" text="Institution-wide policy" /></dt><dd><spring:message code="accounting.policy.effects.scopeHelp" text="Supported accounting workflows select the approved version for the transaction date. Branch access and staff permissions are checked separately." /></dd></div>
            <div><dt><spring:message code="accounting.policy.effects.controls" text="Enforced controls" /></dt><dd><spring:message code="accounting.policy.effects.controlsHelp" text="Policy-backed posting checks the official books, opening date, allowed event and required account mappings. Financial statements and period closing also check the applicable approved policy." /></dd></div>
            <div><dt><spring:message code="accounting.policy.effects.guidance" text="Recorded accounting decisions" /></dt><dd><spring:message code="accounting.policy.effects.guidanceHelp" text="Written decisions preserve the treatment and evidence for review. They do not automatically change fees, loan terms, permissions or calculations. Supported specialised loan accounting also checks its configured interest-recognition mode." /></dd></div>
            <div><dt><spring:message code="accounting.policy.effects.vouchers" text="Core receipt, payment and journal vouchers" /></dt><dd><spring:message code="accounting.policy.effects.vouchersHelp" text="Core vouchers post directly without policy approval or a release gate. Account validation, branch scope, balanced entries, closed-period protection, audit and duplicate-posting checks still apply. Loan repayments retain their separate loan controls." /></dd></div>
        </dl>
    </details>
    <div class="policy-content-grid">
        <section class="erp-detail-card policy-content-section" aria-labelledby="policy-decisions-title">
            <h2 id="policy-decisions-title"><spring:message code="accounting.policy.decisions" /></h2>
            <c:forEach items="${decisionFields}" var="field">
                <details class="policy-entry">
                    <summary><spring:message code="accounting.policy.decision.${field}" /></summary>
                    <div class="policy-entry-body">
                        <p class="policy-entry-text"><c:choose><c:when test="${field eq 'INTEREST_RECOGNITION' and policy.decisions[field] eq 'CASH_DUE_INTEREST_V1'}"><spring:message code="accounting.policy.recognition.cash" text="Recognise due interest when collected." /></c:when><c:when test="${field eq 'INTEREST_RECOGNITION' and policy.decisions[field] eq 'ACCRUAL_DUE_INTEREST_V1'}"><spring:message code="accounting.policy.recognition.accrual" text="Accrue due interest to interest receivable before collection." /></c:when><c:otherwise><c:out value="${policy.decisions[field]}" /></c:otherwise></c:choose></p>
                        <c:if test="${field eq 'INTEREST_RECOGNITION' and policy.decisions[field] ne 'CASH_DUE_INTEREST_V1' and policy.decisions[field] ne 'ACCRUAL_DUE_INTEREST_V1'}">
                            <p class="policy-entry-notice"><spring:message code="accounting.policy.recognition.guidanceOnly" text="This entry records guidance only. Specialised policy-backed loan repayment and interest accrual require a supported recognition mode, even when the event is allowed. Receipt-based loan repayment uses its separate loan controls." /></p>
                        </c:if>
                    </div>
                </details>
            </c:forEach>
        </section>
        <section class="erp-detail-card policy-content-section" aria-labelledby="policy-rules-title">
            <h2 id="policy-rules-title"><spring:message code="accounting.policy.matrix" /></h2>
            <p class="policy-section-note"><spring:message code="accounting.policy.rulesScope" text="These permissions apply where a workflow checks this policy. Allowed does not enable an unsupported workflow or override its other controls." /></p>
            <c:forEach items="${postingEvents}" var="event">
                <details class="policy-entry">
                    <summary><span><spring:message code="accounting.policy.event.${event}" /></span><span class="policy-status ${policy.postingMatrix[event].permission=='ALLOWED'?'policy-status-approved':''}"><spring:message code="accounting.policy.permission.${policy.postingMatrix[event].permission}" /></span></summary>
                    <div class="policy-entry-body"><h3><spring:message code="accounting.policy.treatment" /></h3><p class="policy-entry-text"><c:out value="${policy.postingMatrix[event].treatment}" /></p></div>
                </details>
            </c:forEach>
        </section>
    </div>
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
<%@ include file="../../fragments/footer.jspf" %>
