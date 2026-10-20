<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="support.jspf" %>
<div class="accounting-workspace fs-configure">
 <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="fs.workspace" /></h1></div>
 <c:if test="${not empty errorKey}"><div class="accounting-error-summary" role="alert" tabindex="-1"><spring:message code="${errorKey}" /></div></c:if>
 <form method="post" action="<c:url value='/reports/financial/statements' />" class="fs-setup" data-workspace-submit>
  <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="${requestKey}" />
  <section class="erp-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="fs.reportControls" /></h2></div>
   <div class="erp-panel-body fs-fields">
    <label><spring:message code="fs.financialYear" /><select class="aws-control" data-fs-year><option value=""><spring:message code="fs.customPeriod" /></option><c:forEach items="${years}" var="y"><option value="${y}" ${year==y?'selected':''}>${y}</option></c:forEach></select></label>
    <label><spring:message code="fs.statement" /><select class="aws-control" name="kind"><c:forEach items="${kinds}" var="k"><option value="${k}" ${kind==k?'selected':''}><spring:message code="fs.title.${k}" /></option></c:forEach></select></label>
    <label><spring:message code="fs.from" /><input class="aws-control" type="date" name="from" value="${from}" max="${today}" required /></label>
    <label><spring:message code="fs.through" /><input class="aws-control" type="date" name="through" value="${through}" max="${today}" data-today="${today}" required /></label>
    <label><spring:message code="fs.scope" /><select class="aws-control" name="institutionWide"><option value="false" ${not institutionWide?'selected':''}><spring:message code="fs.branchScope" /></option><sec:authorize access="@access.has(principal, 'FINANCIAL_REPORTS_INSTITUTION')"><option value="true" ${institutionWide?'selected':''}><spring:message code="fs.institutionScope" /></option></sec:authorize></select></label>
   </div>
  </section>
  <div class="accounting-actions accounting-form-actions"><button class="app-btn btn-launch" type="submit"><spring:message code="fs.generate" /></button></div>
 </form>
</div>
<%@ include file="../../fragments/footer.jspf" %>
