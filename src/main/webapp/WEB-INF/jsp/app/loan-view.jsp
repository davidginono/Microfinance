<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/loan-detail-styles.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>
<style>
    @keyframes otp-pop {
        0% { transform: translateY(4px) scale(0.82); opacity: 0; }
        100% { transform: translateY(0) scale(1); opacity: 1; }
    }

    .otp-checkmark-pop {
        animation: otp-pop 180ms ease-out;
    }
    .loan-repayment-summary {
        border: 1px solid #d7e1ea;
        border-radius: 0.35rem;
        background: linear-gradient(180deg, #f8fbfc 0%, #ffffff 100%);
        padding: 1rem 1.05rem;
    }
    .loan-repayment-chip {
        display: inline-flex;
        align-items: center;
        border-radius: 9999px;
        border: 1px solid #cfe5ee;
        background: #eef7fa;
        padding: 0.4rem 0.8rem;
        font-size: 0.8rem;
        font-weight: 700;
        color: #1b6f8a;
        white-space: nowrap;
    }
    .loan-repayment-chip.is-paid {
        border-color: #bbf7d0;
        background: #f0fdf4;
        color: #15803d;
    }
    .loan-repayment-chip.is-defaulted {
        border-color: #fecaca;
        background: #fff1f2;
        color: #b91c1c;
    }
    .loan-repayment-grid {
        display: grid;
        gap: 0.85rem;
        grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
    }
    .loan-repayment-card {
        border: 1px solid #d7e1ea;
        border-radius: 0.35rem;
        background: #ffffff;
        padding: 0.95rem 1rem;
    }
    .loan-repayment-card-value {
        margin-top: 0.55rem;
        font-size: 1rem;
        font-weight: 600;
        line-height: 1.4;
        color: #0f172a;
    }
    .loan-repayment-meta-grid {
        display: grid;
        gap: 0.85rem;
        grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
    }
    .loan-repayment-note {
        border: 1px solid #d7e1ea;
        border-radius: 0.35rem;
        background: #ffffff;
        padding: 0.95rem 1rem;
    }
    .loan-repayment-note-value {
        margin-top: 0.45rem;
        font-size: 0.94rem;
        line-height: 1.55;
        color: #334155;
    }
    .loan-repayment-empty {
        border: 1px dashed #cbd5e1;
        border-radius: 0.35rem;
        background: #f8fafc;
        padding: 1rem;
        font-size: 0.94rem;
        color: #64748b;
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Member Workspace / Application Detail</p>
    <h1 class="erp-page-title"><spring:message code="loan.detail" /></h1>
    <p class="erp-page-subtitle">Review your application information, decision feedback, repayment schedule, and supporting records.</p>
</div>
<c:if test="${app.status eq 'AWAITING_GUARANTORS'}">
    <div class="mb-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
        Waiting for guarantor approval. This page auto-refreshes every 1 hour.
    </div>
    <script>
        setTimeout(function () { window.location.reload(); }, 3600000);
    </script>
</c:if>
<c:if test="${app.status eq 'ALL_GUARANTORS_APPROVED'}">
    <div class="mb-3 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
        All guarantors have approved this application. You can now submit it for manager review.
    </div>
</c:if>

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
                        <span class="inline-flex items-center rounded-full px-3 py-1 text-sm font-semibold ${loanStatusBadgeClass}">
                            <spring:message code="loan.status.${app.status}" text="${app.status}" />
                        </span>
                    </div>
                </div>
            </div>
            <div class="mt-4 loan-hero-facts-grid sm:grid-cols-2">
                <div class="loan-hero-inline-fact">
                    <div class="loan-hero-inline-label">Loan Type</div>
                    <div class="loan-hero-inline-value"><spring:message code="loan.type.${app.loanType}"/></div>
                </div>
                <c:if test="${not empty formFields['Loan Purpose']}">
                    <div class="loan-hero-inline-fact">
                        <div class="loan-hero-inline-label">Loan Purpose</div>
                        <div class="loan-hero-inline-value">${formFields['Loan Purpose']}</div>
                    </div>
                </c:if>
            </div>
        </div>
    </div>

    <div class="px-5 py-5 sm:px-6 sm:py-6 lg:px-8 lg:py-7">
        <div class="space-y-3">
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
            <c:if test="${not applicantExternalAccountStatus.available}">
                <div class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                    ${applicantExternalAccountStatus.statusMessage}
                </div>
            </c:if>
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
    <c:if test="${not empty managerReason}">
        <div class="rounded-lg border border-sacco-brown/30 bg-[#f7efe9] px-4 py-3 text-sm text-sacco-brown">
            <strong>Manager Reason:</strong> ${managerReason}
        </div>
    </c:if>
</div>

<c:if test="${app.status eq 'DRAFT'}">
    <div class="erp-section mt-4">
        <div class="space-y-4">
            <div class="rounded-lg border border-slate-200 bg-white px-4 py-4 text-sm leading-7 text-slate-700">
                <p>
                    <strong>Applicant Declaration:</strong>
                    I,
                    <strong>
                        <c:choose>
                            <c:when test="${not empty currentMember and not empty currentMember.fullName}">${currentMember.fullName}</c:when>
                            <c:otherwise>the applicant</c:otherwise>
                        </c:choose>
                    </strong>,
                    guarantee that I shall make the loan repayments as decided by the loan approval committee and in case of default,
                    I accept to pay loan penalty as specified by the IAA SACCOS LTD by laws. I also accept to adhere to all terms and
                    conditions as stipulated on the attached loan contract.
                </p>
            </div>
            <form action="/app/loan-applications/${app.id}/submit" method="post" class="space-y-3">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <c:choose>
                    <c:when test="${app.requiredGuarantors gt 0}">
                        <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4 text-sm text-slate-600">
                            The applicant OTP will be available only after all guarantors have approved this application.
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
                            <div class="flex flex-wrap items-center justify-between gap-3">
                                <div>
                                    <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">OTP Verification</div>
                                    <p class="mt-2 text-sm text-slate-600">Request a one-time code before sending this application.</p>
                                </div>
                                <button type="button" class="app-btn btn-primary otp-request-button signature-otp-request inline-flex items-center justify-center gap-2">
                                    <span class="otp-button-spinner hidden"></span>
                                    <span class="otp-button-label">Send OTP Code</span>
                                </button>
                            </div>
                            <div data-auto-scroll-message="true" class="signature-otp-feedback mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
                            <div class="mt-3">
                                <label class="mb-1 block text-sm font-medium text-slate-700">OTP Code</label>
                                <input type="text" name="applicantSignatureOtpCode"
                                       inputmode="numeric" maxlength="6" autocomplete="one-time-code"
                                       class="w-full rounded-lg border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                                       placeholder="123456" />
                                <div class="signature-otp-live-status mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                                    <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
                                    <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                        <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                                    </svg>
                                    <span data-otp-text>Checking code...</span>
                                </div>
                            </div>
                        </div>
                    </c:otherwise>
                </c:choose>
                <c:choose>
                    <c:when test="${app.requiredGuarantors gt 0}">
                        <button type="submit" class="app-btn btn-approve">Send to Guarantors</button>
                        <p class="text-sm text-slate-500">The Submit button will appear after all required guarantors have approved.</p>
                    </c:when>
                    <c:otherwise>
                        <button type="submit" class="app-btn btn-approve">Submit</button>
                    </c:otherwise>
                </c:choose>
            </form>
        </div>
    </div>
</c:if>

<c:if test="${app.status eq 'READY_FOR_MANAGER' and not empty pendingManagerStageWithdrawal}">
    <div class="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
        Your application removal request is already waiting for the manager's decision.
    </div>
</c:if>

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Loan Details
    </h5>
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
                <tr>
                    <td colspan="2" class="px-3 py-3 text-slate-500">Financial details have not been loaded for this
                        application yet.</td>
                </tr>
            </c:if>
        </tbody>
    </table>
</div>

<c:if test="${app.status eq 'FINAL_APPROVED' or app.status eq 'DEFAULTED' or app.status eq 'PAID'}">
    <div class="erp-section">
        <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
            <div>
                <h5 class="text-sm font-semibold uppercase tracking-wide text-slate-500">Repayment Plan</h5>
                <p class="mt-1 text-sm text-slate-500">
                    <c:choose>
                        <c:when test="${app.status eq 'PAID'}">This loan was disbursed and fully cleared. The schedule below shows the agreed repayment trail.</c:when>
                        <c:when test="${app.status eq 'DEFAULTED'}">This loan reached the final due date with an outstanding balance. Use the repayment details below to track what is still unpaid.</c:when>
                        <c:otherwise>This disbursed loan now follows the repayment timetable below.</c:otherwise>
                    </c:choose>
                </p>
            </div>
            <div class="flex flex-wrap items-center justify-end gap-2">
                <c:if test="${not empty app.loanId}">
                    <form action="/app/loan-applications/${app.id}/sync-payments" method="post" class="inline-flex">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                        <button type="submit" class="app-btn btn-neutral">Refresh Payments</button>
                    </form>
                </c:if>
                <span class="loan-repayment-chip<c:if test="${app.status eq 'PAID'}"> is-paid</c:if><c:if test="${app.status eq 'DEFAULTED'}"> is-defaulted</c:if>">
                    <c:choose>
                        <c:when test="${app.status eq 'PAID'}">&#10003; Fully Paid</c:when>
                        <c:when test="${app.status eq 'DEFAULTED'}">Defaulted / Not Paid</c:when>
                        <c:otherwise>Active Schedule</c:otherwise>
                    </c:choose>
                </span>
                <c:if test="${not empty repaymentCountdown and app.status ne 'PAID'}">
                    <span class="loan-repayment-chip">${repaymentCountdown}</span>
                </c:if>
            </div>
        </div>
        <div class="loan-repayment-summary">
            <div class="loan-repayment-grid">
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">Installment Amount</div>
                    <div class="loan-repayment-card-value">
                        <c:out value="${repaymentSummary['Installment Amount']}" default="Pending update" />
                    </div>
                </div>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">Repayment Frequency</div>
                    <div class="loan-repayment-card-value">
                        <c:out value="${repaymentSummary['Repayment Frequency']}" default="Pending update" />
                    </div>
                </div>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">First Repayment</div>
                    <div class="loan-repayment-card-value">
                        <c:out value="${repaymentSummary['First Repayment Date']}" default="Pending update" />
                    </div>
                </div>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                        <c:choose>
                            <c:when test="${app.status eq 'PAID'}">Paid On</c:when>
                            <c:otherwise>Final Due Date</c:otherwise>
                        </c:choose>
                    </div>
                    <div class="loan-repayment-card-value">
                        <c:choose>
                            <c:when test="${app.status eq 'PAID'}">
                                <c:out value="${repaymentSummary['Paid At']}" default="${repaymentSummary['Final Due Date']}" />
                            </c:when>
                            <c:otherwise>
                                <c:out value="${repaymentSummary['Final Due Date']}" default="Pending update" />
                            </c:otherwise>
                        </c:choose>
                    </div>
                </div>
            </div>
            <div class="mt-4 loan-repayment-meta-grid">
                <c:if test="${not empty repaymentSummary['Installments']}">
                    <div class="loan-repayment-note">
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">Installments</div>
                        <div class="loan-repayment-note-value">${repaymentSummary['Installments']}</div>
                    </div>
                </c:if>
                <c:if test="${not empty repaymentSummary['Disbursement Date']}">
                    <div class="loan-repayment-note">
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">Disbursement Date</div>
                        <div class="loan-repayment-note-value">${repaymentSummary['Disbursement Date']}</div>
                    </div>
                </c:if>
                <c:if test="${not empty repaymentSummary['Disbursement Reference']}">
                    <div class="loan-repayment-note">
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">Disbursement Reference</div>
                        <div class="loan-repayment-note-value">${repaymentSummary['Disbursement Reference']}</div>
                    </div>
                </c:if>
                <c:if test="${app.status ne 'PAID' and not empty repaymentDaysLeft}">
                    <div class="loan-repayment-note">
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <c:choose>
                                <c:when test="${app.status eq 'DEFAULTED'}">Overdue</c:when>
                                <c:otherwise>Time Left</c:otherwise>
                            </c:choose>
                        </div>
                        <div class="loan-repayment-note-value">
                            <c:choose>
                                <c:when test="${app.status eq 'DEFAULTED'}">
                                    <c:set var="overdueDays" value="${0 - repaymentDaysLeft}" />
                                    ${overdueDays} day<c:if test="${overdueDays ne 1}">s</c:if> overdue
                                </c:when>
                                <c:otherwise>
                                    ${repaymentDaysLeft} day<c:if test="${repaymentDaysLeft ne 1}">s</c:if> remaining
                                    <c:if test="${not empty repaymentMonthsLeft or not empty repaymentWeeksLeft}">
                                        <span class="text-slate-400">|</span>
                                        ${repaymentMonthsLeft} month<c:if test="${repaymentMonthsLeft ne 1}">s</c:if>,
                                        ${repaymentWeeksLeft} week<c:if test="${repaymentWeeksLeft ne 1}">s</c:if>
                                    </c:if>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>
                </c:if>
                <c:if test="${app.status eq 'PAID' and not empty repaymentSummary['Final Due Date']}">
                    <div class="loan-repayment-note">
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">Original Final Due Date</div>
                        <div class="loan-repayment-note-value">${repaymentSummary['Final Due Date']}</div>
                    </div>
                </c:if>
                <c:if test="${not empty repaymentSummary['Manager Notes']}">
                    <div class="loan-repayment-note">
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">Manager Notes</div>
                        <div class="loan-repayment-note-value">${repaymentSummary['Manager Notes']}</div>
                    </div>
                </c:if>
            </div>
        </div>
        <c:choose>
            <c:when test="${not empty repaymentRows}">
                <div class="mt-5 erp-table-wrap overflow-x-auto">
                    <div class="border-b border-slate-200 bg-slate-50 px-4 py-3">
                        <div class="text-sm font-semibold text-slate-900">Installment Schedule</div>
                        <div class="mt-1 text-sm text-slate-500">A quick view of each expected repayment in order.</div>
                    </div>
                    <table class="erp-table">
                        <thead>
                            <tr>
                                <th class="px-3 py-2 text-left">Installment</th>
                                <th class="px-3 py-2 text-left">Due Date</th>
                                <th class="px-3 py-2 text-left">Scheduled Amount</th>
                                <th class="px-3 py-2 text-left">Outstanding Balance</th>
                                <th class="px-3 py-2 text-left">Principal Paid</th>
                                <th class="px-3 py-2 text-left">Interest Paid</th>
                                <th class="px-3 py-2 text-left">Total Paid</th>
                                <th class="px-3 py-2 text-left">Payment Date</th>
                            </tr>
                        </thead>
                        <tbody class="divide-y divide-slate-100">
                            <c:forEach items="${repaymentRows}" var="row">
                                <tr>
                                    <td class="px-3 py-2 font-medium text-slate-700">${row.installment}</td>
                                    <td class="px-3 py-2">${row.dueDate}</td>
                                    <td class="px-3 py-2 font-medium text-slate-900">${row.amount}</td>
                                    <td class="px-3 py-2">${row.outstandingBalance}</td>
                                    <td class="px-3 py-2">${row.principalPaid}</td>
                                    <td class="px-3 py-2">${row.interestPaid}</td>
                                    <td class="px-3 py-2 font-medium text-slate-900">${row.totalPaid}</td>
                                    <td class="px-3 py-2">${row.paymentDate}</td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:when>
            <c:otherwise>
                <div class="mt-5 loan-repayment-empty">
                    Repayment schedule details are not available yet for this disbursed loan. Core repayment dates will appear here once the schedule is fully posted.
                </div>
            </c:otherwise>
        </c:choose>
    </div>
</c:if>

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
                                <a href="/documents/loan-applications/${app.id}/attachments/${file.id}?inline=true" target="_blank" rel="noopener" class="app-btn btn-neutral">View</a>
                                <a href="/documents/loan-applications/${app.id}/attachments/${file.id}" class="app-btn btn-primary">Download</a>
                            </div>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty disbursementProofAttachments}">
                    <tr>
                        <td colspan="4" class="px-3 py-3 text-slate-500">No disbursement proof has been uploaded for this loan yet.</td>
                    </tr>
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
                    <td class="px-3 py-2"><a href="/documents/loan-applications/${app.id}/attachments/${file.id}"
                            class="app-btn btn-primary">Download</a></td>
                </tr>
            </c:forEach>
            <c:if test="${empty attachments}">
                <tr>
                    <td colspan="4" class="px-3 py-3 text-slate-500">No attachments uploaded.</td>
                </tr>
            </c:if>
        </tbody>
    </table>
</div>

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
        <spring:message code="loan.guarantors" />
    </h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
            <tr>
                <th class="px-3 py-2 text-left">Guarantor</th>
                <th class="px-3 py-2 text-left">Status</th>
                <th class="px-3 py-2 text-left">Date</th>
                <th class="px-3 py-2 text-left">Removal Request</th>
            </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
            <c:forEach items="${guarantorRequests}" var="req">
                <tr>
                    <td class="px-3 py-2">
                        <c:choose>
                            <c:when test="${not empty guarantorNames[req.guarantorMemberId]}">
                                ${guarantorNames[req.guarantorMemberId]}</c:when>
                            <c:otherwise>#${fn:substring(req.guarantorMemberId, 0, 8)}</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2">${req.status}</td>
                    <td class="px-3 py-2">
                        <c:choose>
                            <c:when test="${not empty req.decidedAt}">${fn:replace(fn:substring(req.decidedAt, 0, 16),
                                'T', ' ')}</c:when>
                            <c:otherwise>${fn:replace(fn:substring(req.createdAt, 0, 16), 'T', ' ')}</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2">
                        <c:set var="pendingUndo" value="${pendingGuarantorUndoRequests[req.id]}" />
                        <c:choose>
                            <c:when test="${not empty pendingUndo}">
                                <div class="space-y-2">
                                    <div class="text-sm text-amber-700">Guarantor asked to be removed from this loan.</div>
                                    <div class="flex flex-wrap gap-2">
                                        <form action="/app/loan-applications/${app.id}/guarantor-reversal-requests/${pendingUndo.id}/approve"
                                              method="post"
                                              data-confirm-title="Approve Removal"
                                              data-confirm-message="Approve this request and remove the guarantor from this application?"
                                              data-confirm-proceed="Approve Removal">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                            <button type="submit" class="app-btn btn-primary">Approve Removal</button>
                                        </form>
                                        <form action="/app/loan-applications/${app.id}/guarantor-reversal-requests/${pendingUndo.id}/reject"
                                              method="post"
                                              data-confirm-title="Keep Guarantor"
                                              data-confirm-message="Keep this guarantor on the loan and decline the removal request?"
                                              data-confirm-proceed="Keep Guarantor">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                            <button type="submit" class="app-btn btn-reject">Keep Guarantor</button>
                                        </form>
                                    </div>
                                </div>
                            </c:when>
                            <c:otherwise>
                                <span class="text-slate-400">-</span>
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty guarantorRequests}">
                <c:choose>
                    <c:when test="${not empty draftSelectedGuarantors}">
                        <c:forEach items="${draftSelectedGuarantors}" var="draftGuarantor">
                            <tr>
                                <td class="px-3 py-2">${draftGuarantor}</td>
                                <td class="px-3 py-2">SELECTED IN DRAFT</td>
                                <td class="px-3 py-2">-</td>
                                <td class="px-3 py-2">-</td>
                            </tr>
                        </c:forEach>
                    </c:when>
                    <c:otherwise>
                        <tr>
                            <td colspan="4" class="px-3 py-3 text-slate-500">No guarantors selected yet.</td>
                        </tr>
                    </c:otherwise>
                </c:choose>
            </c:if>
        </tbody>
    </table>
</div>

<c:set var="showPrintAction" value="${canPrint}" />
<c:set var="showEditAction" value="${app.status eq 'DRAFT'}" />
<c:set var="showTopUpAction" value="${canRequestTopUp}" />
<c:set var="showMemberReversalAction" value="${app.status eq 'AWAITING_GUARANTORS' or app.status eq 'ALL_GUARANTORS_APPROVED' or app.status eq 'READY_FOR_MANAGER'}" />
<c:set var="showDeleteAction" value="${app.status eq 'DRAFT' or app.status eq 'AWAITING_GUARANTORS'}" />
<c:set var="showLoanActionCard" value="${showPrintAction or showEditAction or showTopUpAction or (showMemberReversalAction and app.status ne 'ALL_GUARANTORS_APPROVED') or showDeleteAction}" />
<c:if test="${showLoanActionCard}">
    <div class="erp-form-wrap loan-page-bottom-actions">
        <div class="loan-view-action-cluster">
            <c:if test="${showPrintAction}">
                <a href="/documents/loan-applications/${app.id}/print" class="app-btn btn-primary">
                    Print Loan Application
                </a>
            </c:if>
            <c:if test="${showEditAction}">
                <a href="/app/loan-applications/${app.id}/edit" class="app-btn btn-neutral">
                    Re-edit
                </a>
            </c:if>
            <c:if test="${showTopUpAction}">
                <a href="/app/loan-applications/new?loanType=${app.loanType}&topUpLoanId=${app.id}" class="app-btn btn-neutral">
                    Request Loan Top-Up
                </a>
            </c:if>
            <c:if test="${showMemberReversalAction and app.status ne 'ALL_GUARANTORS_APPROVED'}">
                <c:choose>
                    <c:when test="${app.status eq 'READY_FOR_MANAGER'}">
                        <c:choose>
                            <c:when test="${not empty pendingManagerStageWithdrawal}">
                                <button type="button" class="app-btn btn-neutral action-button-disabled" disabled>Removal Request Sent</button>
                            </c:when>
                            <c:otherwise>
                                <form action="/app/loan-applications/${app.id}/cancel"
                                      method="post"
                                      data-confirm-title="Request Manager Removal"
                                      data-confirm-message="Send a removal request to the manager? If approved, this application will be removed from review."
                                      data-confirm-proceed="Send Request">
                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                    <button type="submit"
                                            class="app-btn btn-neutral ${memberReversalWindowOpen ? '' : 'action-button-disabled'}"
                                            ${memberReversalWindowOpen ? '' : 'disabled'}>
                                        Request Manager Removal
                                    </button>
                                </form>
                            </c:otherwise>
                        </c:choose>
                    </c:when>
                    <c:otherwise>
                        <form action="/app/loan-applications/${app.id}/cancel"
                              method="post"
                              data-confirm-title="Cancel Submission"
                              data-confirm-message="Move this application back to draft so you can keep editing it?"
                              data-confirm-proceed="Move to Draft">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                            <button type="submit"
                                    class="app-btn btn-neutral ${memberReversalWindowOpen ? '' : 'action-button-disabled'}"
                                    ${memberReversalWindowOpen ? '' : 'disabled'}>
                                Cancel Submission
                            </button>
                        </form>
                    </c:otherwise>
                </c:choose>
            </c:if>
            <c:if test="${showDeleteAction}">
                <form action="/app/loan-applications/${app.id}/delete"
                      method="post"
                      data-confirm-title="Delete Application"
                      data-confirm-message="Delete this application completely? This will remove it from your view and from every review queue."
                      data-confirm-proceed="Delete Application">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <button type="submit" class="app-btn btn-reject">Remove Application</button>
                </form>
            </c:if>
        </div>
    </div>
</c:if>

<c:if test="${app.status eq 'ALL_GUARANTORS_APPROVED'}">
    <div class="erp-form-wrap mt-4 space-y-4">
        <div>
            <p class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Final Step</p>
            <h2 class="mt-2 text-lg font-semibold text-sacco-ink">Submit to Manager or Loan Officer</h2>
            <p class="mt-1 text-sm text-slate-600">All guarantors have accepted this loan. Confirm your declaration and OTP, then submit the application for review.</p>
        </div>
        <div class="rounded-lg border border-slate-200 bg-white px-4 py-4 text-sm leading-7 text-slate-700">
            <p>
                <strong>Applicant Declaration:</strong>
                I,
                <strong>
                    <c:choose>
                        <c:when test="${not empty currentMember and not empty currentMember.fullName}">${currentMember.fullName}</c:when>
                        <c:otherwise>the applicant</c:otherwise>
                    </c:choose>
                </strong>,
                confirm that the information and documents provided in this application are true and complete,
                that this loan request is mine, and that if approved I will repay it according to the agreed terms,
                loan schedule, and the IAA SACCOS LTD by-laws.
            </p>
        </div>
        <form id="loan-submit-manager-form" action="/app/loan-applications/${app.id}/submit" method="post" class="space-y-3">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
                <div class="flex flex-wrap items-center justify-between gap-3">
                    <div>
                        <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">OTP Verification</div>
                        <p class="mt-2 text-sm text-slate-600">Request a one-time code before submitting this application for manager review.</p>
                    </div>
                    <button type="button" class="app-btn btn-primary otp-request-button signature-otp-request inline-flex items-center justify-center gap-2">
                        <span class="otp-button-spinner hidden"></span>
                        <span class="otp-button-label">Send OTP Code</span>
                    </button>
                </div>
                <div data-auto-scroll-message="true" class="signature-otp-feedback mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
                <div class="mt-3">
                    <label class="mb-1 block text-sm font-medium text-slate-700">OTP Code</label>
                    <input type="text" name="applicantSignatureOtpCode"
                           inputmode="numeric" maxlength="6" autocomplete="one-time-code"
                           class="w-full rounded-lg border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                           placeholder="123456" />
                    <div class="signature-otp-live-status mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                        <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
                        <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                        </svg>
                        <span data-otp-text>Checking code...</span>
                    </div>
                </div>
            </div>
        </form>
        <c:if test="${showMemberReversalAction}">
            <form id="loan-cancel-submission-form"
                  action="/app/loan-applications/${app.id}/cancel"
                  method="post"
                  data-confirm-title="Cancel Submission"
                  data-confirm-message="Move this application back to draft so you can keep editing it?"
                  data-confirm-proceed="Move to Draft">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            </form>
        </c:if>
        <div class="grid gap-3 sm:grid-cols-2 loan-final-action-row">
            <c:if test="${showMemberReversalAction}">
                <button type="submit"
                        form="loan-cancel-submission-form"
                        class="app-btn btn-neutral w-full ${memberReversalWindowOpen ? '' : 'action-button-disabled'}"
                        ${memberReversalWindowOpen ? '' : 'disabled'}>
                    Cancel Submission
                </button>
            </c:if>
            <button type="submit"
                    form="loan-submit-manager-form"
                    class="app-btn btn-approve w-full ${showMemberReversalAction ? '' : 'sm:col-span-2'}">
                Submit to Manager or Loan Officer
            </button>
        </div>
    </div>
</c:if>

<%@ include file="../fragments/confirm-modal.jspf" %>

<script>
    (function () {
        const csrfToken = "${_csrf.token}";
        function bindOtpLiveStatus(input, statusBox, proceedButton, verifyOtp) {
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
            let activeVerification = 0;

            function setProceedEnabled(enabled) {
                if (!proceedButton) {
                    return;
                }
                proceedButton.disabled = !enabled;
                proceedButton.classList.toggle("action-button-disabled", !enabled);
            }

            function renderHidden() {
                statusBox.classList.add("hidden");
                statusBox.classList.remove("flex", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-rose-200", "bg-rose-50", "text-rose-700");
                statusBox.classList.add("border-slate-200", "bg-white", "text-slate-600");
                spinner.classList.remove("hidden");
                tick.classList.add("hidden");
                text.textContent = "Checking code...";
            }

            function renderPending() {
                statusBox.classList.remove("hidden", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-rose-200", "bg-rose-50", "text-rose-700");
                statusBox.classList.add("flex", "border-slate-200", "bg-white", "text-slate-600");
                spinner.classList.remove("hidden");
                tick.classList.add("hidden");
                text.textContent = "Verifying code...";
            }

            function renderVerified() {
                statusBox.classList.remove("hidden", "border-slate-200", "bg-white", "text-slate-600", "border-rose-200", "bg-rose-50", "text-rose-700");
                statusBox.classList.add("flex", "border-emerald-200", "bg-emerald-50", "text-emerald-700");
                spinner.classList.add("hidden");
                tick.classList.remove("hidden");
                tick.classList.remove("otp-checkmark-pop");
                void tick.offsetWidth;
                tick.classList.add("otp-checkmark-pop");
                text.textContent = "Verified";
            }

            function renderInvalid(message) {
                statusBox.classList.remove("hidden", "border-slate-200", "bg-white", "text-slate-600", "border-emerald-200", "bg-emerald-50", "text-emerald-700");
                statusBox.classList.add("flex", "border-rose-200", "bg-rose-50", "text-rose-700");
                spinner.classList.add("hidden");
                tick.classList.add("hidden");
                text.textContent = message || "The OTP code is invalid.";
            }

            function render() {
                input.value = (input.value || "").replace(/\D/g, "").slice(0, 6);
                const code = input.value;
                const ready = /^\d{6}$/.test(code);
                if (!otpRequested || !input.value) {
                    activeVerification += 1;
                    verifiedCode = "";
                    setProceedEnabled(false);
                    renderHidden();
                    return;
                }
                if (!ready) {
                    activeVerification += 1;
                    verifiedCode = "";
                    setProceedEnabled(false);
                    renderHidden();
                    return;
                }
                if (verifiedCode === code) {
                    setProceedEnabled(true);
                    renderVerified();
                    return;
                }

                const requestId = ++activeVerification;
                verifiedCode = "";
                setProceedEnabled(false);
                renderPending();
                Promise.resolve(verifyOtp(code))
                    .then(function () {
                        if (requestId !== activeVerification || input.value !== code) {
                            return;
                        }
                        verifiedCode = code;
                        renderVerified();
                        setProceedEnabled(true);
                        if (proceedButton) {
                            try {
                                proceedButton.focus({ preventScroll: true });
                            } catch (ignored) {
                                proceedButton.focus();
                            }
                        }
                    })
                    .catch(function (error) {
                        if (requestId !== activeVerification || input.value !== code) {
                            return;
                        }
                        verifiedCode = "";
                        setProceedEnabled(false);
                        renderInvalid(error && error.message ? error.message : "The OTP code is invalid.");
                    });
            }

            input.addEventListener("input", render);
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
                    activeVerification += 1;
                    verifiedCode = "";
                    setProceedEnabled(false);
                    render();
                }
            };
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

        document.querySelectorAll(".signature-otp-request").forEach(function (button) {
            button.addEventListener("click", async function () {
                const wrapper = button.closest(".rounded-xl")?.parentElement;
                const input = wrapper ? wrapper.querySelector("input[name='applicantSignatureOtpCode']") : null;
                const liveStatus = wrapper ? wrapper.querySelector(".signature-otp-live-status") : null;
                const proceedButton = button.closest("form")?.querySelector("button[type='submit']");
                const otpUi = bindOtpLiveStatus(input, liveStatus, proceedButton, async function (code) {
                    const response = await fetch("/app/loan-applications/verify-signature-otp", {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                            "Accept": "application/json"
                        },
                        body: new URLSearchParams({
                            "${_csrf.parameterName}": csrfToken,
                            "applicationId": "${app.id}",
                            "otpCode": code
                        })
                    });
                    const payload = await response.json();
                    if (!response.ok || payload.valid === false) {
                        throw new Error(payload.message || "The OTP code is invalid.");
                    }
                    return payload;
                });
                const card = button.closest(".rounded-xl");
                const feedback = card && card.nextElementSibling && card.nextElementSibling.classList.contains("signature-otp-feedback")
                    ? card.nextElementSibling
                    : button.closest(".space-y-3")?.querySelector(".signature-otp-feedback");
                if (!feedback) {
                    return;
                }
                otpUi.reset();
                setOtpButtonState(button, "loading", "Send OTP Code", "Sending...", "OTP Sent");
                try {
                    const response = await fetch("/app/loan-applications/request-signature-otp", {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                            "Accept": "application/json"
                        },
                        body: new URLSearchParams({
                            "${_csrf.parameterName}": csrfToken,
                            "applicationId": "${app.id}"
                        })
                    });
                    const payload = await response.json();
                    feedback.classList.remove("hidden", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-sacco-brown/30", "bg-[#f7efe9]", "text-sacco-brown");
                    if (!response.ok || payload.valid === false) {
                        throw new Error(payload.message || "Unable to send the OTP code right now.");
                    }
                    feedback.classList.add("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                    feedback.textContent = payload.message || "We sent an OTP code to your email.";
                    setOtpButtonState(button, "sent", "Send OTP Code", "Sending...", "OTP Sent");
                    otpUi.markRequested();
                    if (input) {
                        input.focus();
                    }
                } catch (error) {
                    feedback.classList.remove("hidden", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-sacco-brown/30", "bg-[#f7efe9]", "text-sacco-brown");
                    feedback.classList.add("border-sacco-brown/30", "bg-[#f7efe9]", "text-sacco-brown");
                    feedback.textContent = error.message || "Unable to send the OTP code right now.";
                    setOtpButtonState(button, "idle", "Send OTP Code", "Sending...", "OTP Sent");
                }
            });
        });

        document.querySelectorAll("form[action$='/submit'], form[data-confirm-title]").forEach(function (form) {
            form.addEventListener("submit", function () {
                const submitButton = form.querySelector("button[type='submit']");
                if (!submitButton || submitButton.disabled) {
                    return;
                }
                submitButton.disabled = true;
                submitButton.classList.add("action-button-disabled");
            });
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
