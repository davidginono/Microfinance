<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/loan-detail-styles.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>

<div class="erp-page-header flex flex-wrap items-start justify-between gap-3">
    <div>
        <p class="erp-breadcrumb">${reviewPanelBreadcrumb}</p>
        <h1 class="erp-page-title">${reviewPanelTitle}</h1>
        <c:if test="${not empty reviewPanelSubtitle}">
            <p class="erp-page-subtitle">${reviewPanelSubtitle}</p>
        </c:if>
    </div>
    <a href="/documents/loan-applications/${app.id}/print" class="app-btn btn-primary">Export</a>
</div>
<div class="loan-view-hero-summary">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6 lg:px-8">
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
    </div>

    <div class="px-5 py-5 sm:px-6 sm:py-6 lg:px-8 lg:py-7">
        <div class="space-y-3"
             data-live-account-status-url="${pageContext.request.contextPath}${reviewBasePath}/loan-applications/${app.id}/applicant-financial-status">
            <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <div class="loan-view-summary-card px-4 py-4">
                    <div class="loan-stat-label"><spring:message code="loan.amount.label" text="Loan Amount" /></div>
                    <div class="loan-stat-value">${app.amount}</div>
                </div>
                <div class="loan-view-summary-card px-4 py-4">
                    <div class="loan-stat-label"><spring:message code="loan.tenor.label" text="Tenor" /></div>
                    <div class="loan-stat-value">${app.tenorMonths} Month<c:if test="${app.tenorMonths ne 1}">s</c:if></div>
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
    <c:if test="${app.status eq 'AWAITING_BOARD'}">
        <div class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            <spring:message code="review.waitingBoardDecision" text="Loan is on review by board. Waiting for decisions." />
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
                <div class="applicant-info-label">Email</div>
                <div class="applicant-info-value">
                    <c:choose>
                        <c:when test="${not empty applicant.email}">${applicant.email}</c:when>
                        <c:otherwise>Not provided</c:otherwise>
                    </c:choose>
                </div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label">Phone</div>
                <div class="applicant-info-value">
                    <c:choose>
                        <c:when test="${not empty applicant.phone}">${applicant.phone}</c:when>
                        <c:otherwise>Not provided</c:otherwise>
                    </c:choose>
                </div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label">Station ID</div>
                <div class="applicant-info-value">
                    <c:choose>
                        <c:when test="${not empty applicant.stationId}">${applicant.stationId}</c:when>
                        <c:otherwise>Not available</c:otherwise>
                    </c:choose>
                </div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label">Quick Context</div>
                <div class="applicant-info-value">
                    ${fn:length(attachments)} attachment(s), ${fn:length(guarantorRequests)} guarantor request(s)
                </div>
            </div>
        </div>
    </div>
</div>

<div class="loan-view-summary-card px-5 py-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
            <p class="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500"><spring:message code="review.applicantActiveLoans" text="Applicant Active Loans" /></p>
            <p class="mt-2 text-base text-slate-600"><spring:message code="review.applicantActiveLoansHelp" text="Other disbursed loans that are still active for this applicant at the time of review." /></p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
            <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                ${activeApplicantLoanCount} active loan<c:if test="${activeApplicantLoanCount ne 1}">s</c:if>
            </span>
            <span class="rounded-full bg-sacco-blue/10 px-3 py-1 text-xs font-semibold text-sacco-blue">
                Total exposure ${activeApplicantLoanTotalAmount}
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

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.details" text="Loan Details" /></h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
        <tr>
            <th class="px-3 py-2 text-left"><spring:message code="common.section" text="Section" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="common.value" text="Value" /></th>
        </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
        <c:forEach items="${financialFields}" var="entry">
            <tr>
                <td class="px-3 py-2 font-medium text-slate-700">${entry.key}</td>
                <td class="px-3 py-2">${entry.value}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty financialFields}">
            <tr><td colspan="2" class="px-3 py-3 text-slate-500">No official financial details loaded yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<c:if test="${not empty disbursementProofAttachments or app.status eq 'FINAL_APPROVED' or app.status eq 'DEFAULTED' or app.status eq 'PAID'}">
    <div class="erp-table-wrap overflow-x-auto">
        <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Disbursement Proof</h5>
        <table class="min-w-full divide-y divide-slate-200 text-sm">
            <thead class="bg-slate-50">
            <tr>
                <th class="px-3 py-2 text-left">File</th>
                <th class="px-3 py-2 text-left">Size</th>
                <th class="px-3 py-2 text-left">Uploaded</th>
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
                            <a href="/documents/loan-applications/${app.id}/attachments/${file.id}/view" target="_blank" rel="noopener" class="app-btn btn-neutral">View</a>
                            <a href="/documents/loan-applications/${app.id}/attachments/${file.id}" class="app-btn btn-primary">Download</a>
                        </div>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty disbursementProofAttachments}">
                <tr><td colspan="4" class="px-3 py-3 text-slate-500">No disbursement proof uploaded yet.</td></tr>
            </c:if>
            </tbody>
        </table>
    </div>
</c:if>

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Application Attachments</h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
        <tr>
            <th class="px-3 py-2 text-left">File</th>
            <th class="px-3 py-2 text-left">Size</th>
            <th class="px-3 py-2 text-left">Uploaded</th>
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

<div class="erp-table-wrap overflow-x-auto">
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

<c:if test="${not empty repaymentSummary}">
    <div class="erp-section">
        <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
            <h5 class="text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.schedule" text="Repayment Schedule" /></h5>
            <c:if test="${not empty repaymentCountdown}">
                <span class="rounded-full bg-sacco-blue/10 px-3 py-1 text-sm font-semibold text-sacco-blue">${repaymentCountdown}</span>
            </c:if>
        </div>
        <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <c:forEach items="${repaymentSummary}" var="entry">
                <div class="erp-section-muted">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">${entry.key}</div>
                    <div class="mt-2 text-sm font-semibold text-slate-800">${entry.value}</div>
                </div>
            </c:forEach>
        </div>
        <c:if test="${not empty repaymentRows}">
            <c:if test="${allowPaymentSync and not empty app.loanId}">
                <div class="mt-4 flex flex-wrap items-center justify-end gap-2">
                    <c:if test="${app.status eq 'DEFAULTED'}">
                        <form action="${reviewBasePath}/loan-applications/${app.id}/recheck-defaulted-payment"
                              method="post"
                              data-confirm-title="Recheck Defaulted Loan"
                              data-confirm-message="Verify this defaulted loan against the payment system now? If the outstanding balance is zero, the status will change to PAID and guarantor capacity will be released."
                              data-confirm-proceed="Recheck Loan">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                            <button type="submit" class="app-btn btn-primary">Recheck Defaulted Loan</button>
                        </form>
                    </c:if>
                    <form action="${reviewBasePath}/loan-applications/${app.id}/sync-payments" method="post">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                        <button type="submit" class="app-btn btn-neutral"><spring:message code="loan.repayment.refresh" text="Refresh Payments" /></button>
                    </form>
                </div>
            </c:if>
            <div class="mt-3 erp-table-wrap overflow-x-auto">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.table.installment" text="Installment" /></th>
                        <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.table.dueDate" text="Due Date" /></th>
                        <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.table.scheduledAmount" text="Scheduled Amount" /></th>
                        <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.table.outstandingBalance" text="Outstanding Balance" /></th>
                        <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.table.principalPaid" text="Principal Paid" /></th>
                        <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.table.interestPaid" text="Interest Paid" /></th>
                        <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.table.totalPaid" text="Total Paid" /></th>
                        <th class="px-3 py-2 text-left"><spring:message code="loan.repayment.table.paymentDate" text="Payment Date" /></th>
                    </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100">
                    <c:forEach items="${repaymentRows}" var="row">
                        <tr>
                            <td class="px-3 py-2">${row.installment}</td>
                            <td class="px-3 py-2">${row.dueDate}</td>
                            <td class="px-3 py-2">
                                <div>${row.amount}</div>
                                <c:if test="${not empty row.scheduledBreakdown}">
                                    <div class="mt-1 text-xs text-slate-500">${row.scheduledBreakdown}</div>
                                </c:if>
                            </td>
                            <td class="px-3 py-2">${row.outstandingBalance}</td>
                            <td class="px-3 py-2">${row.principalPaid}</td>
                            <td class="px-3 py-2">${row.interestPaid}</td>
                            <td class="px-3 py-2">${row.totalPaid}</td>
                            <td class="px-3 py-2">${row.paymentDate}</td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:if>
    </div>
</c:if>

<c:if test="${showReviewDecisionForm}">
    <form action="${reviewBasePath}/loan-applications/${app.id}/decision" method="post" class="erp-form-wrap space-y-3" data-manager-decision-form="true">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <label class="block text-sm font-medium text-slate-700">${reviewCommentLabel}</label>
        <textarea name="reasons" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" placeholder="${reviewCommentPlaceholder}"></textarea>
        <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
            <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">OTP Verification</div>
                    <p class="mt-2 text-sm text-slate-600">Request a one-time code before submitting your manager decision.</p>
                </div>
                <button type="button"
                        class="app-btn btn-primary otp-request-button staff-otp-request inline-flex items-center justify-center gap-2"
                        data-request-url="${reviewBasePath}/loan-applications/${app.id}/request-decision-otp">
                    <span class="otp-button-spinner hidden"></span>
                    <span class="otp-button-label">Send OTP Code</span>
                </button>
            </div>
            <div class="staff-otp-feedback mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
            <div class="mt-3">
                <label class="mb-1 block text-sm font-medium text-slate-700">OTP Code</label>
                <input type="text"
                       name="managerDecisionOtpCode"
                       inputmode="numeric"
                       maxlength="6"
                       autocomplete="one-time-code"
                       required
                       class="w-full rounded-lg border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                       placeholder="123456" />
                <p class="mt-2 text-sm text-slate-500">Enter the 6-digit code sent to your email before approving or rejecting.</p>
            </div>
        </div>
        <div class="flex flex-wrap gap-2">
            <button type="submit" name="decision" value="ACCEPT" class="app-btn btn-primary">${approveActionLabel}</button>
            <button type="submit" name="decision" value="REJECT" class="app-btn btn-reject">${rejectActionLabel}</button>
        </div>
    </form>
</c:if>

<c:if test="${showManagerReversalRequests and not empty pendingManagerStageWithdrawal}">
    <div class="erp-form-wrap space-y-3">
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
                <button type="submit" class="app-btn btn-reject">Decline Removal</button>
            </form>
        </div>
    </div>
</c:if>

<c:if test="${showDisbursementForm}">
    <form action="${reviewBasePath}/loan-applications/${app.id}/finalize"
          method="post"
          enctype="multipart/form-data"
          class="erp-form-wrap space-y-3"
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
                <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="loan.loanId" text="Loan ID" /> <span class="text-rose-600">*</span></label>
                <input type="text" name="loanId" value="${app.loanId}"
                       pattern="[0-9]{4,20}" inputmode="numeric" required maxlength="20"
                       title="Enter the SACCO loan-book number (4-20 digits)"
                       class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                       placeholder="SACCO loan-book number (digits only)" />
                <p class="mt-1 text-xs text-slate-500">Entered at disbursement. 4-20 digits, unique within this SACCO.</p>
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-slate-700">Disbursement Reference</label>
                <input type="text" name="disbursementReference" value="${app.disbursementReference}" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" placeholder="Receipt, voucher, or transfer reference" />
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-slate-700">Tenor Guidance</label>
                <div class="erp-section-muted text-sm text-slate-600">
                    Tenor is ${app.tenorMonths} month(s). The repayment schedule will use the standard monthly cycle and calculate the final due date automatically.
                </div>
            </div>
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-slate-700">Disbursement Proof <span class="text-rose-600">*</span></label>
            <input type="file"
                   name="disbursementProofFile"
                   accept=".pdf,.png,.jpg,.jpeg,.doc,.docx"
                   class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none"
                   required />
            <p class="mt-1 text-xs text-slate-500">Attach the receipt, voucher, or signed proof the applicant can later view.</p>
        </div>
        <div>
            <label class="mb-1 block text-sm font-medium text-slate-700">${disbursementNotesLabel}</label>
            <textarea name="disbursementNotes" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" rows="3" placeholder="Optional disbursement or repayment instructions">${app.disbursementNotes}</textarea>
        </div>
        <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
            <div class="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">OTP Verification</div>
                    <p class="mt-2 text-sm text-slate-600">Request a one-time code before disbursing this loan.</p>
                </div>
                <button type="button"
                        class="app-btn btn-primary otp-request-button staff-otp-request inline-flex items-center justify-center gap-2"
                        data-request-url="${reviewBasePath}/loan-applications/${app.id}/request-disbursement-otp">
                    <span class="otp-button-spinner hidden"></span>
                    <span class="otp-button-label">Send OTP Code</span>
                </button>
            </div>
            <div class="staff-otp-feedback mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
            <div class="mt-3">
                <label class="mb-1 block text-sm font-medium text-slate-700">OTP Code</label>
                <input type="text"
                       name="disbursementOtpCode"
                       inputmode="numeric"
                       maxlength="6"
                       autocomplete="one-time-code"
                       required
                       class="w-full rounded-lg border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                       placeholder="123456" />
                <p class="mt-2 text-sm text-slate-500">Enter the 6-digit code sent to your email before completing disbursement.</p>
            </div>
        </div>
        <button type="submit" class="app-btn btn-approve">${disbursementActionLabel}</button>
    </form>
</c:if>

<c:if test="${showUndoForm}">
    <form action="${reviewBasePath}/loan-applications/${app.id}/undo-decision"
          method="post"
          class="erp-form-wrap"
          data-confirm-title='<spring:message code="review.reverseManagerAction" text="Reverse Manager Action" />'
          data-confirm-message="Return this rejected application to the manager queue for another review?"
          data-confirm-proceed="Return to Queue">
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

        function setOtpButtonState(button, state) {
            const spinner = button.querySelector(".otp-button-spinner");
            const label = button.querySelector(".otp-button-label");
            const loading = state === "loading";
            const sent = state === "sent";
            button.disabled = loading;
            spinner?.classList.toggle("hidden", !loading);
            if (label) {
                label.textContent = loading ? "Sending..." : (sent ? "OTP Sent" : "Send OTP Code");
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

        document.querySelectorAll(".staff-otp-request").forEach((button) => {
            button.addEventListener("click", async () => {
                const form = button.closest("form");
                const feedback = form ? form.querySelector(".staff-otp-feedback") : null;
                const requestUrl = button.dataset.requestUrl;
                if (!requestUrl) {
                    showOtpFeedback(feedback, "error", "OTP request is not configured.");
                    return;
                }
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
                    const payload = await response.json();
                    if (!response.ok || !payload.valid) {
                        throw new Error(payload.message || "Unable to send the OTP code right now.");
                    }
                    setOtpButtonState(button, "sent");
                    showOtpFeedback(feedback, "success", payload.message || "We sent an OTP code to your email.");
                } catch (error) {
                    setOtpButtonState(button, "idle");
                    showOtpFeedback(feedback, "error", error.message || "Unable to send the OTP code right now.");
                }
            });
        });

        document.querySelectorAll("form[data-manager-decision-form='true']").forEach((form) => {
            const reasonsField = form.querySelector("textarea[name='reasons']");
            reasonsField?.addEventListener("input", () => {
                reasonsField.setCustomValidity("");
            });

            form.addEventListener("submit", (event) => {
                const decision = event.submitter ? event.submitter.value : "";
                if (decision === "REJECT") {
                    if (reasonsField && !reasonsField.value.trim()) {
                        event.preventDefault();
                        reasonsField.setCustomValidity("Add a reason before rejecting this loan application.");
                        reasonsField.reportValidity();
                        reasonsField.focus();
                        return;
                    }
                    form.dataset.confirmEyebrow = "Confirm Rejection";
                    form.dataset.confirmTitle = "Reject Loan Review";
                    form.dataset.confirmMessage = "Reject this loan application? The applicant will see the rejection and the application will leave this review queue.";
                    form.dataset.confirmProceed = "Reject Review";
                    return;
                }
                if (decision === "ACCEPT") {
                    form.dataset.confirmEyebrow = "Confirm Approval";
                    form.dataset.confirmTitle = "Approve Loan Review";
                    form.dataset.confirmMessage = "Approve this loan application review? Your decision will be recorded and the application will move to the next configured workflow step.";
                    form.dataset.confirmProceed = "Approve Review";
                }
            });
        });
    })();
</script>

<%@ include file="../fragments/confirm-modal.jspf" %>
<%@ include file="../fragments/guarantor-financial-fetch.jspf" %>
<%@ include file="../fragments/live-account-status-hydration.jspf" %>
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
