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
<c:url value="${waiverBase}" var="loanUrl" />
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb' />">
    <a class="erp-page-path__item erp-page-path__link" href="${loanUrl}"><c:out value="${waiverSchedule.loanNumber}" /></a>
    <span class="erp-page-path__separator" aria-hidden="true">›</span>
    <span class="erp-page-path__item" aria-current="page"><spring:message code="prepayment.waiver" /></span>
</nav>
<div class="accounting-workspace accounting-servicing-workspace" id="interest-waivers" data-resource-form-page>
    <c:if test="${prepaymentSaved}"><p class="console-message" data-message-type="success" role="status"><spring:message code="prepayment.saved" /></p></c:if>
    <div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="prepayment.waiver" /></h1></div>

    <sec:authorize access="@access.has(principal,'LOAN_INTEREST_WAIVE')" var="mayWaive" />
    <c:if test="${mayWaive and waiverSchedule.active}">
        <section class="erp-resource-section erp-form-wrap">
            <h2><spring:message code="voucher.date" /></h2>
            <form method="get" action="${waiverAction}" class="waiver-date-form">
                <label><span class="sr-only"><spring:message code="voucher.date" /></span>
                    <input class="aws-control" name="date" type="date" value="${waiverForm.effectiveDate}" min="${waiverSchedule.disbursedOn}" max="${waiverToday}" required />
                </label>
                <button class="app-btn btn-neutral" type="submit"><spring:message code="fs.apply" /></button>
            </form>
        </section>

        <c:if test="${waiverSchedule.legacy and waiverSchedule.sequence == 1}">
            <details class="erp-resource-section erp-form-wrap">
                <summary><spring:message code="prepayment.adopt" /></summary>
                <form method="post" action="<c:url value='${waiverBase}/prepayment-terms' />" class="waiver-fields">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <label class="waiver-field"><spring:message code="prepayment.reason" />
                        <textarea class="aws-control" name="reason" maxlength="500" required></textarea>
                    </label>
                    <label class="accounting-servicing-confirm">
                        <input type="checkbox" name="confirmed" value="true" required />
                        <span><spring:message code="prepayment.adoptHelp" /></span>
                    </label>
                    <div><button type="submit" class="app-btn btn-neutral"><spring:message code="prepayment.adopt" /></button></div>
                </form>
            </details>
        </c:if>

        <form method="post" action="${waiverAction}" class="waiver-fields waiver-work" data-waiver-selection
              data-waiver-selection-required="<spring:message code='prepayment.selectRequired' />">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <input type="hidden" name="requestKey" value="${waiverForm.requestKey}" />
            <input type="hidden" name="sequence" value="${waiverSchedule.sequence}" />
            <input type="hidden" name="effectiveDate" value="${waiverForm.effectiveDate}" />
            <section class="erp-table-wrap" data-resource-table="waiver-schedule" data-aws-no-refresh="true">
                <div class="app-table-titlebar"><h2><spring:message code="recording.schedule" /></h2></div>
                <p class="waiver-table-note" id="waiver-eligibility"><spring:message code="prepayment.earnedOnly" /></p>
                <div class="erp-table-scroll erp-table-scroll-lg">
                    <table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="editor">
                        <thead><tr>
                            <th scope="col" data-table-column-locked><spring:message code="coa.select" /></th>
                            <th scope="col" data-table-identifier><spring:message code="recording.dueDate" /></th>
                            <th scope="col"><spring:message code="recording.interest" /> (TZS)</th>
                            <th scope="col" data-table-column-locked><spring:message code="prepayment.eligible" /> (TZS)</th>
                            <th scope="col" data-table-column-locked><spring:message code="prepayment.waiverAmount" /> (TZS)</th>
                        </tr></thead>
                        <tbody>
                            <c:forEach items="${waiverSchedule.installments}" var="row" varStatus="status">
                                <c:set var="entered" value="${waiverEntered[row.id]}" />
                                <tr data-waiver-row>
                                    <td>
                                        <c:choose><c:when test="${row.eligible gt 0}">
                                            <input type="hidden" name="selections[${status.index}].installmentId" value="${row.id}" />
                                            <input data-waiver-check type="checkbox" name="selections[${status.index}].selected" value="true"
                                                aria-label="<spring:message code='prepayment.selectPeriod' arguments='${row.number}' />" ${entered.selected ? 'checked' : ''} />
                                        </c:when><c:otherwise>
                                            <input type="checkbox" disabled aria-describedby="waiver-eligibility"
                                                aria-label="<spring:message code='prepayment.selectPeriod' arguments='${row.number}' />" />
                                        </c:otherwise></c:choose>
                                    </td>
                                    <td><c:out value="${appTime:display(row.dueDate)}" /></td>
                                    <td><fmt:formatNumber value="${row.scheduled}" minFractionDigits="2" maxFractionDigits="2" /></td>
                                    <td><fmt:formatNumber value="${row.eligible}" minFractionDigits="2" maxFractionDigits="2" /></td>
                                    <td><c:choose><c:when test="${row.eligible gt 0}">
                                        <input class="aws-control waiver-amount" data-waiver-amount data-amount-input="true" type="number"
                                            name="selections[${status.index}].amount" min="0.01" max="${row.eligible}" step="0.01"
                                            aria-label="<spring:message code='prepayment.amountForPeriod' arguments='${row.number}' />"
                                            value="${empty entered.amount ? row.eligible : entered.amount}" />
                                    </c:when><c:otherwise>—</c:otherwise></c:choose></td>
                                </tr>
                            </c:forEach>
                            <c:if test="${empty waiverSchedule.installments}"><tr><td colspan="5"><spring:message code="prepayment.error.date" /></td></tr></c:if>
                        </tbody>
                    </table>
                </div>
            </section>
            <c:if test="${waiverSchedule.hasEligibleInterest()}">
                <section class="erp-resource-section erp-form-wrap">
                    <h2><spring:message code="prepayment.waiverDetails" /></h2>
                    <div class="waiver-fields">
                        <dl class="erp-detail-facts">
                            <div><dt><spring:message code="prepayment.waiverAmount" /> (TZS)</dt><dd><output data-waiver-total aria-live="polite">0.00</output></dd></div>
                        </dl>
                        <label class="waiver-field"><spring:message code="prepayment.reason" />
                            <textarea class="aws-control" name="reason" rows="3" maxlength="500" required><c:if test="${empty waiverForm.reverses}"><c:out value="${waiverForm.reason}" /></c:if></textarea>
                        </label>
                        <label class="accounting-servicing-confirm">
                            <input type="checkbox" name="confirmed" value="true" required />
                            <span><spring:message code="prepayment.confirmWaiver" /></span>
                        </label>
                    </div>
                </section>
            </c:if>
            <c:if test="${not empty prepaymentError and empty waiverForm.reverses}">
                <p class="console-message" data-message-type="error" role="alert"><spring:message code="${prepaymentError}" /></p>
            </c:if>
            <div class="accounting-actions waiver-actions">
                <a class="app-btn btn-neutral" href="${loanUrl}"><spring:message code="common.cancel" /></a>
                <c:if test="${waiverSchedule.hasEligibleInterest()}"><button class="app-btn btn-launch" type="submit"><spring:message code="prepayment.waiveAction" /></button></c:if>
            </div>
        </form>
    </c:if>

    <section class="erp-table-wrap" data-resource-table="interest-waivers" data-table-page-param="waiverPage">
        <div class="app-table-titlebar"><h2><spring:message code="prepayment.history" /></h2></div>
        <div class="erp-table-scroll">
            <table class="erp-table">
                <thead><tr>
                    <th scope="col" data-table-order="reference" data-table-identifier><spring:message code="voucher.reference" /></th>
                    <th scope="col" data-table-order="date"><spring:message code="voucher.date" /></th>
                    <th scope="col" data-table-order="amount"><spring:message code="voucher.amount" /> (TZS)</th>
                    <th scope="col" data-table-order="kind"><spring:message code="voucher.type" /></th>
                    <th scope="col" data-table-order="reason"><spring:message code="prepayment.reason" /></th>
                </tr></thead>
                <tbody>
                    <c:forEach items="${waivers}" var="w">
                        <c:url value="${waiverBase}/interest-waivers" var="historyUrl">
                            <c:param name="waiverPage" value="${waiverPage}" /><c:param name="waiverId" value="${w.id}" />
                            <c:param name="date" value="${waiverForm.effectiveDate}" />
                        </c:url>
                        <tr>
                            <td><a class="resource-table-id-link" href="${historyUrl}#waiver-activity">IW-<c:out value="${w.sequence}" /></a></td>
                            <td><c:out value="${appTime:display(w.date)}" /></td>
                            <td><fmt:formatNumber value="${w.amount}" minFractionDigits="2" maxFractionDigits="2" /></td>
                            <td><c:choose>
                                <c:when test="${not empty w.reverses}"><spring:message code="prepayment.reversal" /></c:when>
                                <c:when test="${not empty w.reversedBy}"><span data-resource-status="REVERSED"><spring:message code="voucher.status.REVERSED" /></span></c:when>
                                <c:otherwise><spring:message code="prepayment.waiver" /></c:otherwise>
                            </c:choose></td>
                            <td><c:out value="${w.reason}" /></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty waivers}"><tr><td colspan="5"><spring:message code="prepayment.empty" /></td></tr></c:if>
                </tbody>
            </table>
        </div>
        <div data-table-pagination data-page="${waiverPage}" data-table-for="interest-waivers">
            <c:if test="${waiverPage > 0}"><a data-page-direction="previous" href="?waiverPage=${waiverPage-1}&amp;date=${waiverForm.effectiveDate}"><spring:message code="common.previous" /></a></c:if>
            <c:if test="${waiverHasNext}"><a data-page-direction="next" href="?waiverPage=${waiverPage+1}&amp;date=${waiverForm.effectiveDate}"><spring:message code="common.next" /></a></c:if>
        </div>
    </section>

    <c:if test="${not empty selectedWaiver}">
        <section class="erp-resource-section" id="waiver-activity">
            <h2><spring:message code="prepayment.waiverDetails" /></h2>
            <dl class="erp-detail-facts">
                <div><dt><spring:message code="voucher.reference" /></dt><dd>IW-<c:out value="${selectedWaiver.sequence}" /></dd></div>
                <div><dt><spring:message code="voucher.date" /></dt><dd><c:out value="${appTime:display(selectedWaiver.date)}" /></dd></div>
                <div><dt><spring:message code="prepayment.waiverAmount" /> (TZS)</dt><dd><fmt:formatNumber value="${selectedWaiver.amount}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
                <div><dt><spring:message code="prepayment.reason" /></dt><dd><c:out value="${selectedWaiver.reason}" /></dd></div>
            </dl>
        </section>
        <sec:authorize access="@access.has(principal,'LOAN_INTEREST_WAIVE') and @access.hasAny(principal,'LOAN_RECORDING_REVERSE','LOAN_REPAYMENTS_REVERSE')">
            <c:if test="${empty selectedWaiver.reverses and empty selectedWaiver.reversedBy and selectedWaiver.actor ne principal.memberId}">
                <form method="post" action="${waiverAction}" class="waiver-fields waiver-work">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <input type="hidden" name="requestKey" value="${waiverForm.requestKey}" />
                    <input type="hidden" name="reverses" value="${selectedWaiver.id}" />
                    <input type="hidden" name="waiverPage" value="${waiverPage}" />
                    <section class="erp-resource-section erp-form-wrap">
                        <h2><spring:message code="prepayment.reverse" /></h2>
                        <div class="waiver-fields">
                            <label class="waiver-field"><spring:message code="voucher.date" />
                                <input class="aws-control waiver-date" name="effectiveDate" type="date" value="${empty waiverForm.reverses ? waiverToday : waiverForm.effectiveDate}" min="${selectedWaiver.date}" max="${waiverToday}" required />
                            </label>
                            <label class="waiver-field"><spring:message code="prepayment.reason" />
                                <textarea class="aws-control" name="reason" rows="3" maxlength="500" required><c:if test="${not empty waiverForm.reverses}"><c:out value="${waiverForm.reason}" /></c:if></textarea>
                            </label>
                            <label class="accounting-servicing-confirm">
                                <input type="checkbox" name="confirmed" value="true" required />
                                <span><spring:message code="prepayment.confirmReversal" /></span>
                            </label>
                        </div>
                    </section>
                    <div class="accounting-actions waiver-actions"><button class="app-btn btn-launch" type="submit"><spring:message code="prepayment.reverse" /></button></div>
                </form>
            </c:if>
        </sec:authorize>
    </c:if>
    <c:if test="${not empty prepaymentError and (not empty waiverForm.reverses or not waiverSchedule.active)}">
        <p class="console-message" data-message-type="error" role="alert"><spring:message code="${prepaymentError}" /></p>
    </c:if>
</div>
<script src="<c:url value='/js/interest-waivers.js' />" defer></script>
<%@ include file="../fragments/footer.jspf" %>
