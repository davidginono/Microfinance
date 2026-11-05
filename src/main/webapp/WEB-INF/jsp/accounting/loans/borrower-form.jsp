<%@ include file="application-head.jspf" %>
<c:url value="${applicationRoot}" var="root"/>
<c:set var="formAction" value="${borrowerWorkspace ? root : root.concat('/save')}"/>
<c:set var="formTitle" value="${borrowerWorkspace ? 'newloan.title' : 'recording.create.LOAN'}"/>
<c:if test="${borrowerWorkspace}">
    <%@ include file="../../fragments/otp-ui-styles.jspf" %>
    <%@ include file="../../fragments/attachment-dropzone.jspf" %>
</c:if>
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a class="erp-page-path__link" href="${borrowerWorkspace or portalWorkspace ? root : pageContext.request.contextPath.concat('/finance/loan-records')}"><spring:message code="${borrowerWorkspace or portalWorkspace ? 'apps.title' : 'recording.register.LOAN'}"/></a><span aria-hidden="true">&#8250;</span><span aria-current="page"><spring:message code="${formTitle}"/></span></nav>
<h1 class="erp-page-title"><spring:message code="${formTitle}"/></h1>
<form:form id="loanApplicationForm" modelAttribute="applicationForm" method="post" action="${formAction}" enctype="multipart/form-data" cssClass="accounting-create-form application-create-workspace" data-workspace-submit="recover" data-resource-form-page="true" data-portal-recover="${portalWorkspace}" data-client-application="true" data-root="${root}"
    data-borrower-workspace="${borrowerWorkspace}" data-csrf-name="${_csrf.parameterName}"
    data-initial-tab="${loanFormInitialStep == 3 or param.step == '3' ? 'guarantors' : param.step == '4' ? 'documents' : 'information'}"
    data-submit-confirmation="<spring:message code='applications.confirmSubmission'/>"
    data-purpose-error="<spring:message code='newloan.purpose.maxWords'/>">
<c:forEach items="${['principalAmount','interestAmount','applicationFee','insuranceFee','processingFee','totalDeductions','periodicRepaymentAmount','monthlyRepaymentAmount','principalPlusInterest','numberOfPayments','topUpAmountAfterFees','interestRate','interestMethod','repaymentFrequency']}" var="key"><span hidden data-quote-label="${key}"><spring:message code="applications.quote.${key}"/></span></c:forEach>
<form:hidden path="applicationId"/>
<c:choose><c:when test="${borrowerWorkspace}">
    <input type="hidden" name="loanProductId" value="<c:out value='${loanProductId}'/>"/>
    <input type="hidden" name="loanType" value="<c:out value='${loanType}'/>"/>
    <input type="hidden" name="topUpLoanId" value="<c:out value='${topUpLoanId}'/>"/>
    <input type="hidden" name="financialSnapshotJson" value="<c:out value='${formValues.financialSnapshotJson}'/>"/>
</c:when><c:otherwise><form:hidden path="requestKey"/><form:hidden path="expectedVersion"/></c:otherwise></c:choose>
<c:forEach items="${['DAYS','MONTHS','YEARS','MONTHLY','WEEKLY','FLAT_RATE','REDUCING_BALANCE']}" var="key"><span hidden data-application-value="${key}"><spring:message code="applications.value.${key}"/></span></c:forEach>
<span hidden data-application-value="true"><spring:message code="common.yes"/></span><span hidden data-application-value="false"><spring:message code="common.no"/></span>
<div class="console-detail-tabs application-tabs" role="tablist" aria-label="<spring:message code='applications.title'/>"><c:forEach items="${['information','guarantors','collateral','documents']}" var="tab"><button type="button" role="tab" aria-selected="${tab=='information'}" aria-controls="application-${tab}" id="tab-${tab}" data-application-tab="${tab}"><spring:message code="applications.${tab}"/></button></c:forEach></div>
<section class="erp-resource-section application-panel" id="application-information" role="tabpanel" aria-labelledby="tab-information" data-application-panel="information"><h2><spring:message code="recording.loanDetails"/></h2><div class="application-grid">
<c:if test="${not borrowerWorkspace}"><div><label for="clientId"><spring:message code="applications.client"/> *</label><c:choose><c:when test="${not empty selectedClient}"><form:hidden path="clientId"/><p><c:out value="${selectedClient.label}"/></p></c:when><c:otherwise><form:select path="clientId" id="clientId" cssClass="aws-control" data-application-choice="clients"><form:option value=""><spring:message code="applications.select"/></form:option><c:forEach items="${clients}" var="choice"><form:option value="${choice.id}"><c:out value="${choice.label}"/></form:option></c:forEach></form:select></c:otherwise></c:choose></div></c:if>
<div><c:choose><c:when test="${borrowerWorkspace}"><span class="application-field-label"><spring:message code="applications.product"/></span><p><c:out value="${product.displayName}"/></p>
<c:if test="${not empty topUpSourceLoan}"><p><spring:message code="newloan.topUp.notice"/> <strong><c:out value="${topUpSourceLoan.applicationNumber}"/></strong></p></c:if>
</c:when><c:otherwise><label for="productId"><spring:message code="applications.product"/> *</label><form:select path="productId" id="productId" cssClass="aws-control" data-application-choice="products"><form:option value=""><spring:message code="applications.select"/></form:option><c:forEach items="${products}" var="choice"><form:option value="${choice.id}"><c:out value="${choice.label}"/></form:option></c:forEach></form:select></c:otherwise></c:choose></div>
<div><label for="applicationDate"><spring:message code="applications.date"/> *</label><form:input path="applicationDate" type="date" cssClass="aws-control"/></div>
<c:forEach items="${['amount','tenureValue','income','expenses','otherDebt']}" var="field"><div><label for="${field}"><spring:message code="${field=='amount' and not empty topUpSourceLoan ? 'newloan.topUp.additionalAmount' : 'applications.'.concat(field)}"/> <c:if test="${field=='amount' or field=='tenureValue'}">*</c:if></label><c:choose><c:when test="${borrowerWorkspace and (field=='amount' or field=='tenureValue')}"><form:input path="${field}" type="number" min="${field=='tenureValue'?'1':'0.01'}" step="${field=='tenureValue'?'1':'0.01'}" cssClass="aws-control" required="required"/>
<c:if test="${field=='amount'}">
    <c:choose>
        <c:when test="${minimumAmountLabel ne '-' and maximumAmountLabel ne '-'}">
            <p class="text-sm"><spring:message code="${empty topUpSourceLoan ? 'newloan.applicationRules.amountRange' : 'newloan.topUp.rangeHelp'}"/> <c:out value="${minimumAmountLabel}"/> <spring:message code="common.to"/> <c:out value="${maximumAmountLabel}"/></p>
        </c:when>
        <c:when test="${minimumAmountLabel ne '-'}"><p class="text-sm"><spring:message code="applications.minimumPrincipal" arguments="${minimumAmountLabel}"/></p></c:when>
        <c:when test="${maximumAmountLabel ne '-'}"><p class="text-sm"><spring:message code="applications.maximumPrincipal" arguments="${maximumAmountLabel}"/></p></c:when>
    </c:choose>
</c:if>
</c:when>
<c:otherwise><form:input path="${field}" type="number" min="0" step="${field=='tenureValue'?'1':'0.01'}" cssClass="aws-control"/></c:otherwise></c:choose></div></c:forEach>
<div><label for="tenureUnit"><spring:message code="applications.tenureUnit"/></label><form:select path="tenureUnit" cssClass="aws-control"><c:forEach items="${['DAYS','MONTHS','YEARS']}" var="unit"><form:option value="${unit}"><spring:message code="recording.tenure.${unit}"/></form:option></c:forEach></form:select></div>
<c:forEach items="${['purpose','business','useOfFunds']}" var="field"><div class="application-wide"><label for="${field}"><spring:message code="applications.${field}"/></label><form:textarea path="${field}" maxlength="${field=='purpose'?255:field=='business'?1000:2000}" rows="2" cssClass="aws-control"/>
<c:if test="${borrowerWorkspace and field=='purpose'}"><p class="text-sm" id="application-purpose-help"><spring:message code="newloan.purpose.help"/></p></c:if>
</div></c:forEach>
</div><p class="text-sm"><spring:message code="applications.declared"/></p><div class="application-grid" data-product-fields><c:forEach items="${borrowerWorkspace ? borrowerProductFields : productForm.fields}" var="field" varStatus="fieldIndex"><c:if test="${field.name!='purpose' and field.name!='business' and field.name!='monthlyIncome' and field.name!='monthlyExpenses' and field.name!='otherDebtRepayments' and field.name!='collateralDescription' and field.name!='collateralEstimatedValue'}"><c:set var="fieldName" value="${borrowerWorkspace ? field.name : 'fields['.concat(field.name).concat(']')}"/><div><label for="application-product-field-${fieldIndex.index}"><spring:message code="${field.labelKey}" text="${field.name}"/> ${field.required?'*':''}</label><c:choose><c:when test="${not empty field.enumOptions or field.type=='boolean'}"><select class="aws-control" id="application-product-field-${fieldIndex.index}" name="<c:out value='${fieldName}'/>" data-product-field><option value=""><spring:message code="applications.select"/></option><c:choose><c:when test="${field.type=='boolean'}"><option value="true" ${applicationForm.fields[field.name]=='true'?'selected':''}><spring:message code="common.yes"/></option><option value="false" ${applicationForm.fields[field.name]=='false'?'selected':''}><spring:message code="common.no"/></option></c:when><c:otherwise><c:forEach items="${field.enumOptions}" var="option"><option value="<c:out value='${option}'/>" ${applicationForm.fields[field.name]==option?'selected':''}><c:out value="${option}"/></option></c:forEach></c:otherwise></c:choose></select></c:when><c:when test="${field.type=='textarea'}"><textarea class="aws-control" id="application-product-field-${fieldIndex.index}" name="<c:out value='${fieldName}'/>" data-product-field maxlength="1000" rows="2"><c:out value="${applicationForm.fields[field.name]}"/></textarea></c:when><c:otherwise><input class="aws-control" id="application-product-field-${fieldIndex.index}" name="<c:out value='${fieldName}'/>" data-product-field value="<c:out value='${applicationForm.fields[field.name]}'/>" type="${field.type=='number' or field.type=='integer'?'number':field.type=='date'?'date':'text'}" step="${field.type=='integer'?'1':'0.01'}" maxlength="1000"/></c:otherwise></c:choose></div></c:if></c:forEach></div>
<p data-product-summary aria-live="polite" data-guarantors="<spring:message code='applications.guarantors'/>" data-prefix="<spring:message code='applications.productTerms'/>"></p><button type="button" class="app-btn btn-neutral" data-application-quote><spring:message code="applications.quote"/></button><p data-quote-status role="status" hidden></p>
<dl class="erp-detail-facts" data-quote-output aria-live="polite"></dl>
<%@ include file="repayment-estimate.jspf" %>
</section>
<section class="erp-resource-section application-panel" id="application-guarantors" role="tabpanel" aria-labelledby="tab-guarantors" data-application-panel="guarantors"><h2><spring:message code="applications.guarantors"/></h2>
<c:choose><c:when test="${borrowerWorkspace}"><%@ include file="borrower-guarantors.jspf" %></c:when><c:otherwise><div data-repeat-list="guarantors"><c:forEach items="${applicationForm.guarantors}" var="row" varStatus="i"><div class="application-row" data-repeat-row><div class="application-grid"><div><label><spring:message code="applications.client"/></label><form:select path="guarantors[${i.index}].clientId" cssClass="aws-control" data-application-choice="guarantors"><form:option value="${row.clientId}"><c:out value="${guarantorLabels[row.clientId]}"/></form:option></form:select></div><c:forEach items="${['relationship','business','location','phone']}" var="key"><div><label><spring:message code="applications.${key}"/></label><form:input path="guarantors[${i.index}].${key}" cssClass="aws-control" maxlength="255"/></div></c:forEach></div><button type="button" class="app-btn btn-neutral" data-remove-row><spring:message code="applications.remove"/></button></div></c:forEach></div><button type="button" class="app-btn btn-neutral" data-add-row="guarantors"><spring:message code="applications.add"/></button></c:otherwise></c:choose></section>
<section class="erp-resource-section application-panel" id="application-collateral" role="tabpanel" aria-labelledby="tab-collateral" data-application-panel="collateral"><h2><spring:message code="applications.collateral"/></h2>
<c:choose><c:when test="${borrowerWorkspace}"><%@ include file="borrower-collateral.jspf" %></c:when><c:otherwise><div data-repeat-list="collateral"><c:forEach items="${applicationForm.collateral}" var="row" varStatus="i"><div class="application-row" data-repeat-row><div class="application-grid"><c:forEach items="${['itemName','itemNumber','itemType','description','owner','phone','location','estimatedValue']}" var="key"><div><label><spring:message code="applications.${key}"/></label><form:input path="collateral[${i.index}].${key}" cssClass="aws-control" maxlength="500"/></div></c:forEach></div><button type="button" class="app-btn btn-neutral" data-remove-row><spring:message code="applications.remove"/></button></div></c:forEach></div><button type="button" class="app-btn btn-neutral" data-add-row="collateral"><spring:message code="applications.add"/></button></c:otherwise></c:choose></section>
<section class="erp-resource-section application-panel" id="application-documents" role="tabpanel" aria-labelledby="tab-documents" data-application-panel="documents"><h2><spring:message code="applications.documents"/></h2><%@ include file="saved-documents.jspf" %>
<c:choose><c:when test="${borrowerWorkspace}"><%@ include file="borrower-uploads.jspf" %></c:when><c:otherwise><p><spring:message code="applications.fileHelp"/></p><p data-required-documents aria-live="polite"></p><div data-repeat-list="documents"></div><button type="button" class="app-btn btn-neutral" data-add-row="documents"><spring:message code="applications.add"/></button></c:otherwise></c:choose></section>
<template data-row-template="guarantors"><div class="application-row" data-repeat-row><div class="application-grid"><div><label><spring:message code="applications.client"/></label><select class="aws-control" data-field="clientId" data-application-choice="guarantors"><option value=""><spring:message code="applications.select"/></option></select></div><c:forEach items="${['relationship','business','location','phone']}" var="key"><div><label><spring:message code="applications.${key}"/></label><input class="aws-control" data-field="${key}" maxlength="255"/></div></c:forEach></div><button type="button" class="app-btn btn-neutral" data-remove-row><spring:message code="applications.remove"/></button></div></template>
<template data-row-template="collateral"><div class="application-row" data-repeat-row><div class="application-grid"><c:forEach items="${['itemName','itemNumber','itemType','description','owner','phone','location','estimatedValue']}" var="key"><div><label><spring:message code="applications.${key}"/></label><input class="aws-control" data-field="${key}" maxlength="500" <c:if test="${key=='estimatedValue'}">type="number" min="0" step="0.01"</c:if>/></div></c:forEach></div><button type="button" class="app-btn btn-neutral" data-remove-row><spring:message code="applications.remove"/></button></div></template>
<template data-row-template="documents"><div class="application-row" data-repeat-row><div class="application-grid"><div><label><spring:message code="applications.documentName"/></label><input class="aws-control" data-document-field="documentNames" maxlength="120"/></div><div><label><spring:message code="applications.documentType"/></label><select class="aws-control" data-document-field="documentTypes"><c:forEach items="${documentTypes}" var="type"><option value="${type}"><spring:message code="applications.document.${type}"/></option></c:forEach></select></div><div><label><spring:message code="applications.requirement"/></label><select class="aws-control" data-document-field="requiredAttachmentIds"><option value=""><spring:message code="applications.optional"/></option></select></div><div><label><spring:message code="applications.file"/></label><input class="aws-control" name="files" type="file" accept="application/pdf,image/png,image/jpeg"/></div></div><button type="button" class="app-btn btn-neutral" data-remove-row><spring:message code="applications.remove"/></button></div></template>
<c:choose><c:when test="${borrowerWorkspace}"><%@ include file="borrower-declaration.jspf" %></c:when><c:otherwise><div class="erp-resource-section application-declaration"><form:checkbox path="declarationAccepted" id="declarationAccepted"/><label for="declarationAccepted"><spring:message code="applications.declaration"/></label></div></c:otherwise></c:choose>
<spring:hasBindErrors name="applicationForm"><div data-workspace-response-error><form:errors path="*" cssClass="field-error" element="div"/></div></spring:hasBindErrors>
<c:if test="${not empty error}"><p role="alert" class="field-error" tabindex="-1" data-application-server-error data-workspace-response-error><c:out value="${error}"/></p></c:if>
<p role="alert" class="field-error" data-application-error data-error-text="<spring:message code='applications.error.details'/>" hidden></p>
<div class="accounting-actions application-footer"><a class="app-btn btn-neutral" href="${root}"><spring:message code="common.cancel"/></a><c:choose><c:when test="${borrowerWorkspace}">
    <button type="submit" class="app-btn ${empty applicationForm.applicationId ? 'btn-launch' : 'btn-neutral'}" name="action" value="SAVE_DRAFT" data-form-action="SAVE_DRAFT"><spring:message code="applications.save"/></button>
    <c:if test="${not empty applicationForm.applicationId}">
        <button type="submit" class="app-btn btn-launch" name="action" value="SEND_TO_GUARANTORS" data-form-action="SEND_TO_GUARANTORS">
            <spring:message code="${requiredGuarantors gt 0 ? 'newloan.actions.sendToGuarantors' : 'applications.submit'}"/>
        </button>
    </c:if>
</c:when><c:otherwise>
    <button type="submit" class="app-btn btn-neutral" name="command" value="DRAFT" formnovalidate><spring:message code="applications.save"/></button>
    <button type="submit" class="app-btn btn-launch" name="command" value="SUBMIT"><spring:message code="applications.submit"/></button>
</c:otherwise></c:choose></div>
</form:form>
<c:if test="${not empty existingApplicationAttachments}">
    <c:forEach items="${existingApplicationAttachments}" var="file" varStatus="index">
        <c:choose><c:when test="${borrowerWorkspace}">
            <c:url value="/app/loan-applications/${applicationForm.applicationId}/attachments/${file.id}/delete" var="deleteUrl"/>
        </c:when><c:otherwise>
            <c:url value="${applicationRoot}/${applicationForm.applicationId}/documents/${file.id}/remove" var="deleteUrl"/>
        </c:otherwise></c:choose>
        <form id="remove-application-file-${index.index}" method="post" action="${deleteUrl}"
              data-confirm-message="<spring:message code='applications.confirmRemoveDocument'/>" data-confirm-proceed="<spring:message code='applications.remove'/>">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <c:if test="${not borrowerWorkspace}">
                <input type="hidden" name="requestKey" value="${applicationForm.requestKey}"/>
                <input type="hidden" name="expectedVersion" value="${applicationForm.expectedVersion}"/>
            </c:if>
        </form>
    </c:forEach>
</c:if>
<c:if test="${borrowerWorkspace or not empty existingApplicationAttachments}"><%@ include file="../../fragments/confirm-modal.jspf" %></c:if>
<c:if test="${borrowerWorkspace}">
    <script src="<c:url value='/js/application-attachment-titles.js'/>" defer></script>
</c:if>
<script src="<c:url value='/js/voucher-selects.js'/>" defer></script>
<script src="<c:url value='/js/client-applications.js'/>" defer></script>
<%@ include file="../accounting-form-support.jspf" %>
<%@ include file="application-foot.jspf" %>
