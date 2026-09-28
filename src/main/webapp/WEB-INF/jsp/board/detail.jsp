<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>
<%@ include file="../fragments/confirm-modal.jspf" %>

<c:set var="showLoanExportAction" value="${true}" />
<c:set var="reviewDocumentLoanId" value="${app.id}" />
<%@ include file="../fragments/staff-loan-detail-header.jspf" %>
<c:set var="reviewStatusBadgeClass" value="${boardStatusBadgeClass}" />
<%@ include file="../fragments/staff-loan-review-summary.jspf" %>

<div class="mt-4 space-y-4">
    <c:if test="${not empty managerReason}">
        <div class="border border-sacco-brown/30 bg-[#f7efe9] px-4 py-3 text-sm text-sacco-brown">
            <strong><spring:message code="review.managerReason" text="Manager Reason:" /></strong> ${managerReason}
        </div>
    </c:if>
    <%@ include file="../fragments/previous-approved-reviews.jspf" %>
    <div class="loan-view-summary-card px-5 py-5">
        <div>
            <p class="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500"><spring:message code="review.applicantDetails" text="Applicant Details" /></p>
            <p class="mt-1 text-sm text-slate-600">Key applicant information for ${reviewRoleLabelLower} review before making a decision.</p>
        </div>
        <div class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <div class="applicant-info-card">
                <div class="applicant-info-label"><spring:message code="board.myReview" text="My Review" /></div>
                <div class="applicant-info-value">${myReview.decision}</div>
            </div>
        </div>
    </div>
</div>

<%@ include file="../fragments/applicant-payment-details.jspf" %>

<div class="loan-view-summary-card mt-5 px-5 py-5"
     <c:if test="${activeApplicantLoansEnabled}">data-staff-active-loans-section="true" data-active-loans-url="${reviewBasePath}/loan-applications/${app.id}/applicant-active-loans"</c:if>>
    <div>
        <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
                <p class="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500"><spring:message code="review.applicantActiveLoans" text="Applicant Active Loans" /></p>
                <p class="mt-2 text-base text-slate-600"><spring:message code="review.applicantActiveLoansHelp" text="Other disbursed loans that are still active for this applicant at the time of review." /></p>
            </div>
            <c:if test="${activeApplicantLoansEnabled}">
                <button type="button"
                        class="app-icon-button btn-neutral shrink-0"
                        data-staff-active-loans-refresh
                        data-loading-label="Refreshing active loans..."
                        data-error-message="Applicant active loans could not load. Please retry again later."
                        aria-label="Refresh applicant active loans"
                        title="Refresh applicant active loans">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <path d="M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5" />
                        <path d="M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5" />
                    </svg>
                </button>
            </c:if>
        </div>
        <div class="mt-3 flex flex-wrap items-center gap-2">
            <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700" data-staff-active-loans-count-label>
                ${activeApplicantLoanCount} active loan<c:if test="${activeApplicantLoanCount ne 1}">s</c:if>
            </span>
            <span class="rounded-full bg-sacco-blue/10 px-3 py-1 text-xs font-semibold text-sacco-blue" data-staff-active-loans-total-label>
                Total exposure ${activeApplicantLoanTotalAmount}
            </span>
        </div>
    </div>
    <div class="mt-5 grid gap-3 sm:grid-cols-2">
        <div class="applicant-info-card">
            <div class="applicant-info-label"><spring:message code="analytics.activeLoans" text="Active Loans" /></div>
            <div class="applicant-info-value" data-staff-active-loans-count>${activeApplicantLoanCount}</div>
        </div>
        <div class="applicant-info-card">
            <div class="applicant-info-label"><spring:message code="analytics.totalActiveAmount" text="Total Active Amount" /></div>
            <div class="applicant-info-value" data-staff-active-loans-total>${activeApplicantLoanTotalAmount}</div>
        </div>
    </div>
    <div class="mt-5 erp-table-wrap" data-aws-table-region data-loading-label="Loading active loans...">
        <div class="erp-table-scroll">
            <table class="erp-table">
                <thead>
                <tr>
                    <th class="px-3 py-2 text-left"><spring:message code="loan.single" text="Loan" /></th>
                    <th class="px-3 py-2 text-left"><spring:message code="common.type" text="Type" /></th>
                    <th class="px-3 py-2 text-left"><spring:message code="common.amount" text="Amount" /></th>
                    <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.table.outstandingBalance" text="Outstanding Balance" /></th>
                    <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.installmentAmount" text="Installment" /></th>
                    <th class="px-3 py-2 text-left"><spring:message code="analytics.disbursed" text="Disbursed" /></th>
                    <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.finalDueDate" text="Final Due" /></th>
                    <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.schedule" text="Schedule" /></th>
                </tr>
                </thead>
                <tbody class="divide-y divide-slate-100" data-staff-active-loans-body>
                <c:choose>
                    <c:when test="${not empty activeApplicantLoans}">
                        <c:forEach items="${activeApplicantLoans}" var="loan">
                            <c:set var="loanScheduleId" value="${not empty loan.scheduleLoanId ? loan.scheduleLoanId : loan.loanId}" />
                            <tr>
                                <td class="px-3 py-2">
                                    <div class="font-medium text-slate-900">${loan.shortId}</div>
                                </td>
                                <td class="px-3 py-2 text-slate-700">${loan.loanTypeLabel}</td>
                                <td class="px-3 py-2 font-medium text-slate-900">${loan.amount}</td>
                                <td class="px-3 py-2 font-medium text-slate-900" data-active-loan-outstanding="${loan.id}">${loan.outstandingBalance}</td>
                                <td class="px-3 py-2">
                                    <div class="font-medium text-slate-900">${loan.installmentAmount}</div>
                                    <div class="mt-1 text-xs uppercase tracking-wide text-slate-500">${loan.repaymentFrequency}</div>
                                </td>
                                <td class="px-3 py-2 text-slate-700">${loan.disbursedAt}</td>
                                <td class="px-3 py-2">
                                    <div class="font-medium text-slate-900">${loan.finalDueDate}</div>
                                    <c:if test="${not empty loan.countdown}">
                                        <div class="mt-1 text-xs text-slate-500">${loan.countdown}</div>
                                    </c:if>
                                </td>
                                <td class="px-3 py-2">
                                    <c:choose>
                                        <c:when test="${loan.scheduleAvailable and not empty loanScheduleId and loanScheduleId ne '-'}">
                                            <button type="button"
                                                    class="app-btn btn-neutral inline-flex justify-center whitespace-nowrap px-3 py-2 text-sm"
                                                    data-open-repayment-schedule
                                                    data-schedule-url="${reviewBasePath}/loan-applications/${app.id}/applicant-active-loans/${loanScheduleId}/repayment-schedule"
                                                    data-schedule-title="Repayment Schedule ${loanScheduleId}">
                                                <spring:message code="loan.repayment.viewSchedule" text="View Schedule" />
                                            </button>
                                        </c:when>
                                        <c:otherwise>
                                            <span class="text-sm font-semibold text-slate-500">-</span>
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                    </c:when>
                    <c:otherwise>
                        <tr data-staff-active-loans-empty>
                            <td colspan="8" class="px-3 py-4 text-center text-sm text-slate-600">
                                <spring:message code="review.noActiveApplicantLoans" text="This applicant has no other active disbursed loans at the moment." />
                            </td>
                        </tr>
                    </c:otherwise>
                </c:choose>
                </tbody>
            </table>
            </div>
            </div>
    </div>
<%@ include file="../fragments/staff-active-loans-hydration.jspf" %>
<%@ include file="../fragments/repayment-schedule-hydration.jspf" %>

<div class="loan-view-summary-card mt-5 px-5 py-5">
    <div>
        <p class="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500">${reviewAssessorTitle}</p>
        <p class="mt-2 text-base text-slate-600">${reviewAssessorDescription}</p>
    </div>
<div class="mt-5 erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
        <div class="erp-table-scroll">
        <table class="erp-table">
            <thead>
            <tr>
                <th class="px-3 py-2 text-left"><spring:message code="review.assessor" text="Assessor" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="Member.memberNo" text="Client No" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="review.decision" text="Decision" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="review.comment" text="Comment" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="review.submitted" text="Submitted" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="review.signature" text="Signature" /></th>
            </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
            <c:forEach items="${boardAssessors}" var="assessor">
                <tr>
                    <td class="px-3 py-2">
                        <div class="font-medium text-slate-900">${assessor.name}</div>
                        <c:if test="${assessor.isMine}">
                            <div class="mt-1 text-xs uppercase tracking-wide text-sacco-blue"><spring:message code="board.yourReview" text="Your review" /></div>
                        </c:if>
                    </td>
                    <td class="px-3 py-2 text-slate-700">${assessor.memberNo}</td>
                    <td class="px-3 py-2 text-slate-700">${assessor.decision}</td>
                    <td class="px-3 py-2 text-slate-700">
                        <c:choose>
                            <c:when test="${not empty assessor.comment}">${assessor.comment}</c:when>
                            <c:otherwise>-</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2 text-slate-700">
                        <c:choose>
                            <c:when test="${not empty assessor.decidedAt}"><c:out value="${appTime:format(assessor.decidedAt)}" /></c:when>
                            <c:otherwise>-</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2 text-slate-700">
                        <c:choose>
                            <c:when test="${not empty assessor.signatureText}">
                                <div class="aws-signature-text text-xl text-slate-900">${assessor.signatureText}</div>
                                <c:if test="${not empty assessor.signatureVerifiedAt}">
                                    <div class="mt-1 text-xs text-slate-500"><c:out value="${appTime:format(assessor.signatureVerifiedAt)}" /></div>
                                </c:if>
                            </c:when>
                            <c:otherwise>-</c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
</div>
                        </div>
    </div>

<%@ include file="../fragments/financial-field-sections.jspf" %>

<%@ include file="../fragments/staff-repayment-summary.jspf" %>

<c:if test="${not empty disbursementProofAttachments or app.status eq 'DISBURSED' or app.status eq 'DEFAULTED' or app.status eq 'PAID'}">
    <div class="erp-table-wrap mt-5" data-aws-table-region data-loading-label="Loading results...">
        <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="admin.settings.disbursementProof" text="Disbursement Proof" /></h5>
        <div class="erp-table-scroll">
        <table class="erp-table min-w-full divide-y divide-slate-200 text-sm">
            <thead class="bg-slate-50">
            <tr>
                <th class="px-3 py-2 text-left"><spring:message code="common.file" text="File" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="common.size" text="Size" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="common.uploaded" text="Uploaded" /></th>
                <th class="px-3 py-2 text-left"></th>
            </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
            <c:forEach items="${disbursementProofAttachments}" var="file">
                <tr>
                    <td class="px-3 py-2">${file.originalName}</td>
                    <td class="px-3 py-2">${file.sizeLabel}</td>
                    <td class="px-3 py-2"><c:out value="${appTime:format(file.uploadedAt)}" /></td>
                    <td class="px-3 py-2">
                        <div class="flex flex-wrap gap-2">
                            <a href="/documents/loan-applications/${app.id}/attachments/${file.id}/view" target="_blank" rel="noopener" class="app-btn btn-neutral"><spring:message code="common.view" text="View" /></a>
                            <a href="/documents/loan-applications/${app.id}/attachments/${file.id}" class="app-btn btn-primary" data-download-action="true"><spring:message code="common.download" text="Download" /></a>
</div>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty disbursementProofAttachments}">
                <tr><td colspan="4" class="px-3 py-3 text-slate-500"><spring:message code="loan.disbursementProof.empty" text="No disbursement proof uploaded yet." /></td></tr>
            </c:if>
            </tbody>
        </table>
                        </div>
                        </div>
</c:if>

<div class="erp-table-wrap mt-5" data-aws-table-region data-loading-label="Loading results...">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.applicationAttachments" text="Application Attachments" /></h5>
    <div class="erp-table-scroll">
    <table class="erp-table min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
        <tr>
            <th class="px-3 py-2 text-left"><spring:message code="common.file" text="File" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="common.size" text="Size" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="common.uploaded" text="Uploaded" /></th>
            <th class="px-3 py-2 text-left"></th>
        </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
        <c:forEach items="${attachments}" var="file">
            <tr>
                <td class="px-3 py-2">${file.originalName}</td>
                <td class="px-3 py-2">${file.sizeLabel}</td>
                <td class="px-3 py-2"><c:out value="${appTime:format(file.uploadedAt)}" /></td>
                <td class="px-3 py-2"><a href="/documents/loan-applications/${app.id}/attachments/${file.id}" class="app-btn btn-primary" data-download-action="true"><spring:message code="common.download" text="Download" /></a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty attachments}">
            <tr><td colspan="4" class="px-3 py-3 text-slate-500"><spring:message code="loan.attachments.empty" text="No attachments uploaded." /></td></tr>
        </c:if>
        </tbody>
    </table>
                    </div>
</div>

<div class="erp-table-wrap mt-5" data-aws-table-region data-loading-label="Loading results...">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.guarantors" text="Guarantors" /></h5>
    <div class="erp-table-scroll">
    <table class="erp-table min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
        <tr>
            <th class="px-3 py-2 text-left"><spring:message code="loan.guarantor" text="Guarantor" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="Member.memberNo" text="Client No" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="common.status" text="Status" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="loan.date" text="Date" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="financial.status" text="Financial Status" /></th>
        </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
        <c:forEach items="${guarantorRequests}" var="req">
            <tr>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty guarantorNames[req.guarantorMemberId]}">${guarantorNames[req.guarantorMemberId]}</c:when>
                        <c:when test="${not empty req.externalFullName}">${req.externalFullName}</c:when>
                        <c:when test="${not empty req.externalMemberNo}">${req.externalMemberNo}</c:when>
                        <c:when test="${not empty req.guarantorMemberId}">#${fn:substring(req.guarantorMemberId, 0, 8)}</c:when>
                        <c:otherwise>Guarantor</c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty guarantorMembersById[req.guarantorMemberId] and not empty guarantorMembersById[req.guarantorMemberId].memberNo}">${guarantorMembersById[req.guarantorMemberId].memberNo}</c:when>
                        <c:when test="${not empty req.externalMemberNo}">${req.externalMemberNo}</c:when>
                        <c:otherwise>-</c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2">${req.status}</td>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty req.decidedAt}"><c:out value="${appTime:format(req.decidedAt)}" /></c:when>
                        <c:otherwise><c:out value="${appTime:format(req.createdAt)}" /></c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2">
                    <c:if test="${not empty req.guarantorMemberId}">
                    <button type="button"
                            class="app-btn btn-neutral guarantor-financial-trigger"
                            data-url="${reviewBasePath}/loan-applications/${app.id}/guarantors/${req.guarantorMemberId}/financial-status">
                        <span class="guarantor-financial-spinner hidden" data-financial-spinner></span>
                        <span data-financial-label><spring:message code="financial.loadStatus" text="Load Status" /></span>
                    </button>
                    <div class="guarantor-financial-result hidden">
                        <div class="guarantor-financial-result-row">
                            <span class="guarantor-financial-result-label"><spring:message code="financial.savings" text="Disposable Income" /></span>
                            <span class="guarantor-financial-result-value" data-financial-savings>-</span>
            </div>
                        <div class="guarantor-financial-result-row">
                            <span class="guarantor-financial-result-label"><spring:message code="financial.shares" text="Risk History" /></span>
                            <span class="guarantor-financial-result-value" data-financial-shares>-</span>
            </div>
                        <div class="guarantor-financial-result-note" data-financial-note></div>
                            </div>
                    </c:if>
                    <c:if test="${empty req.guarantorMemberId}">
                        <span class="text-slate-400">-</span>
                    </c:if>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty guarantorRequests}">
            <tr><td colspan="5" class="px-3 py-3 text-slate-500"><spring:message code="loan.guarantor.empty" text="No guarantors selected yet." /></td></tr>
        </c:if>
        </tbody>
    </table>
                        </div>
                            </div>

<c:choose>
    <c:when test="${myReview.decision eq 'PENDING' and app.status eq reviewAwaitingStatus}">
        <form action="${reviewBasePath}/loan-applications/${app.id}/decision" method="post" class="loan-view-summary-card mt-5 space-y-4 px-5 py-5" data-board-decision-form="true">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div>
                <p class="text-xs font-semibold uppercase tracking-[0.2em] text-slate-500">${reviewDecisionLabel}</p>
                <p class="mt-1 text-sm text-slate-600"><spring:message code="review.commentHelp" text="Comments are optional for approval and required for rejection." /></p>
                        </div>
            <div>
                <label class="mb-2 block text-sm font-medium text-slate-700"><spring:message code="review.comment" text="Comment" /></label>
                <textarea class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" name="comment"></textarea>
                    </div>
            <c:choose>
                <c:when test="${reviewApprovalOtpEnabled}">
                    <div class="border border-slate-200 bg-slate-50 px-4 py-4">
                        <div class="flex flex-wrap items-center justify-between gap-3">
                            <div>
                                <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="otp.verification" text="OTP Verification" /></div>
                                <p class="mt-2 text-sm text-slate-600"><spring:message code="otp.approvalHelp" arguments="${reviewRoleLabelLower}" text="Request a one-time code to confirm this review decision." /></p>
                    </div>
                             <button type="button"
                                     class="app-btn btn-primary otp-request-button board-otp-request inline-flex items-center justify-center gap-2"
                                     data-request-url="${reviewBasePath}/loan-applications/${app.id}/request-signature-otp"
                                     data-verify-url="${reviewBasePath}/loan-applications/${app.id}/verify-signature-otp">
                                <span class="otp-button-spinner hidden"></span>
                                <span class="otp-button-label"><spring:message code="loan.otp.sendCode" text="Send OTP Code" /></span>
                            </button>
            </div>
                        <div class="board-otp-feedback mt-3 hidden border px-4 py-3 text-sm"></div>
                        <div class="mt-3">
                            <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="loan.otp.code" text="OTP Code" /></label>
                            <input type="text"
                                   name="boardSignatureOtpCode"
                                   inputmode="numeric"
                                   maxlength="6"
                                   autocomplete="one-time-code"
                                   data-otp-hidden="true" data-otp-label="<spring:message code='loan.otp.roleCodeLabel' arguments='${reviewRoleLabel}' text='${reviewRoleLabel} OTP code' />"
                                   class="w-full border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                                   placeholder="123456"
                                   ${reviewApprovalOtpEnabled ? 'required' : ''} />
                            <p class="mt-2 text-sm text-slate-500"><spring:message code="loan.otp.codeHelp" text="Enter the 6-digit code sent to your email before confirming your decision." /></p>
                            <div class="board-otp-live-status mt-3 hidden items-center gap-2 border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                                <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
                                <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                    <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                                </svg>
                                <span data-otp-text><spring:message code="loan.otp.checking" text="Checking code..." /></span>
                            </div>
                        </div>
                    </div>
                </c:when>
                <c:otherwise>
                    <div class="border border-slate-200 bg-slate-50 px-4 py-4">
                        <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="otp.verification" text="OTP Verification" /></div>
                        <p class="mt-2 text-sm text-slate-600"><spring:message code="otp.approvalDisabledHelp" arguments="${reviewRoleLabelLower}" text="OTP confirmation is disabled for this approval." /></p>
                    </div>
                </c:otherwise>
            </c:choose>
            <div class="grid gap-3 sm:grid-cols-2 loan-final-action-row">
                <button type="submit" name="decision" value="APPROVED" class="app-btn btn-primary board-approve-submit ${reviewApprovalOtpEnabled ? 'action-button-disabled' : ''}" ${reviewApprovalOtpEnabled ? 'disabled' : ''}><spring:message code="review.approveReview" text="Approve Review" /></button>
                <button type="submit" name="decision" value="REJECTED" class="app-btn btn-reject board-reject-submit ${reviewApprovalOtpEnabled ? 'action-button-disabled' : ''}" ${reviewApprovalOtpEnabled ? 'disabled' : ''}><spring:message code="review.rejectReview" text="Reject Review" /></button>
            </div>
        </form>
    </c:when>
    <c:otherwise>
    </c:otherwise>
</c:choose>

<script>
    (() => {
        const csrfToken = "${_csrf.token}";
        const otpMessages = {
            send: "<spring:message code='loan.otp.sendCode' text='Send OTP Code' javaScriptEscape='true' />",
            sending: "<spring:message code='loan.otp.sending' text='Sending...' javaScriptEscape='true' />",
            sent: "<spring:message code='loan.otp.sent' text='OTP Sent' javaScriptEscape='true' />",
            verifying: "<spring:message code='loan.otp.verifying' text='Verifying code...' javaScriptEscape='true' />",
            invalid: "<spring:message code='loan.otp.invalid' text='The OTP code is invalid.' javaScriptEscape='true' />",
            verified: "<spring:message code='loan.otp.codeVerified' text='OTP code verified.' javaScriptEscape='true' />",
            enterDigits: "<spring:message code='loan.otp.enterAllDigits' text='Enter all 6 digits to verify the code.' javaScriptEscape='true' />",
            unableToSend: "<spring:message code='loan.otp.unableToSend' text='Unable to send the OTP code right now.' javaScriptEscape='true' />",
            approvalCodeSent: "<spring:message code='loan.otp.approvalCodeSent' text='We sent a review decision code to your email.' javaScriptEscape='true' />",
            requestNotConfigured: "<spring:message code='loan.otp.roleRequestNotConfigured' arguments='${reviewRoleLabel}' text='OTP request is not configured.' javaScriptEscape='true' />"
        };
        const confirmMessages = {
            approvalEyebrow: "<spring:message code='review.confirm.approvalEyebrow' text='Confirm Approval' javaScriptEscape='true' />",
            approvalTitle: "<spring:message code='review.confirm.approvalTitle' arguments='${reviewRoleLabel}' text='Approve Review' javaScriptEscape='true' />",
            approvalMessage: "<spring:message code='review.confirm.approvalMessage' arguments='${reviewRoleLabelLower}' text='Approve this loan application review?' javaScriptEscape='true' />",
            approvalProceed: "<spring:message code='review.approveReview' text='Approve Review' javaScriptEscape='true' />",
            rejectionEyebrow: "<spring:message code='review.confirm.rejectionEyebrow' text='Confirm Rejection' javaScriptEscape='true' />",
            rejectionTitle: "<spring:message code='review.confirm.rejectionTitle' arguments='${reviewRoleLabel}' text='Reject Review' javaScriptEscape='true' />",
            rejectionMessage: "<spring:message code='review.confirm.rejectionMessage' arguments='${reviewRoleLabelLower}' text='Reject this loan application review?' javaScriptEscape='true' />",
            rejectionProceed: "<spring:message code='review.rejectReview' text='Reject Review' javaScriptEscape='true' />",
            rejectionCommentRequired: "<spring:message code='review.commentRequiredForRejection' text='Add a comment before rejecting this review.' javaScriptEscape='true' />"
        };

        function showOtpFeedback(element, type, text) {
            if (!element) {
                return;
            }
            element.textContent = text;
            element.classList.remove("hidden", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-rose-200", "bg-rose-50", "text-rose-700");
            if (type === "success") {
                element.classList.add("border-emerald-200", "bg-emerald-50", "text-emerald-700");
            } else {
                element.classList.add("border-rose-200", "bg-rose-50", "text-rose-700");
            }
        }

        function setOtpButtonState(button, state, idleLabel, loadingLabel, sentLabel) {
            if (!button) {
                return;
            }
            const spinner = button.querySelector(".otp-button-spinner");
            const label = button.querySelector(".otp-button-label");
            const loading = state === "loading";
            const sent = state === "sent";
            if (spinner) {
                spinner.classList.toggle("hidden", !loading);
            }
            if (label) {
                label.textContent = loading ? loadingLabel : (sent ? sentLabel : idleLabel);
            }
            button.disabled = loading || sent;
            button.classList.toggle("is-loading", loading);
            button.classList.toggle("is-sent", sent);
        }

        function bindOtpLiveStatus(input, statusBox, proceedButtons, verifyUrl) {
            if (!input || !statusBox) {
                return {
                    markRequested: function () {},
                    reset: function () {}
                };
            }
            const spinner = statusBox.querySelector("[data-otp-spinner]");
            const tick = statusBox.querySelector("[data-otp-tick]");
            const text = statusBox.querySelector("[data-otp-text]");
            let otpRequested = Boolean((input.value || "").trim());
            let verifiedCode = "";
            let verificationTimer = null;
            let verificationRun = 0;

            function clearVerification() {
                if (verificationTimer) {
                    window.clearTimeout(verificationTimer);
                    verificationTimer = null;
                }
            }

            function setProceedEnabled(enabled) {
                proceedButtons.forEach((button) => {
                    button.disabled = !enabled;
                    button.classList.toggle("action-button-disabled", !enabled);
                });
            }

            function setStatus(state, message) {
                statusBox.classList.remove("hidden", "border-slate-200", "bg-white", "text-slate-600", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-rose-200", "bg-rose-50", "text-rose-700");
                statusBox.classList.add("flex");
                spinner.classList.toggle("hidden", state !== "checking");
                tick.classList.toggle("hidden", state !== "valid");
                if (state === "valid") {
                    statusBox.classList.add("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                    tick.classList.remove("otp-checkmark-pop");
                    void tick.offsetWidth;
                    tick.classList.add("otp-checkmark-pop");
                } else if (state === "invalid") {
                    statusBox.classList.add("border-rose-200", "bg-rose-50", "text-rose-700");
                } else {
                    statusBox.classList.add("border-slate-200", "bg-white", "text-slate-600");
                }
                text.textContent = message;
            }

            async function verifyCode(code, runId) {
                setStatus("checking", otpMessages.verifying);
                try {
                    const response = await fetch(verifyUrl, {
                        method: "POST",
                        headers: {"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","Accept":"application/json","X-CSRF-TOKEN": csrfToken
                        },
                        body: new URLSearchParams({"otpCode": code
                        }),
                        credentials: "same-origin"
                    });
                    const payload = await response.json().catch(() => ({}));
                    if (runId !== verificationRun || input.value !== code) {
                        return;
                    }
                    if (!response.ok || payload.valid === false) {
                        throw new Error(payload.message || otpMessages.invalid);
                    }
                    verifiedCode = code;
                    input.setCustomValidity("");
                    setProceedEnabled(true);
                    setStatus("valid", payload.message || otpMessages.verified);
                } catch (error) {
                    if (runId !== verificationRun || input.value !== code) {
                        return;
                    }
                    verifiedCode = "";
                    input.setCustomValidity(error.message || otpMessages.invalid);
                    setProceedEnabled(false);
                    setStatus("invalid", error.message || otpMessages.invalid);
                }
            }

            function render() {
                input.value = (input.value || "").replace(/\D/g, "").slice(0, 6);
                const code = input.value;
                const ready = /^\d{6}$/.test(code);
                clearVerification();
                verificationRun += 1;
                input.setCustomValidity("");
                if (!otpRequested || !code) {
                    verifiedCode = "";
                    setProceedEnabled(false);
                    statusBox.classList.add("hidden");
                    statusBox.classList.remove("flex");
                    return;
                }
                if (!ready) {
                    verifiedCode = "";
                    setProceedEnabled(false);
                    setStatus("idle", otpMessages.enterDigits);
                    return;
                }
                if (verifiedCode === code) {
                    setProceedEnabled(true);
                    setStatus("valid", otpMessages.verified);
                    return;
                }
                const runId = verificationRun;
                setProceedEnabled(false);
                verificationTimer = window.setTimeout(function () {
                    verifyCode(code, runId);
                }, 250);
                setStatus("checking", otpMessages.verifying);
            }

            input.addEventListener("input", render);
            setProceedEnabled(false);
            render();
            return {
                markRequested: function () {
                    otpRequested = true;
                    verifiedCode = "";
                    setProceedEnabled(false);
                    render();
                },
                reset: function () {
                    otpRequested = false;
                    clearVerification();
                    verificationRun += 1;
                    verifiedCode = "";
                    input.value = "";
                    input.setCustomValidity("");
                    setProceedEnabled(false);
                    render();
                }
            };
        }

        document.querySelectorAll(".board-otp-request").forEach((button) => {
            button.addEventListener("click", async () => {
                const form = button.closest("form");
                const input = form ? form.querySelector("input[name='boardSignatureOtpCode']") : null;
                const statusBox = form ? form.querySelector(".board-otp-live-status") : null;
                const feedback = form ? form.querySelector(".board-otp-feedback") : null;
                const proceedButtons = form ? Array.from(form.querySelectorAll("button[name='decision']")) : [];
                const otpUi = bindOtpLiveStatus(input, statusBox, proceedButtons, button.dataset.verifyUrl);
                const requestUrl = button.getAttribute("data-request-url");

                if (!requestUrl) {
                    showOtpFeedback(feedback, "error", otpMessages.requestNotConfigured);
                    return;
                }

                setOtpButtonState(button, "loading", otpMessages.send, otpMessages.sending, otpMessages.sent);
                try {
                    const response = await fetch(requestUrl, {
                        method: "POST",
                        headers: {"Content-Type":"application/x-www-form-urlencoded; charset=UTF-8","X-CSRF-TOKEN": csrfToken
                        },
                        body: "_csrf=" + encodeURIComponent(csrfToken)
                    });
                    const payload = await response.json().catch(() => ({}));
                    if (!response.ok || payload.valid === false) {
                        throw new Error(payload.message || otpMessages.unableToSend);
                    }
                    showOtpFeedback(feedback, "success", payload.message || otpMessages.approvalCodeSent);
                    setOtpButtonState(button, "sent", otpMessages.send, otpMessages.sending, otpMessages.sent);
                    window.SaccosOtp?.startCooldown(button, payload, { idle: otpMessages.send });
                    otpUi.reset();
                    otpUi.markRequested();
                    if (input) {
                        window.SaccosOtp?.focusBoxes(input);
                    }
                } catch (error) {
                    showOtpFeedback(feedback, "error", error.message || otpMessages.unableToSend);
                    setOtpButtonState(button, "idle", otpMessages.send, otpMessages.sending, otpMessages.sent);
                }
            });
        });

        document.querySelectorAll("form[data-board-decision-form='true']").forEach((form) => {
            const approveButton = form.querySelector(".board-approve-submit");
            const rejectButton = form.querySelector(".board-reject-submit");
            const commentField = form.querySelector("textarea[name='comment']");

            commentField?.addEventListener("input", () => {
                commentField.setCustomValidity("");
            });

            approveButton?.addEventListener("click", () => {
                commentField?.setCustomValidity("");
                form.dataset.confirmEyebrow = confirmMessages.approvalEyebrow;
                form.dataset.confirmTitle = confirmMessages.approvalTitle;
                form.dataset.confirmMessage = confirmMessages.approvalMessage;
                form.dataset.confirmProceed = confirmMessages.approvalProceed;
            });

            rejectButton?.addEventListener("click", () => {
                form.dataset.confirmEyebrow = confirmMessages.rejectionEyebrow;
                form.dataset.confirmTitle = confirmMessages.rejectionTitle;
                form.dataset.confirmMessage = confirmMessages.rejectionMessage;
                form.dataset.confirmProceed = confirmMessages.rejectionProceed;
            });

            form.addEventListener("submit", (event) => {
                commentField?.setCustomValidity("");
                const submittedDecision = event.submitter ? event.submitter.value : "";
                if (submittedDecision === "REJECTED" && commentField && !commentField.value.trim()) {
                    event.preventDefault();
                    commentField.setCustomValidity(confirmMessages.rejectionCommentRequired);
                    commentField.reportValidity();
                    commentField.focus();
                    return;
                }
            });
        });
    })();
</script>

<%@ include file="../fragments/guarantor-financial-fetch.jspf" %>
<%@ include file="../fragments/live-account-status-hydration.jspf" %>
<%@ include file="../fragments/loan-export-modal.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
