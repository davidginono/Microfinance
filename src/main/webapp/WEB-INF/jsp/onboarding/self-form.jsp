<%@ include file="head.jspf" %>
<div class="accounting-workspace accounting-create">
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a href="<c:url value='/app/onboarding'/>"><spring:message code="kyc.ownTitle"/></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><spring:message code="kyc.form.${formKind}"/></span></nav>
<h1><spring:message code="kyc.form.${formKind}"/></h1>
<c:set var="modelName" value="${formKind=='self'?'selfForm':'documentForm'}"/>
<form:form method="post" modelAttribute="${modelName}" enctype="${formKind=='document'?'multipart/form-data':'application/x-www-form-urlencoded'}" cssClass="accounting-form" data-accounting-dirty-form="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/><form:hidden path="requestKey"/>
<c:choose><c:when test="${formKind=='self'}">
<form:hidden path="expectedSequence"/><form:hidden path="noticeId"/>
<section class="erp-resource-section"><h2><spring:message code="kyc.personal"/></h2><div class="accounting-form-grid">
<label><span><spring:message code="kyc.field.identityType"/></span><form:select path="profile.identityType" cssClass="aws-control"><form:option value=""><spring:message code="kyc.choose"/></form:option><c:forEach items="${['NATIONAL_ID','PASSPORT','VOTER_ID','DRIVING_LICENCE','OTHER']}" var="type"><form:option value="${type}"><spring:message code="kyc.identity.${type}"/></form:option></c:forEach></form:select><form:errors path="profile.identityType" cssClass="coa-error"/></label>
<c:forEach items="${['issuingCountry','identityNumber']}" var="field"><label><span><spring:message code="kyc.field.${field}"/></span><form:input path="profile.${field}" cssClass="aws-control" maxlength="${field=='issuingCountry'?2:64}"/><form:errors path="profile.${field}" cssClass="coa-error"/></label></c:forEach>
<c:forEach items="${['birthDate','identityExpiresOn']}" var="field"><label><span><spring:message code="kyc.field.${field}"/></span><form:input path="profile.${field}" type="date" cssClass="aws-control"/><form:errors path="profile.${field}" cssClass="coa-error"/></label></c:forEach>
<label class="aws-settings-checkbox-row"><form:checkbox path="profile.identityNoExpiry"/><span><spring:message code="kyc.field.identityNoExpiry"/></span></label>
<label><span><spring:message code="kyc.field.address"/></span><form:textarea path="profile.address" cssClass="aws-control" rows="3" maxlength="1000"/><form:errors path="profile.address" cssClass="coa-error"/></label>
</div></section>
<section class="erp-resource-section"><h2><spring:message code="kyc.activity"/></h2><div class="accounting-form-grid"><c:forEach items="${['activity','activityAddress','sourceOfFunds']}" var="field"><label><span><spring:message code="kyc.field.${field}"/></span><form:textarea path="profile.${field}" cssClass="aws-control" rows="3" maxlength="1000"/><form:errors path="profile.${field}" cssClass="coa-error"/></label></c:forEach></div></section>
<section class="erp-resource-section"><h2><spring:message code="kyc.notice"/></h2><div class="kyc-section-body">
<c:choose><c:when test="${not empty notice}">
<p><spring:message code="kyc.field.noticeVersion"/>: <c:out value="${notice.version}"/></p>
<div class="kyc-notice-text"><c:out value="${pageContext.response.locale.language=='sw'?notice.kiswahili:notice.english}"/></div>
<label class="aws-settings-checkbox-row"><form:checkbox path="noticeAcknowledged"/><span><spring:message code="${notice.lawfulBasis=='CONSENT'?'kyc.acceptConsent':'kyc.acceptNotice'}"/></span></label><form:errors path="noticeAcknowledged" cssClass="coa-error"/>
<label class="aws-settings-checkbox-row"><form:checkbox path="declarationAccepted"/><span><spring:message code="kyc.acceptDeclaration"/></span></label><form:errors path="declarationAccepted" cssClass="coa-error"/>
</c:when><c:otherwise><p class="console-message" data-message-type="warning" role="status"><spring:message code="kyc.error.noticeUnavailable"/></p></c:otherwise></c:choose>
</div></section>
<p class="console-message" data-message-type="info" role="status"><spring:message code="kyc.beforeSubmit"/></p>
</c:when><c:otherwise>
<form:hidden path="revisionId"/>
<section class="erp-resource-section"><h2><spring:message code="kyc.documents"/></h2><div class="accounting-form-grid">
<label><span><spring:message code="kyc.field.category"/></span><form:select path="category" cssClass="aws-control"><c:forEach items="${['IDENTITY','ADDRESS','ACTIVITY','CONSENT']}" var="category"><form:option value="${category}"><spring:message code="kyc.category.${category}"/></form:option></c:forEach></form:select><form:errors path="category" cssClass="coa-error"/></label>
<label><span><spring:message code="kyc.field.expiresOn"/></span><form:input path="expiresOn" type="date" cssClass="aws-control"/><form:errors path="expiresOn" cssClass="coa-error"/></label>
<label><span><spring:message code="kyc.field.file"/></span><input class="aws-control" type="file" name="file" accept=".pdf,.png,.jpg,.jpeg" required aria-describedby="kyc-file-help"/></label>
<p id="kyc-file-help"><spring:message code="kyc.fileHelp"/></p>
</div></section>
</c:otherwise></c:choose>
<spring:bind path="${modelName}"><c:if test="${not empty status.errors.globalErrors}"><div class="console-message" data-message-type="error" role="alert" tabindex="-1"><c:forEach items="${status.errors.globalErrors}" var="error"><p><spring:message message="${error}"/></p></c:forEach></div></c:if></spring:bind>
<div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/app/onboarding'/>"><spring:message code="common.cancel"/></a>
<c:choose><c:when test="${formKind=='self'}"><button class="app-btn btn-neutral" type="submit" name="action" value="DRAFT" formnovalidate><spring:message code="kyc.saveDraft"/></button><button class="app-btn btn-launch" type="submit" name="action" value="SUBMITTED"><spring:message code="kyc.submit"/></button></c:when><c:otherwise><button class="app-btn btn-launch" type="submit"><spring:message code="kyc.addDocument"/></button></c:otherwise></c:choose>
</div></form:form></div>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
