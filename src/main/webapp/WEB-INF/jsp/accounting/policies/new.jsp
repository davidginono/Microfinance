<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-policy.css' />" />
<div class="policy-workspace">
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="policy.new" /></h1></div>
    <p class="policy-copy"><spring:message code="policy.draftHelp" /></p>
    <c:if test="${not empty policyError}"><p class="policy-error" role="alert"><spring:message code="${policyError}" /></p></c:if>
    <form class="policy-form" method="post" action="<c:url value='/accounting/policies' />">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <input type="hidden" name="requestKey" value="<c:out value='${form.requestKey}' />" />
        <section class="policy-section">
            <h2><spring:message code="policy.books" /></h2>
            <div class="policy-fields">
                <label class="policy-field" for="authority"><spring:message code="policy.authority" /><select class="aws-control" id="authority" name="authority" required>
                    <option value=""><spring:message code="policy.choose" /></option>
                    <option value="LOCAL" ${form.authority == 'LOCAL' ? 'selected' : ''}><spring:message code="policy.authority.LOCAL" /></option>
                    <option value="EXTERNAL" ${form.authority == 'EXTERNAL' ? 'selected' : ''}><spring:message code="policy.authority.EXTERNAL" /></option>
                </select></label>
                <label class="policy-field" for="authorityEvidence"><spring:message code="policy.authorityEvidence" /><input class="aws-control" id="authorityEvidence" name="authorityEvidence" maxlength="500" required value="<c:out value='${form.authorityEvidence}' />" /></label>
                <label class="policy-field" for="effectiveFrom"><spring:message code="policy.effectiveFrom" /><input class="aws-control" type="date" id="effectiveFrom" name="effectiveFrom" required value="<c:out value='${form.effectiveFrom}' />" /></label>
                <label class="policy-field" for="openingDate"><spring:message code="policy.openingDate" /><input class="aws-control" type="date" id="openingDate" name="openingDate" required value="<c:out value='${form.openingDate}' />" /></label>
            </div>
        </section>
        <section class="policy-section"><h2><spring:message code="policy.decisions" /></h2><div class="policy-fields">
            <c:forEach items="${decisionKeys}" var="key"><c:set var="fieldKey" value="decision.${key}" />
                <label class="policy-field" for="decision-${key}"><spring:message code="policy.decision.${key}" />
                <textarea class="aws-control" id="decision-${key}" name="${fieldKey}" rows="3" maxlength="2000"><c:out value="${form[fieldKey]}" /></textarea></label>
            </c:forEach>
        </div></section>
        <section class="policy-section"><h2><spring:message code="policy.postingMatrix" /></h2>
            <p class="policy-copy"><spring:message code="policy.matrixHelp" /></p>
            <c:forEach items="${eventKeys}" var="event"><c:set var="prefix" value="rule.${event}." />
                <details class="policy-rule"><summary><spring:message code="policy.event.${event}" /></summary><div class="policy-fields">
                    <c:set var="fieldKey" value="${prefix}enabled" />
                    <label class="policy-field" for="enabled-${event}"><spring:message code="policy.treatmentState" /><select class="aws-control" id="enabled-${event}" name="${fieldKey}">
                        <option value=""><spring:message code="policy.undecided" /></option>
                        <option value="true" ${form[fieldKey] == 'true' ? 'selected' : ''}><spring:message code="policy.enabled" /></option>
                        <option value="false" ${form[fieldKey] == 'false' ? 'selected' : ''}><spring:message code="policy.disabled" /></option>
                    </select></label>
                    <c:set var="fieldKey" value="${prefix}evidence" />
                    <label class="policy-field" for="evidence-${event}"><spring:message code="policy.evidence" /><input class="aws-control" id="evidence-${event}" name="${fieldKey}" maxlength="500" value="<c:out value='${form[fieldKey]}' />" /></label>
                    <c:set var="fieldKey" value="${prefix}treatment" />
                    <label class="policy-field" for="treatment-${event}"><spring:message code="policy.treatment" /><textarea class="aws-control" id="treatment-${event}" name="${fieldKey}" maxlength="2000" rows="3"><c:out value="${form[fieldKey]}" /></textarea></label>
                </div><details class="policy-rule"><summary><spring:message code="policy.accountMappings" /></summary><div class="policy-fields">
                    <c:forEach items="${accountRoles}" var="role"><c:set var="fieldKey" value="${prefix}account.${role}" />
                        <label class="policy-field" for="account-${event}-${role}"><spring:message code="policy.role.${role}" /><input class="aws-control" id="account-${event}-${role}" name="${fieldKey}" maxlength="40" pattern="[A-Za-z0-9][A-Za-z0-9._-]{0,39}" value="<c:out value='${form[fieldKey]}' />" /></label>
                    </c:forEach>
                </div></details></details>
            </c:forEach>
        </section>
        <div class="policy-actions"><button class="app-btn btn-primary" type="submit"><spring:message code="policy.saveDraft" /></button></div>
    </form>
</div>
<%@ include file="../../fragments/footer.jspf" %>
