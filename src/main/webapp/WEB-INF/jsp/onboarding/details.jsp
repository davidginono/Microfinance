<%@ include file="head.jspf" %>
<div class="accounting-workspace" data-resource-details-page>
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a class="erp-page-path__link" href="<c:url value='/finance/clients/onboarding'/>"><spring:message code="kyc.title"/></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><spring:message code="kyc.details"/></span></nav>
<div class="erp-page-header erp-detail-header" data-aws-page-header>
<h1 class="erp-page-title"><spring:message code="kyc.details"/></h1>
<c:if test="${not historical}"><sec:authorize access="@access.hasAny(principal,'LOAN_RECORDING_CREATE','CLIENT_ONBOARDING_UPDATE')"><details class="page-action-menu" data-page-action-menu data-aws-action-pin="true">
        <summary class="app-btn btn-neutral"><spring:message code="common.actions" /></summary>
        <div class="page-action-options">
    <sec:authorize access="@access.has(principal,'LOAN_RECORDING_CREATE')"><a class="app-btn btn-launch" href="<c:url value='/finance/loan-records/new'><c:param name='clientId' value='${client.id}'/></c:url>"><spring:message code="applications.create"/></a></sec:authorize>
    <sec:authorize access="@access.has(principal,'CLIENT_ONBOARDING_UPDATE')"><a class="app-btn btn-neutral" href="<c:url value='/finance/clients/onboarding/${client.id}/edit'/>"><spring:message code="kyc.edit"/></a><c:if test="${client.state=='DRAFT' or client.state=='RECORDED'}"><a class="app-btn btn-neutral" href="<c:url value='/finance/clients/onboarding/${client.id}/documents/new'/>"><spring:message code="kyc.addDocument"/></a></c:if></sec:authorize>
</div></details></sec:authorize></c:if>
</div>
<c:if test="${provisionalClientCreated}">
    <div class="workspace-flashbar" data-workspace-flashbar><div class="app-notification-bar console-message" data-message-type="success" data-app-toast role="status" tabindex="-1">
        <p data-toast-text><spring:message code="kyc.provisionalCreated"/></p>
        <button type="button" class="app-toast-close" data-feedback-dismiss aria-label="<spring:message code='form.notification.dismiss'/>"><svg class="app-toast-close-icon" viewBox="0 0 16 16" aria-hidden="true"><path d="M3 3l10 10M13 3L3 13" fill="none" stroke="currentColor" stroke-width="2"/></svg></button>
    </div></div>
</c:if>
<c:if test="${duplicate}"><p class="console-message" data-message-type="warning" role="status"><spring:message code="kyc.error.duplicate"/></p></c:if>
<%@ include file="facts.jspf" %>
<c:if test="${not empty revision}">
    <div id="client-profile" class="kyc-detail-group" data-detail-panel>
    <h2 class="sr-only"><spring:message code="kyc.tab.profile"/></h2>
    <%@ include file="profile.jspf" %>
    <c:set var="profileValues" value="${revision.profile}"/>
    <c:set var="profileSections" value="${['bio','contact','spouse']}"/>
    <%@ include file="profile-details.jspf" %>
    </div>
    <div id="client-business" class="kyc-detail-group" data-detail-panel>
    <h2 class="sr-only"><spring:message code="kyc.tab.business"/></h2>
    <c:set var="profileSections" value="${['business','bank']}"/>
    <%@ include file="profile-details.jspf" %>
    </div>
    <div id="client-documents" data-detail-panel>
    <%@ include file="documents.jspf" %>
    </div>
</c:if>
<div id="client-history" class="kyc-detail-group" data-detail-panel>
<h2 class="sr-only"><spring:message code="kyc.history"/></h2>
<c:if test="${not empty revision}">
    <c:forEach items="${decisions}" var="decision"><section class="erp-resource-section"><h2><spring:message code="kyc.field.decision"/></h2><dl class="erp-detail-facts">
        <div><dt><spring:message code="kyc.field.state"/></dt><dd><c:set var="onboardingStatusState" value="${decision.kind}"/><%@ include file="status.jspf" %></dd></div>
        <c:forEach items="${historical ? ['reason','clientMessage','screening','screeningReference','checkedOn','reviewDue','policyReference'] : ['reason','clientMessage','checkedOn']}" var="field"><div><dt><spring:message code="kyc.field.${field}"/></dt><dd><c:choose><c:when test="${field=='screening'}"><spring:message code="kyc.screening.${decision.screening}"/></c:when><c:otherwise><c:out value="${decision[field]}"/></c:otherwise></c:choose></dd></div></c:forEach>
        <div><dt><spring:message code="kyc.recordedBy"/></dt><dd><c:out value="${decision.actorName}"/> &#183; <c:out value="${appTime:display(decision.recordedAt)}"/></dd></div>
    </dl></section></c:forEach>
</c:if>
<section class="erp-resource-section"><h2><spring:message code="kyc.history"/></h2>
    <c:forEach items="${history.rows}" var="entry"><p><a class="resource-table-id-link" href="<c:url value='/finance/clients/onboarding/${client.id}/versions/${entry.sequence}'/>"><spring:message code="kyc.version"/> <c:out value="${entry.sequence}"/></a> &#183; <c:set var="onboardingStatusState" value="${entry.result}"/><%@ include file="status.jspf" %> &#183; <c:out value="${entry.actor}"/> &#183; <c:out value="${appTime:display(entry.recordedAt)}"/></p></c:forEach>
    <c:if test="${history.page>0}"><a href="?page=${history.page-1}"><spring:message code="common.previous"/></a></c:if>
    <c:if test="${history.hasNext}"><a href="?page=${history.page+1}"><spring:message code="common.next"/></a></c:if>
</section>
</div>
</div>
<script src="<c:url value='/js/client-onboarding.js'/>" defer></script>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
