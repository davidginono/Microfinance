<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>

<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>

<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

<spring:htmlEscape defaultHtmlEscape="true" />

<%@ include file="../../fragments/header.jspf" %>

<%@ include file="../../fragments/sidebar.jspf" %>

<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />

<link rel="stylesheet" href="<c:url value='/css/voucher-approval.css' />" />

<c:set var="w" value="${details.workflow}" /><nav class="erp-page-path"><a href="<c:url value='/finance/approval?type=${w.type}' />"><spring:message code="approval.workspace" /></a><span aria-hidden="true">›</span><span><c:out value="${w.number}" /></span></nav><div class="erp-page-header"><h1 class="erp-page-title"><c:out value="${w.title}" /></h1></div>

<div class="accounting-workspace approval-workspace"><c:if test="${not empty workflowError}"><p class="approval-alert" role="alert"><spring:message code="${workflowError}" /></p></c:if>

<section class="approval-overview erp-resource-section"><h2><spring:message code="approval.voucherHeader" /></h2><dl class="erp-detail-facts"><div><dt><spring:message code="voucher.number" /></dt><dd><c:out value="${w.number}" /></dd></div><div><dt><spring:message code="approval.scope" /></dt><dd><spring:message code="approval.scope.${w.scope}" /></dd></div><div><dt><spring:message code="approval.owner" /></dt><dd><c:out value="${ownerNames[w.owner]}" /></dd></div><div><dt><spring:message code="voucher.total" /> (TZS)</dt><dd><c:choose><c:when test="${w.validation=='VALIDATED'}"><fmt:formatNumber value="${w.total}" minFractionDigits="2" maxFractionDigits="2" /></c:when><c:otherwise>—</c:otherwise></c:choose></dd></div><c:forEach items="${['validation','approval','posting']}" var="group"><div><dt><spring:message code="approval.group.${group}" /></dt><dd><span class="approval-status" data-state="${w[group]}"><spring:message code="approval.status.${w[group]}" /></span></dd></div></c:forEach></dl>

<div class="accounting-actions"><c:forEach items="${['VALIDATE','INITIATE','RESUBMIT','WITHDRAW','HOLD','APPROVE','REJECT','FORCE','POST']}" var="action"><c:if test="${details.actions.contains(action)}"><button type="button" class="app-btn btn-neutral" data-workflow-action="${action}" data-action-label="<spring:message code='approval.action.${action}' />"><spring:message code="approval.action.${action}" /></button></c:if></c:forEach></div>

<c:if test="${not empty w.failure}"><p role="alert" class="approval-alert"><spring:message code="${w.failure}" /></p></c:if></section>

<c:if test="${not empty w.stages}"><section class="approval-overview erp-resource-section"><h2><spring:message code="approval.route" /></h2><ol><c:forEach items="${w.stages}" var="stage" varStatus="n"><li><spring:message code="approval.reviewer.${stage.claim=='VOUCHER_APPROVAL_ESCALATED_APPROVE'?'escalated':'standard'}" /> · <spring:message code="${n.index<w.stage?'approval.route.complete':n.index==w.stage and w.approval=='INITIATED'?'approval.route.current':'approval.route.waiting'}" /></li></c:forEach></ol></section></c:if><%@ include file="documents.jspf" %>

<sec:authorize access="@access.has(principal, 'VOUCHER_DOCUMENT_CREATE')"><c:if test="${details.actions.contains('EDIT')}"><form method="post" action="<c:url value='/finance/approval/${w.id}/documents' />" enctype="multipart/form-data" class="approval-upload"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><label><spring:message code="approval.uploadHelp" /><input type="file" name="file" accept="application/pdf,image/png,image/jpeg" required /></label><button class="app-btn btn-neutral"><spring:message code="approval.upload" /></button></form></c:if></sec:authorize>

<section class="erp-table-wrap" data-resource-table="voucher-approval-items" data-table-selection="single"><div class="app-table-titlebar"><h2 class="erp-panel-title"><spring:message code="approval.vouchers" /> (${w.itemCount})</h2><div class="accounting-actions"><button class="app-btn btn-neutral" data-table-command data-item-open disabled type="button"><spring:message code="${details.actions.contains('EDIT')?'approval.correct':'common.view'}" /></button></div></div><p class="sr-only" role="status" aria-live="polite" data-item-selection data-empty="<spring:message code='voucher.selectHelp' />" data-selected="<spring:message code='voucher.selected' />"></p><div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="local"><thead><tr><th><spring:message code="voucher.select" /></th><th>#</th><th><spring:message code="voucher.reference" /></th><th><spring:message code="voucher.date" /></th><th><spring:message code="voucher.party" /></th><th><spring:message code="voucher.transactions" /></th><th><spring:message code="approval.group.validation" /></th><th><spring:message code="approval.group.posting" /></th></tr></thead><tbody>

<c:forEach items="${details.items}" var="item"><c:url value="/finance/approval/${w.id}/vouchers/${item.id}" var="itemUrl" /><c:if test="${not empty item.voucherId}"><c:url value="/finance/vouchers/${w.type=='JOURNAL'?'journals':w.type=='PAYMENT'?'payments':'receipts'}/${item.voucherId}" var="itemUrl" /></c:if><tr data-item-url="<c:out value='${itemUrl}' />" class="${empty item.issues?'':'approval-warning-row'}"><td><input type="checkbox" data-item-select ${empty itemUrl?'disabled':''} aria-label="<spring:message code='voucher.select' /> ${item.line}" /></td><td>${item.line}</td><td><c:choose><c:when test="${not empty itemUrl}"><a href="<c:out value='${itemUrl}' />"><c:out value="${empty item.form.reference?item.line:item.form.reference}" /></a></c:when><c:otherwise><c:out value="${item.form.reference}" /></c:otherwise></c:choose></td><td><c:out value="${appTime:display(item.form.effectiveDate)}" /></td><td><c:out value="${item.form.party}" /></td><td>${item.form.rows.size()}</td><td><c:choose><c:when test="${not empty item.issues}"><details class="approval-issues"><summary><spring:message code="approval.warnings" /> (${item.issues.size()})</summary><ul><c:forEach items="${item.issues}" var="issue"><li><a href="<c:out value='${itemUrl}' />#field-${issue.field}"><spring:message code="${issue.code}" /></a></li></c:forEach></ul></details></c:when><c:otherwise><spring:message code="approval.status.${empty item.planHash?'NOT_VALIDATED':'VALIDATED'}" /></c:otherwise></c:choose></td><td><spring:message code="approval.status.${empty item.voucherId?'NOT_POSTED':'POSTED'}" /></td></tr></c:forEach><c:if test="${empty details.items}"><tr><td colspan="8"><spring:message code="approval.empty" /></td></tr></c:if></tbody></table></div></section>

<section class="approval-history erp-resource-section"><h2><spring:message code="approval.history" /></h2><ol><c:forEach items="${details.history}" var="event"><li><strong><spring:message code="approval.action.${event.action}" /></strong> · <c:out value="${event.actorName}" /> · <c:out value="${event.time.toLocalDate()} ${event.time.toLocalTime()}" /><c:if test="${not empty event.comments}"><p><c:out value="${event.comments}" /></p></c:if></li></c:forEach></ol></section>

<dialog class="approval-dialog" data-action-dialog><c:url value="/finance/approval/${w.id}/actions" var="actionUrl" /><form method="post" action="${actionUrl}"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="version" value="${w.version}" /><input type="hidden" name="action" /><div class="approval-dialog-header"><h2 data-dialog-title></h2><button type="button" class="erp-icon-btn" data-dialog-close aria-label="<spring:message code='common.close' />">×</button></div><p><c:out value="${w.number}" /> · <c:choose><c:when test="${w.validation=='VALIDATED'}"><fmt:formatNumber value="${w.total}" minFractionDigits="2" maxFractionDigits="2" /></c:when><c:otherwise>—</c:otherwise></c:choose> TZS</p><p data-post-warning hidden><spring:message code="approval.postHelp" /></p><label><spring:message code="approval.comments" /><textarea name="comments" maxlength="1000" rows="3"></textarea></label><div class="accounting-actions approval-footer"><button type="button" class="app-btn btn-neutral" data-dialog-close><spring:message code="common.cancel" /></button><button class="app-btn btn-neutral" data-confirm-action><spring:message code="approval.confirm" /></button></div></form></dialog></div><script src="<c:url value='/js/voucher-approval.js' />" defer></script>

<%@ include file="../../fragments/footer.jspf" %>