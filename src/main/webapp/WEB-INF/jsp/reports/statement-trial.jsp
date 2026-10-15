<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="report" tagdir="/WEB-INF/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting.css' />" />
<link rel="stylesheet" href="<c:url value='/css/statement-reference.css' />" />
<div class="accounting-workspace">
 <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="financial.report.trial" /></h1>
 <sec:authorize access="@access.has(principal, 'STATEMENT_EXPORT') and @access.has(principal, 'FINANCIAL_REPORTS_EXPORT')"><div class="accounting-actions">
 <a class="app-btn btn-neutral" href="<c:url value='${detailBase}/trial-balance/pdf${detailQuery}' />"><spring:message code="voucher.pdf" /></a>
 <a class="app-btn btn-neutral" target="_blank" href="<c:url value='${detailBase}/trial-balance/pdf${detailQuery}&inline=true' />"><spring:message code="voucher.print" /></a>
 </div></sec:authorize></div>
 <article class="reference-report reference-trial">
 <h2 class="reference-title"><spring:message code="financial.report.trial" /></h2>
 <p class="reference-period"><spring:message code="financial.print.asAt" />: <c:out value="${result.through}" /> <span>TZS</span></p>
 <p><c:out value="${pageContext.response.locale.language eq 'sw'?detail.row.labelSw:detail.row.labelEn}" /> · <c:out value="${result.institution}" /> / <c:out value="${trial.scope.institutionWide?'':result.branch}" /></p>
 <div class="reference-scroll" tabindex="0" role="region" aria-label="<spring:message code='financial.report.trial' />"><table class="reference-table">
 <thead><tr><th><spring:message code="accounting.code" /></th><th><spring:message code="accounting.name" /></th><c:if test="${not trial.scope.institutionWide}"><th class="reference-details"><spring:message code="statement.reference.details" /></th></c:if><th class="reference-amount"><spring:message code="financial.report.opening" /></th><th class="reference-amount"><spring:message code="financial.report.debit" /></th><th class="reference-amount"><spring:message code="financial.report.credit" /></th><th class="reference-amount"><spring:message code="financial.print.netChange" /></th><th class="reference-amount"><spring:message code="financial.report.closing" /></th></tr></thead>
 <tbody><c:forEach items="${trial.accounts}" var="a"><tr>
 <td><c:out value="${a.code}" /></td><td><c:out value="${a.name}" /></td>
 <c:if test="${not trial.scope.institutionWide}"><td class="reference-details"><a class="reference-detail-button" href="<c:url value='${detailBase}/accounts/${a.id}${detailQuery}' />"><spring:message code="statement.reference.details" /><span class="sr-only"> — <c:out value="${a.code}" /></span></a></td></c:if>
 <td class="reference-amount"><report:reportMoney value="${a.opening}" /></td><td class="reference-amount"><report:reportMoney value="${a.debit}" /></td><td class="reference-amount"><report:reportMoney value="${a.credit}" /></td><td class="reference-amount"><report:reportMoney value="${a.debit-a.credit}" /></td><td class="reference-amount"><report:reportMoney value="${a.closing}" /></td>
 </tr></c:forEach>
 <c:if test="${empty trial.accounts}"><tr><td colspan="${trial.scope.institutionWide?7:8}"><spring:message code="statement.reference.empty" /></td></tr></c:if>
 </tbody><tfoot><tr class="reference-total"><td colspan="${trial.scope.institutionWide?2:3}"><spring:message code="financial.print.total" /></td><td class="reference-amount"><report:reportMoney value="${trial.totals.openingDebit eq null?null:trial.totals.openingDebit-trial.totals.openingCredit}" /></td><td class="reference-amount"><report:reportMoney value="${trial.totals.movementDebit}" /></td><td class="reference-amount"><report:reportMoney value="${trial.totals.movementCredit}" /></td><td class="reference-amount"><report:reportMoney value="${trial.totals.movementDebit-trial.totals.movementCredit}" /></td><td class="reference-amount"><report:reportMoney value="${trial.totals.closingDebit eq null?null:trial.totals.closingDebit-trial.totals.closingCredit}" /></td></tr></tfoot>
 </table></div>
 <p class="reference-notes"><spring:message code="statement.reference.trialBasis" /> <c:if test="${not empty result.id}"><spring:message code="statement.reference.accountNames" /></c:if> <spring:message code="financial.report.balanceSign" /></p>
 <p class="reference-notes"><spring:message code="statement.cutoff" />: <c:out value="${result.recordedCutoff}" /> · <spring:message code="statement.status.${result.status}" /></p>
 </article>
 <nav class="reference-pagination" aria-label="<spring:message code='voucher.page' />"><spring:message code="voucher.page" /> ${trial.parameters.page+1}
 <c:if test="${trial.parameters.page>0}"><a class="app-btn btn-neutral" href="<c:url value='${detailBase}/trial-balance${detailQuery}&page=${trial.parameters.page-1}' />"><spring:message code="common.previous" /></a></c:if>
 <c:if test="${trial.hasNext}"><a class="app-btn btn-neutral" href="<c:url value='${detailBase}/trial-balance${detailQuery}&page=${trial.parameters.page+1}' />"><spring:message code="common.next" /></a></c:if></nav>
</div><%@ include file="../fragments/footer.jspf" %>
