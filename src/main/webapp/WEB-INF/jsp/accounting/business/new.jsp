<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-business.css' />" />
<div class="business-workspace">
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="finance.business.navigation" /></h1></div>
<p class="business-gate"><spring:message code="finance.business.gate" /></p>
<c:if test="${not empty sourceError}"><div class="business-error" role="alert"><spring:message code="${sourceError}" /></div></c:if>
<form:form modelAttribute="sourceForm" method="post" action="${pageContext.request.contextPath}/finance/business" cssClass="business-form"><input type="hidden" name="${_csrf.parameterName}" value="<c:out value='${_csrf.token}' />" /><form:hidden path="requestKey"/><div class="business-grid">
<label><spring:message code="finance.business.type" /><form:select path="kind" required="required"><form:option value="" label="—"/><c:forEach items="${sourceKinds}" var="kind"><form:option value="${kind}"><spring:message code="finance.business.kind.${kind}"/></form:option></c:forEach></form:select></label><label><spring:message code="finance.business.effectiveDate"/><form:input path="effectiveDate" type="date"  required="required" maxlength="100"/></label>
<label><spring:message code="finance.business.amount"/><form:input path="amount" type="number" step="0.01" min="0.01" required="required" maxlength="100"/></label>
<label><spring:message code="finance.business.description"/><form:input path="description" type="text"  required="required" maxlength="500"/></label>
<label><spring:message code="finance.business.evidenceReference"/><form:input path="evidenceReference" type="text"  required="required" maxlength="500"/></label>
<label><spring:message code="finance.business.channelReference"/><form:input path="channelReference" type="text"   maxlength="100"/></label>
<label><spring:message code="finance.business.loanId"/><form:input path="loanId" type="text"   maxlength="100"/></label>
<label><spring:message code="finance.business.relatedDocumentId"/><form:input path="relatedDocumentId" type="text"   maxlength="100"/></label>
<label><spring:message code="finance.business.supplierId"/><form:input path="supplierId" type="text"   maxlength="100"/></label>
<label><spring:message code="finance.business.destinationBranch"/><form:input path="destinationBranch" type="text"   maxlength="100"/></label>
<label><spring:message code="finance.business.loanNumber"/><form:input path="loanNumber" type="text"   maxlength="100"/></label>
<label><spring:message code="finance.business.firstRepaymentDate"/><form:input path="firstRepaymentDate" type="date"   maxlength="100"/></label>
<label><spring:message code="finance.business.installmentAmount"/><form:input path="installmentAmount" type="number" step="0.01" min="0.01"  maxlength="100"/></label>
<label><spring:message code="finance.business.moneyAccountKey"/><form:select path="moneyAccountKey"><form:option value="" label="—"/><form:option value="CASH"><spring:message code="repayment.channel.CASH"/></form:option><form:option value="BANK"><spring:message code="repayment.channel.BANK"/></form:option><form:option value="MOBILE_MONEY"><spring:message code="repayment.channel.MOBILE_MONEY"/></form:option></form:select></label>
<label><spring:message code="finance.business.frequency"/><form:select path="frequency"><form:option value="" label="—"/><form:option value="MONTHLY"><spring:message code="loan.frequency.MONTHLY" text="Monthly"/></form:option><form:option value="WEEKLY"><spring:message code="loan.frequency.WEEKLY" text="Weekly"/></form:option></form:select></label></div><p><spring:message code="finance.business.formHelp"/></p><div class="business-actions"><button class="app-btn btn-primary" type="submit"><spring:message code="finance.business.saveDraft"/></button></div></form:form>
</div>
<%@ include file="../../fragments/footer.jspf" %>

