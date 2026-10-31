<%@ include file="head.jspf" %>
<div class="kyc-form-workspace" data-resource-form-page>
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a href="<c:url value='${loanRoot}'/>"><spring:message code="kyc.loan.title"/></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><spring:message code="kyc.edit"/></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="kyc.loan.title"/></h1></div>
<form:form modelAttribute="loanRequestForm" method="post" cssClass="accounting-create-form kyc-client-form" data-accounting-dirty-form="true" data-preserve-submit-command="true" data-workspace-submit="recover" data-workspace-return="loan-request-form">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/><form:hidden path="requestKey"/><form:hidden path="expectedSequence"/>
<section class="erp-resource-section"><h2><spring:message code="recording.loanDetails"/></h2><div class="kyc-section-body kyc-form-fields">
<p><c:out value="${intakeClient.number}"/> &#183; <c:out value="${intakeClient.name}"/></p>
<c:url value="${loanRoot}/products" var="productsUrl"/>
<div data-loan-choice data-category="products" data-choice-url="${productsUrl}"><label class="kyc-field" for="intake-product"><span><spring:message code="recording.product"/></span></label>
<spring:message code="recording.product" var="intakeProductLabel"/>
<form:select path="details.productId" aria-label="${intakeProductLabel}" id="intake-product" cssClass="aws-control" data-choice-select="true"><form:option value=""><spring:message code="kyc.choose"/></form:option>
<c:if test="${not empty loanRequestForm.details.productId}"><form:option value="${loanRequestForm.details.productId}" data-tenure-unit="${selectedProduct.unit}" data-minimum-amount="${selectedProduct.minimum}" data-maximum-amount="${selectedProduct.maximum}" data-minimum-tenure="${selectedProduct.minTenure}" data-maximum-tenure="${selectedProduct.maxTenure}"><c:choose><c:when test="${not empty selectedProduct}"><c:out value="${selectedProduct.label}"/></c:when><c:otherwise><spring:message code="recording.unavailable"/></c:otherwise></c:choose></form:option></c:if></form:select>
<%@ include file="../accounting/loans/choice-search.jspf" %></div>
<div class="kyc-field-pair"><label class="kyc-field" for="intake-principal"><span><spring:message code="recording.loanAmount"/> (TZS)</span><form:input path="details.principal" id="intake-principal" type="number" min="0.01" step="0.01" cssClass="aws-control"/></label>
<label class="kyc-field" for="intake-tenure"><span><spring:message code="kyc.loan.period"/> <span data-intake-unit data-days="<spring:message code='recording.tenure.DAYS'/>" data-months="<spring:message code='recording.tenure.MONTHS'/>" data-years="<spring:message code='recording.tenure.YEARS'/>"></span></span><form:input path="details.tenureValue" id="intake-tenure" type="number" min="1" max="12000" step="1" cssClass="aws-control"/></label></div>
<c:forEach items="${['purpose','business']}" var="field"><label class="kyc-field" for="intake-${field}"><span><spring:message code="recording.${field}"/></span><form:textarea path="details.${field}" id="intake-${field}" rows="3" maxlength="1000" cssClass="aws-control"/></label></c:forEach>
</div></section>
<section class="erp-resource-section"><h2><spring:message code="kyc.loan.cashflow"/></h2><div class="kyc-section-body kyc-form-fields">
<c:forEach items="${['income','expenses','otherDebt']}" var="field"><label class="kyc-field" for="intake-${field}"><span><spring:message code="${field=='otherDebt'?'kyc.loan.otherDebt':'recording.'.concat(field)}"/> (TZS)</span><form:input path="details.${field}" id="intake-${field}" type="number" min="0" step="0.01" cssClass="aws-control"/></label></c:forEach>
<p class="kyc-field-hint"><spring:message code="kyc.loan.declared"/></p></div></section>
<form:errors path="*" cssClass="console-message" element="div" role="alert" data-message-type="error"/>
<div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='${loanRoot}'/>"><spring:message code="common.cancel"/></a><button class="app-btn btn-neutral" name="action" value="DRAFT" type="submit" formnovalidate><spring:message code="kyc.saveDraft"/></button><button class="app-btn btn-launch" name="action" value="SUBMITTED" type="submit"><spring:message code="kyc.loan.send"/></button></div>
</form:form></div>
<%@ include file="../accounting/accounting-form-support.jspf" %>
<script src="<c:url value='/js/loan-recording.js'/>" defer></script><script src="<c:url value='/js/loan-intake.js'/>" defer></script>
<%@ include file="../fragments/footer.jspf" %>
