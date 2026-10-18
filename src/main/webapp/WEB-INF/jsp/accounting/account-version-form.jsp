<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="account-version-shell.jspf" %>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="coav.newVersion" /></h1></div>
<div class="accounting-workspace accounting-create-workspace">
 <p><spring:message code="coav.saveHelp" /></p>
 <section class="accounting-form accounting-create-card">
  <h2><c:out value="${account.code}" /> · <c:out value="${account.name}" /></h2>
  <p><spring:message code="coav.operation.${operation}" /></p>
  <c:url value="${savePath}" var="saveUrl" />
  <form:form method="post" action="${saveUrl}" modelAttribute="versionForm" cssClass="accounting-create-form" data-account-version-form="true">
   <form:hidden path="revision" />
   <c:if test="${not empty requestKey}"><input type="hidden" name="requestKey" value="${requestKey}" /><c:if test="${not empty baseId}"><input type="hidden" name="baseId" value="${baseId}" /></c:if><input type="hidden" name="operation" value="<c:out value='${operation}' />" /></c:if>
   <div class="accounting-error-summary" role="alert"><form:errors path="*" cssClass="coa-error" element="p" /></div>
   <c:choose><c:when test="${operation=='UPDATE' or operation=='CREATE'}">
    <label><spring:message code="coav.field.name" /><form:input path="name" cssClass="aws-control" maxlength="160" required="required" /></label>
    <label><spring:message code="coav.field.description" /><form:textarea path="description" cssClass="aws-control" maxlength="500" rows="3" /></label>
   </c:when><c:otherwise><form:hidden path="name" /><form:hidden path="description" /></c:otherwise></c:choose>
   <label><spring:message code="coav.reason" /><form:textarea path="reason" cssClass="aws-control" maxlength="500" rows="2" required="required" /></label>
   <div class="accounting-actions accounting-form-actions">
    <a class="app-btn btn-neutral" href="<c:url value='/finance/accounts/versions' />"><spring:message code="common.cancel" /></a>
    <button class="app-btn btn-neutral" name="command" value="DRAFT" formnovalidate><spring:message code="coav.saveDraft" /></button>
    <button class="app-btn btn-launch" name="command" value="SAVE"><spring:message code="coav.saveVersion" /></button>
   </div>
  </form:form>
 </section>
</div>
<%@ include file="accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
