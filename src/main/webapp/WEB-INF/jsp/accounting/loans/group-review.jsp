<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="support.jspf" %>
<%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
<nav class="erp-page-path"><a href="<c:url value='/finance/loan-records' />"><spring:message code="recording.register.LOAN" /></a><span>›</span><span aria-current="page"><spring:message code="loanGroups.review" /></span></nav>
<div class="erp-page-header" data-aws-page-header><h1 class="erp-page-title"><spring:message code="loanGroups.review" /></h1></div>
<div class="accounting-workspace">
<c:if test="${not empty groupError}"><div class="accounting-alert error" role="alert"><spring:message code="${groupError}" /></div></c:if>
<section class="accounting-section"><dl class="erp-detail-facts">
<div><dt><spring:message code="loan.loanId" /></dt><dd><c:out value="${reviewLoan.number}" /></dd></div>
<div><dt><spring:message code="recording.borrower" /></dt><dd><c:out value="${reviewLoan.clientName}" /></dd></div>
<div><dt><spring:message code="voucher.amount" /> (TZS)</dt><dd><fmt:formatNumber value="${reviewLoan.amount}" minFractionDigits="2" maxFractionDigits="2" /></dd></div>
<div><dt><spring:message code="voucher.status" /></dt><dd><spring:message code="recording.status.${reviewLoan.status}" text="${reviewLoan.status}" /></dd></div>
</dl></section>
<c:if test="${reviewLoan.status!='SUBMITTED' and reviewLoan.status!='READY_FOR_DISBURSEMENT' and reviewLoan.status!='DISBURSED' and reviewLoan.status!='PAID' and reviewLoan.status!='PAR' and reviewLoan.status!='DEFAULTED' and reviewLoan.status!='WRITTEN_OFF' and reviewLoan.status!='DISBURSEMENT_REVERSED' and reviewLoan.status!='WITHDRAWN'}">
<sec:authorize access="@access.has(principal,'LOAN_RECORDING_CREATE')"><form method="post" action="<c:url value='/finance/loan-records/${loanId}/submit-for-review' />" class="accounting-actions">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="${requestKey}" /><button class="app-btn btn-primary"><spring:message code="recording.submitReview" /></button></form></sec:authorize>
</c:if>
<c:if test="${not empty groupReview.steps}">
<section class="erp-table-wrap" data-aws-table-region><div class="app-table-titlebar"><h2><spring:message code="loanGroups.steps" /></h2></div><div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="evidence-history"><thead><tr><th>#</th><th><spring:message code="loanGroups.group" /></th><th><spring:message code="loanGroups.count" /></th></tr></thead><tbody>
<c:forEach items="${groupReview.steps}" var="s" varStatus="i"><tr ${i.index==groupReview.step?'aria-current="step"':''}><td>${i.count}</td><td><spring:message code="userGroup.${s.group}" /></td><td>${s.approvals}</td></tr></c:forEach>
</tbody></table></div></section>
</c:if>
<c:if test="${groupReview.canApprove or groupReview.canReject}">
<form method="post" class="accounting-section" data-workspace-submit="recover">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" /><input type="hidden" name="requestKey" value="${requestKey}" /><input type="hidden" name="approvalRequest" value="${groupReview.request.id}" />
<div class="accounting-form-grid"><label><spring:message code="voucher.reason" /><input class="aws-control" name="reason" maxlength="500" required value="<c:out value='${reviewReason}' />" /></label><label><spring:message code="voucher.evidence" /><input class="aws-control" name="evidence" maxlength="500" required value="<c:out value='${reviewEvidence}' />" /></label></div>
<div class="accounting-actions"><c:if test="${groupReview.canReject}"><button class="app-btn btn-neutral" name="decision" value="REJECT"><spring:message code="loanGroups.reject" /></button></c:if><c:if test="${groupReview.canApprove}"><button class="app-btn btn-primary" name="decision" value="APPROVE"><spring:message code="loanGroups.approve" /></button></c:if></div>
</form>
</c:if>
<section class="erp-table-wrap" data-aws-table-region><div class="app-table-titlebar"><h2><spring:message code="recording.history" /></h2></div><div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="evidence-history"><thead><tr><th><spring:message code="voucher.date" /></th><th><spring:message code="recording.recordedBy" /></th><th><spring:message code="loanGroups.decision" /></th><th><spring:message code="voucher.reason" /></th><th><spring:message code="voucher.evidence" /></th></tr></thead><tbody>
<c:forEach items="${groupReview.decisions}" var="d"><tr><td>${appTime:display(d.at)}</td><td><c:out value="${d.name}" /></td><td><spring:message code="loanGroups.${d.decision}" /></td><td><c:out value="${d.reason}" /></td><td><c:out value="${d.evidence}" /></td></tr></c:forEach>
<c:if test="${empty groupReview.decisions}"><tr><td colspan="5"><spring:message code="recording.statement.empty" /></td></tr></c:if>
</tbody></table></div></section>
</div><%@ include file="../../fragments/footer.jspf" %>
