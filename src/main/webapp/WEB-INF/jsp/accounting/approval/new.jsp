<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../../fragments/header.jspf" %>
<%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<link rel="stylesheet" href="<c:url value='/css/voucher-approval.css' />" />
<nav class="erp-page-path"><a href="<c:url value='/finance/approval' />"><spring:message code="approval.workspace" /></a></nav><div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="approval.create" /></h1></div><div class="accounting-workspace approval-workspace"><c:url value="/finance/approval" var="saveUrl" /><form:form modelAttribute="createForm" action="${saveUrl}" method="post" cssClass="accounting-form approval-form accounting-create-form" data-voucher-form="true" data-dirty-form="true"><form:hidden path="requestKey" /><div role="alert"><form:errors path="*" element="p" cssClass="coa-error" /></div><div class="approval-fields"><label><spring:message code="approval.title" /><form:input path="title" maxlength="160" required="required" /></label><label><spring:message code="approval.voucherType" /><form:select path="type"><c:forEach items="${['RECEIPT','PAYMENT','JOURNAL']}" var="t"><form:option value="${t}"><spring:message code="voucher.register.${t}" /></form:option></c:forEach></form:select></label><label><spring:message code="approval.scope" /><form:select path="scope"><form:option value="SINGLE"><spring:message code="approval.scope.SINGLE" /></form:option><form:option value="BATCH"><spring:message code="approval.scope.BATCH" /></form:option></form:select></label></div><div class="accounting-actions approval-footer"><a class="app-btn btn-neutral" href="<c:url value='/finance/approval' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-neutral"><spring:message code="approval.create" /></button></div></form:form></div><%@ include file="../accounting-form-support.jspf" %>
<script src="<c:url value='/js/voucher-approval.js' />" defer></script>
<%@ include file="../../fragments/footer.jspf" %>