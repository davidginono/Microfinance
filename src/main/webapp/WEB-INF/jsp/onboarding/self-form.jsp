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
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
        <form:hidden path="requestKey"/>
        <c:choose>
            <c:when test="${formKind=='self'}">
                <form:hidden path="expectedSequence"/>
                <form:hidden path="noticeId"/>
                <section class="erp-resource-section" aria-labelledby="kyc-verification-heading" data-detail-persistent>
                    <h2 id="kyc-verification-heading"><spring:message code="kyc.verification"/></h2>
                    <div class="kyc-form-fields">
                        <span data-resource-status="${workspace.client.state=='VERIFY'?'SUCCESS':workspace.client.state=='SUBMITTED'?'PENDING':workspace.client.state=='REJECT' or workspace.client.state=='REVOKE'?'ERROR':workspace.client.state=='RETURN' or workspace.client.state=='EXPIRED' or workspace.client.state=='STALE'?'WARNING':'DRAFT'}"><spring:message code="kyc.state.${workspace.client.state}"/></span>
                        <c:choose>
                            <c:when test="${profileReadOnly}"><p class="kyc-field-hint"><spring:message code="kyc.awaitingReview"/></p></c:when>
                            <c:when test="${workspace.client.state=='VERIFY'}"><p class="kyc-field-hint"><spring:message code="kyc.verifiedMeaning"/></p></c:when>
                            <c:when test="${clientOnboardingRequired}"><p class="kyc-field-hint"><spring:message code="kyc.workspaceRequired"/></p></c:when>
                        </c:choose>
                        <c:if test="${not empty workspace.feedback}"><div class="console-message" data-message-type="${workspace.feedback.kind=='REJECT' or workspace.feedback.kind=='REVOKE'?'error':'warning'}" role="${workspace.feedback.kind=='REJECT' or workspace.feedback.kind=='REVOKE'?'alert':'status'}"><strong><spring:message code="kyc.state.${workspace.feedback.kind}"/></strong><p class="kyc-notice-text"><c:out value="${workspace.feedback.message}"/></p></div></c:if>
                    </div>
                </section>
<c:set var="client" value="${workspace.client}"/><c:set var="identityReadOnly" value="${true}"/><c:set var="profileValues" value="${selfForm.profile}"/><c:set var="groupLookup" value="${onboardingRoot}/groups"/>
<c:if test="${not profileReadOnly}"><%@ include file="profile-wizard-labels.jspf" %></c:if>
<div ${profileReadOnly?'data-profile-tabs':'data-profile-wizard'}><div id="profile-bio" ${profileReadOnly?'data-detail-panel':''}><h2 class="sr-only"><spring:message code="kyc.section.bio"/></h2><fieldset class="kyc-profile-fields" ${profileReadOnly?'disabled':''}><%@ include file="profile-fields.jspf" %></fieldset>
                <section class="erp-resource-section" aria-labelledby="kyc-notice-heading" data-profile-section="documents">
                    <h2 id="kyc-notice-heading"><spring:message code="kyc.notice"/></h2>
                    <div class="kyc-form-fields">
                        <c:if test="${workspace.client.state!='VERIFY'}"><p class="kyc-field-hint"><spring:message code="kyc.declaredMeaning"/></p></c:if>
                        <c:choose>
                            <c:when test="${profileReadOnly and not empty workspace.revision}">
                                <div class="kyc-notice-text"><c:out value="${pageContext.response.locale.language=='sw'?workspace.revision.noticeKiswahili:workspace.revision.noticeEnglish}"/></div>
                                <p><spring:message code="kyc.acknowledged"/>: <spring:message code="${workspace.revision.acknowledged?'kyc.yes':'kyc.no'}"/> &#183; <c:out value="${appTime:display(workspace.revision.acknowledgedAt)}"/></p>
                            </c:when>
                            <c:when test="${not empty notice}">
                                <div class="kyc-notice-text"><c:out value="${pageContext.response.locale.language=='sw'?notice.kiswahili:notice.english}"/></div>
                                <div class="kyc-acknowledgement">
                                    <label class="kyc-checkbox">
                                        <form:checkbox path="noticeAcknowledged" required="true"/>
                                        <span><spring:message code="kyc.acceptConsent"/></span>
                                    </label>
                                    <form:errors path="noticeAcknowledged" cssClass="coa-error"/>
                                </div>
                            </c:when>
                        </c:choose>
                    </div>
                </section>
                </div>
<c:set var="profileDocuments" value="${workspace.documents}"/><c:set var="canReadProfileDocuments" value="${true}"/><c:set var="uploadPath" value="${onboardingRoot}/documents/new"/><c:set var="documentRoot" value="${onboardingRoot}/documents"/><%@ include file="profile-attachments.jspf" %></div>
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
                            <input id="kyc-document-file" class="aws-control" type="file" name="file" accept=".pdf,.png,.jpg,.jpeg" required aria-describedby="kyc-file-help"/>
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
        <c:if test="${not profileReadOnly}"><div class="accounting-actions accounting-form-actions" data-form-actions>
            <a class="app-btn btn-neutral" href="<c:url value='${onboardingRoot}'/>"><spring:message code="common.cancel"/></a>
            <c:choose>
                <c:when test="${formKind=='self'}">
                    <button class="app-btn btn-neutral" type="submit" name="action" value="DRAFT" formaction="<c:url value='${onboardingRoot}/edit'/>" formnovalidate><spring:message code="kyc.saveDraft"/></button>
                    <button class="app-btn btn-neutral" type="button" data-profile-previous hidden><spring:message code="common.previous"/></button>
                    <button class="app-btn btn-launch" type="button" data-profile-next hidden><spring:message code="common.next"/></button>
                    <button class="app-btn btn-launch" type="submit" name="action" value="SUBMITTED" formaction="<c:url value='${onboardingRoot}/edit'/>"><spring:message code="kyc.submit"/></button>
                </c:when>
                <c:otherwise><button class="app-btn btn-launch" type="submit"><spring:message code="kyc.addDocument"/></button></c:otherwise>
            </c:choose>
        </div></c:if>
    </form:form>
</div>
<script src="<c:url value='/js/loan-recording.js'/>" defer></script><script src="<c:url value='/js/client-onboarding.js'/>" defer></script>
<c:if test="${formKind=='self' and not profileReadOnly}"><script src="<c:url value='/js/client-profile-wizard.js'/>" defer></script></c:if>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<c:choose><c:when test="${portalWorkspace}"><%@ include file="../portal/foot.jspf" %></c:when><c:otherwise><%@ include file="../fragments/footer.jspf" %></c:otherwise></c:choose>
