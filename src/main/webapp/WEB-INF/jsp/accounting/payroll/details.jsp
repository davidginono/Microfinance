<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />"><a class="erp-page-path__item erp-page-path__link" href="<c:url value='/finance/payroll' />"><spring:message code="payroll.title" /></a><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="payroll.details" /></span></nav>
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="payroll.details" /></h1><div class="accounting-actions">
    <sec:authorize access="@access.has(principal,'PAYROLL_DEDUCTIONS_EXPORT')"><a class="app-btn btn-neutral" href="<c:url value='/finance/payroll/${batch.id}/pdf' />"><spring:message code="payroll.pdf" /></a><button type="button" class="app-btn btn-neutral" data-payroll-print="<c:url value='/finance/payroll/${batch.id}/print' />"><spring:message code="payroll.print" /></button></sec:authorize>
    <c:if test="${batch.state=='CANCELLED'}"><sec:authorize access="@access.has(principal,'PAYROLL_DEDUCTIONS_CREATE')"><a class="app-btn btn-launch" href="<c:url value='/finance/payroll/new?replacesId=${batch.id}' />"><spring:message code="payroll.replace" /></a></sec:authorize></c:if>
</div></div>
<div class="accounting-workspace">
<%@ include file="error.jspf" %>
<c:if test="${batch.state=='CANCELLED'}"><p role="status"><spring:message code="payroll.cancelledNotice" /></p></c:if>
<section class="erp-resource-section"><h2 class="break-words"><c:out value="${batch.reference}" /></h2><dl class="erp-detail-facts">
    <div><dt><spring:message code="payroll.employer" /></dt><dd><c:out value="${batch.employer}" /></dd></div><div><dt><spring:message code="payroll.month" /></dt><dd><c:out value="${batch.month.toString().substring(0,7)}" /></dd></div><div><dt><spring:message code="payroll.deductionDate" /></dt><dd><c:out value="${appTime:date(batch.deductionDate)}" /></dd></div>
    <div><dt><spring:message code="payroll.state" /></dt><dd><spring:message code="payroll.state.${batch.state}" /></dd></div><div><dt><spring:message code="payroll.total" /></dt><dd>TZS <fmt:formatNumber value="${batch.total}" minFractionDigits="2" maxFractionDigits="2" /></dd></div><div><dt><spring:message code="payroll.employees" /></dt><dd><c:out value="${batch.employees}" /></dd></div>
    <div><dt><spring:message code="payroll.cutoff" /></dt><dd><c:out value="${appTime:display(batch.cutoff)}" /></dd></div><div><dt><spring:message code="payroll.createdBy" /></dt><dd><c:out value="${batch.actor}" /></dd></div><div><dt><spring:message code="payroll.includeArrears" /></dt><dd><spring:message code="payroll.${batch.includeArrears?'yes':'no'}" /></dd></div>
    <div><dt><spring:message code="payroll.exceptions" /></dt><dd><c:out value="${batch.exceptions}" /></dd></div><c:if test="${batch.replacesId!=null}"><div><dt><spring:message code="payroll.replaces" /></dt><dd><a class="resource-table-id-link" href="<c:url value='/finance/payroll/${batch.replacesId}' />"><c:out value="PAY-${batch.replacesId}" /></a></dd></div></c:if>
</dl><p><spring:message code="payroll.requestOnly" /></p></section>
<section class="erp-table-wrap" data-resource-table="payroll-lines"><div class="app-table-titlebar"><h2><spring:message code="payroll.loanBreakdown" /></h2></div>
    <c:set var="payrollLines" value="${lines.rows}" /><%@ include file="lines.jspf" %>
    <div data-table-pagination data-page="${lines.page}" data-table-for="payroll-lines"><c:forEach items="${['previous','next']}" var="move"><c:if test="${move=='previous'?lines.page>0:lines.hasNext}"><c:url value="/finance/payroll/${batch.id}" var="linePage"><c:param name="page" value="${lines.page+(move=='previous'?-1:1)}" /><c:param name="historyPage" value="${events.page}" /></c:url><a data-page-direction="${move}" href="<c:out value='${linePage}' />"><spring:message code="common.${move}" /></a></c:if></c:forEach></div>
</section>
<section class="erp-table-wrap mt-4" data-resource-table="payroll-history"><div class="app-table-titlebar"><h2><spring:message code="payroll.history" /></h2></div>
    <div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="payroll.event" /></th><th><spring:message code="payroll.createdBy" /></th><th><spring:message code="payroll.evidence" /></th><th><spring:message code="payroll.column.date" /></th></tr></thead><tbody><c:forEach items="${events.rows}" var="event"><tr><td><spring:message code="payroll.event.${event.kind}" /></td><td><c:out value="${event.actor}" /></td><td><c:out value="${event.evidence}" /></td><td><c:out value="${appTime:display(event.date)}" /></td></tr></c:forEach><c:if test="${empty events.rows}"><tr><td colspan="4"><spring:message code="payroll.empty" /></td></tr></c:if></tbody></table></div>
    <div data-table-pagination data-page="${events.page}" data-table-for="payroll-history"><c:forEach items="${['previous','next']}" var="move"><c:if test="${move=='previous'?events.page>0:events.hasNext}"><c:url value="/finance/payroll/${batch.id}" var="historyPage"><c:param name="page" value="${lines.page}" /><c:param name="historyPage" value="${events.page+(move=='previous'?-1:1)}" /></c:url><a data-page-direction="${move}" href="<c:out value='${historyPage}' />"><spring:message code="common.${move}" /></a></c:if></c:forEach></div>
</section>
<c:if test="${batch.state!='CANCELLED'}"><sec:authorize access="@access.has(principal,'PAYROLL_DEDUCTIONS_UPDATE')">
    <form method="post" action="<c:url value='/finance/payroll/${batch.id}/state' />" class="accounting-create-form mt-4">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <section class="erp-resource-section"><h2><spring:message code="payroll.updateState" /></h2><div class="grid gap-4">
            <label class="grid gap-2"><spring:message code="payroll.event" /><select name="kind" class="aws-control"><c:if test="${batch.state=='FROZEN'}"><option value="SENT"><spring:message code="payroll.markSent" /></option></c:if><option value="CANCELLED"><spring:message code="payroll.cancelBatch" /></option></select></label>
            <label class="grid gap-2"><spring:message code="payroll.stateEvidence" /><textarea name="evidence" class="aws-control" required maxlength="500" rows="3"></textarea></label>
            <c:if test="${batch.state=='SENT'}"><label><input type="checkbox" name="withdrawalConfirmed" value="true" required /> <spring:message code="payroll.withdrawalConfirmed" /></label></c:if>
            <p><spring:message code="payroll.manualStateHelp" /></p>
        </div></section><div class="accounting-actions accounting-form-actions"><button class="app-btn btn-launch"><spring:message code="common.save" /></button></div>
    </form><%@ include file="../accounting-form-support.jspf" %>
</sec:authorize></c:if>
</div><script src="<c:url value='/js/payroll-deductions.js' />" defer></script><%@ include file="../../fragments/footer.jspf" %>
