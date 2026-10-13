<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/statement-designer.css' />" />
<div class="statement-designer" data-statement-register>
 <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="statements.title" /></h1></div>
 <section class="statement-panel">
  <form method="get" class="statement-fields" data-aws-filter-toolbar>
   <label><spring:message code="statement.type" /><select name="kind"><c:forEach items="${['PROFIT_AND_LOSS','BALANCE_SHEET','CASH_FLOW','CHANGES_IN_EQUITY']}" var="type"><option value="${type}" ${kind eq type?'selected':''}><spring:message code="statement.type.${type}" /></option></c:forEach></select></label>
   <button class="app-btn btn-neutral"><spring:message code="common.view" /></button>
  </form>
  <h2><spring:message code="statement.type.${kind}" /></h2>
  <c:choose><c:when test="${not empty selected}">
   <p><c:out value="${pageContext.response.locale.language eq 'sw'?selected.definition.titleSw:selected.definition.titleEn}" /> · <spring:message code="statement.mappingVersion" /> ${selected.version} · <spring:message code="statement.state.${selected.state}" /></p>
   <form method="get" class="statement-fields"><input type="hidden" name="kind" value="${kind}" /><label><spring:message code="statements.mapping" /><select name="version"><option value="${selected.id}" selected><c:out value="${pageContext.response.locale.language eq 'sw'?selected.definition.titleSw:selected.definition.titleEn}" /> (${selected.version})</option><c:forEach items="${mappings}" var="mapping"><c:if test="${mapping.id ne selected.id}"><option value="${mapping.id}"><c:out value="${pageContext.response.locale.language eq 'sw'?mapping.definition.titleSw:mapping.definition.titleEn}" /> (${mapping.version})</option></c:if></c:forEach></select></label><button class="app-btn btn-neutral"><spring:message code="common.view" /></button></form>
   <form method="post" action="<c:url value='/reports/statements/versions/${selected.id}/preview' />" class="statement-fields">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    <label><spring:message code="statement.from" /><input type="date" name="from" value="${from}" max="${today}" required /></label>
    <label><spring:message code="statement.through" /><input type="date" name="through" value="${today}" max="${today}" required /></label>
    <c:if test="${selected.definition.comparison}"><label><spring:message code="statement.comparisonFrom" /><input type="date" name="comparisonFrom" max="${today}" required /></label><label><spring:message code="statement.comparisonThrough" /><input type="date" name="comparisonThrough" max="${today}" required /></label></c:if>
    <button class="app-btn btn-primary"><spring:message code="statement.preview" /></button>
   </form>
   <sec:authorize access="hasAuthority('STATEMENT_FINALIZE')"><c:if test="${selected.state eq 'APPROVED'}"><a class="app-btn btn-neutral" href="<c:url value='/reports/statements/versions/${selected.id}/closing' />"><spring:message code="statement.selectClose" /></a></c:if></sec:authorize>
  </c:when><c:otherwise><p role="status"><spring:message code="statements.noMapping" /></p></c:otherwise></c:choose>
  <c:if test="${moreMappings}"><p><spring:message code="statements.moreMappings" /></p></c:if>
  <sec:authorize access="hasAuthority('STATEMENT_DESIGN')"><a class="app-btn btn-neutral" href="<c:url value='/reports/statements/mappings/new?kind=${kind}' />"><spring:message code="statements.configure" /></a><c:if test="${not empty selected}"><a class="app-btn btn-neutral" href="<c:url value='/reports/statements/mappings?version=${selected.id}' />"><spring:message code="statements.editMapping" /></a></c:if></sec:authorize>
 </section>
 <section data-resource-table="reports-financial-statements-1" data-table-selection="single" class="erp-table-wrap" data-aws-table-region>
  <div class="app-table-titlebar statement-toolbar"><h2 class="erp-panel-title"><spring:message code="statements.finalized" /></h2><div class="app-table-toolbar">
   <button type="button" class="app-btn btn-neutral" data-statement-view disabled><spring:message code="common.view" /></button>
   <sec:authorize access="hasAuthority('STATEMENT_EXPORT')"><button type="submit" form="statement-export" class="app-btn btn-neutral" data-statement-export disabled><spring:message code="voucher.pdf" /></button><button type="submit" form="statement-export" name="inline" value="true" class="app-btn btn-neutral" data-statement-export disabled><spring:message code="voucher.print" /></button></sec:authorize>
  </div></div>
  <form id="statement-export" method="post" target="_blank"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /></form>
  <p class="sr-only" role="status" aria-live="polite" data-statement-selection data-empty="<spring:message code='voucher.selectHelp' />" data-selected="<spring:message code='voucher.selected' />"><spring:message code="voucher.selectHelp" /></p>
  <div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="voucher.select" /></th><th><spring:message code="statement.from" /></th><th><spring:message code="statement.through" /></th><th><spring:message code="statement.mappingVersion" /></th><th><spring:message code="statement.export.generated" /></th></tr></thead><tbody>
   <c:forEach items="${results}" var="record"><tr data-statement-id="${record.id}"><td><input type="checkbox" aria-label="<spring:message code='voucher.select' /> ${record.from} / ${record.through}" /></td><td><c:out value="${record.from}" /></td><td><c:out value="${record.through}" /></td><td>${record.mappingVersion}</td><td><c:out value="${record.generatedAt}" /></td></tr></c:forEach>
   <c:if test="${empty results}"><tr><td colspan="5"><spring:message code="statements.empty" /></td></tr></c:if>
  </tbody></table></div>
  <div data-table-pagination data-page="${page}" data-table-for="reports-financial-statements-1" class="statement-actions"><c:if test="${page gt 0}"><a data-page-direction="previous" class="app-btn btn-neutral" href="<c:url value='/reports/statements?kind=${kind}&page=${page-1}' />"><spring:message code="common.previous" /></a></c:if><c:if test="${hasNext}"><a data-page-direction="next" class="app-btn btn-neutral" href="<c:url value='/reports/statements?kind=${kind}&page=${page+1}' />"><spring:message code="common.next" /></a></c:if></div>
 </section>
</div><script src="<c:url value='/js/financial-statements.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
