<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>

<spring:message code="chairperson.processedLoans" text="Processed Loans" var="processedLoansLabel" />
<spring:message code="chairperson.processedLoanDetail" text="Reviewed Loan Application" var="processedLoanTitle" />
<sec:authorize access="@access.has(principal, 'LOAN_DOCUMENTS_EXPORT')" var="canExportProcessedLoan" />
<c:set var="reviewPanelBreadcrumb" value="${processedLoansLabel} > ${loan.applicationId}" />
<c:set var="reviewPanelTitle" value="${processedLoanTitle}" />
<c:set var="showLoanExportAction" value="${canExportProcessedLoan}" />
<c:set var="reviewDocumentLoanId" value="${loan.id}" />
<%@ include file="../fragments/staff-loan-detail-header.jspf" %>

<c:set var="processedLoanDetail" value="${true}" />
<c:set var="loanProgressItems" value="${loan.progressItems}" />
<c:choose>
    <c:when test="${fn:contains(loan.status, 'REJECTED') or loan.status eq 'REJECTED'}">
        <c:set var="reviewStatusBadgeClass" value="bg-rose-50 text-rose-700" />
    </c:when>
    <c:when test="${loan.status eq 'DISBURSED' or loan.status eq 'PAID'}">
        <c:set var="reviewStatusBadgeClass" value="bg-emerald-50 text-emerald-700" />
    </c:when>
    <c:when test="${loan.status eq 'DEFAULTED'}">
        <c:set var="reviewStatusBadgeClass" value="bg-rose-50 text-rose-700" />
    </c:when>
    <c:otherwise>
        <c:set var="reviewStatusBadgeClass" value="bg-blue-50 text-blue-700" />
    </c:otherwise>
</c:choose>
<%@ include file="../fragments/staff-loan-review-summary.jspf" %>

<section class="erp-panel mt-4 overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-4 py-3">
        <h2 class="text-sm font-semibold text-slate-900"><spring:message code="review.applicationDetails" text="Application Details" /></h2>
    </div>
    <dl class="divide-y divide-slate-100 px-4">
        <c:forEach items="${loan.formFields}" var="field">
            <div class="grid gap-1 py-3 sm:grid-cols-2">
                <dt class="text-sm font-semibold text-slate-600"><c:out value="${field.key}" /></dt>
                <dd class="text-sm text-slate-900"><c:out value="${field.value}" /></dd>
            </div>
        </c:forEach>
        <c:if test="${empty loan.formFields}">
            <div class="py-5 text-sm text-slate-500"><spring:message code="common.noData" text="No data available." /></div>
        </c:if>
    </dl>
</section>

<c:set var="financialFieldSections" value="${loan.financialFieldSections}" />
<%@ include file="../fragments/financial-field-sections.jspf" %>

<c:set var="repaymentSummary" value="${loan.repaymentSummary}" />
<c:set var="repaymentSummaryEstimated" value="${false}" />
<%@ include file="../fragments/staff-repayment-summary.jspf" %>

<section class="erp-panel mt-4 overflow-hidden" data-aws-table-region>
    <div class="border-b border-slate-200 px-5 py-4"><h2 class="font-semibold text-slate-900"><spring:message code="review.guarantors" text="Guarantors" /></h2></div>
    <div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="common.name" text="Name" /></th><th><spring:message code="member.memberNo" text="Member No" /></th><th><spring:message code="common.status" text="Status" /></th><th><spring:message code="common.amount" text="Amount" /></th><th><spring:message code="review.reason" text="Reason" /></th></tr></thead><tbody>
        <c:forEach items="${loan.guarantors}" var="guarantor"><tr><td><c:out value="${guarantor.name}" /></td><td><c:out value="${guarantor.memberNo}" /></td><td><c:out value="${guarantor.status}" /></td><td><fmt:formatNumber value="${guarantor.committedAmount}" maxFractionDigits="2" /></td><td><c:out value="${guarantor.reason}" /></td></tr></c:forEach>
        <c:if test="${empty loan.guarantors}"><tr><td colspan="5" class="py-5 text-center text-sm text-slate-500"><spring:message code="review.noGuarantors" text="No guarantor records." /></td></tr></c:if>
    </tbody></table></div>
</section>

<sec:authorize access="@access.has(principal, 'LOAN_DOCUMENTS_VIEW')">
<section class="erp-panel mt-4 overflow-hidden">
    <div class="border-b border-slate-200 px-5 py-4"><h2 class="font-semibold text-slate-900"><spring:message code="loan.attachments" text="Attachments" /></h2></div>
    <div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="common.file" text="File" /></th><th><spring:message code="common.size" text="Size" /></th><th><spring:message code="common.uploaded" text="Uploaded" /></th><th></th></tr></thead><tbody>
        <c:forEach items="${loan.attachments}" var="attachment"><tr><td><c:out value="${not empty attachment.originalName ? attachment.originalName : attachment.name}" /></td><td><c:out value="${attachment.sizeLabel}" /></td><td>${fn:replace(fn:substring(attachment.uploadedAt, 0, 16), 'T', ' ')}</td><td><div class="flex flex-wrap gap-2"><a class="app-btn btn-neutral" target="_blank" rel="noopener" href="${pageContext.request.contextPath}/documents/loan-applications/${loan.id}/attachments/${attachment.id}/view"><spring:message code="common.view" text="View" /></a><a class="app-btn btn-primary" data-download-action="true" href="${pageContext.request.contextPath}/documents/loan-applications/${loan.id}/attachments/${attachment.id}"><spring:message code="common.download" text="Download" /></a></div></td></tr></c:forEach>
        <c:if test="${empty loan.attachments}"><tr><td colspan="4" class="py-5 text-center text-sm text-slate-500"><spring:message code="loan.noAttachments" text="No attachments submitted." /></td></tr></c:if>
    </tbody></table></div>
</section>
</sec:authorize>

<section class="erp-panel mt-4 overflow-hidden">
    <div class="border-b border-slate-200 px-5 py-4"><h2 class="font-semibold text-slate-900"><spring:message code="chairperson.staffDecisions" text="Staff Decision History" /></h2></div>
    <div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="common.stage" text="Stage" /></th><th><spring:message code="common.decision" text="Decision" /></th><th><spring:message code="common.reviewer" text="Reviewer" /></th><th><spring:message code="review.reason" text="Comment / Reason" /></th><th><spring:message code="loan.date" text="Date" /></th></tr></thead><tbody>
        <c:forEach items="${loan.decisions}" var="decision"><tr><td><c:out value="${decision.stageLabel}" /></td><td><span class="font-semibold"><spring:message code="chairperson.decision.${decision.decision}" text="${decision.decision}" /></span></td><td><c:out value="${decision.reviewerName}" /><div class="text-xs text-slate-500"><c:out value="${decision.reviewerNumber}" /></div></td><td><c:out value="${decision.note}" /></td><td>${fn:replace(fn:substring(decision.decidedAt, 0, 16), 'T', ' ')}</td></tr></c:forEach>
        <c:if test="${empty loan.decisions}"><tr><td colspan="5" class="py-5 text-center text-sm text-slate-500"><spring:message code="chairperson.awaitingDecision" text="Awaiting staff decision" /></td></tr></c:if>
    </tbody></table></div>
</section>

<%@ include file="../fragments/loan-export-modal.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
