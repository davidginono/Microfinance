<%@ include file="head.jspf" %>
<div class="accounting-workspace accounting-create">
<h1><spring:message code="kyc.noticeSettings"/></h1>
<form:form method="post" modelAttribute="noticeForm" cssClass="accounting-form" data-accounting-dirty-form="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/><form:hidden path="requestKey"/><form:hidden path="expectedSequence"/>
<section class="erp-resource-section"><h2><spring:message code="kyc.notice"/></h2><div class="accounting-form-grid">
<label class="aws-settings-checkbox-row"><form:checkbox path="enabled"/><span><spring:message code="kyc.enableSelfService"/></span></label>
<label><span><spring:message code="kyc.field.noticeVersion"/></span><form:input path="version" cssClass="aws-control" maxlength="100" required="required"/><form:errors path="version" cssClass="coa-error"/></label>
<c:forEach items="${['english','kiswahili']}" var="field"><label><span><spring:message code="kyc.notice.${field}"/></span><form:textarea path="${field}" cssClass="aws-control" rows="8" maxlength="12000" required="required"/><form:errors path="${field}" cssClass="coa-error"/></label></c:forEach>
<label><span><spring:message code="kyc.field.lawfulBasis"/></span><form:select path="lawfulBasis" cssClass="aws-control"><form:option value=""><spring:message code="kyc.choose"/></form:option><c:forEach items="${['CONSENT','CONTRACT','LEGAL_OBLIGATION']}" var="basis"><form:option value="${basis}"><spring:message code="kyc.basis.${basis}"/></form:option></c:forEach></form:select><form:errors path="lawfulBasis" cssClass="coa-error"/></label>
<label><span><spring:message code="kyc.field.retentionReference"/></span><form:textarea path="retentionReference" cssClass="aws-control" rows="3" maxlength="500" required="required"/><form:errors path="retentionReference" cssClass="coa-error"/></label>
<label><span><spring:message code="kyc.field.retentionReviewOn"/></span><form:input path="retentionReviewOn" type="date" cssClass="aws-control" required="required"/><form:errors path="retentionReviewOn" cssClass="coa-error"/></label>
</div></section>
<p class="console-message" data-message-type="info" role="status"><spring:message code="kyc.noticeSettingsHelp"/></p>
<spring:bind path="noticeForm"><c:if test="${not empty status.errors.globalErrors}"><div class="console-message" data-message-type="error" role="alert" tabindex="-1"><c:forEach items="${status.errors.globalErrors}" var="error"><p><spring:message message="${error}"/></p></c:forEach></div></c:if></spring:bind>
<div class="accounting-actions accounting-form-actions"><button class="app-btn btn-launch" type="submit"><spring:message code="kyc.publishNotice"/></button></div>
</form:form></div>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
