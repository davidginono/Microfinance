<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/loan-detail-styles.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Manager Panel / Review Detail</p>
    <h1 class="erp-page-title">Manager Review</h1>
</div>
<div class="loan-view-hero-summary">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6 lg:px-8">
        <div class="min-w-0">
            <div class="loan-hero-primary-grid">
                <div class="loan-hero-inline-fact">
                    <div class="loan-hero-inline-label">Loan Application ID</div>
                    <div class="loan-hero-inline-value loan-hero-inline-value--id">${loanIdShort}</div>
                </div>
                <c:if test="${not empty disbursedLoanId}">
                    <div class="loan-hero-inline-fact">
                        <div class="loan-hero-inline-label">Loan ID</div>
                        <div class="loan-hero-inline-value loan-hero-inline-value--id">${disbursedLoanId}</div>
                    </div>
                </c:if>
                <div class="loan-hero-inline-fact">
                    <div class="loan-hero-inline-label">Status</div>
                    <div class="loan-hero-inline-value">
                        <span class="inline-flex items-center rounded-full px-3 py-1 text-sm font-semibold ${managerStatusBadgeClass}">
                            <spring:message code="loan.status.${app.status}" text="${app.status}" />
                        </span>
                    </div>
                </div>
            </div>
            <div class="mt-4 loan-hero-facts-grid sm:grid-cols-2">
                <div class="loan-hero-fact">
                    <div class="loan-hero-fact-label">Applicant Name</div>
                    <div class="loan-hero-fact-value">${applicant.fullName}</div>
                </div>
                <div class="loan-hero-fact">
                    <div class="loan-hero-fact-label">Loan Type</div>
                    <div class="loan-hero-fact-value"><spring:message code="loan.type.${app.loanType}" text="${app.loanType}" /></div>
                </div>
            </div>
        </div>
    </div>

    <div class="px-5 py-5 sm:px-6 sm:py-6 lg:px-8 lg:py-7">
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div class="loan-view-summary-card px-4 py-4">
            <div class="loan-stat-label">Loan Amount</div>
            <div class="loan-stat-value">${app.amount}</div>
        </div>
        <div class="loan-view-summary-card px-4 py-4">
            <div class="loan-stat-label">Tenor</div>
            <div class="loan-stat-value">${app.tenorMonths} Month<c:if test="${app.tenorMonths ne 1}">s</c:if></div>
        </div>
            <div class="loan-view-summary-card px-4 py-4">
                <div class="loan-stat-label">Current Savings</div>
                <div class="loan-stat-value">${applicantExternalAccountStatus.savingsLabel}</div>
            </div>
            <div class="loan-view-summary-card px-4 py-4">
                <div class="loan-stat-label">Current Shares</div>
                <div class="loan-stat-value">${applicantExternalAccountStatus.sharesLabel}</div>
            </div>
    </div>

    <div class="mt-6 loan-simple-progress">
        <h3 class="text-lg font-semibold text-slate-900">Progress</h3>
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

<div class="mt-4 space-y-4">
    <c:if test="${not applicantExternalAccountStatus.available}">
        <div class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            ${applicantExternalAccountStatus.statusMessage}
        </div>
    </c:if>
    <c:if test="${not empty managerReason}">
        <div class="rounded-lg border border-sacco-brown/30 bg-[#f7efe9] px-4 py-3 text-sm text-sacco-brown">
            <strong>Current Rejection Reason:</strong> ${managerReason}
        </div>
    </c:if>
    <c:if test="${app.status eq 'AWAITING_BOARD'}">
        <div class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            Loan is on review by board. Waiting for decisions.
        </div>
    </c:if>
    <div class="loan-view-summary-card px-5 py-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
                <p class="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500">Applicant Details</p>
                <p class="mt-2 text-base text-slate-600">Key applicant information for review before making a decision.</p>
            </div>
            <c:if test="${not empty repaymentCountdown}">
                <span class="rounded-full bg-sacco-blue/10 px-3 py-1 text-xs font-semibold text-sacco-blue">${repaymentCountdown}</span>
            </c:if>
        </div>
        <div class="mt-5 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            <div class="applicant-info-card">
                <div class="applicant-info-label">Full Name</div>
                <div class="applicant-info-value">${applicant.fullName}</div>
            </div>
            <div class="applicant-info-card">
                <div class="applicant-info-label">Member Number</div>
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
            <p class="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500">Applicant Active Loans</p>
            <p class="mt-2 text-base text-slate-600">Other disbursed loans that are still active for this applicant at the time of review.</p>
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
                    <div class="applicant-info-label">Active Loans</div>
                    <div class="applicant-info-value">${activeApplicantLoanCount}</div>
                </div>
                <div class="applicant-info-card">
                    <div class="applicant-info-label">Total Active Amount</div>
                    <div class="applicant-info-value">${activeApplicantLoanTotalAmount}</div>
                </div>
            </div>
            <div class="mt-5 erp-table-wrap overflow-x-auto">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th class="px-3 py-2 text-left">Loan</th>
                        <th class="px-3 py-2 text-left">Type</th>
                        <th class="px-3 py-2 text-left">Amount</th>
                        <th class="px-3 py-2 text-left">Installment</th>
                        <th class="px-3 py-2 text-left">Disbursed</th>
                        <th class="px-3 py-2 text-left">Final Due</th>
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
                This applicant has no other active disbursed loans at the moment.
            </div>
        </c:otherwise>
    </c:choose>
</div>

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Loan Details</h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
        <tr>
            <th class="px-3 py-2 text-left">Section</th>
            <th class="px-3 py-2 text-left">Value</th>
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

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Attachments</h5>
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
                <td class="px-3 py-2"><a href="/documents/loan-applications/${app.id}/attachments/${file.id}" class="app-btn btn-primary">Download</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty attachments}">
            <tr><td colspan="4" class="px-3 py-3 text-slate-500">No attachments uploaded.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Guarantor Requests</h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
        <tr>
            <th class="px-3 py-2 text-left">Guarantor</th>
            <th class="px-3 py-2 text-left">Status</th>
            <th class="px-3 py-2 text-left">Date</th>
            <th class="px-3 py-2 text-left">Financial Status</th>
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
                            data-url="/manager/loan-applications/${app.id}/guarantors/${g.guarantorMemberId}/financial-status">
                        <span class="guarantor-financial-spinner hidden" data-financial-spinner></span>
                        <span data-financial-label>Load Status</span>
                    </button>
                    <div class="guarantor-financial-result hidden">
                        <div class="guarantor-financial-result-row">
                            <span class="guarantor-financial-result-label">Savings</span>
                            <span class="guarantor-financial-result-value" data-financial-savings>-</span>
                        </div>
                        <div class="guarantor-financial-result-row">
                            <span class="guarantor-financial-result-label">Shares</span>
                            <span class="guarantor-financial-result-value" data-financial-shares>-</span>
                        </div>
                        <div class="guarantor-financial-result-note" data-financial-note></div>
                    </div>
                </td>
            </tr>
        </c:forEach>
        <c:if test="${empty guarantorRequests}">
            <tr><td colspan="4" class="px-3 py-3 text-slate-500">No guarantors selected yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<c:if test="${not empty repaymentSummary}">
    <div class="erp-section">
        <div class="mb-4 flex flex-wrap items-center justify-between gap-3">
            <h5 class="text-sm font-semibold uppercase tracking-wide text-slate-500">Repayment Schedule</h5>
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
            <c:if test="${not empty app.loanId}">
                <form action="/manager/loan-applications/${app.id}/sync-payments" method="post" class="mt-4 flex items-center justify-end">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <button type="submit" class="app-btn btn-neutral">Refresh Payments</button>
                </form>
            </c:if>
            <div class="mt-3 erp-table-wrap overflow-x-auto">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th class="px-3 py-2 text-left">Installment</th>
                        <th class="px-3 py-2 text-left">Due Date</th>
                        <th class="px-3 py-2 text-left">Scheduled Amount</th>
                        <th class="px-3 py-2 text-left">Principal Paid</th>
                        <th class="px-3 py-2 text-left">Interest Paid</th>
                        <th class="px-3 py-2 text-left">Total Paid</th>
                        <th class="px-3 py-2 text-left">Payment Date</th>
                        <th class="px-3 py-2 text-left">Status</th>
                    </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100">
                    <c:forEach items="${repaymentRows}" var="row">
                        <tr>
                            <td class="px-3 py-2">${row.installment}</td>
                            <td class="px-3 py-2">${row.dueDate}</td>
                            <td class="px-3 py-2">${row.amount}</td>
                            <td class="px-3 py-2">${row.principalPaid}</td>
                            <td class="px-3 py-2">${row.interestPaid}</td>
                            <td class="px-3 py-2">${row.totalPaid}</td>
                            <td class="px-3 py-2">${row.paymentDate}</td>
                            <td class="px-3 py-2">${row.status}</td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:if>
    </div>
</c:if>

<c:if test="${app.status eq 'READY_FOR_MANAGER'}">
    <form action="/manager/loan-applications/${app.id}/decision" method="post" class="erp-form-wrap space-y-3">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <label class="block text-sm font-medium text-slate-700">Reasons</label>
        <textarea name="reasons" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" placeholder="Write the reason for approval note or rejection"></textarea>
        <div class="flex flex-wrap gap-2">
            <button type="submit" name="decision" value="ACCEPT" class="app-btn btn-primary">Approve Loan</button>
            <button type="submit" name="decision" value="REJECT" class="app-btn btn-reject">Reject Loan</button>
        </div>
    </form>
</c:if>

<c:if test="${not empty pendingManagerStageWithdrawal}">
    <div class="erp-form-wrap space-y-3">
        <div class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            The applicant wants this loan application removed while it is still under manager review. Review the request before anything changes.
        </div>
        <div class="flex flex-wrap gap-2">
            <form action="/manager/loan-applications/${app.id}/reversal-requests/${pendingManagerStageWithdrawal.id}/approve"
                  method="post"
                  data-confirm-title="Approve Removal"
                  data-confirm-message="Approve this request and remove the application from manager review?"
                  data-confirm-proceed="Approve Removal">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <button type="submit" class="app-btn btn-primary">Approve Removal</button>
            </form>
            <form action="/manager/loan-applications/${app.id}/reversal-requests/${pendingManagerStageWithdrawal.id}/reject"
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

<c:if test="${app.status eq 'BOARD_APPROVED'}">
    <form action="/manager/loan-applications/${app.id}/finalize"
          method="post"
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
            <span class="rounded-full bg-emerald-50 px-3 py-1 text-sm font-semibold text-emerald-700">Board quorum reached</span>
        </div>
        <div class="grid gap-4 md:grid-cols-2">
            <div>
                <label class="mb-1 block text-sm font-medium text-slate-700">Disbursement Date</label>
                <input type="date" name="disbursementDate" value="${app.disbursementDate}" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required />
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-slate-700">First Repayment Date</label>
                <input type="date" name="firstRepaymentDate" value="${app.firstRepaymentDate}" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required />
            </div>
            <div>
                <label class="mb-1 block text-sm font-medium text-slate-700">Loan ID <span class="text-rose-600">*</span></label>
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
            <label class="mb-1 block text-sm font-medium text-slate-700">Manager Notes</label>
            <textarea name="disbursementNotes" class="w-full rounded-lg border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" rows="3" placeholder="Optional disbursement or repayment instructions">${app.disbursementNotes}</textarea>
        </div>
        <button type="submit" class="app-btn btn-approve">Disburse Loan</button>
    </form>
</c:if>

<c:if test="${app.status eq 'BOARD_REJECTED'}">
    <form action="/manager/loan-applications/${app.id}/finalize" method="post" class="erp-form-wrap space-y-3">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <input type="hidden" name="decision" value="FINAL_REJECT" />
        <label class="block text-sm font-medium text-slate-700">Finalize Decision</label>
        <p class="text-sm text-slate-500">Board quorum rejected this application. Finalize to close the workflow.</p>
        <button type="submit" class="app-btn btn-reject">Finalize Rejection</button>
    </form>
</c:if>

<c:if test="${app.status eq 'MANAGER_REJECTED'}">
    <form action="/manager/loan-applications/${app.id}/undo-decision"
          method="post"
          class="erp-form-wrap"
          data-confirm-title="Reverse Manager Action"
          data-confirm-message="Return this rejected application to the manager queue for another review?"
          data-confirm-proceed="Return to Queue">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <button type="submit"
                class="app-btn btn-neutral ${managerUndoWindowOpen ? '' : 'action-button-disabled'}"
                ${managerUndoWindowOpen ? '' : 'disabled'}>
            Reverse Last Manager Action
        </button>
    </form>
</c:if>

<div class="erp-form-wrap loan-page-bottom-actions">
    <div class="loan-view-action-cluster">
        <a href="/documents/loan-applications/${app.id}/print" class="app-btn btn-primary">Print Loan Application</a>
    </div>
</div>

<%@ include file="../fragments/confirm-modal.jspf" %>
<%@ include file="../fragments/guarantor-financial-fetch.jspf" %>

<%@ include file="../fragments/footer.jspf" %>
