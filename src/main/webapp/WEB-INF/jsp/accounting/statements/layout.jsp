<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="layout-support.jspf" %>
<div class="accounting-workspace statement-layout" data-resource-details-page>
<%@ include file="feedback.jspf" %>
<c:if test="${param.unchanged=='true'}"><div data-workspace-flashbar><div class="app-toast app-toast-info console-message" data-app-toast data-toast-type="info" role="status"><p data-toast-text><spring:message code="fs.layout.unchanged" /></p><button type="button" class="app-toast-close" data-feedback-dismiss aria-label="<spring:message code='form.notification.dismiss' />">×</button></div></div></c:if>
<h1 class="erp-page-title" data-aws-page-header><spring:message code="fs.layout.title" /></h1>
<p class="fs-layout-scope"><spring:message code="fs.layout.scope" /></p>
<section id="layout-groups" class="erp-table-wrap" data-resource-table="statement-layout-groups" data-table-selection="single" data-layout-register>
<div class="app-table-titlebar"><h2><span data-detail-tab-label><spring:message code="fs.layout.groups" /></span></h2><div class="app-table-toolbar">
<button type="button" class="app-btn btn-neutral" data-table-command data-layout-command="edit" disabled><spring:message code="common.edit" /></button><button type="button" class="app-btn btn-neutral" data-table-command data-layout-command="remove" disabled><spring:message code="fs.layout.remove" /></button>
<c:choose><c:when test="${layout.groups.size()>=9}"><button type="button" class="app-btn btn-launch" disabled title="<spring:message code='fs.layout.groupLimit' />"><spring:message code="fs.layout.addGroup" /></button></c:when><c:otherwise><a class="app-btn btn-launch" href="<c:url value='/reports/financial/statements/layout/groups/new' />"><spring:message code="fs.layout.addGroup" /></a></c:otherwise></c:choose>
</div></div>
<div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="editor"><thead><tr><th><spring:message code="fs.setup.select" /></th><th><spring:message code="fs.layout.name" /></th><th><spring:message code="fs.layout.accounts" /></th><th><spring:message code="fs.layout.retained" /></th></tr></thead><tbody>
<c:forEach items="${layout.groups}" var="row"><c:url value="/reports/financial/statements/layout/groups/${row.index}/edit" var="editUrl"><c:param name="version" value="${layout.version}" /></c:url><c:url value="/reports/financial/statements/layout/groups/${row.index}/remove" var="removeUrl"><c:param name="version" value="${layout.version}" /></c:url>
<tr data-layout-row data-edit-url="<c:out value='${editUrl}' />" data-remove-url="<c:out value='${removeUrl}' />"><td class="resource-table-selection"><input type="checkbox" data-layout-select aria-label="<c:out value='${row.name}' />" /></td><td><c:out value="${pageContext.response.locale.language=='sw' and not empty row.nameSw?row.nameSw:row.name}" /></td><td>${row.accounts}</td><td><spring:message code="${row.retained?'common.yes':'common.no'}" /></td></tr></c:forEach>
<c:if test="${empty layout.groups}"><tr><td colspan="4"><span class="resource-table-empty-message"><spring:message code="fs.layout.noGroups" /></span></td></tr></c:if>
</tbody></table></div><div data-table-pagination data-page="0" data-table-for="statement-layout-groups"></div><p class="sr-only" aria-live="polite" data-layout-selection></p>
</section>
<section id="layout-movements" class="erp-table-wrap" data-resource-table="statement-layout-movements" data-table-selection="single" data-layout-register>
<div class="app-table-titlebar"><h2><span data-detail-tab-label><spring:message code="fs.layout.movementRows" /></span></h2><div class="app-table-toolbar">
<button type="button" class="app-btn btn-neutral" data-table-command data-layout-command="edit" disabled><spring:message code="common.edit" /></button>
<c:choose><c:when test="${layout.movementRows.size()>=movementRowsLimit and not empty movementRowsLimit}"><button type="button" class="app-btn btn-launch" disabled title="<spring:message code='fs.layout.movementRowsLimit' />"><spring:message code="fs.layout.addMovementRow" /></button></c:when><c:otherwise><a class="app-btn btn-launch" href="<c:url value='/reports/financial/statements/layout/movement-rows/new' />"><spring:message code="fs.layout.addMovementRow" /></a></c:otherwise></c:choose>
</div></div>
<div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="editor"><thead><tr><th><spring:message code="fs.setup.select" /></th><th><spring:message code="fs.layout.name" /></th><th><spring:message code="fs.setup.category" /></th></tr></thead><tbody>
<c:forEach items="${layout.movementRows}" var="row" varStatus="rowStatus">
<c:url value="/reports/financial/statements/layout/movement-rows/${rowStatus.index}/edit" var="editUrl"><c:param name="version" value="${layout.version}" /></c:url>
<c:set var="movementName" value="${pageContext.response.locale.language=='sw' and not empty row.nameSw?row.nameSw:row.name}" />
<tr data-layout-row data-edit-url="<c:out value='${editUrl}' />"><td class="resource-table-selection"><input type="checkbox" data-layout-select aria-label="<c:out value='${movementName}' />" /></td><td><c:out value="${movementName}" /></td><td><spring:message code="fs.movement.${row.category}" /></td></tr>
</c:forEach>
<c:if test="${empty layout.movementRows}"><tr><td colspan="3"><span class="resource-table-empty-message"><spring:message code="fs.layout.noMovementRows" /></span></td></tr></c:if>
</tbody></table></div><div data-table-pagination data-page="0" data-table-for="statement-layout-movements"></div><p class="sr-only" aria-live="polite" data-layout-selection></p>
</section>
<section id="layout-notes" class="erp-table-wrap" data-resource-table="statement-layout-notes" data-table-selection="single" data-layout-register>
<div class="app-table-titlebar"><h2><span data-detail-tab-label><spring:message code="fs.notes" /></span></h2><div class="app-table-toolbar">
<button type="button" class="app-btn btn-neutral" data-table-command data-layout-command="edit" disabled><spring:message code="common.edit" /></button><button type="button" class="app-btn btn-neutral" data-table-command data-layout-command="remove" disabled><spring:message code="fs.layout.remove" /></button>
<c:choose><c:when test="${layout.notes.size()>=30}"><button type="button" class="app-btn btn-launch" disabled title="<spring:message code='fs.layout.noteLimit' />"><spring:message code="fs.layout.addNote" /></button></c:when><c:otherwise><a class="app-btn btn-launch" href="<c:url value='/reports/financial/statements/layout/notes/new' />"><spring:message code="fs.layout.addNote" /></a></c:otherwise></c:choose>
</div></div>
<div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="editor"><thead><tr><th><spring:message code="fs.setup.select" /></th><th><spring:message code="fs.note" /></th><th><spring:message code="fs.layout.noteTitle" /></th><th><spring:message code="fs.layout.figures" /></th><th><spring:message code="fs.layout.accounts" /></th></tr></thead><tbody>
<c:forEach items="${layout.notes}" var="row"><c:url value="/reports/financial/statements/layout/notes/${row.index}/edit" var="editUrl"><c:param name="version" value="${layout.version}" /></c:url><c:url value="/reports/financial/statements/layout/notes/${row.index}/remove" var="removeUrl"><c:param name="version" value="${layout.version}" /></c:url>
<tr data-layout-row data-edit-url="<c:out value='${editUrl}' />" data-remove-url="<c:out value='${removeUrl}' />"><td class="resource-table-selection"><input type="checkbox" data-layout-select aria-label="${row.number} — <c:out value='${row.title}' />" /></td><td>${row.number}</td><td><c:out value="${pageContext.response.locale.language=='sw' and not empty row.titleSw?row.titleSw:row.title}" /></td><td><spring:message code="fs.layout.figures.${row.basis}" /></td><td>${row.accounts}<c:if test="${row.includeProfit}"><small class="fs-layout-added-profit"><spring:message code="fs.layout.retained" /></small></c:if></td></tr></c:forEach>
<c:if test="${empty layout.notes}"><tr><td colspan="5"><span class="resource-table-empty-message"><spring:message code="fs.layout.noNotes" /></span></td></tr></c:if>
</tbody></table></div><div data-table-pagination data-page="0" data-table-for="statement-layout-notes"></div><p class="sr-only" aria-live="polite" data-layout-selection></p>
</section></div>
<%@ include file="../../fragments/footer.jspf" %>
