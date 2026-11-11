<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %><%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %><%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %><%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../fragments/header.jspf" %><%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css'/>"/>
<nav class="erp-page-path"><a href="<c:url value='/finance/groups/cycles/${cycleId}'/>"><spring:message code="group.cycle"/></a><span aria-hidden="true">›</span><span><spring:message code="group.paper.title"/></span></nav>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="group.paper.title"/></h1></div>
<div class="accounting-workspace">
<sec:authorize access="@access.has(principal,'GROUP_LENDING_UPDATE') and @access.has(principal,'LOAN_RECORDING_CREATE')">
<c:url value="/finance/groups/cycles/${cycleId}/signed-papers" var="paperUrl"/>
<form:form modelAttribute="paperForm" action="${paperUrl}" method="post" enctype="multipart/form-data" cssClass="accounting-create-form">
<form:hidden path="requestKey"/><form:hidden path="expectedHash"/>
<form:errors path="*" element="p" cssClass="coa-error"/>
<section class="accounting-section erp-detail-card"><h2><spring:message code="group.paper.record"/></h2><div class="accounting-form">
<label><spring:message code="group.paper.person"/><form:select path="target" cssClass="aws-control" required="true" data-paper-target="true"><form:option value=""><spring:message code="voucher.select"/></form:option><c:forEach items="${paperTargets}" var="target"><form:option value="${target.key}"><spring:message code="group.paper.kind.${target.kind}"/> · <c:out value="${target.number}"/> · <c:out value="${target.name}"/><c:if test="${not empty target.amount}"> · TZS <fmt:formatNumber value="${target.amount}" minFractionDigits="2" maxFractionDigits="2"/></c:if></form:option></c:forEach></form:select></label>
<label><spring:message code="group.paper.reference"/><form:input path="reference" cssClass="aws-control" maxlength="500" required="true"/></label>
<label><spring:message code="group.paper.signedAt"/><form:input path="signedAt" type="datetime-local" cssClass="aws-control" required="true"/></label>
<label><spring:message code="orgDocument.language"/><form:select path="language" cssClass="aws-control"><form:option value="SW">Kiswahili</form:option><form:option value="EN">English</form:option></form:select></label>
</div>
<fieldset data-paper-destination ${paperForm.target.startsWith('SHARED_PAYOUT/')?'':'hidden disabled'}><legend><spring:message code="group.paper.destination"/></legend><div class="accounting-form">
<label><spring:message code="group.paper.destinationType"/><form:select path="destinationType" cssClass="aws-control"><c:forEach items="${['BANK_ACCOUNT','MOBILE_MONEY','OTHER']}" var="type"><form:option value="${type}"><spring:message code="group.paper.destination.${type}"/></form:option></c:forEach></form:select></label>
<label><spring:message code="group.paper.provider"/><form:input path="provider" cssClass="aws-control" maxlength="120"/></label><label><spring:message code="group.paper.holder"/><form:input path="accountHolderName" cssClass="aws-control" maxlength="160"/></label><label><spring:message code="group.paper.account"/><form:input path="accountIdentifier" cssClass="aws-control" maxlength="100"/></label>
</div></fieldset>
<p><spring:message code="group.paper.checkFacts"/></p>
<div class="accounting-actions"><button class="app-btn btn-neutral" name="action" value="preview"><spring:message code="group.paper.check"/></button></div>
<c:if test="${not empty paperForm.expectedHash}"><div class="accounting-form"><label><spring:message code="group.paper.file"/><input class="aws-control" type="file" name="signedPdf" accept="application/pdf"/></label><label class="aws-settings-checkbox-row"><form:checkbox path="checked" cssClass="aws-settings-checkbox"/><span><spring:message code="group.paper.checked"/></span></label></div><button class="app-btn btn-launch" name="action" value="record"><spring:message code="common.save"/></button></c:if>
</section></form:form></sec:authorize>
<section class="erp-table-wrap" data-resource-table="group-signed-papers"><div class="app-table-titlebar"><h2><spring:message code="group.paper.history"/></h2></div><div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="evidence-history"><thead><tr><c:forEach items="${['group.paper.reference','group.paper.person','group.paper.kind','group.paper.signedAt','group.actor','group.paper.recordedAt']}" var="label"><th><spring:message code="${label}"/></th></c:forEach></tr></thead><tbody>
<c:forEach items="${papers.rows}" var="paper"><tr><td><sec:authorize access="@access.has(principal,'LOAN_DOCUMENTS_VIEW') and @access.has(principal,'LOAN_DOCUMENTS_EXPORT')" var="canReadPaper"/><c:choose><c:when test="${canReadPaper}"><a class="resource-table-id-link" href="<c:url value='/finance/groups/cycles/${cycleId}/signed-papers/${paper.id}/pdf'/>"><c:out value="${paper.reference}"/></a></c:when><c:otherwise><c:out value="${paper.reference}"/></c:otherwise></c:choose></td><td><c:out value="${paper.person}"/></td><td><spring:message code="group.paper.kind.${paper.kind}"/></td><td><c:out value="${appTime:display(paper.signedAt)}"/></td><td><c:out value="${paper.actor}"/></td><td><c:out value="${appTime:display(paper.recordedAt)}"/></td></tr></c:forEach><c:if test="${empty papers.rows}"><tr><td colspan="6"><spring:message code="voucher.noMatches"/></td></tr></c:if>
</tbody></table></div><div class="voucher-pagination" data-table-pagination data-table-for="group-signed-papers" data-page="${papers.page}"><spring:message code="voucher.page"/> ${papers.page+1}<div class="accounting-actions"><c:if test="${papers.page>0}"><a class="app-btn btn-neutral" data-page-direction="previous" href="?page=${papers.page-1}"><spring:message code="common.previous"/></a></c:if><c:if test="${papers.hasNext}"><a class="app-btn btn-neutral" data-page-direction="next" href="?page=${papers.page+1}"><spring:message code="common.next"/></a></c:if></div></div></section>
</div>
<script>
(function () {
    function init(root) {
        root.querySelectorAll('[data-paper-target]').forEach(function (target) {
            if (target.dataset.paperReady) return;
            target.dataset.paperReady = 'true';
            var fields = target.form.querySelector('[data-paper-destination]');
            function update() { fields.hidden = fields.disabled = !target.value.startsWith('SHARED_PAYOUT/'); }
            target.addEventListener('change', update); update();
        });
    }
    if (window.WorkspaceNavigation) window.WorkspaceNavigation.onReady(function () { init(document); });
    else if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { init(document); });
    else init(document);
}());
</script><%@ include file="../fragments/footer.jspf" %>
