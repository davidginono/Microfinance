<%@ include file="head.jspf" %>
<c:if test="${workspace.historical}"><nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a href="<c:url value='/app/onboarding'/>"><spring:message code="kyc.ownTitle"/></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><spring:message code="kyc.version"/> <c:out value="${workspace.revision.sequence}"/></span></nav></c:if>
<h1><spring:message code="kyc.ownTitle"/></h1>
<div data-resource-details-page>
<section class="erp-resource-section"><h2><spring:message code="kyc.field.state"/></h2><dl class="erp-detail-facts">
<c:forEach items="${['number','name','phone','email']}" var="field"><div><dt><spring:message code="kyc.field.${field}"/></dt><dd><c:out value="${workspace.client[field]}"/></dd></div></c:forEach>
<div><dt><spring:message code="kyc.field.state"/></dt><dd><span data-resource-status="${workspace.client.state=='VERIFY'?'SUCCESS':workspace.client.state=='SUBMITTED'?'PENDING':workspace.client.state=='RETURN' or workspace.client.state=='EXPIRED' or workspace.client.state=='STALE'?'WARNING':workspace.client.state=='REVOKE'?'STOPPED':workspace.client.state}"><spring:message code="kyc.state.${workspace.client.state}"/></span></dd></div>
<div><dt><spring:message code="kyc.field.due"/></dt><dd><c:out value="${appTime:display(workspace.client.reviewDue)}"/></dd></div>
<c:if test="${not empty workspace.revision}"><div><dt><spring:message code="kyc.version"/></dt><dd><c:out value="${workspace.revision.sequence}"/></dd></div></c:if>
</dl>
<div class="kyc-section-body">
<c:if test="${not empty workspace.feedback}"><div class="console-message" data-message-type="info" role="status"><strong><spring:message code="kyc.state.${workspace.feedback.kind}"/></strong><p class="kyc-notice-text"><c:out value="${workspace.feedback.message}"/></p><p><c:out value="${appTime:display(workspace.feedback.recordedAt)}"/></p></div></c:if>
<c:if test="${not workspace.historical}"><div class="accounting-actions">
<c:if test="${workspace.client.state!='SUBMITTED'}"><a class="app-btn btn-launch" href="<c:url value='/app/onboarding/edit'/>"><spring:message code="${workspace.client.state=='NOT_STARTED'?'kyc.start':'kyc.edit'}"/></a></c:if>
<c:if test="${workspace.client.state=='DRAFT'}"><a class="app-btn btn-neutral" href="<c:url value='/app/onboarding/documents/new'/>"><spring:message code="kyc.addDocument"/></a></c:if>
</div><p class="console-message" data-message-type="info" role="status"><spring:message code="${workspace.client.state=='SUBMITTED'?'kyc.awaitingReview':workspace.client.state=='VERIFY'?'kyc.verifiedMeaning':'kyc.declaredMeaning'}"/></p></c:if>
</div></section>
<c:if test="${not empty workspace.revision}">
<section class="erp-resource-section"><h2><spring:message code="kyc.personal"/></h2><dl class="erp-detail-facts">
<c:forEach items="${['identityType','issuingCountry','birthDate','identityExpiresOn','address','activity','activityAddress','sourceOfFunds']}" var="field"><div><dt><spring:message code="kyc.field.${field}"/></dt><dd><c:choose><c:when test="${field=='identityType' and not empty workspace.revision.profile.identityType}"><spring:message code="kyc.identity.${workspace.revision.profile.identityType}"/></c:when><c:when test="${field=='birthDate' or field=='identityExpiresOn'}"><c:out value="${appTime:display(workspace.revision.profile[field])}"/></c:when><c:otherwise><c:out value="${workspace.revision.profile[field]}"/></c:otherwise></c:choose></dd></div></c:forEach>
<div><dt><spring:message code="kyc.field.identityNumber"/></dt><dd>&#8226;&#8226;&#8226;&#8226;<c:out value="${workspace.revision.profile.identityNumber.length()>4?workspace.revision.profile.identityNumber.substring(workspace.revision.profile.identityNumber.length()-4):''}"/></dd></div>
<div><dt><spring:message code="kyc.field.identityNoExpiry"/></dt><dd><spring:message code="${workspace.revision.profile.identityNoExpiry?'kyc.yes':'kyc.no'}"/></dd></div>
</dl></section>
<section class="erp-resource-section"><h2><spring:message code="kyc.documents"/></h2><div class="kyc-section-body">
<c:forEach items="${workspace.documents}" var="document"><p><spring:message code="kyc.category.${document.category}"/>: <a class="resource-table-id-link" href="<c:url value='/app/onboarding/documents/${document.id}'/>"><c:out value="${document.name}"/></a><c:if test="${not empty document.expiresOn}"> &#183; <spring:message code="kyc.field.expiresOn"/> <c:out value="${appTime:display(document.expiresOn)}"/></c:if></p></c:forEach>
<c:if test="${empty workspace.documents}"><p><spring:message code="kyc.noDocuments"/></p></c:if>
<c:if test="${not workspace.historical and workspace.client.state=='DRAFT'}"><p><spring:message code="kyc.requiredDocuments"/></p></c:if>
</div></section>
<c:if test="${not empty workspace.revision.noticeEnglish}"><section class="erp-resource-section"><h2><spring:message code="kyc.notice"/></h2><div class="kyc-section-body"><p><c:out value="${workspace.revision.noticeVersion}"/></p><div class="kyc-notice-text"><c:out value="${pageContext.response.locale.language=='sw'?workspace.revision.noticeKiswahili:workspace.revision.noticeEnglish}"/></div><p><spring:message code="kyc.acknowledged"/>: <spring:message code="${workspace.revision.acknowledged?'kyc.yes':'kyc.no'}"/> &#183; <c:out value="${appTime:display(workspace.revision.acknowledgedAt)}"/></p></div></section></c:if>
</c:if>
<section class="erp-resource-section"><h2><spring:message code="kyc.history"/></h2><div class="kyc-section-body">
<c:forEach items="${workspace.history.rows}" var="entry"><p><a class="resource-table-id-link" href="<c:url value='/app/onboarding/versions/${entry.sequence}'/>"><spring:message code="kyc.version"/> <c:out value="${entry.sequence}"/></a> &#183; <spring:message code="kyc.state.${entry.result}"/> &#183; <c:out value="${appTime:display(entry.recordedAt)}"/></p></c:forEach>
<c:if test="${empty workspace.history.rows}"><p><spring:message code="kyc.noHistory"/></p></c:if>
<c:if test="${workspace.history.page>0}"><a href="<c:url value='/app/onboarding?page=${workspace.history.page-1}'/>"><spring:message code="common.previous"/></a></c:if>
<c:if test="${workspace.history.hasNext}"><a href="<c:url value='/app/onboarding?page=${workspace.history.page+1}'/>"><spring:message code="common.next"/></a></c:if>
</div></section>
</div>
<%@ include file="../fragments/footer.jspf" %>
