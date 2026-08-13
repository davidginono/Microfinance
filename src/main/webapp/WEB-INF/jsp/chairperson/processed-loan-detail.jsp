<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="chairperson.processedLoans" text="Processed Loans" /> / <c:out value="${loan.applicationId}" /></p>
    <h1 class="erp-page-title"><spring:message code="chairperson.processedLoanDetail" text="Reviewed Loan Application" /></h1>
</div>

<c:if test="${not empty loan.repaymentSummary or not empty loan.disbursementDate or not empty loan.disbursementReference}">
<section class="erp-panel mt-4 p-5">
    <h2 class="font-semibold text-slate-900"><spring:message code="chairperson.repaymentDisbursement" text="Repayment & Disbursement" /></h2>
    <div class="mt-3 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="loan.disbursementDate" text="Disbursement Date" /></div><div class="mt-1"><c:out value="${loan.disbursementDate}" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="loan.firstRepaymentDate" text="First Repayment Date" /></div><div class="mt-1"><c:out value="${loan.firstRepaymentDate}" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="loan.finalDueDate" text="Final Due Date" /></div><div class="mt-1"><c:out value="${loan.finalDueDate}" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="loan.installmentAmount" text="Installment Amount" /></div><div class="mt-1"><fmt:formatNumber value="${loan.installmentAmount}" maxFractionDigits="2" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="loan.disbursementReference" text="Disbursement Reference" /></div><div class="mt-1"><c:out value="${loan.disbursementReference}" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="loan.depositAmount" text="Deposit Amount" /></div><div class="mt-1"><fmt:formatNumber value="${loan.depositAmount}" maxFractionDigits="2" /></div></div>
        <div class="sm:col-span-2"><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="loan.disbursementNotes" text="Disbursement Notes" /></div><div class="mt-1"><c:out value="${loan.disbursementNotes}" /></div></div>
    </div>
</section>
</c:if>

<section class="erp-panel p-5 sm:p-6">
    <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="loan.applicationId" text="Loan Application ID" /></div><div class="mt-1 font-semibold"><c:out value="${loan.applicationId}" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="common.status" text="Status" /></div><div class="mt-1"><spring:message code="loan.status.${loan.status}" text="${fn:replace(loan.status, '_', ' ')}" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="common.applicant" text="Applicant" /></div><div class="mt-1"><c:out value="${loan.applicantName}" /></div><div class="text-xs text-slate-500"><c:out value="${loan.memberNo}" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="reports.loanProduct" text="Loan Product" /></div><div class="mt-1"><c:out value="${loan.productName}" /></div><div class="text-xs text-slate-500"><c:out value="${loan.productCode}" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="common.amount" text="Amount" /></div><div class="mt-1 font-semibold">TSh <fmt:formatNumber value="${loan.amount}" maxFractionDigits="2" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="loan.tenor.label" text="Tenor" /></div><div class="mt-1"><c:out value="${loan.tenorMonths}" /> <spring:message code="common.months" text="months" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="register.member.email" text="Email" /></div><div class="mt-1 break-all"><c:out value="${loan.email}" /></div></div>
        <div><div class="text-xs font-semibold uppercase text-slate-500"><spring:message code="register.member.phone" text="Phone" /></div><div class="mt-1"><c:out value="${loan.phone}" /></div></div>
    </div>
</section>

<div class="mt-4 grid gap-4 xl:grid-cols-2">
    <section class="erp-panel overflow-hidden"><div class="border-b border-slate-200 px-5 py-4"><h2 class="font-semibold text-slate-900"><spring:message code="review.applicationDetails" text="Application Details" /></h2></div><dl class="divide-y divide-slate-100 px-5">
        <c:forEach items="${loan.formFields}" var="field"><div class="grid gap-1 py-3 sm:grid-cols-2"><dt class="text-sm font-medium text-slate-600"><c:out value="${field.key}" /></dt><dd class="text-sm text-slate-900"><c:out value="${field.value}" /></dd></div></c:forEach>
        <c:if test="${empty loan.formFields}"><div class="py-5 text-sm text-slate-500"><spring:message code="common.noData" text="No data available." /></div></c:if>
    </dl></section>
    <section class="erp-panel overflow-hidden"><div class="border-b border-slate-200 px-5 py-4"><h2 class="font-semibold text-slate-900"><spring:message code="review.financialDetails" text="Financial Details" /></h2></div><dl class="divide-y divide-slate-100 px-5">
        <c:forEach items="${loan.financialFields}" var="field"><div class="grid gap-1 py-3 sm:grid-cols-2"><dt class="text-sm font-medium text-slate-600"><c:out value="${field.key}" /></dt><dd class="text-sm text-slate-900"><c:out value="${field.value}" /></dd></div></c:forEach>
        <c:if test="${empty loan.financialFields}"><div class="py-5 text-sm text-slate-500"><spring:message code="common.noData" text="No data available." /></div></c:if>
    </dl></section>
</div>

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

<%@ include file="../fragments/footer.jspf" %>
