<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %><%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<%@ include file="../fragments/header.jspf" %><%@ include file="../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="accounting-workspace accounting-create-workspace accounting-servicing-workspace">
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="prepayment.settlement" /> <c:out value="${quote.loanNumber}" /></h1></div>
<c:if test="${not empty prepaymentError}"><div class="coa-error" role="alert" tabindex="-1"><spring:message code="${prepaymentError}" /></div></c:if>
<c:if test="${prepaymentSaved}"><p role="status"><spring:message code="prepayment.saved" /></p></c:if>
<section class="accounting-section accounting-form"><h2><spring:message code="prepayment.quote" /></h2>
<form method="get" class="accounting-actions"><label><spring:message code="prepayment.date" /><input type="date" class="aws-control" name="date" value="${date}" min="${today}" required /></label><button class="app-btn btn-neutral"><spring:message code="prepayment.calculate" /></button></form>
<c:if test="${not empty quote}"><dl class="erp-detail-facts">
<div><dt><spring:message code="recording.principal" /> (TZS)</dt><dd><fmt:formatNumber value="${quote.principal}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
<div><dt><spring:message code="prepayment.earned" /> (TZS)</dt><dd><fmt:formatNumber value="${quote.interest}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
<div><dt><spring:message code="prepayment.excluded" /> (TZS)</dt><dd><fmt:formatNumber value="${quote.futureInterest}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
<div><dt><spring:message code="prepayment.charges" /> (TZS)</dt><dd><fmt:formatNumber value="${quote.charges}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
<div><dt><spring:message code="prepayment.debtTotal" /> (TZS)</dt><dd><fmt:formatNumber value="${quote.total}" minFractionDigits="2" maxFractionDigits="2" /></dd></div></dl>
<p><spring:message code="prepayment.quoteHelp" /></p>
<c:choose><c:when test="${quote.legacy}"><p><spring:message code="prepayment.legacy" /></p></c:when><c:otherwise><p><spring:message code="prepayment.schedule.${quote.terms.schedule}" /> · <spring:message code="prepayment.partial.${quote.terms.partialInterest}" /> · <spring:message code="prepayment.basis.${quote.terms.dayBasis}" /></p></c:otherwise></c:choose>
</c:if></section>
<sec:authorize access="@access.has(principal,'LOAN_INTEREST_WAIVE')">
<c:url value="/repayments/loans/${loanId}/interest-waivers" var="waiverAction" />
<c:if test="${quote.legacy and quote.sequence==1}"><section class="accounting-section accounting-form"><h2><spring:message code="prepayment.adopt" /></h2><p><spring:message code="prepayment.adoptHelp" /></p>
<form method="post" action="<c:url value='/repayments/loans/${loanId}/prepayment-terms' />"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><label><spring:message code="prepayment.reason" /><textarea name="reason" maxlength="500" required></textarea></label><label class="accounting-servicing-confirm"><input type="checkbox" name="confirmed" value="true" required /><span><spring:message code="prepayment.adoptHelp" /></span></label><div class="accounting-actions"><button class="app-btn btn-neutral"><spring:message code="prepayment.adopt" /></button></div></form></section></c:if>
<c:if test="${empty waiverForm.reverses}"><form:form method="post" action="${waiverAction}" modelAttribute="waiverForm" cssClass="accounting-create-form">
<section class="accounting-section accounting-form"><h2><spring:message code="prepayment.waive" /></h2>
<form:hidden path="requestKey" /><div class="accounting-grid">
<label><spring:message code="voucher.date" /><form:input path="effectiveDate" cssClass="aws-control" type="date" max="${today}" required="required" /></label>
<label><spring:message code="voucher.amount" /> (TZS)<form:input path="amount" cssClass="aws-control" type="number" min="0.01" step="0.01" required="required" /></label>
<label><spring:message code="prepayment.reason" /><form:textarea path="reason" cssClass="aws-control" rows="3" maxlength="500" required="required" /></label>
</div><p><spring:message code="prepayment.waiverHelp" /></p>
<label class="accounting-servicing-confirm"><input type="checkbox" name="confirmed" value="true" required /><span><spring:message code="prepayment.confirmWaiver" /></span></label>
</section><div class="accounting-actions accounting-form-actions"><button class="app-btn btn-launch"><spring:message code="prepayment.waive" /></button></div>
</form:form></c:if></sec:authorize>
<section class="erp-table-wrap" data-resource-table="servicing-schedule"><div class="app-table-titlebar"><h2><spring:message code="prepayment.remainingSchedule" /></h2></div>
<div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="repayment.dueDate" /></th><th><spring:message code="prepayment.remainingPrincipal" /> (TZS)</th><th><spring:message code="prepayment.remainingInterest" /> (TZS)</th><th><spring:message code="prepayment.rebated" /> (TZS)</th><th><spring:message code="prepayment.waiver" /> (TZS)</th></tr></thead><tbody>
<c:forEach items="${servicingSchedule.content}" var="s"><tr><td><c:out value="${appTime:display(s.dueDate)}" /></td><td><fmt:formatNumber value="${s.getRemainingPrincipal()}" minFractionDigits="2" maxFractionDigits="2" /></td><td><fmt:formatNumber value="${s.getRemainingInterest()}" minFractionDigits="2" maxFractionDigits="2" /></td><td><fmt:formatNumber value="${s.interestExcluded}" minFractionDigits="2" maxFractionDigits="2" /></td><td><fmt:formatNumber value="${s.interestWaived}" minFractionDigits="2" maxFractionDigits="2" /></td></tr></c:forEach></tbody></table></div>
<div data-table-pagination data-page="${servicingSchedule.number}" data-table-for="servicing-schedule"><c:if test="${servicingSchedule.hasPrevious()}"><a data-page-direction="previous" href="?date=${date}&amp;page=${page}&amp;schedulePage=${servicingSchedule.number-1}"><spring:message code="common.previous" /></a></c:if><c:if test="${servicingSchedule.hasNext()}"><a data-page-direction="next" href="?date=${date}&amp;page=${page}&amp;schedulePage=${servicingSchedule.number+1}"><spring:message code="common.next" /></a></c:if></div></section>
<section class="erp-table-wrap" data-resource-table="interest-waivers"><div class="app-table-titlebar"><h2><spring:message code="prepayment.history" /></h2></div>
<div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="voucher.reference" /></th><th><spring:message code="voucher.date" /></th><th><spring:message code="voucher.amount" /> (TZS)</th><th><spring:message code="voucher.type" /></th><th><spring:message code="prepayment.reason" /></th></tr></thead><tbody>
<c:forEach items="${waivers}" var="w"><tr><td><a class="resource-table-id-link" href="?date=${date}&amp;page=${page}&amp;waiverId=${w.id}">IW-<c:out value="${w.sequence}" /></a></td><td><c:out value="${appTime:display(w.date)}" /></td><td><fmt:formatNumber value="${w.amount}" minFractionDigits="2" maxFractionDigits="2" /></td><td><c:choose><c:when test="${not empty w.reverses}"><spring:message code="prepayment.reversal" /></c:when><c:when test="${not empty w.reversedBy}"><spring:message code="voucher.status.REVERSED" /></c:when><c:otherwise><spring:message code="prepayment.waiver" /></c:otherwise></c:choose></td><td><c:out value="${w.reason}" /></td></tr></c:forEach>
<c:if test="${empty waivers}"><tr><td colspan="5"><spring:message code="prepayment.empty" /></td></tr></c:if></tbody></table></div>
<div data-table-pagination data-page="${page}" data-table-for="interest-waivers"><c:if test="${page>0}"><a data-page-direction="previous" href="?date=${date}&amp;page=${page-1}"><spring:message code="common.previous" /></a></c:if><c:if test="${hasNext}"><a data-page-direction="next" href="?date=${date}&amp;page=${page+1}"><spring:message code="common.next" /></a></c:if></div></section>
<c:if test="${not empty selectedWaiver}"><section class="accounting-section accounting-form"><h2><spring:message code="prepayment.waiver" /></h2><p>IW-<c:out value="${selectedWaiver.sequence}" /> · TZS <fmt:formatNumber value="${selectedWaiver.amount}" minFractionDigits="2" maxFractionDigits="2" /></p><p><c:out value="${selectedWaiver.reason}" /></p>
<sec:authorize access="@access.has(principal,'LOAN_INTEREST_WAIVE') and @access.hasAny(principal,'LOAN_RECORDING_REVERSE','LOAN_REPAYMENTS_REVERSE')"><c:if test="${empty selectedWaiver.reverses and empty selectedWaiver.reversedBy and selectedWaiver.actor ne principal.memberId}">
<form method="post" action="<c:url value='/repayments/loans/${loanId}/interest-waivers' />" class="accounting-form">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="${waiverForm.requestKey}" /><input type="hidden" name="reverses" value="${selectedWaiver.id}" /><input type="hidden" name="amount" value="${selectedWaiver.amount}" />
<label><spring:message code="voucher.date" /><input type="date" name="effectiveDate" value="${today}" min="${selectedWaiver.date}" max="${today}" required /></label><label><spring:message code="prepayment.reason" /><textarea name="reason" maxlength="500" required><c:out value="${waiverForm.reason}" /></textarea></label>
<label class="accounting-servicing-confirm"><input type="checkbox" name="confirmed" value="true" required /><span><spring:message code="prepayment.confirmReversal" /></span></label><div class="accounting-actions"><button class="app-btn btn-neutral"><spring:message code="prepayment.reverse" /></button></div></form></c:if></sec:authorize></section></c:if>
</div><%@ include file="../accounting/accounting-form-support.jspf" %><%@ include file="../fragments/footer.jspf" %>
