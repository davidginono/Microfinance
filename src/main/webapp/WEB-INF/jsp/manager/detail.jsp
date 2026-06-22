<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/loan-detail-styles.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>
<%@ include file="../fragments/attachment-dropzone.jspf" %>

<div class="erp-page-header flex flex-wrap items-start justify-between gap-3">
    <div>
        <p class="erp-breadcrumb">${reviewPanelBreadcrumb}</p>
        <h1 class="erp-page-title">${reviewPanelTitle}</h1>
        <c:if test="${not empty reviewPanelSubtitle}">
            <p class="erp-page-subtitle">${reviewPanelSubtitle}</p>
        </c:if>
    </div>
    <button type="button" data-loan-export-url="${pageContext.request.contextPath}/documents/loan-applications/${app.id}/print" class="app-btn btn-primary"><spring:message code="common.export" text="Export" /></button>
</div>
<c:if test="${app.status eq 'AWAITING_BOARD'}">
    <div class="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
        <spring:message code="review.waitingBoardDecision" text="Loan is on review by board. Waiting for decisions." />
    </div>
</c:if>
<div class="loan-view-hero-summary">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6 lg:px-8">
        <div class="loan-hero-with-photo">
        <div class="min-w-0">
            <div class="loan-hero-primary-grid">
                <div class="loan-hero-inline-fact">
                    <div class="loan-hero-inline-label"><spring:message code="loan.applicationId" text="Loan Application ID" /></div>
                    <div class="loan-hero-inline-value loan-hero-inline-value--id">${loanIdShort}</div>
                </div>
                <c:if test="${not empty disbursedLoanId}">
                    <div class="loan-hero-inline-fact">
                        <div class="loan-hero-inline-label"><spring:message code="loan.loanId" text="Loan ID" /></div>
                        <div class="loan-hero-inline-value loan-hero-inline-value--id">${disbursedLoanId}</div>
                    </div>
                </c:if>
                <div class="loan-hero-inline-fact">
                    <div class="loan-hero-inline-label"><spring:message code="common.status" text="Status" /></div>
                    <div class="loan-hero-inline-value">
                        <span class="inline-flex items-center rounded-full px-3 py-1 text-sm font-semibold ${managerStatusBadgeClass}">
                            <spring:message code="loan.status.${app.status}" text="${app.status}" />
                        </span>
                    </div>
                </div>
            </div>
            <div class="mt-4 loan-hero-facts-grid sm:grid-cols-2">
                <div class="loan-hero-fact">
                    <div class="loan-hero-fact-label"><spring:message code="loan.applicantName" text="Applicant Name" /></div>
                    <div class="loan-hero-fact-value"><c:out value="${applicant.fullName}" /></div>
                </div>
                <div class="loan-hero-fact">
                    <div class="loan-hero-fact-label"><spring:message code="loan.type" text="Loan Type" /></div>
                    <div class="loan-hero-fact-value"><spring:message code="loan.type.${app.loanType}" text="${app.loanType}" /></div>
                </div>
                <c:if test="${not empty formFields['Loan Purpose']}">
                    <div class="loan-hero-fact sm:col-span-2">
                        <div class="loan-hero-fact-label"><spring:message code="loan.purpose" text="Loan Purpose" /></div>
                        <div class="loan-hero-fact-value"><c:out value="${formFields['Loan Purpose']}" /></div>
                    </div>
                </c:if>
            </div>
        </div>
        <div class="loan-applicant-photo-card">
            <div class="loan-applicant-photo-frame" title="<spring:message code='profile.photo.applicant' text='Applicant profile photo' />">
                <img src="<c:url value='/profile/image/members/${applicant.id}' />"
                     alt="<spring:message code='profile.photo.applicantAlt' text='Applicant profile photo' />"
                     onerror="this.classList.add('hidden'); this.nextElementSibling.classList.remove('hidden');" />
                <svg xmlns="http://www.w3.org/2000/svg" class="hidden h-14 w-14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                    <path d="M20 21a8 8 0 0 0-16 0"/>
                    <circle cx="12" cy="8" r="4"/>
                </svg>
            </div>
        </div>
        </div>
    </div>

    <div class="px-5 py-5 sm:px-6 sm:py-6 lg:px-8 lg:py-7">
        <div class="space-y-3"
             data-live-account-status-url="${pageContext.request.contextPath}${reviewBasePath}/loan-applications/${app.id}/applicant-financial-status">
            <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <div class="loan-view-summary-card px-4 py-4">
                    <div class="loan-stat-label"><spring:message code="loan.amount.label" text="Loan Amount" /></div>
                    <div class="loan-stat-value" data-disbursement-loan-amount-display>${app.amount}</div>
                </div>
                <div class="loan-view-summary-card px-4 py-4">
                    <div class="loan-stat-label"><spring:message code="loan.tenor.label" text="Tenor" /></div>
                    <div class="loan-stat-value">${app.tenorMonths} <spring:message code="common.months" text="month(s)" /></div>
                </div>
                <div class="loan-view-summary-card px-4 py-4">
                    <div class="loan-stat-label"><spring:message code="loan.currentSavings" text="Current Savings" /></div>
                    <div class="loan-stat-value" data-live-account-status-savings>${applicantExternalAccountStatus.savingsLabel}</div>
                </div>
                <div class="loan-view-summary-card px-4 py-4">
                    <div class="loan-stat-label"><spring:message code="loan.currentShares" text="Current Shares" /></div>
                    <div class="loan-stat-value" data-live-account-status-shares>${applicantExternalAccountStatus.sharesLabel}</div>
                </div>
            </div>
            <div class="${applicantExternalAccountStatus.available ? 'hidden ' : ''}rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600"
                 data-live-account-status-box>
                ${applicantExternalAccountStatus.statusMessage}
            </div>
        </div>

        <div class="mt-6 loan-simple-progress">
            <h3 class="text-lg font-semibold text-slate-900"><spring:message code="loan.progress" text="Progress" /></h3>
            <div class="loan-simple-progress-list">
                <c:forEach items="${loanProgressItems}" var="item">
                    <div class="loan-simple-progress-item${item.active ? ' is-active' : ' is-pending'}${item.current ? ' is-current' : ''}">
                        <span class="loan-simple-progress-dot"></span>
                        <span>${item.label}</span>
                    </div>
                </c:forEach>
            </div>
        </div>
    </div>
</div>
</div>

<div class="mt-4 space-y-4">
    <c:if test="${not empty managerReason}">
        <div class="rounded-lg border border-sacco-brown/30 bg-[#f7efe9] px-4 py-3 text-sm text-sacco-brown">
            <strong><spring:message code="review.currentRejectionReason" text="Current Rejection Reason:" /></strong> ${managerReason}
        </div>
    </c:if>
    <div class="loan-view-summary-card px-5 py-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
                <p class="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500"><spring:message code="review.applicantDetails" text="Applicant Details" /></p>
                <p class="mt-2 text-base text-slate-600"><spring:message code="review.applicantDetailsHelp" text="Key applicant information for review before making a decision." /></p>
            </div>
            <c:if test="${not empty repaymentCountdown}">
                <span class="rounded-full bg-sacco-blue/10 px-3 py-1 text-xs font-semibold text-sacco-blue">${repaymentCountdown}</span>
            </c:if>
        </div>
        <div class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            <div class="applicant-info-card">
                <div class="applicant-info-label"><spring:message code="member.fullName" text="Full Name" /></div>
                <div class="applicant-info-value">${applicant.fullName}</div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label"><spring:message code="member.memberNumber" text="Member Number" /></div>
                <div class="applicant-info-value">${applicant.memberNo}</div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label"><spring:message code="register.member.email" text="Email" /></div>
                <div class="applicant-info-value">
                    <c:choose>
                        <c:when test="${not empty applicant.email}">${applicant.email}</c:when>
                        <c:otherwise><spring:message code="common.notProvided" text="Not provided" /></c:otherwise>
                    </c:choose>
                </div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label"><spring:message code="register.member.phone" text="Phone" /></div>
                <div class="applicant-info-value">
                    <c:choose>
                        <c:when test="${not empty applicant.phone}">${applicant.phone}</c:when>
                        <c:otherwise><spring:message code="common.notProvided" text="Not provided" /></c:otherwise>
                    </c:choose>
                </div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label"><spring:message code="admin.saccoRegistry.station" text="Station" /></div>
                <div class="applicant-info-value">
                    <c:choose>
                        <c:when test="${not empty applicant.stationId}">${applicant.stationId}</c:when>
                        <c:otherwise><spring:message code="common.notAvailable" text="Not available" /></c:otherwise>
                    </c:choose>
                </div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label"><spring:message code="review.quickContext" text="Quick Context" /></div>
                <div class="applicant-info-value">
                    <spring:message code="review.quickContextCounts" arguments="${fn:length(attachments)},${fn:length(guarantorRequests)}" text="${fn:length(attachments)} attachment(s), ${fn:length(guarantorRequests)} guarantor request(s)" />
                </div>
            </div>
        </div>
    </div>
</div>

<%@ include file="../fragments/applicant-payment-details.jspf" %>

<div class="loan-view-summary-card mt-5 px-5 py-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
            <p class="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500"><spring:message code="review.applicantActiveLoans" text="Applicant Active Loans" /></p>
            <p class="mt-2 text-base text-slate-600"><spring:message code="review.applicantActiveLoansHelp" text="Other disbursed loans that are still active for this applicant at the time of review." /></p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
            <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                <spring:message code="review.activeLoanCount" arguments="${activeApplicantLoanCount}" text="${activeApplicantLoanCount} active loan(s)" />
            </span>
            <span class="rounded-full bg-sacco-blue/10 px-3 py-1 text-xs font-semibold text-sacco-blue">
                <spring:message code="review.totalExposure" arguments="${activeApplicantLoanTotalAmount}" text="Total exposure ${activeApplicantLoanTotalAmount}" />
            </span>
        </div>
    </div>
    <c:choose>
        <c:when test="${not empty activeApplicantLoans}">
            <div class="mt-5 grid gap-3 sm:grid-cols-2">
                <div class="applicant-info-card">
                    <div class="applicant-info-label"><spring:message code="analytics.activeLoans" text="Active Loans" /></div>
                    <div class="applicant-info-value">${activeApplicantLoanCount}</div>
                </div>
                <div class="applicant-info-card">
                    <div class="applicant-info-label"><spring:message code="analytics.totalActiveAmount" text="Total Active Amount" /></div>
                    <div class="applicant-info-value">${activeApplicantLoanTotalAmount}</div>
                </div>
            </div>
            <div class="mt-5 erp-table-wrap overflow-x-auto">
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
                    </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100">
                    <c:forEach items="${activeApplicantLoans}" var="loan">
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
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:when>
        <c:otherwise>
            <div class="mt-5 rounded-lg border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
                <spring:message code="review.noActiveApplicantLoans" text="This applicant has no other active disbursed loans at the moment." />
            </div>
        </c:otherwise>
    </c:choose>
</div>

<%@ include file="../fragments/financial-field-sections.jspf" %>

<c:if test="${not empty disbursementProofAttachments or app.status eq 'FINAL_APPROVED' or app.status eq 'DEFAULTED' or app.status eq 'PAID'}">
    <div class="erp-table-wrap mt-5 overflow-x-auto">
        <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="admin.settings.disbursementProof" text="Disbursement Proof" /></h5>
        <table class="min-w-full divide-y divide-slate-200 text-sm">
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
                    <td class="px-3 py-2">${fn:replace(fn:substring(file.uploadedAt, 0, 16), 'T', ' ')}</td>
                    <td class="px-3 py-2">
                        <div class="flex flex-wrap gap-2">
                            <a href="/documents/loan-applications/${app.id}/attachments/${file.id}/view" target="_blank" rel="noopener" class="app-btn btn-neutral"><spring:message code="common.view" text="View" /></a>
                            <a href="/documents/loan-applications/${app.id}/attachments/${file.id}" class="app-btn btn-primary"><spring:message code="common.download" text="Download" /></a>
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
</c:if>

<div class="erp-table-wrap mt-5 overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.applicationAttachments" text="Application Attachments" /></h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
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
                <td class="px-3 py-2">${fn:replace(fn:substring(file.uploadedAt, 0, 16), 'T', ' ')}</td>
                <td class="px-3 py-2"><a href="/documents/loan-applications/${app.id}/attachments/${file.id}" class="app-btn btn-primary"><spring:message code="common.download" text="Download" /></a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty attachments}">
            <tr><td colspan="4" class="px-3 py-3 text-slate-500"><spring:message code="loan.attachments.empty" text="No attachments uploaded." /></td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<div class="erp-table-wrap mt-5 overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.guarantors" text="Guarantors" /></h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
        <tr>
            <th class="px-3 py-2 text-left"><spring:message code="loan.guarantor" text="Guarantor" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="member.memberNo" text="Member No" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="common.status" text="Status" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="loan.date" text="Date" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="financial.status" text="Financial Status" /></th>
        </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
        <c:forEach items="${guarantorRequests}" var="g">
            <tr>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty guarantorNames[g.guarantorMemberId]}">${guarantorNames[g.guarantorMemberId]}</c:when>
                        <c:otherwise>#${fn:substring(g.guarantorMemberId, 0, 8)}</c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty guarantorMembersById[g.guarantorMemberId] and not empty guarantorMembersById[g.guarantorMemberId].memberNo}">${guarantorMembersById[g.guarantorMemberId].memberNo}</c:when>
                        <c:otherwise>-</c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2">${g.status}</td>
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${not empty g.decidedAt}">${fn:replace(fn:substring(g.decidedAt, 0, 16), 'T', ' ')}</c:when>
                        <c:otherwise>${fn:replace(fn:substring(g.createdAt, 0, 16), 'T', ' ')}</c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2">
                    <button type="button"
                            class="app-btn btn-neutral guarantor-financial-trigger"
                            data-url="${reviewBasePath}/loan-applications/${app.id}/guarantors/${g.guarantorMemberId}/financial-status">
                        <span class="guarantor-financial-spinner hidden" data-financial-spinner></span>
                        <span data-financial-label><spring:message code="financial.loadStatus" text="Load Status" /></span>
                    </button>
                    <div class="guarantor-financial-result hidden">
                        <div class="guarantor-financial-result-row">
                            <span class="guarantor-financial-result-label"><spring:message code="financial.savings" text="Savings" /></span>
                            <span class="guarantor-financial-result-value" data-financial-savings>-</span>
                        </div>
                        <div class="guarantor-financial-result-row">
                            <span class="guarantor-financial-result-label"><spring:message code="financial.shares" text="Shares" /></span>
                            <span class="guarantor-financial-result-value" data-financial-shares>-</span>
                        </div>
                        <div class="guarantor-financial-result-note" data-financial-note></div>
                    </div>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty guarantorRequests}">
            <tr><td colspan="5" class="px-3 py-3 text-slate-500"><spring:message code="loan.guarantor.empty" text="No guarantors selected yet." /></td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/staff-repayment-summary.jspf" %>

<c:if test="${showReviewDecisionForm}">
    <form action="${reviewBasePath}/loan-applications/${app.id}/decision" method="post" class="erp-form-wrap mt-5 space-y-3" data-manager-decision-form="true">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <label class="block text-sm font-medium text-slate-700">${reviewCommentLabel}</label>
        <textarea name="reasons" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" placeholder="${reviewCommentPlaceholder}"></textarea>
        <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
            <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="otp.verification" text="OTP Verification" /></div>
                    <p class="mt-2 text-sm text-slate-600"><spring:message code="loan.otp.requestBeforeDecision" text="Request a one-time code before submitting your review decision." /></p>
                </div>
                <button type="button"
                        class="app-btn btn-primary otp-request-button staff-otp-request inline-flex items-center justify-center gap-2"
                        data-request-url="${reviewBasePath}/loan-applications/${app.id}/request-decision-otp"
                        data-verify-url="${reviewBasePath}/loan-applications/${app.id}/verify-decision-otp">
                    <span class="otp-button-spinner hidden"></span>
                    <span class="otp-button-label"><spring:message code="loan.otp.sendCode" text="Send OTP Code" /></span>
                </button>
            </div>
            <div class="staff-otp-feedback mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
            <div class="mt-3">
                <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="loan.otp.code" text="OTP Code" /></label>
                <input type="text"
                       name="managerDecisionOtpCode"
                       inputmode="numeric"
                       maxlength="6"
                       autocomplete="one-time-code"
                       data-otp-hidden="true" data-otp-label="<spring:message code='loan.otp.decisionCode' text='Decision OTP code' />"
                       required
                       class="w-full rounded-lg border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                       placeholder="123456" />
                <p class="mt-2 text-sm text-slate-500"><spring:message code="loan.otp.decisionCodeHelp" text="Enter the 6-digit code sent to your email before approving or rejecting." /></p>
                <div class="staff-otp-live-status mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                    <span data-otp-spinner class="hidden h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
                    <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                        <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.414l-7.25 7.25a1 1 0 01-1.414 0L3.296 9.21A1 1 0 114.71 7.796l4.037 4.037 6.543-6.543a1 1 0 011.414 0z" clip-rule="evenodd" />
                    </svg>
                    <span data-otp-text>Enter the code to verify it.</span>
                </div>
            </div>
        </div>
        <div class="flex flex-wrap gap-2">
            <button type="submit" name="decision" value="ACCEPT" class="app-btn btn-primary">${approveActionLabel}</button>
            <button type="submit" name="decision" value="REJECT" class="app-btn btn-reject">${rejectActionLabel}</button>
        </div>
    </form>
</c:if>

<c:if test="${showManagerReversalRequests and not empty pendingManagerStageWithdrawal}">
    <div class="erp-form-wrap mt-5 space-y-3">
        <div class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            The applicant wants this loan application removed while it is still under manager review. Review the request before anything changes.
        </div>
        <div class="flex flex-wrap gap-2">
            <form action="${reviewBasePath}/loan-applications/${app.id}/reversal-requests/${pendingManagerStageWithdrawal.id}/approve"
                  method="post"
                  data-confirm-title="Approve Removal"
                  data-confirm-message="Approve this request and remove the application from manager review?"
                  data-confirm-proceed="Approve Removal">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <button type="submit" class="app-btn btn-primary">Approve Removal</button>
            </form>
            <form action="${reviewBasePath}/loan-applications/${app.id}/reversal-requests/${pendingManagerStageWithdrawal.id}/reject"
                  method="post"
                  data-confirm-title="Decline Removal"
                  data-confirm-message="Keep this application in the manager queue and decline the removal request?"
                  data-confirm-proceed="Decline Removal">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <label class="mb-1 block text-sm font-medium text-slate-700">
                    Decline Reason <span class="text-rose-600">*</span>
                    <textarea name="decisionReason"
                              rows="2"
                              required
                              class="mt-1 w-full min-w-[16rem] rounded-lg border border-slate-300 px-3 py-2 text-sm font-normal text-slate-700 focus:border-sacco-blue focus:outline-none"
                              placeholder="Enter why this removal request is declined."></textarea>
                </label>
                <button type="submit" class="app-btn btn-reject">Decline Removal</button>
            </form>
        </div>
    </div>
</c:if>

<c:if test="${showDisbursementPermissionMessage}">
    <div class="erp-form-wrap mt-5">
        <div class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-800">
            This loan is ready for disbursement, but your account does not have the Disburse Loan claim. Ask the admin to update your role claims, then sign in again.
        </div>
    </div>
</c:if>

<c:if test="${showDisbursementForm}">
    <form action="${reviewBasePath}/loan-applications/${app.id}/finalize"
          method="post"
          enctype="multipart/form-data"
          class="erp-form-wrap mt-5 space-y-3"
          data-confirm-title="Disburse Loan"
          data-confirm-message="Disburse this loan now? There will be no reversal of this action."
          data-confirm-proceed="Disburse Loan">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <input type="hidden" name="decision" value="FINAL_APPROVE" />
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
                <label class="block text-sm font-medium text-slate-700">Loan Disbursement</label>
                <p class="mt-1 text-sm text-slate-500">Enter the release date and repayment terms before final approval.</p>
            </div>
            <span class="rounded-full bg-emerald-50 px-3 py-1 text-sm font-semibold text-emerald-700">
                <c:choose>
                    <c:when test="${app.status eq 'BOARD_APPROVED'}">Committee approval threshold reached</c:when>
                    <c:otherwise>Ready for disbursement</c:otherwise>
                </c:choose>
            </span>
        </div>
        <div class="grid gap-4 md:grid-cols-2">
            <div>
                <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="loan.repayment.disbursementDate" text="Disbursement Date" /></label>
                <input type="date" name="disbursementDate" value="${app.disbursementDate}" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required />
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="loan.repayment.firstRepayment" text="First Repayment Date" /></label>
                <input type="date" name="firstRepaymentDate" value="${app.firstRepaymentDate}" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required />
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-slate-700">Disbursement Amount <span class="text-rose-600">*</span></label>
                <input type="text"
                       id="disbursementAmountInput"
                       name="disbursementAmount"
                       value="${app.amount}"
                       inputmode="decimal"
                       autocomplete="off"
                       required
                       class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                       placeholder="100,000.00" />
                <p class="mt-1 text-xs text-slate-500">This amount becomes the final disbursed principal and repayment basis.</p>
            </div>
            <div class="md:col-span-2">
                <div class="rounded-lg border border-cyan-200 bg-cyan-50 px-4 py-3 text-sm text-slate-700">
                    <div class="grid gap-3 sm:grid-cols-2">
                        <div>
                            <span class="block text-xs font-semibold uppercase tracking-wide text-slate-500">Requested Amount</span>
                            <span class="mt-1 block font-semibold text-slate-900" data-disbursement-requested-amount>${app.amount}</span>
                        </div>
                        <div>
                            <span class="block text-xs font-semibold uppercase tracking-wide text-slate-500">Loaded Disbursement Amount</span>
                            <span class="mt-1 block font-semibold text-slate-900" data-disbursement-preview-amount>${app.amount}</span>
                        </div>
                    </div>
                    <p class="mt-3 text-xs text-slate-600">The loan details and repayment schedule will use the loaded disbursement amount when this loan is released.</p>
                </div>
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="loan.loanId" text="Loan ID" /> <span class="text-rose-600">*</span></label>
                <input type="text" name="loanId" value="${app.loanId}"
                       pattern="[0-9]{4,20}" inputmode="numeric" required maxlength="20"
                       title="Enter the SACCO loan-book number (4-20 digits)"
                       class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                       placeholder="SACCO loan-book number (digits only)" />
                <p class="mt-1 text-xs text-slate-500">Entered at disbursement. 4-20 digits, unique within this SACCO.</p>
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-slate-700">Tenor Guidance</label>
                <div class="erp-section-muted text-sm text-slate-600">
                    Tenor is ${app.tenorMonths} month(s). The repayment schedule will use the standard monthly cycle and calculate the final due date automatically.
                </div>
            </div>
        </div>
        <div>
            <label class="mb-1 flex flex-wrap items-center gap-2 text-sm font-medium text-slate-700">
                <span>Disbursement Proof</span>
                <span class="rounded-full px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wide ${disbursementProofRequired ? 'bg-amber-100 text-amber-700' : 'bg-slate-100 text-slate-600'}">
                    <c:choose>
                        <c:when test="${disbursementProofRequired}">
                            <spring:message code="common.required" text="Required" />
                        </c:when>
                        <c:otherwise>
                            <spring:message code="common.optional" text="Optional" />
                        </c:otherwise>
                    </c:choose>
                </span>
            </label>
            <label class="attachment-dropzone" data-attachment-dropzone>
                <input type="file"
                       name="disbursementProofFile"
                       accept=".pdf,.png,.jpg,.jpeg,.doc,.docx"
                       class="attachment-dropzone-input"
                       data-attachment-input
                       ${disbursementProofRequired ? 'required' : ''} />
                <span class="attachment-dropzone-main">
                    <span class="attachment-dropzone-copy">
                        <span class="attachment-dropzone-title">Drop proof here or choose from device</span>
                        <span class="attachment-dropzone-help">Receipt, voucher, signed proof, image, or document.</span>
                        <span class="attachment-dropzone-files" data-attachment-files>No file selected</span>
                    </span>
                    <span class="attachment-dropzone-action">Choose file</span>
                </span>
            </label>
            <p class="mt-1 text-xs text-slate-500">
                <c:choose>
                    <c:when test="${disbursementProofRequired}">Attach the proof before disbursement.</c:when>
                    <c:otherwise>This product allows disbursement without proof, but a proof file can still be attached.</c:otherwise>
                </c:choose>
            </p>
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-slate-700">${disbursementNotesLabel}</label>
            <textarea name="disbursementNotes" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" rows="3" placeholder="<spring:message code='loan.disbursement.notesPlaceholder' text='Optional disbursement or repayment instructions' />">${app.disbursementNotes}</textarea>
        </div>
        <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
            <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="otp.verification" text="OTP Verification" /></div>
                    <p class="mt-2 text-sm text-slate-600"><spring:message code="loan.disbursement.otpHelp" text="Request a one-time code before disbursing this loan." /></p>
                </div>
                <button type="button"
                        class="app-btn btn-primary otp-request-button staff-otp-request inline-flex items-center justify-center gap-2"
                        data-request-url="${reviewBasePath}/loan-applications/${app.id}/request-disbursement-otp"
                        data-verify-url="${reviewBasePath}/loan-applications/${app.id}/verify-disbursement-otp">
                    <span class="otp-button-spinner hidden"></span>
                    <span class="otp-button-label"><spring:message code="loan.otp.sendCode" text="Send OTP Code" /></span>
                </button>
            </div>
            <div class="staff-otp-feedback mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
            <div class="mt-3">
                <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="loan.otp.code" text="OTP Code" /></label>
                <input type="text"
                       name="disbursementOtpCode"
                       inputmode="numeric"
                       maxlength="6"
                       autocomplete="one-time-code"
                       data-otp-hidden="true" data-otp-label="<spring:message code='loan.disbursement.otpCode' text='Disbursement OTP code' />"
                       required
                       class="w-full rounded-lg border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                       placeholder="123456" />
                <p class="mt-2 text-sm text-slate-500"><spring:message code="loan.disbursement.codeHelp" text="Enter the 6-digit code sent to your email before completing disbursement." /></p>
                <div class="staff-otp-live-status mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                    <span data-otp-spinner class="hidden h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
                    <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                        <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.414l-7.25 7.25a1 1 0 01-1.414 0L3.296 9.21A1 1 0 114.71 7.796l4.037 4.037 6.543-6.543a1 1 0 011.414 0z" clip-rule="evenodd" />
                    </svg>
                    <span data-otp-text><spring:message code="loan.otp.enterCodeToVerify" text="Enter the code to verify it." /></span>
                </div>
            </div>
        </div>
        <button type="submit" class="app-btn btn-approve">${disbursementActionLabel}</button>
    </form>
</c:if>

<c:if test="${showUndoForm}">
    <form action="${reviewBasePath}/loan-applications/${app.id}/undo-decision"
          method="post"
          class="erp-form-wrap mt-5"
          data-confirm-title='<spring:message code="review.reverseManagerAction" text="Reverse Manager Action" />'
          data-confirm-message="<spring:message code='review.confirm.returnToQueueMessage' text='Return this rejected application to the manager queue for another review?' />"
          data-confirm-proceed="<spring:message code='review.confirm.returnToQueue' text='Return to Queue' />">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <button type="submit"
                class="app-btn btn-neutral ${managerUndoWindowOpen ? '' : 'action-button-disabled'}"
                ${managerUndoWindowOpen ? '' : 'disabled'}>
            <spring:message code="review.reverseLastManagerAction" text="Reverse Last Manager Action" />
        </button>
    </form>
</c:if>

<script>
    (() => {
        const csrfToken = "${_csrf.token}";
        const otpMessages = {
            send: "<spring:message code='loan.otp.sendCode' text='Send OTP Code' javaScriptEscape='true' />",
            sending: "<spring:message code='loan.otp.sending' text='Sending...' javaScriptEscape='true' />",
            sent: "<spring:message code='loan.otp.sent' text='OTP Sent' javaScriptEscape='true' />",
            checking: "<spring:message code='loan.otp.checking' text='Checking code...' javaScriptEscape='true' />",
            verified: "<spring:message code='loan.otp.codeVerified' text='OTP code verified.' javaScriptEscape='true' />",
            invalid: "<spring:message code='loan.otp.invalid' text='The OTP code is invalid.' javaScriptEscape='true' />",
            enterDigits: "<spring:message code='loan.otp.enterAllDigits' text='Enter all 6 digits to verify the code.' javaScriptEscape='true' />",
            verifyBeforeContinue: "<spring:message code='loan.otp.verifyBeforeContinue' text='Verify the OTP code before continuing.' javaScriptEscape='true' />",
            requestAndVerify: "<spring:message code='loan.otp.requestAndVerifyBeforeContinue' text='Request and verify an OTP code before continuing.' javaScriptEscape='true' />",
            enterCode: "<spring:message code='loan.otp.enterSixDigitCode' text='Enter the 6-digit code to verify it.' javaScriptEscape='true' />",
            requestNotConfigured: "<spring:message code='loan.otp.requestNotConfigured' text='OTP request is not configured.' javaScriptEscape='true' />",
            unableToSend: "<spring:message code='loan.otp.unableToSend' text='Unable to send the OTP code right now.' javaScriptEscape='true' />",
            missingPermission: "<spring:message code='loan.otp.missingPermission' text='Your account is missing permission to request this OTP. Ask the admin to enable the Disburse Loan claim, then sign in again.' javaScriptEscape='true' />",
            sessionExpired: "<spring:message code='loan.otp.sessionExpired' text='Your session has expired. Sign in again before requesting the OTP code.' javaScriptEscape='true' />",
            sentToEmail: "<spring:message code='loan.otp.sentToEmail' text='We sent an OTP code to your email.' javaScriptEscape='true' />"
        };
        const confirmMessages = {
            approvalEyebrow: "<spring:message code='review.confirm.approvalEyebrow' text='Confirm Approval' javaScriptEscape='true' />",
            approvalTitle: "<spring:message code='review.confirm.managerApprovalTitle' text='Approve Loan Review' javaScriptEscape='true' />",
            approvalMessage: "<spring:message code='review.confirm.managerApprovalMessage' text='Approve this loan application review? Your decision will be recorded and the application will move to the next configured workflow step.' javaScriptEscape='true' />",
            approvalProceed: "<spring:message code='review.approveReview' text='Approve Review' javaScriptEscape='true' />",
            rejectionEyebrow: "<spring:message code='review.confirm.rejectionEyebrow' text='Confirm Rejection' javaScriptEscape='true' />",
            rejectionTitle: "<spring:message code='review.confirm.managerRejectionTitle' text='Reject Loan Review' javaScriptEscape='true' />",
            rejectionMessage: "<spring:message code='review.confirm.managerRejectionMessage' text='Reject this loan application? The applicant will see the rejection and the application will leave this review queue.' javaScriptEscape='true' />",
            rejectionProceed: "<spring:message code='review.rejectReview' text='Reject Review' javaScriptEscape='true' />"
        };

        function setOtpButtonState(button, state) {
            const spinner = button.querySelector(".otp-button-spinner");
            const label = button.querySelector(".otp-button-label");
            const loading = state === "loading";
            const sent = state === "sent";
            button.disabled = loading;
            spinner?.classList.toggle("hidden", !loading);
            if (label) {
                label.textContent = loading ? otpMessages.sending : (sent ? otpMessages.sent : otpMessages.send);
            }
        }

        function showOtpFeedback(element, type, text) {
            if (!element) {
                return;
            }
            element.textContent = text;
            element.classList.remove("hidden", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-rose-200", "bg-rose-50", "text-rose-700");
            element.classList.add(
                type === "success" ? "border-emerald-200" : "border-rose-200",
                type === "success" ? "bg-emerald-50" : "bg-rose-50",
                type === "success" ? "text-emerald-700" : "text-rose-700"
            );
        }

        function formatDisbursementAmount(value) {
            const amount = Number(normalizeDisbursementAmount(value));
            if (!Number.isFinite(amount) || amount <= 0) {
                return "-";
            }
            return "TSh " + new Intl.NumberFormat("en-US", {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            }).format(amount);
        }

        function normalizeDisbursementAmount(value) {
            const raw = String(value || "").replace(/[^0-9.]/g, "");
            const parts = raw.split(".");
            if (parts.length <= 1) {
                return parts[0];
            }
            return parts[0] + "." + parts.slice(1).join("");
        }

        function formatDisbursementInputAmount(value, forceDecimals) {
            const normalized = normalizeDisbursementAmount(value);
            if (!normalized) {
                return "";
            }
            const hasDecimal = normalized.includes(".");
            const parts = normalized.split(".");
            const whole = parts[0] || "0";
            const decimals = parts[1] || "";
            const formattedWhole = new Intl.NumberFormat("en-US", {
                maximumFractionDigits: 0
            }).format(Number(whole));
            if (forceDecimals) {
                return formattedWhole + "." + decimals.padEnd(2, "0").slice(0, 2);
            }
            if (hasDecimal) {
                return formattedWhole + "." + decimals;
            }
            return formattedWhole;
        }

        function formatDisbursementInputWhileTyping(input) {
            const rawValue = input.value;
            const selectionStart = input.selectionStart || rawValue.length;
            const valueBeforeCaret = rawValue.slice(0, selectionStart);
            const digitsBeforeCaret = valueBeforeCaret.replace(/\D/g, "").length;
            const decimalIndexBeforeCaret = valueBeforeCaret.indexOf(".");
            const decimalDigitsBeforeCaret = decimalIndexBeforeCaret >= 0
                ? valueBeforeCaret.slice(decimalIndexBeforeCaret + 1).replace(/\D/g, "").length
                : 0;
            input.value = formatDisbursementInputAmount(rawValue, false);

            let nextCaret;
            const formattedDecimalIndex = input.value.indexOf(".");
            if (decimalIndexBeforeCaret >= 0 && formattedDecimalIndex >= 0) {
                nextCaret = formattedDecimalIndex + 1 + decimalDigitsBeforeCaret;
            } else {
                let seenDigits = 0;
                nextCaret = input.value.length;
                for (let index = 0; index < input.value.length; index++) {
                    if (/\d/.test(input.value.charAt(index))) {
                        seenDigits++;
                    }
                    if (seenDigits >= digitsBeforeCaret) {
                        nextCaret = index + 1;
                        break;
                    }
                }
            }
            nextCaret = Math.min(nextCaret, input.value.length);
            input.setSelectionRange(nextCaret, nextCaret);
        }

        const disbursementAmountInput = document.getElementById("disbursementAmountInput");
        const disbursementAmountDisplay = document.querySelector("[data-disbursement-loan-amount-display]");
        const disbursementRequestedAmount = document.querySelector("[data-disbursement-requested-amount]");
        const disbursementPreviewAmount = document.querySelector("[data-disbursement-preview-amount]");
        function refreshDisbursementAmountPreview() {
            if (!disbursementAmountInput) {
                return;
            }
            const label = formatDisbursementAmount(disbursementAmountInput.value);
            if (disbursementAmountDisplay) {
                disbursementAmountDisplay.textContent = label;
            }
            if (disbursementPreviewAmount) {
                disbursementPreviewAmount.textContent = label;
            }
        }
        if (disbursementRequestedAmount) {
            disbursementRequestedAmount.textContent = formatDisbursementAmount(disbursementRequestedAmount.textContent);
        }
        disbursementAmountInput?.addEventListener("input", () => {
            formatDisbursementInputWhileTyping(disbursementAmountInput);
            refreshDisbursementAmountPreview();
        });
        disbursementAmountInput?.addEventListener("blur", () => {
            disbursementAmountInput.value = formatDisbursementInputAmount(disbursementAmountInput.value, true);
            refreshDisbursementAmountPreview();
        });
        disbursementAmountInput?.form?.addEventListener("submit", () => {
            disbursementAmountInput.value = normalizeDisbursementAmount(disbursementAmountInput.value);
        });
        refreshDisbursementAmountPreview();
        if (disbursementAmountInput) {
            disbursementAmountInput.value = formatDisbursementInputAmount(disbursementAmountInput.value, true);
        }

        function bindStaffOtpLiveStatus(input, statusBox, proceedButtons, verifyUrl) {
            if (!input || !statusBox || !verifyUrl) {
                return {
                    markRequested: () => {},
                    reset: () => {},
                    isVerified: () => true
                };
            }

            const spinner = statusBox.querySelector("[data-otp-spinner]");
            const tick = statusBox.querySelector("[data-otp-tick]");
            const text = statusBox.querySelector("[data-otp-text]");
            let requested = false;
            let verified = false;
            let verifyTimer;
            let verifyRun = 0;

            function setProceedEnabled(enabled) {
                proceedButtons.forEach((button) => {
                    button.disabled = !enabled;
                    button.classList.toggle("action-button-disabled", !enabled);
                });
            }

            function setStatus(state, message) {
                statusBox.classList.remove(
                    "hidden", "border-slate-200", "bg-white", "text-slate-600",
                    "border-emerald-200", "bg-emerald-50", "text-emerald-700",
                    "border-rose-200", "bg-rose-50", "text-rose-700"
                );
                statusBox.classList.add("flex");
                spinner?.classList.toggle("hidden", state !== "checking");
                tick?.classList.toggle("hidden", state !== "valid");
                if (tick && state === "valid") {
                    tick.classList.remove("otp-checkmark-pop");
                    void tick.offsetWidth;
                    tick.classList.add("otp-checkmark-pop");
                }
                if (text) {
                    text.textContent = message;
                }
                if (state === "valid") {
                    statusBox.classList.add("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                } else if (state === "invalid") {
                    statusBox.classList.add("border-rose-200", "bg-rose-50", "text-rose-700");
                } else {
                    statusBox.classList.add("border-slate-200", "bg-white", "text-slate-600");
                }
            }

            function resetStatus() {
                window.clearTimeout(verifyTimer);
                verifyRun += 1;
                requested = false;
                verified = false;
                input.value = "";
                input.setCustomValidity("");
                statusBox.classList.add("hidden");
                statusBox.classList.remove("flex");
                setProceedEnabled(false);
            }

            async function verifyCode(code, runId) {
                setStatus("checking", otpMessages.checking);
                try {
                    const body = new URLSearchParams();
                    body.set("otpCode", code);
                    const response = await fetch(verifyUrl, {
                        method: "POST",
                        headers: {
                            "X-CSRF-TOKEN": csrfToken,
                            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                            "Accept": "application/json"
                        },
                        body,
                        credentials: "same-origin"
                    });
                    const contentType = response.headers.get("content-type") || "";
                    if (!contentType.includes("application/json")) {
                        if (response.status === 403) {
                            throw new Error("Your account is missing permission to verify this OTP.");
                        }
                        if (response.status === 401 || response.redirected) {
                            throw new Error("Your session has expired. Sign in again before verifying the OTP code.");
                        }
                        throw new Error("Unable to verify the OTP code right now.");
                    }
                    const payload = await response.json();
                    if (runId !== verifyRun) {
                        return;
                    }
                    if (!response.ok || !payload.valid) {
                        throw new Error(payload.message || "The OTP code is not valid.");
                    }
                    verified = true;
                    input.setCustomValidity("");
                    setProceedEnabled(true);
                    setStatus("valid", payload.message || otpMessages.verified);
                } catch (error) {
                    if (runId !== verifyRun) {
                        return;
                    }
                    verified = false;
                    setProceedEnabled(false);
                    input.setCustomValidity(error.message || otpMessages.invalid);
                    setStatus("invalid", error.message || otpMessages.invalid);
                }
            }

            input.addEventListener("input", () => {
                const code = input.value.replace(/\D/g, "").slice(0, 6);
                if (input.value !== code) {
                    input.value = code;
                }
                window.clearTimeout(verifyTimer);
                verifyRun += 1;
                verified = false;
                setProceedEnabled(false);
                input.setCustomValidity("");
                if (!requested) {
                    statusBox.classList.add("hidden");
                    statusBox.classList.remove("flex");
                    return;
                }
                if (code.length < 6) {
                    setStatus("idle", otpMessages.enterDigits);
                    return;
                }
                const runId = verifyRun;
                verifyTimer = window.setTimeout(() => verifyCode(code, runId), 250);
            });

            input.form?.addEventListener("submit", (event) => {
                if (!verified) {
                    event.preventDefault();
                    input.setCustomValidity(requested ? otpMessages.verifyBeforeContinue : otpMessages.requestAndVerify);
                    input.reportValidity();
                }
            });

            setProceedEnabled(false);

            return {
                markRequested: () => {
                    requested = true;
                    verified = false;
                    setProceedEnabled(false);
                    input.setCustomValidity("");
                    setStatus("idle", otpMessages.enterCode);
                    window.SaccosOtp?.focusBoxes(input);
                },
                reset: resetStatus,
                isVerified: () => verified
            };
        }

        const otpBindings = new WeakMap();
        function otpBindingFor(button) {
            if (otpBindings.has(button)) {
                return otpBindings.get(button);
            }
            const form = button.closest("form");
            const input = form ? form.querySelector("input[name='managerDecisionOtpCode'], input[name='disbursementOtpCode']") : null;
            const statusBox = form ? form.querySelector(".staff-otp-live-status") : null;
            const proceedButtons = form ? Array.from(form.querySelectorAll("button[type='submit']")) : [];
            const binding = bindStaffOtpLiveStatus(input, statusBox, proceedButtons, button.dataset.verifyUrl);
            otpBindings.set(button, binding);
            return binding;
        }

        document.querySelectorAll(".staff-otp-request").forEach((button) => {
            otpBindingFor(button);
            button.addEventListener("click", async () => {
                const form = button.closest("form");
                const feedback = form ? form.querySelector(".staff-otp-feedback") : null;
                const otpUi = otpBindingFor(button);
                const requestUrl = button.dataset.requestUrl;
                if (!requestUrl) {
                    showOtpFeedback(feedback, "error", otpMessages.requestNotConfigured);
                    return;
                }
                otpUi.reset();
                setOtpButtonState(button, "loading");
                try {
                    const response = await fetch(requestUrl, {
                        method: "POST",
                        headers: {
                            "X-CSRF-TOKEN": csrfToken,
                            "Accept": "application/json"
                        },
                        credentials: "same-origin"
                    });
                    const contentType = response.headers.get("content-type") || "";
                    if (!contentType.includes("application/json")) {
                        if (response.status === 403) {
                            throw new Error(otpMessages.missingPermission);
                        }
                        if (response.status === 401 || response.redirected) {
                            throw new Error(otpMessages.sessionExpired);
                        }
                        throw new Error(otpMessages.unableToSend);
                    }
                    const payload = await response.json();
                    if (!response.ok || !payload.valid) {
                        throw new Error(payload.message || otpMessages.unableToSend);
                    }
                    setOtpButtonState(button, "sent");
                    window.SaccosOtp?.startCooldown(button, payload, { idle: otpMessages.send });
                    showOtpFeedback(feedback, "success", payload.message || otpMessages.sentToEmail);
                    otpUi.markRequested();
                } catch (error) {
                    setOtpButtonState(button, "idle");
                    showOtpFeedback(feedback, "error", error.message || otpMessages.unableToSend);
                }
            });
        });

        document.querySelectorAll("form[data-manager-decision-form='true']").forEach((form) => {
            const reasonsField = form.querySelector("textarea[name='reasons']");
            const decisionButtons = form.querySelectorAll("button[type='submit'][name='decision']");
            reasonsField?.addEventListener("input", () => {
                reasonsField.setCustomValidity("");
            });
            decisionButtons.forEach((button) => {
                button.addEventListener("click", () => {
                    if (button.value === "ACCEPT") {
                        reasonsField?.setCustomValidity("");
                    }
                });
            });

            form.addEventListener("submit", (event) => {
                const decision = event.submitter ? event.submitter.value : "";
                reasonsField?.setCustomValidity("");
                if (decision === "REJECT") {
                    if (reasonsField && !reasonsField.value.trim()) {
                        event.preventDefault();
                        reasonsField.setCustomValidity("Add a reason before rejecting this loan application.");
                        reasonsField.reportValidity();
                        reasonsField.focus();
                        return;
                    }
                    form.dataset.confirmEyebrow = confirmMessages.rejectionEyebrow;
                    form.dataset.confirmTitle = confirmMessages.rejectionTitle;
                    form.dataset.confirmMessage = confirmMessages.rejectionMessage;
                    form.dataset.confirmProceed = confirmMessages.rejectionProceed;
                    return;
                }
                if (decision === "ACCEPT") {
                    form.dataset.confirmEyebrow = confirmMessages.approvalEyebrow;
                    form.dataset.confirmTitle = confirmMessages.approvalTitle;
                    form.dataset.confirmMessage = confirmMessages.approvalMessage;
                    form.dataset.confirmProceed = confirmMessages.approvalProceed;
                }
            });
        });
    })();
</script>

<%@ include file="../fragments/confirm-modal.jspf" %>
<%@ include file="../fragments/guarantor-financial-fetch.jspf" %>
<%@ include file="../fragments/live-account-status-hydration.jspf" %>
<%@ include file="../fragments/loan-export-modal.jspf" %>
<script>
    (() => {
        const url = "${pageContext.request.contextPath}${reviewBasePath}/loan-applications/${app.id}/active-loans/outstanding-balances";
        const cells = document.querySelectorAll("[data-active-loan-outstanding]");
        if (!cells.length) {
            return;
        }
        fetch(url, {
            headers: {
                "Accept": "application/json"
            },
            credentials: "same-origin"
        })
            .then((response) => response.json().then((payload) => ({ ok: response.ok, payload })))
            .then(({ ok, payload }) => {
                if (!ok || !payload || !Array.isArray(payload.rows)) {
                    return;
                }
                payload.rows.forEach((row) => {
                    const cell = document.querySelector('[data-active-loan-outstanding="' + row.id + '"]');
                    if (cell && row.outstandingBalance) {
                        cell.textContent = row.outstandingBalance;
                    }
                });
            })
            .catch(() => {});
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
