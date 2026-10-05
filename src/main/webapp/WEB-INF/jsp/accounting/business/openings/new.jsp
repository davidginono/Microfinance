<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ include file="../../../fragments/header.jspf" %>
<%@ include file="../../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-business.css' />" />
<div class="business-workspace"><div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="finance.business.opening.upload" /></h1></div>
<p class="business-gate"><spring:message code="finance.business.opening.format" /></p>
<c:if test="${not empty openingError}"><div class="business-error" role="alert"><spring:message code="${openingError}" /></div></c:if>
<c:if test="${not empty preview}"><section class="business-form" aria-live="polite"><h2><spring:message code="finance.business.opening.preview" /></h2><p><spring:message code="finance.business.opening.total" />: TZS <c:out value="${preview.signedBalance}" /> · <spring:message code="finance.business.opening.rowCount" />: <c:out value="${preview.rows.size()}" /></p><p><spring:message code="finance.business.opening.selectAgain" /></p>
<div class="erp-table-wrap" data-aws-table-region><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="finance.business.opening.reference" /></th><th class="business-money">TZS</th><th><spring:message code="finance.business.opening.evidence" /></th></tr></thead><tbody><c:forEach items="${preview.rows}" var="r" end="24"><tr><td><c:out value="${r.reference}" /></td><td class="business-money"><c:out value="${r.signedBalance}" /></td><td><c:out value="${r.evidence}" /></td></tr></c:forEach><c:if test="${empty preview.rows}"><tr><td colspan="3"><spring:message code="finance.business.opening.explicitZero" /></td></tr></c:if></tbody></table></div></div></section></c:if>
<c:url value='/finance/business/openings' var='uploadUrl' />
<form:form method="post" action="${uploadUrl}" enctype="multipart/form-data" modelAttribute="openingForm" htmlEscape="true" cssClass="business-form"><form:hidden path="requestKey" />
<div class="business-grid"><label><spring:message code="finance.business.opening.accountCode" /><form:input path="accountCode" maxlength="40" required="required" /></label><label><spring:message code="finance.business.opening.through" /><form:input path="through" type="date" required="required" /></label>
<label><spring:message code="finance.business.type" /><form:select path="purpose" required="required"><form:option value=""><spring:message code="finance.business.opening.selectPurpose" /></form:option><c:forEach items="${purposes}" var="purpose"><form:option value="${purpose}"><spring:message code="finance.business.opening.purpose.${purpose}" /></form:option></c:forEach></form:select></label>
<label><spring:message code="finance.business.opening.evidence" /><form:input path="evidence" maxlength="500" required="required" /></label><label><spring:message code="finance.business.opening.file" /><input name="file" type="file" accept=".csv,text/csv,text/plain" required /></label></div>
<label class="business-confirm"><form:checkbox path="completeCoverage" /><span><spring:message code="finance.business.opening.coverage" /></span></label>
<div class="business-actions"><button class="app-btn btn-neutral" name="preview" value="true" type="submit"><spring:message code="finance.business.opening.preview" /></button><button class="app-btn btn-primary" name="preview" value="false" type="submit"><spring:message code="finance.business.opening.import" /></button></div></form:form>
</div><%@ include file="../../../fragments/footer.jspf" %>
