<%@ include file="head.jspf" %>
<div class="kyc-form-workspace" data-resource-form-page ${formKind=='self' and profileReadOnly?'data-resource-details-page':''}>
    <c:if test="${formKind!='self'}"><nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>">
        <a href="<c:url value='${onboardingRoot}'/>"><spring:message code="kyc.form.self"/></a>
        <span aria-hidden="true">&#8250;</span>
        <span aria-current="page"><spring:message code="kyc.form.${formKind}"/></span>
    </nav></c:if>
    <c:if test="${formKind=='self' and onboardingSaved}">
        <div class="workspace-flashbar" data-workspace-flashbar><div class="app-notification-bar console-message" data-message-type="success" data-app-toast role="status" tabindex="-1">
            <p data-toast-text><spring:message code="kyc.profileSaved"/></p>
            <button type="button" class="app-toast-close" data-feedback-dismiss aria-label="<spring:message code='form.notification.dismiss'/>"><svg class="app-toast-close-icon" viewBox="0 0 16 16" aria-hidden="true"><path d="M3 3l10 10M13 3L3 13" fill="none" stroke="currentColor" stroke-width="2"/></svg></button>
        </div></div>
    </c:if>
    <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="kyc.form.${formKind}"/></h1></div>
    <c:set var="modelName" value="${formKind=='self'?'selfForm':'documentForm'}"/>
    <c:url value="${formKind=='self'?onboardingRoot.concat('/edit'):onboardingRoot.concat('/documents/new')}" var="selfFormAction"/>
    <form:form id="client-onboarding-form" action="${selfFormAction}" method="post" modelAttribute="${modelName}"
               enctype="${formKind=='document'?'multipart/form-data':'application/x-www-form-urlencoded'}"
               cssClass="accounting-create-form kyc-client-form" data-accounting-dirty-form="true"
               data-portal-recover="${portalWorkspace}" data-onboarding-form="${formKind}" data-preserve-submit-command="true" data-workspace-submit="recover">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" data-profile-csrf/>
        <form:hidden path="requestKey"/>
        <c:choose>
            <c:when test="${formKind=='self'}">
                <form:hidden path="expectedSequence"/>
                <form:hidden path="noticeId"/>
                <section class="erp-resource-section" aria-labelledby="kyc-verification-heading" data-detail-persistent>
                    <h2 id="kyc-verification-heading"><spring:message code="kyc.verification"/></h2>
                    <div class="kyc-form-fields">
                        <c:set var="onboardingStatusState" value="${workspace.client.state}"/><%@ include file="status.jspf" %>
                        <c:choose>
                            <c:when test="${profileReadOnly}"><p class="kyc-field-hint"><spring:message code="kyc.awaitingReview"/></p></c:when>
                            <c:when test="${workspace.client.state=='VERIFY'}"><p class="kyc-field-hint"><spring:message code="kyc.verifiedMeaning"/></p></c:when>
                            <c:when test="${clientOnboardingRequired}"><p class="kyc-field-hint"><spring:message code="kyc.workspaceRequired"/></p></c:when>
                        </c:choose>
                        <c:if test="${not empty workspace.feedback}"><div class="console-message" data-message-type="${workspace.feedback.kind=='REJECT' or workspace.feedback.kind=='REVOKE'?'error':'warning'}" role="${workspace.feedback.kind=='REJECT' or workspace.feedback.kind=='REVOKE'?'alert':'status'}"><strong><spring:message code="kyc.state.${workspace.feedback.kind}"/></strong><p class="kyc-notice-text"><c:out value="${workspace.feedback.message}"/></p></div></c:if>
                    </div>
                </section>
<c:set var="client" value="${workspace.client}"/><c:set var="identityReadOnly" value="${true}"/><c:set var="profileValues" value="${selfForm.profile}"/><c:set var="groupLookup" value="${onboardingRoot}/groups"/>
<c:set var="profileDocuments" value="${workspace.documents}"/><c:set var="canReadProfileDocuments" value="${true}"/><c:set var="uploadPath" value="${onboardingRoot}/documents/new"/><c:set var="documentRoot" value="${onboardingRoot}/documents"/>
<%@ include file="profile-editor.jspf" %>
            </c:when>
            <c:otherwise>
                <form:hidden path="revisionId"/>
                <section class="erp-resource-section" aria-labelledby="kyc-documents-heading">
                    <h2 id="kyc-documents-heading"><spring:message code="kyc.documents"/></h2>
                    <div class="kyc-form-fields">
                        <div class="kyc-field-pair">
                            <label class="kyc-field">
                                <span><spring:message code="kyc.field.category"/></span>
                                <form:select path="category" cssClass="aws-control">
                                    <c:forEach items="${['IDENTITY','ADDRESS','ACTIVITY','CONSENT','PROFILE_PHOTO','SPOUSE_PHOTO']}" var="category">
                                        <form:option value="${category}"><spring:message code="kyc.category.${category}"/></form:option>
                                    </c:forEach>
                                </form:select>
                                <form:errors path="category" cssClass="coa-error"/>
                            </label>
                            <label class="kyc-field">
                                <span><spring:message code="kyc.field.expiresOn"/></span>
                                <form:input path="expiresOn" type="date" cssClass="aws-control"/>
                                <form:errors path="expiresOn" cssClass="coa-error"/>
                            </label>
                        </div>
                        <div class="kyc-field">
                            <label for="kyc-document-file"><spring:message code="kyc.field.file"/></label>
                            <input id="kyc-document-file" class="aws-control" type="file" name="file" accept=".png,.jpg,.jpeg" required aria-describedby="kyc-file-help"
                                   data-upload-max-bytes="${documentForm.maxFileBytes}" data-upload-remaining-files="${documentForm.remainingFiles}" data-upload-remaining-bytes="${documentForm.remainingBytes}"
                                   data-upload-file-error="<spring:message code='kyc.error.file'/>" data-upload-limit-error="<spring:message code='kyc.error.uploadLimit'/>"/>
                            <p id="kyc-file-help" class="kyc-field-hint"><spring:message code="kyc.fileHelp"/></p>
                        </div>
                    </div>
                </section>
            </c:otherwise>
        </c:choose>
        <spring:bind path="${modelName}">
            <c:if test="${not empty status.errors.globalErrors}">
                <div class="console-message" data-message-type="error" data-workspace-form-error data-workspace-response-error role="alert" tabindex="-1">
                    <c:forEach items="${status.errors.globalErrors}" var="error"><p><spring:message message="${error}"/></p></c:forEach>
                </div>
            </c:if>
        </spring:bind>
        <c:if test="${not profileReadOnly}"><c:choose><c:when test="${formKind=='self'}">
            <c:url value="${onboardingRoot}" var="profileCancelUrl"/><c:url value="${onboardingRoot}/edit" var="profileDraftUrl"/><c:set var="profileSubmitUrl" value="${profileDraftUrl}"/><c:set var="profileSubmitValue" value="SUBMITTED"/><c:set var="profileSubmitLabel" value="kyc.submit"/>
            <%@ include file="profile-actions.jspf" %>
        </c:when><c:otherwise><div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='${onboardingRoot}'/>"><spring:message code="common.cancel"/></a><button class="app-btn btn-launch" type="submit"><spring:message code="kyc.addDocument"/></button></div></c:otherwise></c:choose></c:if>
    </form:form>
</div>
<script src="<c:url value='/js/loan-recording.js'/>" defer></script><script src="<c:url value='/js/client-onboarding.js'/>" defer></script>
<c:if test="${formKind=='self' and not profileReadOnly}"><script src="<c:url value='/js/client-profile-wizard.js'/>" defer></script></c:if>
<c:if test="${formKind=='self' and not profileReadOnly}"><script src="<c:url value='/js/client-profile-documents.js'/>" defer></script></c:if>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<c:choose><c:when test="${portalWorkspace}"><%@ include file="../portal/foot.jspf" %></c:when><c:otherwise><%@ include file="../fragments/footer.jspf" %></c:otherwise></c:choose>
