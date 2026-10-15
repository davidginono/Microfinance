<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="report" tagdir="/WEB-INF/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting.css' />" />
<link rel="stylesheet" href="<c:url value='/css/statement-reference.css' />" />
<div class="accounting-workspace">
 <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="statement.reference.accountTitle" /></h1><div class="accounting-actions">
 <a class="app-btn btn-neutral" href="<c:url value='${detailBase}/trial-balance${detailQuery}' />"><spring:message code="financial.report.trial" /></a>
 <sec:authorize access="@access.has(principal, 'STATEMENT_EXPORT') and @access.has(principal, 'FINANCIAL_REPORTS_EXPORT')">
 <a class="app-btn btn-neutral" href="<c:url value='${detailBase}/accounts/${activity.accountId}/pdf${detailQuery}' />"><spring:message code="voucher.pdf" /></a>
 <a class="app-btn btn-neutral" target="_blank" href="<c:url value='${detailBase}/accounts/${activity.accountId}/pdf${detailQuery}&inline=true' />"><spring:message code="voucher.print" /></a>
 </sec:authorize></div></div>
 <article class="reference-report reference-account"><h2 class="reference-title"><spring:message code="statement.reference.accountTitle" /></h2>
 <dl class="reference-facts"><div><dt><spring:message code="accounting.code" /></dt><dd><c:out value="${activity.code}" /></dd></div><div><dt><spring:message code="financial.report.branch" /></dt><dd><c:out value="${activity.scope.branch}" /></dd></div><div><dt><spring:message code="accounting.name" /></dt><dd><c:out value="${activity.name}" /></dd></div><div><dt><spring:message code="financial.report.opening" /></dt><dd><report:reportMoney value="${activity.opening}" /></dd></div><div><dt><spring:message code="report.period" /></dt><dd><c:out value="${result.from}" /> — <c:out value="${result.through}" /></dd></div><div><dt><spring:message code="financial.report.closing" /></dt><dd><report:reportMoney value="${activity.closing}" /></dd></div></dl>
 <div class="reference-scroll" tabindex="0" role="region" aria-label="<spring:message code='statement.reference.accountTitle' />"><table class="reference-table"><thead><tr><th><spring:message code="financial.report.effective" /></th><th><spring:message code="financial.report.reference" /></th><th><spring:message code="financial.report.source" /></th><th class="reference-amount"><spring:message code="financial.report.debit" /></th><th class="reference-amount"><spring:message code="financial.report.credit" /></th><th class="reference-amount"><spring:message code="statement.reference.running" /></th></tr></thead><tbody>
 <c:forEach items="${activity.movements}" var="a"><tr><td><c:out value="${a.effectiveDate}" /></td><td><c:out value="${a.sourceReference}" /></td><td><spring:message code="accounting.source.${a.sourceType}" /></td><td class="reference-amount"><report:reportMoney value="${a.debit}" /></td><td class="reference-amount"><report:reportMoney value="${a.credit}" /></td><td class="reference-amount"><report:reportMoney value="${activity.opening eq null or a.cumulativeMovement eq null?null:activity.opening+a.cumulativeMovement}" /></td></tr></c:forEach>
 <c:if test="${empty activity.movements}"><tr><td colspan="6"><spring:message code="statement.reference.empty" /></td></tr></c:if>
 </tbody></table></div><p class="reference-notes">TZS · <spring:message code="financial.report.balanceSign" /> <spring:message code="statement.cutoff" />: <c:out value="${result.recordedCutoff}" /></p></article>
 <nav class="reference-pagination" aria-label="<spring:message code='voucher.page' />"><spring:message code="voucher.page" /> ${activity.parameters.page+1}
 <c:if test="${activity.parameters.page>0}"><a class="app-btn btn-neutral" href="<c:url value='${detailBase}/accounts/${activity.accountId}${detailQuery}&page=${activity.parameters.page-1}' />"><spring:message code="common.previous" /></a></c:if>
 <c:if test="${activity.hasNext}"><a class="app-btn btn-neutral" href="<c:url value='${detailBase}/accounts/${activity.accountId}${detailQuery}&page=${activity.parameters.page+1}' />"><spring:message code="common.next" /></a></c:if></nav>
</div><%@ include file="../fragments/footer.jspf" %>
