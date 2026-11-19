<%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="support.jspf" %>
<script src="<c:url value='/js/equity-movements.js' />" defer></script>
<div class="accounting-workspace">
<%@ include file="feedback.jspf" %>
<section class="erp-table-wrap" data-aws-table-region data-resource-table="equity-movements" data-table-selection="single" data-equity-movements>
<div class="app-table-titlebar"><h2 class="erp-page-title"><spring:message code="fs.movements" /></h2>
<div class="app-table-toolbar" data-table-toolbar-for="equity-movements"><button class="app-btn btn-neutral" type="button" disabled data-table-command data-equity-edit><spring:message code="fs.setup.editCategory" /></button></div></div>
<form class="erp-table-toolbar" method="get" action="<c:url value='/reports/financial/statements/movements' />"><label><spring:message code="fs.from" /><input class="aws-control" type="date" name="from" value="${from}" required /></label><label><spring:message code="fs.through" /><input class="aws-control" type="date" name="through" value="${through}" required /></label><button class="app-btn btn-neutral"><spring:message code="fs.apply" /></button></form>
<div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="financial-statement"><thead><tr><th><spring:message code="fs.setup.select" /></th><th><spring:message code="fs.date" /></th><th><spring:message code="fs.reference" /></th><th><spring:message code="fs.description" /></th><th><spring:message code="fs.debit" /> (TZS)</th><th><spring:message code="fs.credit" /> (TZS)</th><th><spring:message code="fs.setup.category" /></th></tr></thead><tbody><c:forEach items="${journals}" var="j"><tr><td class="resource-table-selection"><input type="checkbox" data-equity-select value="${j.id}" data-version="${j.version}" data-category="${j.category}" aria-label="<c:out value='${j.reference}' /> — <c:out value='${j.description}' />" /></td><td>${appTime:display(j.date)}</td><td><c:out value="${j.reference}" /></td><td><c:out value="${j.description}" /></td><td><c:out value="${j.debit}" /></td><td><c:out value="${j.credit}" /></td><td><spring:message code="fs.movement.${j.category}" /><c:if test="${not empty j.row}"> · <c:out value="${pageContext.response.locale.language=='sw' and not empty j.row.nameSw?j.row.nameSw:j.row.name}" /></c:if></td></tr></c:forEach><c:if test="${empty journals}"><tr><td colspan="7"><span class="resource-table-empty-message"><spring:message code="fs.noRows" /></span></td></tr></c:if></tbody></table></div>
<div data-table-pagination data-page="${page}" data-table-for="equity-movements"><c:if test="${page>0}"><a data-page-direction="previous" href="<c:url value='/reports/financial/statements/movements?page=${page-1}&amp;from=${from}&amp;through=${through}' />"><spring:message code="fs.previousPage" /></a></c:if><span>${page+1}</span><c:if test="${hasNext}"><a data-page-direction="next" href="<c:url value='/reports/financial/statements/movements?page=${page+1}&amp;from=${from}&amp;through=${through}' />"><spring:message code="fs.nextPage" /></a></c:if></div>
<p class="sr-only" aria-live="polite" data-equity-selection-status></p>
</section>
<div class="app-modal-overlay fs-movement-modal" id="equity-movement-modal" hidden aria-hidden="true">
<div class="app-modal-panel" role="dialog" aria-modal="true" aria-labelledby="equity-movement-title" tabindex="-1">
<div class="app-modal-header"><h2 id="equity-movement-title"><spring:message code="fs.setup.editCategory" /></h2><button type="button" class="app-modal-close" data-equity-cancel aria-label="<spring:message code='common.close' />">×</button></div>
<form id="equity-classify" method="post" action="<c:url value='/reports/financial/statements/movements' />" data-equity-classify data-workspace-submit="recover">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="journal" value="<c:out value='${selectedJournal}' />" /><input type="hidden" name="version" value="<c:out value='${selectedVersion}' />" /><input type="hidden" name="from" value="${from}" /><input type="hidden" name="through" value="${through}" /><input type="hidden" name="page" value="${page}" />
<div class="app-modal-body fs-fields">
<div class="fs-full"><span><spring:message code="fs.reference" /></span><p data-equity-reference></p></div>
<label class="fs-full"><spring:message code="fs.setup.category" /><select class="aws-control" name="category" required><c:forEach items="${categories}" var="category"><option value="${category}" ${selectedCategory==category?'selected':''}><spring:message code="fs.movement.${category}" /></option></c:forEach></select></label>
<label class="fs-full"><spring:message code="fs.setup.reason" /><textarea class="aws-control" name="reason" maxlength="500" rows="3" required><c:out value="${reason}" /></textarea></label>
<c:if test="${not empty errorKey}"><div class="console-message fs-full" data-workspace-response-error data-message-type="error" role="alert" tabindex="-1"><spring:message code="${errorKey}" /></div></c:if>
</div>
<div class="app-modal-actions"><button type="button" class="app-btn btn-neutral" data-equity-cancel><spring:message code="common.cancel" /></button><button class="app-btn btn-launch" type="submit"><spring:message code="fs.setup.saveCategory" /></button></div>
</form></div></div>
</div><%@ include file="../../fragments/footer.jspf" %>
