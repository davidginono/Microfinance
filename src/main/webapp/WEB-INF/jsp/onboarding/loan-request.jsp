<%@ include file="head.jspf" %>
<c:set var="profilePath" value="${staffIntake?'/finance/clients/onboarding/':'/app/onboarding'}${staffIntake?intakeClient.id:''}"/>
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a href="<c:url value='${profilePath}'/>"><spring:message code="kyc.details"/></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><spring:message code="kyc.loan.title"/></span></nav>
<h1><spring:message code="kyc.loan.title"/></h1>
<c:if test="${loanRequestSaved}"><div class="console-message" data-message-type="success" data-workspace-flashbar role="status"><spring:message code="kyc.loan.saved"/></div></c:if>
<div data-resource-details-page>
<section class="erp-resource-section"><h2><spring:message code="kyc.details"/></h2><dl class="erp-detail-facts">
<div><dt><spring:message code="kyc.field.number"/></dt><dd><c:out value="${intakeClient.number}"/></dd></div>
<div><dt><spring:message code="kyc.field.name"/></dt><dd><c:out value="${intakeClient.name}"/></dd></div>
<div><dt><spring:message code="kyc.field.state"/></dt><dd><spring:message code="${intakeClient.state=='VERIFY'?'kyc.loan.verified':'kyc.loan.provisional'}"/></dd></div>
<div><dt><spring:message code="kyc.loan.portal"/></dt><dd><spring:message code="${intakeClient.portalEnabled?'kyc.loan.portalEnabled':'kyc.loan.noPortal'}"/></dd></div>
<c:if test="${not empty loanRequest}">
<div><dt><spring:message code="kyc.loan.title"/></dt><dd><spring:message code="kyc.state.${loanRequest.mode}"/></dd></div>
<div><dt><spring:message code="recording.product"/></dt><dd><c:choose><c:when test="${not empty selectedProduct}"><c:out value="${selectedProduct.label}"/></c:when><c:otherwise><spring:message code="recording.unavailable"/></c:otherwise></c:choose></dd></div>
<c:forEach items="${['principal','income','expenses','otherDebt']}" var="field"><div><dt><spring:message code="${field=='otherDebt'?'kyc.loan.otherDebt':'recording.'.concat(field)}"/> (TZS)</dt><dd><c:choose><c:when test="${not empty loanRequest.details[field]}"><c:out value="${loanRequest.details[field]}"/></c:when><c:otherwise><spring:message code="recording.unavailable"/></c:otherwise></c:choose></dd></div></c:forEach>
<div><dt><spring:message code="kyc.loan.period"/></dt><dd><c:out value="${loanRequest.details.tenureValue}"/> <c:if test="${not empty loanRequest.details.tenureUnit}"><spring:message code="recording.tenure.${loanRequest.details.tenureUnit}"/></c:if></dd></div>
<c:forEach items="${['purpose','business']}" var="field"><div><dt><spring:message code="recording.${field}"/></dt><dd class="kyc-notice-text"><c:out value="${loanRequest.details[field]}"/></dd></div></c:forEach>
<div><dt><spring:message code="kyc.version"/></dt><dd><c:out value="${loanRequest.sequence}"/> &#183; <c:out value="${appTime:display(loanRequest.recordedAt)}"/></dd></div>
</c:if>
</dl><div class="kyc-section-body"><c:if test="${empty loanRequest}"><p><spring:message code="kyc.loan.empty"/></p></c:if><p class="console-message" data-message-type="info" role="status"><spring:message code="kyc.loan.declared"/></p>
<div class="accounting-actions">
<c:choose><c:when test="${staffIntake}"><sec:authorize access="@access.has(principal,'CLIENT_ONBOARDING_UPDATE')"><a class="app-btn btn-neutral" href="<c:url value='${loanRoot}/edit'/>"><spring:message code="kyc.edit"/></a></sec:authorize></c:when><c:otherwise><a class="app-btn btn-neutral" href="<c:url value='${loanRoot}/edit'/>"><spring:message code="kyc.edit"/></a></c:otherwise></c:choose>
<c:if test="${intakeClient.state=='VERIFY' and loanRequest.mode=='SUBMITTED'}"><c:choose><c:when test="${staffIntake}"><sec:authorize access="@access.has(principal,'LOAN_RECORDING_CREATE')"><a class="app-btn btn-launch" href="<c:url value='${loanRoot}/continue'/>"><spring:message code="kyc.loan.continue"/></a></sec:authorize></c:when><c:otherwise><sec:authorize access="@access.has(principal,'MEMBER_LOANS_CREATE')"><a class="app-btn btn-launch" href="<c:url value='/app/loan-applications/new?onboardingRequest=true'/>"><spring:message code="kyc.loan.continue"/></a></sec:authorize></c:otherwise></c:choose></c:if>
</div></div></section></div>
<%@ include file="../fragments/footer.jspf" %>
