<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="accounting-workspace">
<c:if test="${not empty accountingError}"><p role="alert"><spring:message code="${accountingError}" /></p></c:if>
<c:if test="${not empty accountingSuccess}"><p role="status"><spring:message code="${accountingSuccess}" /></p></c:if>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="accounting.journals" /></h1></div><p><spring:message code="accounting.gated" /></p>
<p role="status"><spring:message code="accounting.coverage.${coverage.status}" /> <spring:message code="accounting.unbridged" />: <c:out value="${coverage.unbridgedOperationalVouchers}" />; <spring:message code="accounting.legacy" />: <c:out value="${coverage.uncoveredLegacyLoans}" /></p>
<section class="erp-table-wrap"><div class="erp-table-scroll" data-view-position-key="gl-journals"><table class="erp-table"><thead><tr><th><spring:message code="accounting.reference" /></th><th><spring:message code="accounting.date" /></th><th><spring:message code="accounting.source" /></th><th><spring:message code="accounting.state" /></th><th><spring:message code="common.actions" /></th></tr></thead><tbody>
<c:forEach items="${journals.rows}" var="j"><tr><td><c:out value="${j.sourceReference}" /></td><td><c:out value="${j.effectiveDate}" /></td><td><spring:message code="accounting.source.${j.sourceType}" /></td><td><spring:message code="accounting.state.${j.state}" /><c:if test="${j.reversed}"> / <spring:message code="accounting.reversed" /></c:if></td><td><a class="app-btn btn-neutral" href="<c:url value='/finance/journals/${j.id}' />"><spring:message code="common.view" /></a></td></tr></c:forEach><c:if test="${empty journals.rows}"><tr><td colspan="5"><spring:message code="accounting.empty" /></td></tr></c:if></tbody></table></div>
<div class="accounting-actions"><c:if test="${journals.page > 0}"><a class="app-btn btn-neutral" href="<c:url value='/finance/journals?page=${journals.page-1}' />"><spring:message code="common.previous" /></a></c:if><c:if test="${journals.hasNext}"><a class="app-btn btn-neutral" href="<c:url value='/finance/journals?page=${journals.page+1}' />"><spring:message code="common.next" /></a></c:if></div></section>
<sec:authorize access="@access.hasAny(principal, 'ACCOUNTING_JOURNALS_CREATE', 'ACCOUNTING_OPENINGS_CREATE')"><section class="accounting-form"><h2><spring:message code="accounting.import" /></h2><p><spring:message code="accounting.importHelp" /></p>
<form method="post" action="<c:url value='/finance/journals/import' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="${requestKey}" /><div class="accounting-grid">
<label><spring:message code="accounting.reference" /><input class="aws-control" name="sourceReference" value="<c:out value='${sourceReference}' />" maxlength="160" required /></label><label><spring:message code="accounting.date" /><input class="aws-control" name="effectiveDate" type="date" value="<c:out value='${effectiveDate}' />" required /></label>
<label><spring:message code="accounting.evidence" /><input class="aws-control" name="evidenceReference" value="<c:out value='${evidenceReference}' />" maxlength="500" required /></label><label><spring:message code="accounting.reason" /><input class="aws-control" name="reason" value="<c:out value='${reason}' />" maxlength="500" /></label></div>
<label><input type="checkbox" name="opening" value="true" ${opening ? 'checked' : ''} /><spring:message code="accounting.opening" /></label>
<label><spring:message code="accounting.lines" /> (TZS)<textarea class="aws-control" name="content" rows="8" maxlength="100000" required><c:out value="${content}" /></textarea></label>
<div class="accounting-actions"><button class="app-btn btn-neutral" name="action" value="preview"><spring:message code="accounting.preview" /></button><button class="app-btn btn-primary" name="action" value="save"><spring:message code="accounting.saveDraft" /></button></div></form><c:if test="${not empty preview}"><p role="status"><spring:message code="accounting.previewValid" />: <c:out value="${preview.size()}" /></p></c:if></section></sec:authorize>
</div>
<%@ include file="../fragments/footer.jspf" %>
