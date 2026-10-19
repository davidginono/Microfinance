<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="support.jspf" %>
<div class="accounting-workspace fs-configure"><nav class="erp-page-path"><a href="<c:url value='/reports/financial/statements/mappings' />"><spring:message code="fs.mappings" /></a><span>/</span><span aria-current="page"><c:out value="${account.name}" /></span></nav>
 <h1 class="erp-page-title" data-aws-page-header><spring:message code="fs.editMapping" /></h1><p><c:out value="${account.code}" /> · <c:out value="${account.name}" /></p>
 <c:if test="${not empty errorKey}"><div class="accounting-error-summary" role="alert"><spring:message code="${errorKey}" /></div></c:if>
 <form method="post" action="<c:url value='/reports/financial/statements/mappings/${account.id}' />" class="fs-setup" data-accounting-dirty-form>
  <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="revision" value="${revision}" />
  <section class="erp-panel"><div class="erp-panel-body fs-fields">
   <label><spring:message code="fs.lineLabel" /><input class="aws-control" name="line" value="<c:out value='${empty mapping?account.name:mapping.line}' />" maxlength="160" required /></label>
   <label><spring:message code="fs.section" /><select class="aws-control" name="section" required><option value=""><spring:message code="fs.selectSection" /></option><c:forTokens items="CURRENT_ASSETS,NON_CURRENT_ASSETS,CURRENT_LIABILITIES,NON_CURRENT_LIABILITIES,EQUITY,INCOME,EXPENSE" delims="," var="s"><c:if test="${(account.type=='ASSET' and (s=='CURRENT_ASSETS' or s=='NON_CURRENT_ASSETS')) or (account.type=='LIABILITY' and (s=='CURRENT_LIABILITIES' or s=='NON_CURRENT_LIABILITIES')) or account.type==s}"><option value="${s}" ${mapping.section==s?'selected':''}><spring:message code="fs.section.${s}" /></option></c:if></c:forTokens></select></label>
   <label><spring:message code="fs.cashClassification" /><select class="aws-control" name="cashSection"><c:forTokens items="UNCLASSIFIED,OPERATING,INVESTING,FINANCING" delims="," var="s"><option value="${s}" ${mapping.cashSection==s?'selected':''}><spring:message code="fs.section.${s}" /></option></c:forTokens></select></label>
   <label><spring:message code="fs.equityComponent" /><input class="aws-control" name="equityComponent" value="<c:out value='${mapping.equityComponent}' />" maxlength="100" /></label>
   <label><spring:message code="fs.noteNumber" /><input class="aws-control" type="number" name="note" value="${empty mapping?0:mapping.note}" min="0" max="999" required /></label>
   <label class="fs-full"><spring:message code="fs.noteText" /><textarea class="aws-control" name="noteText" maxlength="2000" rows="4"><c:out value="${mapping.noteText}" /></textarea></label>
  </div></section>
  <p class="fs-help"><spring:message code="fs.mappingHelp" /></p>
  <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/reports/financial/statements/mappings' />"><spring:message code="fs.cancel" /></a><button class="app-btn btn-launch"><spring:message code="fs.saveMapping" /></button></div>
 </form>
</div>
<%@ include file="../../fragments/footer.jspf" %>
