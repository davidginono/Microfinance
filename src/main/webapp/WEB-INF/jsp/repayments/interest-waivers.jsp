<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<c:url value="${waiverBase}/interest-waivers" var="waiverAction" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />">
    <a href="<c:url value='${waiverBase}' />"><c:out value="${waiverSchedule.loanNumber}" /></a><span aria-hidden="true">›</span>
    <span aria-current="page"><spring:message code="prepayment.waiver" /></span>
</nav>
<div class="accounting-workspace accounting-create-workspace" id="interest-waivers">
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="prepayment.waiver" /></h1></div>
<c:if test="${prepaymentSaved}"><p class="console-message" data-message-type="success" role="status"><spring:message code="prepayment.saved" /></p></c:if>
<sec:authorize access="@access.has(principal,'LOAN_INTEREST_WAIVE')">
<c:if test="${waiverSchedule.active}">
<form method="get" action="${waiverAction}" class="erp-table-toolbar">
    <label><spring:message code="voucher.date" /><input class="aws-control" name="date" type="date" value="${waiverForm.effectiveDate}" max="${waiverToday}" required /></label>
    <button class="app-btn btn-neutral"><spring:message code="fs.apply" /></button>
</form>
<c:if test="${waiverSchedule.legacy and waiverSchedule.sequence==1}">
<details class="accounting-section accounting-form"><summary><spring:message code="prepayment.adopt" /></summary>
<form method="post" action="<c:url value='${waiverBase}/prepayment-terms' />">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    <label><spring:message code="prepayment.reason" /><textarea class="aws-control" name="reason" maxlength="500" required></textarea></label>
    <label class="accounting-servicing-confirm"><input type="checkbox" name="confirmed" value="true" required /><span><spring:message code="prepayment.adoptHelp" /></span></label>
    <div class="accounting-actions"><button class="app-btn btn-neutral"><spring:message code="prepayment.adopt" /></button></div>
</form></details>
</c:if>
<form method="post" action="${waiverAction}" data-waiver-selection>
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    <input type="hidden" name="requestKey" value="${waiverForm.requestKey}" />
    <input type="hidden" name="sequence" value="${waiverSchedule.sequence}" />
    <input type="hidden" name="effectiveDate" value="${waiverForm.effectiveDate}" />
    <section class="erp-table-wrap" data-resource-table="waiver-schedule" data-aws-no-refresh="true">
    <div class="app-table-titlebar"><h2><spring:message code="recording.schedule" /></h2></div>
    <div class="erp-table-scroll erp-table-scroll-lg"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="editor">
    <thead><tr><th scope="col"><spring:message code="coa.select" /></th><th scope="col"><spring:message code="recording.dueDate" /></th>
        <th scope="col"><spring:message code="recording.interest" /> (TZS)</th>
        <th scope="col"><spring:message code="prepayment.eligible" /> (TZS)</th>
        <th scope="col"><spring:message code="prepayment.waiverAmount" /> (TZS)</th></tr></thead>
    <tbody><c:forEach items="${waiverSchedule.installments}" var="row" varStatus="status">
        <c:set var="entered" value="${waiverEntered[row.id]}" />
        <tr data-waiver-row><td><c:if test="${row.eligible gt 0}">
            <input type="hidden" name="selections[${status.index}].installmentId" value="${row.id}" />
            <input data-waiver-check type="checkbox" name="selections[${status.index}].selected" value="true"
                aria-label="<spring:message code='prepayment.selectPeriod' arguments='${row.number}' />" ${entered.selected?'checked':''} />
        </c:if></td>
        <td><c:out value="${appTime:display(row.dueDate)}" /></td>
        <td><fmt:formatNumber value="${row.scheduled}" minFractionDigits="2" maxFractionDigits="2" /></td>
        <td><fmt:formatNumber value="${row.eligible}" minFractionDigits="2" maxFractionDigits="2" /></td>
        <td><c:if test="${row.eligible gt 0}">
            <input class="aws-control" data-waiver-amount type="number" name="selections[${status.index}].amount"
                aria-label="<spring:message code='prepayment.amountForPeriod' arguments='${row.number}' />"
                min="0.01" max="${row.eligible}" step="0.01" value="${empty entered.amount?row.eligible:entered.amount}" />
        </c:if></td></tr>
    </c:forEach>
    <c:if test="${empty waiverSchedule.installments}"><tr><td colspan="5"><spring:message code="prepayment.error.date" /></td></tr></c:if></tbody>
    </table></div></section>
    <section class="accounting-section accounting-form"><h2><spring:message code="prepayment.waiver" /></h2>
        <p><spring:message code="prepayment.waiverAmount" /> (TZS) <output data-waiver-total aria-live="polite">0.00</output></p>
        <label><spring:message code="prepayment.reason" /><textarea class="aws-control" name="reason" rows="3" maxlength="500" required><c:if test="${empty waiverForm.reverses}"><c:out value="${waiverForm.reason}" /></c:if></textarea></label>
        <label class="accounting-servicing-confirm"><input type="checkbox" name="confirmed" value="true" required /><span><spring:message code="prepayment.confirmWaiver" /></span></label>
    </section>
    <c:if test="${not empty prepaymentError and empty waiverForm.reverses}"><div class="coa-error" role="alert" tabindex="-1"><spring:message code="${prepaymentError}" /></div></c:if>
    <div class="accounting-actions accounting-form-actions"><a class="app-btn btn-neutral" href="<c:url value='${waiverBase}' />"><spring:message code="common.cancel" /></a><button class="app-btn btn-launch"><spring:message code="prepayment.waiveAction" /></button></div>
</form>
</c:if>
</sec:authorize>
<c:if test="${not empty prepaymentError and (not empty waiverForm.reverses or not waiverSchedule.active)}"><div class="coa-error" role="alert" tabindex="-1"><spring:message code="${prepaymentError}" /></div></c:if>
<section class="erp-table-wrap" data-resource-table="interest-waivers" data-table-page-param="waiverPage"><div class="app-table-titlebar"><h2><spring:message code="prepayment.history" /></h2></div>
<div class="erp-table-scroll"><table class="erp-table"><thead><tr><th data-table-order="reference"><spring:message code="voucher.reference" /></th><th data-table-order="date"><spring:message code="voucher.date" /></th><th data-table-order="amount"><spring:message code="voucher.amount" /> (TZS)</th><th data-table-order="kind"><spring:message code="voucher.type" /></th><th data-table-order="reason"><spring:message code="prepayment.reason" /></th></tr></thead><tbody>
<c:forEach items="${waivers}" var="w"><tr><td><a class="resource-table-id-link" href="?waiverPage=${waiverPage}&amp;waiverId=${w.id}&amp;date=${waiverForm.effectiveDate}#interest-waivers">IW-<c:out value="${w.sequence}" /></a></td><td><c:out value="${appTime:display(w.date)}" /></td><td><fmt:formatNumber value="${w.amount}" minFractionDigits="2" maxFractionDigits="2" /></td><td><c:choose><c:when test="${not empty w.reverses}"><spring:message code="prepayment.reversal" /></c:when><c:when test="${not empty w.reversedBy}"><span data-resource-status="REVERSED"><spring:message code="voucher.status.REVERSED" /></span></c:when><c:otherwise><spring:message code="prepayment.waiver" /></c:otherwise></c:choose></td><td><c:out value="${w.reason}" /></td></tr></c:forEach>
<c:if test="${empty waivers}"><tr><td colspan="5"><spring:message code="prepayment.empty" /></td></tr></c:if></tbody></table></div>
<div data-table-pagination data-page="${waiverPage}" data-table-for="interest-waivers"><c:if test="${waiverPage>0}"><a data-page-direction="previous" href="?waiverPage=${waiverPage-1}&amp;date=${waiverForm.effectiveDate}"><spring:message code="common.previous" /></a></c:if><c:if test="${waiverHasNext}"><a data-page-direction="next" href="?waiverPage=${waiverPage+1}&amp;date=${waiverForm.effectiveDate}"><spring:message code="common.next" /></a></c:if></div></section>
<c:if test="${not empty selectedWaiver}"><section class="accounting-section accounting-form"><h2><spring:message code="prepayment.waiver" /></h2><p>IW-<c:out value="${selectedWaiver.sequence}" /> · TZS <fmt:formatNumber value="${selectedWaiver.amount}" minFractionDigits="2" maxFractionDigits="2" /></p><p><c:out value="${selectedWaiver.reason}" /></p>
<sec:authorize access="@access.has(principal,'LOAN_INTEREST_WAIVE') and @access.hasAny(principal,'LOAN_RECORDING_REVERSE','LOAN_REPAYMENTS_REVERSE')"><c:if test="${empty selectedWaiver.reverses and empty selectedWaiver.reversedBy and selectedWaiver.actor ne principal.memberId}">
<form method="post" action="<c:url value='${waiverBase}/interest-waivers' />" class="accounting-form">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="${waiverForm.requestKey}" /><input type="hidden" name="reverses" value="${selectedWaiver.id}" />
<label><spring:message code="voucher.date" /><input type="date" name="effectiveDate" value="${waiverToday}" min="${selectedWaiver.date}" max="${waiverToday}" required /></label><label><spring:message code="prepayment.reason" /><textarea name="reason" maxlength="500" required><c:out value="${waiverForm.reason}" /></textarea></label>
<label class="accounting-servicing-confirm"><input type="checkbox" name="confirmed" value="true" required /><span><spring:message code="prepayment.confirmReversal" /></span></label><div class="accounting-actions"><button class="app-btn btn-neutral"><spring:message code="prepayment.reverse" /></button></div></form></c:if></sec:authorize></section></c:if>

</div>
<script src="<c:url value='/js/interest-waivers.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
