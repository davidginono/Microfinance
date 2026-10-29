<%@ include file="head.jspf" %>
<c:set var="returnPath" value="${empty client?'/finance/clients/onboarding':'/finance/clients/onboarding/'}${empty client?'':client.id}"/>
<c:set var="modelName" value="${formKind=='create'?'createForm':formKind=='profile'?'profileForm':formKind=='document'?'documentForm':'reviewForm'}"/>
<div class="accounting-workspace accounting-create">
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a class="erp-page-path__link" href="<c:url value='${returnPath}'/>"><spring:message code="kyc.details"/></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><spring:message code="kyc.form.${formKind}"/></span></nav>
<h1><spring:message code="kyc.form.${formKind}"/></h1>
<c:if test="${not empty client}"><%@ include file="facts.jspf" %></c:if>
<form:form method="post" enctype="${formKind=='document'?'multipart/form-data':'application/x-www-form-urlencoded'}" modelAttribute="${modelName}" cssClass="accounting-form" data-accounting-dirty-form="true" data-onboarding-form="${formKind}">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/><form:hidden path="requestKey"/>
    <form:errors path="*" cssClass="field-error" element="p" role="alert"/>
    <section class="erp-resource-section"><h2><spring:message code="kyc.form.${formKind}"/></h2><div class="accounting-form-grid">
    <c:choose>
        <c:when test="${formKind=='create'}"><c:forEach items="${['number','name','phone','email']}" var="field"><label><span><spring:message code="kyc.field.${field}"/></span><form:input path="${field}" cssClass="aws-control" maxlength="255" required="required"/></label></c:forEach></c:when>
        <c:when test="${formKind=='profile'}">
            <form:hidden path="expectedSequence"/>
            <label><span><spring:message code="kyc.field.identityType"/></span><form:select path="profile.identityType" cssClass="aws-control"><form:option value=""><spring:message code="kyc.choose"/></form:option><c:forEach items="${['NATIONAL_ID','PASSPORT','VOTER_ID','DRIVING_LICENCE','OTHER']}" var="type"><form:option value="${type}"><spring:message code="kyc.identity.${type}"/></form:option></c:forEach></form:select></label>
            <c:forEach items="${['issuingCountry','identityNumber','noticeVersion']}" var="field"><label><span><spring:message code="kyc.field.${field}"/></span><form:input path="profile.${field}" cssClass="aws-control" maxlength="${field=='issuingCountry'?2:field=='identityNumber'?64:100}"/></label></c:forEach>
            <c:forEach items="${['birthDate','identityExpiresOn','consentOn','retentionReviewOn']}" var="field"><label><span><spring:message code="kyc.field.${field}"/></span><form:input path="profile.${field}" type="date" cssClass="aws-control"/></label></c:forEach>
            <label class="aws-settings-checkbox-row"><form:checkbox path="profile.identityNoExpiry"/><span><spring:message code="kyc.field.identityNoExpiry"/></span></label>
            <label><span><spring:message code="kyc.field.lawfulBasis"/></span><form:select path="profile.lawfulBasis" cssClass="aws-control"><form:option value=""><spring:message code="kyc.choose"/></form:option><c:forEach items="${['CONSENT','CONTRACT','LEGAL_OBLIGATION']}" var="basis"><form:option value="${basis}"><spring:message code="kyc.basis.${basis}"/></form:option></c:forEach></form:select></label>
            <c:forEach items="${['address','activity','activityAddress','sourceOfFunds','consentReference','retentionReference','legalHoldReference']}" var="field"><label><span><spring:message code="kyc.field.${field}"/></span><form:textarea path="profile.${field}" cssClass="aws-control" rows="3" maxlength="${field=='retentionReference'||field=='legalHoldReference'?500:1000}"/></label></c:forEach>
        </c:when>
        <c:when test="${formKind=='document'}">
            <form:hidden path="revisionId"/>
            <label><span><spring:message code="kyc.field.category"/></span><form:select path="category" cssClass="aws-control"><c:forEach items="${['IDENTITY','ADDRESS','ACTIVITY','CONSENT']}" var="category"><form:option value="${category}"><spring:message code="kyc.category.${category}"/></form:option></c:forEach></form:select></label>
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
    </div></section>
    <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='${returnPath}'/>"><spring:message code="common.cancel"/></a>
        <c:choose><c:when test="${formKind=='profile'}"><button class="app-btn btn-neutral" type="submit" name="action" value="DRAFT"><spring:message code="kyc.saveDraft"/></button><button class="app-btn btn-launch" type="submit" name="action" value="SUBMITTED"><spring:message code="kyc.submit"/></button></c:when><c:otherwise><button class="app-btn btn-launch" type="submit"><spring:message code="credit.save"/></button></c:otherwise></c:choose>
    </div>
</form:form>
<c:if test="${formKind=='review'}"><%@ include file="profile.jspf" %><%@ include file="documents.jspf" %></c:if>
</div>
<script src="<c:url value='/js/client-onboarding.js'/>" defer></script>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
