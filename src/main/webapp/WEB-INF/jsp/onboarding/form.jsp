<%@ include file="head.jspf" %>
<c:set var="returnPath" value="${empty client?'/finance/clients/onboarding':'/finance/clients/onboarding/'}${empty client?'':client.id}"/>
<c:set var="modelName" value="${formKind=='create'?'createForm':formKind=='profile'?'profileForm':formKind=='document'?'documentForm':'reviewForm'}"/>
<div class="accounting-workspace accounting-create" data-resource-form-page ${formKind=='create' or formKind=='profile'?'data-resource-details-page':''}>
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a class="erp-page-path__link" href="<c:url value='${returnPath}'/>"><spring:message code="kyc.details"/></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><spring:message code="kyc.form.${formKind}"/></span></nav>
<h1><spring:message code="kyc.form.${formKind}"/></h1>
<c:if test="${not empty client}"><%@ include file="facts.jspf" %></c:if>
<form:form id="client-onboarding-form" method="post" enctype="${formKind=='document'?'multipart/form-data':'application/x-www-form-urlencoded'}" modelAttribute="${modelName}" cssClass="kyc-client-form" data-accounting-dirty-form="true" data-onboarding-form="${formKind}" data-workspace-submit="recover" data-workspace-return="client-onboarding-form" data-preserve-submit-command="true">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/><form:hidden path="requestKey"/>

    <c:choose><c:when test="${formKind=='create' or formKind=='profile'}">
<c:set var="identityReadOnly" value="${not empty client and client.portalEnabled}"/><c:set var="profileValues" value="${formKind=='create'?createForm.profile:profileForm.profile}"/>
<c:set var="groupLookup" value="/finance/clients/onboarding/groups"/>
<c:if test="${formKind=='profile'}"><form:hidden path="expectedSequence"/><c:set var="groupLookup" value="/finance/clients/onboarding/groups?clientId=${client.id}"/></c:if>
<div data-profile-tabs><div id="profile-bio" data-detail-panel><h2 class="sr-only"><spring:message code="kyc.section.bio"/></h2>
<%@ include file="profile-fields.jspf" %>
<c:if test="${formKind=='profile'}"><section class="erp-resource-section"><h2><spring:message code="kyc.notice"/></h2><div class="kyc-form-fields">
    <p class="kyc-notice-text"><c:out value="${pageContext.response.locale.language=='sw'?notice.kiswahili:notice.english}"/></p>
    <p class="kyc-field-hint"><spring:message code="kyc.assistedConsent"/></p>
    <label class="kyc-field"><span><spring:message code="kyc.field.legalHoldReference"/></span><form:textarea path="profile.legalHoldReference" cssClass="aws-control" rows="3" maxlength="500"/><form:errors path="profile.legalHoldReference" cssClass="coa-error"/></label>
</div></section></c:if>
</div>
<c:set var="profileDocuments" value="${documents}"/><sec:authorize access="@access.has(principal,'CLIENT_ONBOARDING_DOCUMENTS')" var="canReadProfileDocuments"/>
<c:set var="uploadPath" value="/finance/clients/onboarding/${client.id}/documents/new"/><c:set var="documentRoot" value="/finance/clients/onboarding/${client.id}/documents"/>
<%@ include file="profile-attachments.jspf" %></div>
</c:when><c:otherwise><section class="erp-resource-section"><h2><spring:message code="kyc.form.${formKind}"/></h2><div class="accounting-form-grid"><c:choose>
        <c:when test="${formKind=='document'}">
            <form:hidden path="revisionId"/>
            <label><span><spring:message code="kyc.field.category"/></span><form:select path="category" cssClass="aws-control"><c:forEach items="${['IDENTITY','ADDRESS','ACTIVITY','CONSENT','PROFILE_PHOTO','SPOUSE_PHOTO']}" var="category"><form:option value="${category}"><spring:message code="kyc.category.${category}"/></form:option></c:forEach></form:select></label>
            <label><span><spring:message code="kyc.field.expiresOn"/></span><form:input path="expiresOn" type="date" cssClass="aws-control"/></label>
            <label><span><spring:message code="kyc.field.file"/></span><input class="aws-control" type="file" name="file" accept=".pdf,.png,.jpg,.jpeg" required/></label>
        </c:when>
        <c:otherwise>
            <form:hidden path="revisionId"/>
            <label><span><spring:message code="kyc.field.decision"/></span><form:select path="kind" cssClass="aws-control" data-kyc-decision="true"><c:forEach items="${client.state=='SUBMITTED'?['VERIFY','RETURN','REJECT']:['REVOKE']}" var="kind"><form:option value="${kind}"><spring:message code="kyc.action.${kind}"/></form:option></c:forEach></form:select></label>
            <label><span><spring:message code="kyc.field.reason"/></span><form:textarea path="reason" cssClass="aws-control" rows="3" maxlength="1000" required="required"/></label>
            <label><span><spring:message code="kyc.field.clientMessage"/></span><form:textarea path="clientMessage" cssClass="aws-control" rows="3" maxlength="1000"/></label>
            <div class="accounting-form-grid" data-kyc-verify="true">
                <c:forEach items="${['identityChecked','addressChecked','activityChecked','contactChecked','duplicatesChecked']}" var="field"><label class="aws-settings-checkbox-row"><form:checkbox path="${field}"/><span><spring:message code="kyc.field.${field}"/></span></label></c:forEach>
                <label><span><spring:message code="kyc.field.screening"/></span><form:select path="screening" cssClass="aws-control"><c:forEach items="${['UNAVAILABLE','ESCALATED','CLEARED']}" var="result"><form:option value="${result}"><spring:message code="kyc.screening.${result}"/></form:option></c:forEach></form:select></label>
                <c:forEach items="${['screeningReference','policyReference']}" var="field"><label><span><spring:message code="kyc.field.${field}"/></span><form:textarea path="${field}" cssClass="aws-control" rows="3" maxlength="${field=='policyReference'?500:1000}"/></label></c:forEach>
                <c:forEach items="${['checkedOn','reviewDue']}" var="field"><label><span><spring:message code="kyc.field.${field}"/></span><form:input path="${field}" type="date" cssClass="aws-control"/></label></c:forEach>
            </div>
        </c:otherwise>
    </c:choose>
    </div></section></c:otherwise></c:choose>
    <spring:bind path="${modelName}"><c:if test="${not empty status.errors.globalErrors}"><div class="console-message" data-message-type="error" data-workspace-form-error data-workspace-response-error role="alert" tabindex="-1"><c:forEach items="${status.errors.globalErrors}" var="error"><p><spring:message message="${error}"/></p></c:forEach></div></c:if></spring:bind>
    <c:if test="${formKind=='document' or formKind=='review'}"><form:errors path="*" cssClass="field-error" element="p" role="alert"/></c:if>
    <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='${returnPath}'/>"><spring:message code="common.cancel"/></a>
        <c:choose><c:when test="${formKind=='profile'}"><button class="app-btn btn-neutral" type="submit" name="action" value="DRAFT" formnovalidate><spring:message code="kyc.saveDraft"/></button><button class="app-btn btn-launch" type="submit" name="action" value="SUBMITTED"><spring:message code="kyc.submit"/></button></c:when><c:otherwise><button class="app-btn btn-launch" type="submit"><spring:message code="credit.save"/></button></c:otherwise></c:choose>
    </div>
</form:form>
<c:if test="${formKind=='review'}"><%@ include file="profile.jspf" %><%@ include file="documents.jspf" %></c:if>
</div>
<script src="<c:url value='/js/client-onboarding.js'/>" defer></script><script src="<c:url value='/js/loan-recording.js'/>" defer></script>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
