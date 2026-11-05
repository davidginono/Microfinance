<%@ include file="head.jspf" %>
<div class="accounting-workspace" data-resource-details-page>
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a class="erp-page-path__link" href="<c:url value='/finance/clients/onboarding'/>"><spring:message code="kyc.title"/></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><spring:message code="kyc.details"/></span></nav>
<div class="erp-page-header erp-detail-header" data-aws-page-header>
<h1 class="erp-page-title"><spring:message code="kyc.details"/></h1>
<c:if test="${not historical}"><div class="accounting-actions" data-aws-action-pin="true">
    <c:set var="showReview" value="${not empty reviewForm}"/>
    <c:if test="${not client.portalEnabled}"><sec:authorize access="@access.has(principal,'LOAN_RECORDING_CREATE')"><a class="app-btn ${showReview?'btn-neutral':'btn-launch'}" href="<c:url value='/finance/loan-records/new'><c:param name='clientId' value='${client.id}'/></c:url>"><spring:message code="applications.create"/></a></sec:authorize></c:if>
    <c:if test="${not client.portalEnabled and client.state!='SUBMITTED'}"><sec:authorize access="@access.has(principal,'CLIENT_ONBOARDING_UPDATE')"><a class="app-btn btn-neutral" href="<c:url value='/finance/clients/onboarding/${client.id}/edit'/>"><spring:message code="kyc.edit"/></a><c:if test="${client.state=='DRAFT'}"><a class="app-btn btn-neutral" href="<c:url value='/finance/clients/onboarding/${client.id}/documents/new'/>"><spring:message code="kyc.addDocument"/></a></c:if></sec:authorize></c:if>
</div></c:if>
</div>
<c:if test="${duplicate}"><p class="console-message" data-message-type="warning" role="status"><spring:message code="kyc.error.duplicate"/></p></c:if>
<%@ include file="facts.jspf" %>
<c:if test="${not empty revision}">
    <%@ include file="profile.jspf" %>
    <%@ include file="documents.jspf" %>
    <c:forEach items="${decisions}" var="decision"><section class="erp-resource-section"><h2><spring:message code="kyc.review"/></h2><dl class="erp-detail-facts">
        <div><dt><spring:message code="kyc.field.state"/></dt><dd><c:set var="onboardingStatusState" value="${decision.kind}"/><%@ include file="status.jspf" %></dd></div>
        <c:forEach items="${historical ? ['reason','clientMessage','screening','screeningReference','checkedOn','reviewDue','policyReference'] : ['reason','clientMessage','checkedOn']}" var="field"><div><dt><spring:message code="kyc.field.${field}"/></dt><dd><c:choose><c:when test="${field=='screening'}"><spring:message code="kyc.screening.${decision.screening}"/></c:when><c:otherwise><c:out value="${decision[field]}"/></c:otherwise></c:choose></dd></div></c:forEach>
        <div><dt><spring:message code="kyc.recordedBy"/></dt><dd><c:out value="${decision.actorName}"/> &#183; <c:out value="${appTime:display(decision.recordedAt)}"/></dd></div>
    </dl></section></c:forEach>
</c:if>
<c:if test="${not historical and not empty reviewForm}"><sec:authorize access="@access.has(principal,'CLIENT_ONBOARDING_REVIEW')">
<c:url value="/finance/clients/onboarding/${client.id}/review" var="reviewUrl"/>
<%@ include file="review-form.jspf" %>
</sec:authorize></c:if>
<section class="erp-resource-section"><h2><spring:message code="kyc.history"/></h2>
    <c:forEach items="${history.rows}" var="entry"><p><a class="resource-table-id-link" href="<c:url value='/finance/clients/onboarding/${client.id}/versions/${entry.sequence}'/>"><spring:message code="kyc.version"/> <c:out value="${entry.sequence}"/></a> &#183; <c:set var="onboardingStatusState" value="${entry.result}"/><%@ include file="status.jspf" %> &#183; <c:out value="${entry.actor}"/> &#183; <c:out value="${appTime:display(entry.recordedAt)}"/></p></c:forEach>
    <c:if test="${history.page>0}"><a href="?page=${history.page-1}"><spring:message code="common.previous"/></a></c:if>
    <c:if test="${history.hasNext}"><a href="?page=${history.page+1}"><spring:message code="common.next"/></a></c:if>
</section>
</div>
<script src="<c:url value='/js/client-onboarding.js'/>" defer></script>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
