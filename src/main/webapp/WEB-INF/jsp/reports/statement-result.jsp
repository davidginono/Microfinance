<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/statement-designer.css' />" />
<link rel="stylesheet" href="<c:url value='/css/statement-reference.css' />" />
<div class="statement-designer erp-detail-workspace">
 <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><c:choose><c:when test="${pageContext.response.locale.language eq 'sw'}"><c:out value="${result.definition.titleSw}" /></c:when><c:otherwise><c:out value="${result.definition.titleEn}" /></c:otherwise></c:choose></h1></div>
 <section class="erp-detail-card"><p><span class="statement-status"><spring:message code="statement.status.${result.status}" /></span> · TZS · <c:out value="${result.from}" /> — <c:out value="${result.through}" /></p>
 <p><spring:message code="statement.dimension.${result.dimension}" /> · <spring:message code="${result.dimension eq 'INSTITUTION' ? 'statement.latestBranchCutoff' : 'statement.cutoff'}" />: <c:out value="${result.recordedCutoff}" /> · <spring:message code="statement.mappingVersion" />: <c:out value="${result.mappingVersion}" /></p>
 <c:if test="${result.definition.comparison}"><p><spring:message code="statement.comparison" />: <c:out value="${result.comparisonFrom}" /> — <c:out value="${result.comparisonThrough}" /> · <spring:message code="statement.cutoff" />: <c:out value="${result.comparisonCutoff}" /></p></c:if><c:if test="${not empty statementSuccess}"><p role="status"><spring:message code="${statementSuccess}" /></p></c:if>
 <sec:authorize access="@access.has(principal, 'STATEMENT_EXPORT')">
 <form method="post" target="_blank" action="<c:url value='${empty result.id ? "/reports/statements/versions/" : "/reports/statements/results/"}${empty result.id ? result.versionId : result.id}/pdf' />" class="statement-actions">
  <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
  <c:if test="${empty result.id}"><input type="hidden" name="from" value="${result.from}" /><input type="hidden" name="through" value="${result.through}" /><input type="hidden" name="cutoff" value="${result.recordedCutoff}" /><c:if test="${result.definition.comparison}"><input type="hidden" name="comparisonFrom" value="${result.comparisonFrom}" /><input type="hidden" name="comparisonThrough" value="${result.comparisonThrough}" /></c:if></c:if>
  <button class="app-btn btn-neutral"><spring:message code="voucher.pdf" /></button><button name="inline" value="true" class="app-btn btn-neutral"><spring:message code="voucher.print" /></button>
 </form>
 </sec:authorize>
 </section>
 <article class="reference-report reference-${result.definition.kind}">
 <h2 class="reference-title"><c:out value="${pageContext.response.locale.language eq 'sw'?result.definition.titleSw:result.definition.titleEn}" /></h2>
 <p class="reference-generated"><spring:message code="statement.export.generated" />: <c:out value="${appTime:format(result.generatedAt)}" /></p>
 <p class="reference-period"><spring:message code="${result.definition.kind eq 'BALANCE_SHEET'?'financial.print.asAt':'report.period'}" />: <c:if test="${result.definition.kind ne 'BALANCE_SHEET'}"><c:out value="${result.from}" /> — </c:if><c:out value="${result.through}" /> <span>TZS</span></p>
 <c:if test="${not empty result.getEquityRows()}">
 <div class="reference-scroll"><div><table class="reference-table equity-matrix"><thead><tr><th><spring:message code="statement.line" /></th><c:forEach items="${result.getEquityRows()}" var="component"><th class="statement-number"><c:out value="${pageContext.response.locale.language eq 'sw'?component.labelSw:component.labelEn}" /></th></c:forEach><th class="statement-number"><spring:message code="statements.equity.total" /></th></tr></thead><tbody>
 <c:forEach items="${['opening','movement','closing']}" var="measure"><tr class="${measure eq 'closing'?'reference-total':''}"><th scope="row"><spring:message code="statements.equity.${measure}" /></th><c:forEach items="${result.getEquityRows()}" var="component"><c:set var="amount" value="${measure eq 'opening'?component.opening:measure eq 'closing'?component.closing:component.current}" /><td class="statement-number"><c:choose><c:when test="${amount ne null}"><fmt:formatNumber value="${amount}" pattern="#,##0.00;(#,##0.00)" /></c:when><c:otherwise><spring:message code="statement.value.UNAVAILABLE" /></c:otherwise></c:choose></td></c:forEach><c:set var="total" value="${result.reconciliations[measure eq 'opening'?'OPENING_EQUITY':measure eq 'closing'?'EQUITY':'EQUITY_MOVEMENT']}" /><td class="statement-number"><c:choose><c:when test="${total ne null}"><fmt:formatNumber value="${total}" pattern="#,##0.00;(#,##0.00)" /></c:when><c:otherwise><spring:message code="statement.value.UNAVAILABLE" /></c:otherwise></c:choose></td></tr></c:forEach>
 </tbody></table></div></div>
 </c:if>
 <sec:authorize access="@access.has(principal, 'FINANCIAL_REPORTS_VIEW')" var="canDrill" />
 <c:set var="showLineTable" value="${empty result.getEquityRows() or result.definition.comparison}" />
 <c:forEach items="${result.getStatementRows()}" var="line"><c:if test="${not line.mandatory and (line.kind eq 'SUBTOTAL' or line.kind eq 'RATIO')}"><c:set var="showLineTable" value="${true}" /></c:if></c:forEach>
 <div class="reference-scroll" role="region" aria-label="<spring:message code='statement.line' />" tabindex="0">
 <table class="reference-table"><c:if test="${showLineTable}"><thead><tr><th class="reference-description"><spring:message code="statement.line" /></th><th><spring:message code="financial.print.notes" /></th><c:if test="${canDrill}"><th class="reference-details"><spring:message code="statement.reference.details" /></th></c:if><th class="reference-amount"><spring:message code="statement.export.current" /></th><c:if test="${result.definition.comparison}"><th class="reference-amount"><spring:message code="statement.comparison" /></th></c:if></tr></thead></c:if><tbody>
 <c:set var="noteNumber" value="0" />
 <c:forEach items="${result.getStatementRows()}" var="row">
  <c:set var="rowNote" value="${pageContext.response.locale.language eq 'sw'?row.noteSw:row.noteEn}" />
  <c:if test="${not empty rowNote or row.kind eq 'NOTE'}"><c:set var="noteNumber" value="${noteNumber+1}" /></c:if>
  <c:if test="${showLineTable and row.kind ne 'NOTE' and (empty result.getEquityRows() or result.definition.comparison or row.kind ne 'ACCOUNT_GROUP')}">
  <tr class="${row.kind eq 'HEADING'?'reference-heading':row.mandatory or row.kind eq 'SUBTOTAL'?'reference-total':''}">
   <td><c:choose><c:when test="${row.mandatory and row.id.startsWith('REQUIRED_')}"><spring:message code="statement.total.${row.id.substring(9)}" /></c:when><c:otherwise><c:out value="${pageContext.response.locale.language eq 'sw'?row.labelSw:row.labelEn}" /></c:otherwise></c:choose></td>
   <td><c:if test="${not empty rowNote and row.kind ne 'HEADING'}">${noteNumber}</c:if></td>
   <c:if test="${canDrill}"><td class="reference-details"><c:if test="${row.kind eq 'ACCOUNT_GROUP'}">
    <c:url var="lineUrl" value="/reports/statements/${empty result.id?'versions':'results'}/${empty result.id?result.versionId:result.id}/lines/${row.id}/trial-balance"><c:param name="from" value="${result.from}" /><c:param name="through" value="${result.through}" /><c:param name="cutoff" value="${result.recordedCutoff.toInstant()}" /><c:if test="${result.definition.comparison}"><c:param name="comparisonFrom" value="${result.comparisonFrom}" /><c:param name="comparisonThrough" value="${result.comparisonThrough}" /></c:if></c:url>
    <a class="reference-detail-button" href="<c:out value='${lineUrl}' />"><spring:message code="statement.reference.details" /><span class="sr-only"> — <c:out value="${pageContext.response.locale.language eq 'sw'?row.labelSw:row.labelEn}" /></span></a>
   </c:if></td></c:if>
   <td class="reference-amount"><c:choose><c:when test="${row.unit eq 'NONE'}" /><c:when test="${row.status eq 'KNOWN'}"><fmt:formatNumber value="${row.current}" pattern="${row.unit eq 'RATIO'?'0.000000;(0.000000)':'#,##0.00;(#,##0.00)'}" /></c:when><c:otherwise><spring:message code="statement.value.${row.status}" /></c:otherwise></c:choose></td>
   <c:if test="${result.definition.comparison}"><td class="reference-amount"><c:choose><c:when test="${row.unit eq 'NONE'}" /><c:when test="${row.comparisonStatus eq 'KNOWN'}"><fmt:formatNumber value="${row.comparison}" pattern="${row.unit eq 'RATIO'?'0.000000;(0.000000)':'#,##0.00;(#,##0.00)'}" /></c:when><c:otherwise><spring:message code="statement.value.${row.comparisonStatus}" /></c:otherwise></c:choose></td></c:if>
  </tr>
  </c:if>
 </c:forEach>
 </tbody></table></div>
 <c:if test="${noteNumber>0}"><section class="reference-notes"><h3><spring:message code="financial.print.notes" /></h3><ol><c:forEach items="${result.getStatementRows()}" var="row"><c:set var="rowNote" value="${pageContext.response.locale.language eq 'sw'?row.noteSw:row.noteEn}" /><c:if test="${not empty rowNote or row.kind eq 'NOTE'}"><li><c:if test="${row.kind eq 'NOTE'}"><c:out value="${pageContext.response.locale.language eq 'sw'?row.labelSw:row.labelEn}" />: </c:if><c:out value="${rowNote}" /></li></c:if></c:forEach></ol></section></c:if>
 </article>
 <section class="erp-detail-card"><h2><spring:message code="statement.disclosures" /></h2><c:forEach items="${result.getSupportingRows()}" var="row"><p><spring:message code="statement.total.${row.id.substring(9)}" />: <c:choose><c:when test="${row.current ne null}"><fmt:formatNumber value="${row.current}" pattern="#,##0.00;(#,##0.00)" /></c:when><c:otherwise><spring:message code="statement.value.UNAVAILABLE" /></c:otherwise></c:choose><c:if test="${result.definition.comparison}"> / <c:choose><c:when test="${row.comparison ne null}"><fmt:formatNumber value="${row.comparison}" pattern="#,##0.00;(#,##0.00)" /></c:when><c:otherwise><spring:message code="statement.value.UNAVAILABLE" /></c:otherwise></c:choose></c:if></p></c:forEach><p><spring:message code="statement.disclosure.fixed" /></p>
 <c:forEach items="${result.disclosures}" var="disclosure"><p class="statement-disclosure"><c:choose><c:when test="${disclosure eq 'REVIEWED_DIFFERENCES_RETAINED' or disclosure eq 'REVIEWED_COVERAGE'}"><spring:message code="statement.coverage.reviewed" /></c:when><c:when test="${disclosure eq 'INCOMPLETE_NOT_AUTHORITATIVE'}"><spring:message code="statement.coverage.incomplete" /></c:when><c:when test="${disclosure eq 'MEMO_DISCLOSURES_EXCLUDED_FROM_CALCULATIONS'}"><spring:message code="statement.disclosure.memo" /></c:when><c:when test="${disclosure eq 'RESTATEMENT'}"><spring:message code="statement.disclosure.restatement" /></c:when><c:when test="${disclosure.startsWith('ACCOUNTING_BASIS: ')}"><spring:message code="statement.disclosure.basis" />: <c:out value="${disclosure.substring(18)}" /></c:when><c:when test="${disclosure.startsWith('COMPARISON_POLICY: ')}"><spring:message code="statement.comparisonPolicy" />: <c:out value="${disclosure.substring(19)}" /></c:when><c:when test="${disclosure.startsWith('REVIEWED_DIFFERENCE: ')}"><spring:message code="statement.disclosure.difference" />: <c:out value="${disclosure.substring(21)}" /></c:when></c:choose></p></c:forEach>
 </section><c:if test="${not empty result.id}"><p><spring:message code="statement.documentReference" />: <c:out value="${result.id}" /></p> </c:if>
</div><%@ include file="../fragments/footer.jspf" %>
