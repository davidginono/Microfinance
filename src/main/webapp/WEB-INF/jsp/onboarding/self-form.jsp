<%@ include file="head.jspf" %>
<div class="kyc-form-workspace" data-resource-form-page ${formKind=='self'?'data-resource-details-page':''}>
    <c:if test="${formKind!='self'}"><nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>">
        <a href="<c:url value='${onboardingRoot}'/>"><spring:message code="kyc.form.self"/></a>
        <span aria-hidden="true">&#8250;</span>
        <span aria-current="page"><spring:message code="kyc.form.${formKind}"/></span>
    </nav></c:if>
    <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="kyc.form.${formKind}"/></h1></div>
    <c:if test="${formKind=='self'}">
        <c:if test="${onboardingSaved}"><div class="console-message" data-message-type="success" data-workspace-flashbar role="status"><spring:message code="kyc.profileSaved"/></div></c:if>
        <c:if test="${clientOnboardingRequired and not profileReadOnly}"><p class="console-message" data-message-type="info" role="status"><spring:message code="kyc.workspaceRequired"/></p></c:if>
        <p class="console-message" data-message-type="info" role="status"><spring:message code="${profileReadOnly?'kyc.awaitingReview':workspace.client.state=='VERIFY'?'kyc.verifiedMeaning':'kyc.declaredMeaning'}"/></p>
        <c:if test="${not empty workspace.feedback}"><div class="console-message" data-message-type="info" role="status"><strong><spring:message code="kyc.state.${workspace.feedback.kind}"/></strong><p class="kyc-notice-text"><c:out value="${workspace.feedback.message}"/></p></div></c:if>
    </c:if>
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
<c:set var="client" value="${workspace.client}"/><c:set var="identityReadOnly" value="${true}"/><c:set var="profileValues" value="${selfForm.profile}"/><c:set var="groupLookup" value="${onboardingRoot}/groups"/>
<div data-profile-tabs><div id="profile-bio" data-detail-panel><h2 class="sr-only"><spring:message code="kyc.section.bio"/></h2><fieldset class="kyc-profile-fields" ${profileReadOnly?'disabled':''}><%@ include file="profile-fields.jspf" %></fieldset>
                <section class="erp-resource-section" aria-labelledby="kyc-notice-heading">
                    <h2 id="kyc-notice-heading"><spring:message code="kyc.notice"/></h2>
                    <div class="kyc-form-fields">
                        <c:choose>
                            <c:when test="${profileReadOnly and not empty workspace.revision}">
                                <dl class="kyc-notice-version"><dt><spring:message code="kyc.field.noticeVersion"/></dt><dd><c:out value="${workspace.revision.noticeVersion}"/></dd></dl>
                                <div class="kyc-notice-text"><c:out value="${pageContext.response.locale.language=='sw'?workspace.revision.noticeKiswahili:workspace.revision.noticeEnglish}"/></div>
                                <p><spring:message code="kyc.acknowledged"/>: <spring:message code="${workspace.revision.acknowledged?'kyc.yes':'kyc.no'}"/> &#183; <c:out value="${appTime:display(workspace.revision.acknowledgedAt)}"/></p>
                            </c:when>
                            <c:when test="${not empty notice}">
                                <dl class="kyc-notice-version">
                                    <dt><spring:message code="kyc.field.noticeVersion"/></dt>
                                    <dd><c:out value="${notice.version}"/></dd>
                                </dl>
                                <div class="kyc-notice-text"><c:out value="${pageContext.response.locale.language=='sw'?notice.kiswahili:notice.english}"/></div>
                                <div class="kyc-acknowledgement">
                                    <label class="kyc-checkbox">
                                        <form:checkbox path="noticeAcknowledged" required="true"/>
                                        <span><spring:message code="${notice.lawfulBasis=='CONSENT'?'kyc.acceptConsent':'kyc.acceptNotice'}"/></span>
                                    </label>
                                    <form:errors path="noticeAcknowledged" cssClass="coa-error"/>
                                </div>
                                <div class="kyc-acknowledgement">
                                    <label class="kyc-checkbox">
                                        <form:checkbox path="declarationAccepted" required="true"/>
                                        <span><spring:message code="kyc.acceptDeclaration"/></span>
                                    </label>
                                    <form:errors path="declarationAccepted" cssClass="coa-error"/>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <p class="console-message" data-message-type="warning" role="status"><spring:message code="kyc.error.noticeUnavailable"/></p>
                            </c:otherwise>
                        </c:choose>
                    </div>
                </section>
                <c:if test="${not profileReadOnly}"><p class="console-message" data-message-type="info" role="status"><spring:message code="kyc.beforeSubmit"/></p></c:if></div>
<c:set var="profileDocuments" value="${workspace.documents}"/><c:set var="canReadProfileDocuments" value="${true}"/><c:set var="uploadPath" value="${onboardingRoot}/documents/new"/><c:set var="photoRoot" value="${onboardingRoot}/photos"/><c:set var="documentRoot" value="${onboardingRoot}/documents"/><%@ include file="profile-attachments.jspf" %></div>
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
                    <button class="app-btn btn-launch" type="submit" name="action" value="SUBMITTED" formaction="<c:url value='${onboardingRoot}/edit'/>"><spring:message code="kyc.submit"/></button>
                </c:when>
                <c:otherwise><button class="app-btn btn-launch" type="submit"><spring:message code="kyc.addDocument"/></button></c:otherwise>
            </c:choose>
        </div></c:if>
    </form:form>
</div>
<script src="<c:url value='/js/loan-recording.js'/>" defer></script><script src="<c:url value='/js/client-onboarding.js'/>" defer></script>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<c:choose><c:when test="${portalWorkspace}"><%@ include file="../portal/foot.jspf" %></c:when><c:otherwise><%@ include file="../fragments/footer.jspf" %></c:otherwise></c:choose>
