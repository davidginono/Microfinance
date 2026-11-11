<%@ include file="head.jspf" %>
<c:set var="returnPath" value="${empty client?'/finance/clients/onboarding':'/finance/clients/onboarding/'}${empty client?'':client.id}"/>
<c:set var="modelName" value="${formKind=='create'?'createForm':formKind=='profile'?'profileForm':'documentForm'}"/>
<div class="accounting-workspace accounting-create kyc-form-workspace ${formKind=='create'?'kyc-provisional-create':''}" data-resource-form-page>
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a class="erp-page-path__link" href="<c:url value='${returnPath}'/>"><spring:message code="${empty client?'kyc.title':'kyc.details'}"/></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><spring:message code="kyc.form.${formKind}"/></span></nav>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="kyc.form.${formKind}"/></h1></div>
<c:if test="${not empty client}"><%@ include file="facts.jspf" %></c:if>

<c:url value="/finance/clients/onboarding/contact-check" var="contactCheckUrl"/>
<form:form data-contact-check-url="${contactCheckUrl}" data-contact-client-id="${client.id}" id="client-onboarding-form" method="post" enctype="${formKind=='document' or formKind=='create'?'multipart/form-data':'application/x-www-form-urlencoded'}" modelAttribute="${modelName}" cssClass="kyc-client-form" data-accounting-dirty-form="true" data-onboarding-form="${formKind}" data-workspace-submit="recover" data-workspace-return="client-onboarding-form" data-preserve-submit-command="true">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/><form:hidden path="requestKey"/>

    <c:choose><c:when test="${formKind=='create' or formKind=='profile'}">
<c:set var="identityReadOnly" value="${not empty client and client.staffUser}"/><c:set var="profileValues" value="${formKind=='create'?createForm.profile:profileForm.profile}"/>
<c:set var="groupLookup" value="/finance/clients/onboarding/groups"/>
<c:if test="${formKind=='profile'}"><form:hidden path="expectedSequence"/><c:set var="groupLookup" value="/finance/clients/onboarding/groups?clientId=${client.id}"/></c:if>
<c:set var="profileDocuments" value="${documents}"/><sec:authorize access="@access.has(principal,'CLIENT_ONBOARDING_DOCUMENTS')" var="canReadProfileDocuments"/>
<c:set var="uploadPath" value="/finance/clients/onboarding/${client.id}/documents/new"/><c:set var="documentRoot" value="/finance/clients/onboarding/${client.id}/documents"/>
<%@ include file="profile-editor.jspf" %>
</c:when><c:when test="${formKind=='document'}">
    <section class="erp-resource-section"><h2><spring:message code="kyc.form.document"/></h2><div class="kyc-form-fields">
            <form:hidden path="revisionId"/>
            <label class="kyc-field"><span><spring:message code="kyc.field.category"/></span><form:select path="category" cssClass="aws-control"><c:forEach items="${['IDENTITY','ADDRESS','ACTIVITY','CONSENT','PROFILE_PHOTO','SPOUSE_PHOTO']}" var="category"><form:option value="${category}"><spring:message code="kyc.category.${category}"/></form:option></c:forEach></form:select></label>
            <label class="kyc-field"><span><spring:message code="kyc.field.expiresOn"/></span><form:input path="expiresOn" type="date" cssClass="aws-control"/></label>
            <label class="kyc-field"><span><spring:message code="kyc.field.file"/></span><input class="aws-control" type="file" name="file" accept=".png,.jpg,.jpeg" required
                data-upload-max-bytes="${documentForm.maxFileBytes}" data-upload-remaining-files="${documentForm.remainingFiles}" data-upload-remaining-bytes="${documentForm.remainingBytes}"
                data-upload-file-error="<spring:message code='kyc.error.file'/>" data-upload-limit-error="<spring:message code='kyc.error.uploadLimit'/>"/></label>
            <p class="kyc-field-hint"><spring:message code="kyc.fileHelp"/></p>
    </div></section>
</c:when></c:choose>
    <spring:bind path="${modelName}"><c:if test="${not empty status.errors.globalErrors}"><div class="console-message" data-message-type="error" data-workspace-form-error data-workspace-response-error role="alert" tabindex="-1"><c:forEach items="${status.errors.globalErrors}" var="error"><p><spring:message message="${error}"/></p></c:forEach></div></c:if></spring:bind>
    <c:if test="${formKind=='document'}"><form:errors path="*" cssClass="field-error" element="p" role="alert"/></c:if>

    <c:choose><c:when test="${formKind=='create' or formKind=='profile'}">
        <c:url value="${returnPath}" var="profileCancelUrl"/><c:url value="${formKind=='create'?'/finance/clients/onboarding/new':'/finance/clients/onboarding/'.concat(client.id).concat('/edit')}" var="profileSubmitUrl"/>
        <c:if test="${formKind=='profile'}"><c:set var="profileDraftUrl" value="${profileSubmitUrl}"/></c:if>
        <c:set var="profileSubmitValue" value="${formKind=='create'?'CREATE':'RECORDED'}"/><c:set var="profileSubmitLabel" value="${formKind=='create'?'kyc.create':'kyc.saveChanges'}"/>
        <%@ include file="profile-actions.jspf" %>
    </c:when><c:otherwise><div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='${returnPath}'/>"><spring:message code="common.cancel"/></a><button class="app-btn btn-launch" type="submit"><spring:message code="credit.save"/></button></div></c:otherwise></c:choose>
</form:form>
</div>
<script src="<c:url value='/js/client-onboarding.js'/>" defer></script><script src="<c:url value='/js/loan-recording.js'/>" defer></script>
<c:if test="${formKind=='create' or formKind=='profile'}"><script src="<c:url value='/js/client-profile-wizard.js'/>" defer></script></c:if>
<c:if test="${formKind=='create'}"><script src="<c:url value='/js/client-profile-documents.js'/>" defer></script></c:if>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
