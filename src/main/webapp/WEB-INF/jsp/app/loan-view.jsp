<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
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

<div class="erp-page-header flex flex-wrap items-start justify-between gap-3">
    <div>
        <p class="erp-breadcrumb"><spring:message code="loan.view.breadcrumb" text="Member Workspace / Application Detail" /></p>
        <h1 class="erp-page-title"><spring:message code="loan.detail" /></h1>
        <p class="erp-page-subtitle"><spring:message code="loan.view.subtitle" text="Review your application information, decision feedback, repayment schedule, and supporting records." /></p>
    </div>
    <c:if test="${canPrint}">
        <button type="button" data-loan-export-url="${pageContext.request.contextPath}/documents/loan-applications/${app.id}/print" class="app-btn btn-primary"><spring:message code="common.export" text="Export" /></button>
    </c:if>
</div>
<c:set var="declarationSaccoName" value="${not empty activeSaccoName ? activeSaccoName : 'your SACCO'}" />
<c:if test="${app.status eq 'AWAITING_GUARANTORS'}">
    <div class="mb-3 rounded-lg border border-sky-200 bg-sky-50 px-4 py-3 text-sm text-sky-800">
        <spring:message code="loan.view.awaitingGuarantors" text="Waiting for guarantor approval. This page auto-refreshes every 1 hour." />
    </div>
    <script>
        setTimeout(function () { window.location.reload(); }, 3600000);
    </script>
</c:if>
<c:if test="${app.status eq 'ALL_GUARANTORS_APPROVED'}">
    <div class="mb-3 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
        <spring:message code="loan.view.guarantorsApproved" text="All guarantors have approved this application. You can now submit it for review." />
    </div>
</c:if>
<c:if test="${app.status eq 'FINAL_APPROVED' and empty app.applicantDisbursementAcknowledgedAt}">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-3 rounded border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
        <span><spring:message code="loan.disbursement.memberReadyAck" text="Your loan is Final Approved and Disbursed." /></span>
        <form method="post" action="${pageContext.request.contextPath}/app/loan-applications/${app.id}/acknowledge-disbursement" class="m-0">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <input type="hidden" name="returnTo" value="detail" />
            <button type="submit" class="app-btn btn-neutral"><spring:message code="common.acknowledge" text="Acknowledge" /></button>
        </form>
    </div>
</c:if>
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
                    <div class="loan-hero-inline-label"><spring:message code="loan.status" text="Status" /></div>
                    <div class="loan-hero-inline-value">
                        <span class="inline-flex items-center rounded-full px-3 py-1 text-sm font-semibold ${loanStatusBadgeClass}">
                            <spring:message code="loan.status.${app.status}" text="${app.status}" />
                        </span>
                    </div>
                </div>
            </div>
            <div class="mt-4 loan-hero-facts-grid sm:grid-cols-2">
                <div class="loan-hero-inline-fact">
                    <div class="loan-hero-inline-label"><spring:message code="loan.type" text="Loan Type" /></div>
                    <div class="loan-hero-inline-value"><spring:message code="loan.type.${app.loanType}"/></div>
                </div>
                <c:if test="${not empty formFields['Loan Purpose']}">
                    <div class="loan-hero-inline-fact">
                        <div class="loan-hero-inline-label"><spring:message code="loan.purpose" text="Loan Purpose" /></div>
                        <div class="loan-hero-inline-value">${formFields['Loan Purpose']}</div>
                    </div>
                </c:if>
            </div>
        </div>
    </div>

    <div class="px-5 py-5 sm:px-6 sm:py-6 lg:px-8 lg:py-7">
        <div class="space-y-3"
             data-live-account-status-url="${pageContext.request.contextPath}/app/loan-applications/${app.id}/applicant-financial-status">
            <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                <div class="loan-view-summary-card px-4 py-4">
                    <div class="loan-stat-label"><spring:message code="loan.amount.label" text="Loan Amount" /></div>
                    <div class="loan-stat-value">${app.amount}</div>
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

<div class="mt-4 space-y-4">
    <c:if test="${not empty managerReason}">
        <div class="rounded-lg border border-sacco-brown/30 bg-[#f7efe9] px-4 py-3 text-sm text-sacco-brown">
            <strong><spring:message code="review.managerReason" text="Manager Reason:" /></strong> ${managerReason}
        </div>
    </c:if>
</div>

<c:if test="${app.status eq 'READY_FOR_MANAGER' and not empty pendingManagerStageWithdrawal}">
    <div class="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
        <spring:message code="loan.view.removalPending" text="Your application removal request is already waiting for the manager's decision." />
    </div>
</c:if>

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.details" text="Loan Details" />
    </h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
            <tr>
                <th class="px-3 py-2 text-left"><spring:message code="newloan.table.section" text="Section" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="newloan.table.value" text="Value" /></th>
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
                    <td colspan="2" class="px-3 py-3 text-slate-500"><spring:message code="loan.view.noFinancialDetails" text="Financial details have not been loaded for this application yet." /></td>
                </tr>
            </c:if>
        </tbody>
    </table>
</div>

<c:if test="${not empty loanDetailRepaymentPreviewRows}">
    <div class="erp-table-wrap overflow-x-auto">
        <div class="border-b border-slate-200 bg-slate-50 px-4 py-3">
            <div class="text-sm font-semibold text-slate-900"><spring:message code="repayment.estimatedSchedule" text="Estimated Repayment Schedule" /></div>
            <div class="mt-1 text-sm text-slate-500"><spring:message code="repayment.loadedScheduleHelp" text="Monthly installments based on the loaded loan details, including interest and remaining balance." /></div>
        </div>
        <table class="erp-table">
            <thead>
                <tr>
                    <th><spring:message code="repayment.pmtNo" text="Pmt No." /></th>
                    <th><spring:message code="repayment.month" text="Month" /></th>
                    <th><spring:message code="repayment.beginningBalance" text="Beginning Balance" /></th>
                    <th><spring:message code="repayment.amountToPay" text="Amount to Pay" /></th>
                    <th><spring:message code="loan.amount.label" text="Loan Amount" /></th>
                    <th><spring:message code="repayment.interest" text="Interest" /></th>
                    <th><spring:message code="repayment.endingBalance" text="Ending Balance" /></th>
                </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
                <c:forEach items="${loanDetailRepaymentPreviewRows}" var="row">
                    <tr>
                        <td class="px-3 py-2 font-medium text-slate-700">${row.pmtNo}</td>
                        <td class="px-3 py-2">${row.month}</td>
                        <td class="px-3 py-2">${row.beginningBalance}</td>
                        <td class="px-3 py-2 font-medium text-slate-900">${row.payment}</td>
                        <td class="px-3 py-2">${row.loanAmount}</td>
                        <td class="px-3 py-2">${row.interest}</td>
                        <td class="px-3 py-2">${row.endingBalance}</td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>
</c:if>

<c:if test="${app.status eq 'FINAL_APPROVED' or app.status eq 'DEFAULTED' or app.status eq 'PAID'}">
    <div id="repayment-plan" class="erp-section">
        <div class="mb-4 flex flex-wrap items-start justify-between gap-3">
            <div>
                <h5 class="text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.title" text="Repayment Plan" /></h5>
                <p class="mt-1 text-sm text-slate-500">
                    <c:choose>
                        <c:when test="${app.status eq 'PAID'}"><spring:message code="loan.repayment.paidHelp" text="This loan was disbursed and fully cleared. The schedule below shows the agreed repayment trail." /></c:when>
                        <c:when test="${app.status eq 'DEFAULTED'}"><spring:message code="loan.repayment.defaultedHelp" text="This loan reached the final due date with an outstanding balance. Use the repayment details below to track what is still unpaid." /></c:when>
                        <c:otherwise><spring:message code="loan.repayment.activeHelp" text="This disbursed loan now follows the repayment timetable below." /></c:otherwise>
                    </c:choose>
                </p>
            </div>
            <div class="flex flex-wrap items-center justify-end gap-2">
                <c:if test="${not empty app.loanId}">
                    <form action="/app/loan-applications/${app.id}/sync-payments" method="post" class="inline-flex">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                        <button type="submit" class="app-btn btn-neutral"><spring:message code="loan.repayment.refresh" text="Refresh Payments" /></button>
                    </form>
                </c:if>
                <span class="loan-repayment-chip<c:if test="${app.status eq 'PAID'}"> is-paid</c:if><c:if test="${app.status eq 'DEFAULTED'}"> is-defaulted</c:if>">
                    <c:choose>
                        <c:when test="${app.status eq 'PAID'}">&#10003; <spring:message code="loan.repayment.fullyPaid" text="Fully Paid" /></c:when>
                        <c:when test="${app.status eq 'DEFAULTED'}"><spring:message code="loan.repayment.defaulted" text="Defaulted / Not Paid" /></c:when>
                        <c:otherwise><spring:message code="loan.repayment.activeSchedule" text="Active Schedule" /></c:otherwise>
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
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.installmentAmount" text="Installment Amount" /></div>
                    <div class="loan-repayment-card-value">
                        <c:out value="${repaymentSummary['Installment Amount']}" default="Pending update" />
                    </div>
                </div>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.frequency" text="Repayment Frequency" /></div>
                    <div class="loan-repayment-card-value">
                        <c:out value="${repaymentSummary['Repayment Frequency']}" default="Pending update" />
                    </div>
                </div>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.firstRepayment" text="First Repayment" /></div>
                    <div class="loan-repayment-card-value">
                        <c:out value="${repaymentSummary['First Repayment Date']}" default="Pending update" />
                    </div>
                </div>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                        <c:choose>
                            <c:when test="${app.status eq 'PAID'}"><spring:message code="loan.repayment.paidOn" text="Paid On" /></c:when>
                            <c:otherwise><spring:message code="loan.repayment.finalDueDate" text="Final Due Date" /></c:otherwise>
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
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.installments" text="Installments" /></div>
                        <div class="loan-repayment-note-value">${repaymentSummary['Installments']}</div>
                    </div>
                </c:if>
                <c:if test="${not empty repaymentSummary['Disbursement Date']}">
                    <div class="loan-repayment-note">
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.disbursementDate" text="Disbursement Date" /></div>
                        <div class="loan-repayment-note-value">${repaymentSummary['Disbursement Date']}</div>
                    </div>
                </c:if>
                <c:if test="${not empty repaymentSummary['Disbursement Reference']}">
                    <div class="loan-repayment-note">
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.disbursementReference" text="Disbursement Reference" /></div>
                        <div class="loan-repayment-note-value">${repaymentSummary['Disbursement Reference']}</div>
                    </div>
                </c:if>
                <c:if test="${app.status ne 'PAID' and not empty repaymentDaysLeft}">
                    <div class="loan-repayment-note">
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <c:choose>
                                <c:when test="${app.status eq 'DEFAULTED'}"><spring:message code="loan.repayment.overdue" text="Overdue" /></c:when>
                                <c:otherwise><spring:message code="loan.repayment.timeLeft" text="Time Left" /></c:otherwise>
                            </c:choose>
                        </div>
                        <div class="loan-repayment-note-value">
                            <c:choose>
                                <c:when test="${app.status eq 'DEFAULTED'}">
                                    <c:set var="overdueDays" value="${0 - repaymentDaysLeft}" />
                                    ${overdueDays} <spring:message code="common.days" text="day(s)" /> <spring:message code="loan.repayment.overdueSuffix" text="overdue" />
                                </c:when>
                                <c:otherwise>
                                    ${repaymentDaysLeft} <spring:message code="common.days" text="day(s)" /> <spring:message code="loan.repayment.remainingSuffix" text="remaining" />
                                    <c:if test="${not empty repaymentMonthsLeft or not empty repaymentWeeksLeft}">
                                        <span class="text-slate-400">|</span>
                                        ${repaymentMonthsLeft} <spring:message code="common.months" text="month(s)" />,
                                        ${repaymentWeeksLeft} <spring:message code="common.weeks" text="week(s)" />
                                    </c:if>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>
                </c:if>
                <c:if test="${app.status eq 'PAID' and not empty repaymentSummary['Final Due Date']}">
                    <div class="loan-repayment-note">
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.originalFinalDueDate" text="Original Final Due Date" /></div>
                        <div class="loan-repayment-note-value">${repaymentSummary['Final Due Date']}</div>
                    </div>
                </c:if>
                <c:if test="${not empty repaymentSummary['Manager Notes']}">
                    <div class="loan-repayment-note">
                        <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.managerNotes" text="Manager Notes" /></div>
                        <div class="loan-repayment-note-value">${repaymentSummary['Manager Notes']}</div>
                    </div>
                </c:if>
            </div>
        </div>
        <c:choose>
            <c:when test="${not empty repaymentRows}">
                <div class="mt-5 erp-table-wrap overflow-x-auto">
                    <div class="border-b border-slate-200 bg-slate-50 px-4 py-3">
                        <div class="text-sm font-semibold text-slate-900"><spring:message code="loan.repayment.schedule" text="Installment Schedule" /></div>
                        <div class="mt-1 text-sm text-slate-500"><spring:message code="loan.repayment.scheduleHelp" text="A quick view of each expected repayment in order." /></div>
                    </div>
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
                    <spring:message code="loan.repayment.empty" text="Repayment schedule details are not available yet for this disbursed loan. Core repayment dates will appear here once the schedule is fully posted." />
                </div>
            </c:otherwise>
        </c:choose>
    </div>
</c:if>

<c:if test="${not empty disbursementProofAttachments or app.status eq 'FINAL_APPROVED' or app.status eq 'DEFAULTED' or app.status eq 'PAID'}">
    <div class="erp-table-wrap overflow-x-auto">
        <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.attachments.disbursementProof" text="Disbursement Proof" /></h5>
        <table class="min-w-full divide-y divide-slate-200 text-sm">
            <thead class="bg-slate-50">
                <tr>
                    <th class="px-3 py-2 text-left"><spring:message code="loan.attachments.file" text="File" /></th>
                    <th class="px-3 py-2 text-left"><spring:message code="loan.attachments.size" text="Size" /></th>
                    <th class="px-3 py-2 text-left"><spring:message code="loan.attachments.uploaded" text="Uploaded" /></th>
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
                    <tr>
                        <td colspan="4" class="px-3 py-3 text-slate-500"><spring:message code="loan.attachments.noDisbursementProof" text="No disbursement proof has been uploaded for this loan yet." /></td>
                    </tr>
                </c:if>
            </tbody>
        </table>
    </div>
</c:if>

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.attachments.applicationAttachments" text="Application Attachments" /></h5>
    <table class="min-w-full divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
            <tr>
                <th class="px-3 py-2 text-left"><spring:message code="loan.attachments.file" text="File" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="loan.attachments.size" text="Size" /></th>
                <th class="px-3 py-2 text-left"><spring:message code="loan.attachments.uploaded" text="Uploaded" /></th>
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
                            class="app-btn btn-primary"><spring:message code="common.download" text="Download" /></a></td>
                </tr>
            </c:forEach>
            <c:if test="${empty attachments}">
                <tr>
                    <td colspan="4" class="px-3 py-3 text-slate-500"><spring:message code="loan.attachments.empty" text="No attachments uploaded." /></td>
                </tr>
            </c:if>
        </tbody>
    </table>
</div>

<div class="erp-table-wrap overflow-x-auto">
    <h5 class="px-4 pt-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
        <spring:message code="loan.guarantors" />
    </h5>
    <table class="min-w-full table-fixed divide-y divide-slate-200 text-sm">
        <thead class="bg-slate-50">
            <tr>
                <th class="w-1/4 px-3 py-2 text-left"><spring:message code="loan.guarantor" text="Guarantor" /></th>
                <th class="w-1/6 px-3 py-2 text-left"><spring:message code="member.memberNo" text="Member No" /></th>
                <th class="w-1/5 px-3 py-2 text-left"><spring:message code="loan.status" text="Status" /></th>
                <th class="w-1/6 px-3 py-2 text-left"><spring:message code="loan.date" text="Date" /></th>
                <th class="w-1/4 px-3 py-2 text-left"><spring:message code="loan.removalRequest" text="Removal Request" /></th>
            </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
            <c:forEach items="${guarantorRequests}" var="req">
                <tr>
                    <td class="px-3 py-2 align-top">
                        <c:choose>
                            <c:when test="${not empty guarantorNames[req.guarantorMemberId]}">
                                ${guarantorNames[req.guarantorMemberId]}</c:when>
                            <c:otherwise>#${fn:substring(req.guarantorMemberId, 0, 8)}</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2 align-top">
                        <c:choose>
                            <c:when test="${not empty guarantorMembersById[req.guarantorMemberId] and not empty guarantorMembersById[req.guarantorMemberId].memberNo}">${guarantorMembersById[req.guarantorMemberId].memberNo}</c:when>
                            <c:otherwise>-</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2 align-top">${req.status}</td>
                    <td class="px-3 py-2 align-top">
                        <c:choose>
                            <c:when test="${not empty req.decidedAt}">${fn:replace(fn:substring(req.decidedAt, 0, 16),
                                'T', ' ')}</c:when>
                            <c:otherwise>${fn:replace(fn:substring(req.createdAt, 0, 16), 'T', ' ')}</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2 align-top">
                        <c:set var="pendingUndo" value="${pendingGuarantorUndoRequests[req.id]}" />
                        <c:choose>
                            <c:when test="${not empty pendingUndo}">
                                <div class="space-y-2">
                                    <div class="text-sm text-amber-700"><spring:message code="loan.guarantorRemoval.requested" text="Guarantor asked to be removed from this loan." /></div>
                                    <div class="flex flex-wrap gap-2">
                                        <form action="/app/loan-applications/${app.id}/guarantor-reversal-requests/${pendingUndo.id}/approve"
                                              method="post"
                                              data-confirm-title="Approve Removal"
                                              data-confirm-message="Approve this request and remove the guarantor from this application?"
                                              data-confirm-proceed="Approve Removal">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                            <button type="submit" class="app-btn btn-primary"><spring:message code="loan.guarantorRemoval.approve" text="Approve Removal" /></button>
                                        </form>
                                        <form action="/app/loan-applications/${app.id}/guarantor-reversal-requests/${pendingUndo.id}/reject"
                                              method="post"
                                              data-confirm-title="Keep Guarantor"
                                              data-confirm-message="Keep this guarantor on the loan and decline the removal request?"
                                              data-confirm-proceed="Keep Guarantor">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                            <button type="submit" class="app-btn btn-reject"><spring:message code="loan.guarantorRemoval.keep" text="Keep Guarantor" /></button>
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
                                <td class="px-3 py-2 align-top">${draftGuarantor.fullName}</td>
                                <td class="px-3 py-2 align-top">${draftGuarantor.memberNo}</td>
                                <td class="px-3 py-2 align-top"><spring:message code="loan.guarantor.selectedInDraft" text="SELECTED IN DRAFT" /></td>
                                <td class="px-3 py-2 align-top">-</td>
                                <td class="px-3 py-2 align-top">-</td>
                            </tr>
                        </c:forEach>
                    </c:when>
                    <c:otherwise>
                        <tr>
                            <td colspan="5" class="px-3 py-3 text-slate-500"><spring:message code="loan.guarantor.empty" text="No guarantors selected yet." /></td>
                        </tr>
                    </c:otherwise>
                </c:choose>
            </c:if>
        </tbody>
    </table>
</div>

<c:set var="showEditAction" value="${app.status eq 'DRAFT' or app.status eq 'ALL_GUARANTORS_APPROVED'}" />
<c:set var="showTopUpAction" value="${canRequestTopUp}" />
<c:set var="showMemberReversalAction" value="${app.status eq 'AWAITING_GUARANTORS' or app.status eq 'ALL_GUARANTORS_APPROVED' or app.status eq 'READY_FOR_MANAGER'}" />
<c:set var="showDeleteAction" value="${app.status eq 'DRAFT' or app.status eq 'AWAITING_GUARANTORS'}" />
<c:set var="showLoanActionCard" value="${showEditAction or showTopUpAction or (showMemberReversalAction and app.status ne 'READY_FOR_MANAGER') or showDeleteAction or canForfeitApplication}" />
<spring:message code="loan.confirm.cancelSubmission.title" text="Cancel Application" var="cancelSubmissionTitle" />
<spring:message code="loan.confirm.cancelSubmission.message" text="Move this application back to draft so you can keep editing it?" var="cancelSubmissionMessage" />
<spring:message code="loan.confirm.cancelSubmission.proceed" text="Cancel Application" var="cancelSubmissionProceed" />
<spring:message code="loan.confirm.deleteApplication.title" text="Delete Application" var="deleteApplicationTitle" />
<spring:message code="loan.confirm.deleteApplication.message" text="Delete this application completely? This will remove it from your view and from every review queue." var="deleteApplicationMessage" />
<spring:message code="loan.confirm.deleteApplication.proceed" text="Delete Application" var="deleteApplicationProceed" />
<c:if test="${showLoanActionCard}">
    <div class="erp-form-wrap loan-page-bottom-actions">
        <div class="loan-view-action-cluster">
            <c:if test="${showEditAction}">
                <a href="/app/loan-applications/${app.id}/edit" class="app-btn btn-neutral">
                    <spring:message code="loan.actions.reEdit" text="Re-edit Application" />
                </a>
            </c:if>
            <c:if test="${showTopUpAction}">
                <a href="/app/loan-applications/new?loanType=${app.loanType}&topUpLoanId=${app.id}" class="app-btn btn-neutral">
                    <spring:message code="loan.actions.topUp" text="Request Loan Top-Up" />
                </a>
            </c:if>
            <c:if test="${showMemberReversalAction and app.status ne 'READY_FOR_MANAGER'}">
                <form action="/app/loan-applications/${app.id}/cancel"
                      method="post"
                      data-confirm-title="${cancelSubmissionTitle}"
                      data-confirm-message="${cancelSubmissionMessage}"
                      data-confirm-proceed="${cancelSubmissionProceed}">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <button type="submit"
                            class="app-btn btn-neutral ${memberReversalWindowOpen ? '' : 'action-button-disabled'}"
                            ${memberReversalWindowOpen ? '' : 'disabled'}>
                        <spring:message code="loan.actions.cancelSubmission" text="Cancel Application" />
                    </button>
                </form>
            </c:if>
            <c:if test="${app.status eq 'ALL_GUARANTORS_APPROVED'}">
                <button type="button" id="openApplicantSubmitOtp" class="app-btn btn-primary">
                    <c:out value="${loanFinalSubmitLabel}" />
                </button>
            </c:if>
            <c:if test="${showDeleteAction}">
                <form action="/app/loan-applications/${app.id}/delete"
                      method="post"
                      data-confirm-title="${deleteApplicationTitle}"
                      data-confirm-message="${deleteApplicationMessage}"
                      data-confirm-proceed="${deleteApplicationProceed}">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <button type="submit" class="app-btn btn-reject"><spring:message code="loan.actions.removeApplication" text="Remove Application" /></button>
                </form>
            </c:if>
            <c:if test="${canForfeitApplication}">
                <button type="button" id="openForfeitModal" class="app-btn btn-reject"><spring:message code="loan.actions.forfeit" text="Forfeit Application" /></button>
            </c:if>
        </div>
    </div>
</c:if>

<c:if test="${app.status eq 'ALL_GUARANTORS_APPROVED'}">
    <div id="loanSubmitOtpCard" class="erp-form-wrap mt-4 hidden space-y-4">
        <div>
            <p class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="loan.otp.verification" text="OTP Verification" /></p>
            <h2 class="mt-2 text-lg font-semibold text-sacco-ink"><c:out value="${loanFinalSubmitLabel}" /></h2>
            <p class="mt-1 text-sm text-slate-600"><spring:message code="loan.otp.requestBeforeManager" text="Request a one-time code before submitting this application for review." /></p>
        </div>
        <form id="loan-submit-manager-form"
              action="/app/loan-applications/${app.id}/submit"
              method="post"
              class="space-y-3"
              data-confirm-eyebrow="Confirm Submission"
              data-confirm-title="${loanFinalSubmitLabel}"
              data-confirm-message="Submit this application for review? After submission, it will move to the configured staff review stage."
              data-confirm-proceed="${loanFinalSubmitLabel}">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <label class="flex items-start gap-3 rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-700">
                <input type="checkbox" name="termsAccepted" value="true" required class="mt-1 h-4 w-4 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue" />
                <span><spring:message code="loan.terms.accept" text="I accept the terms and conditions for this loan application." /></span>
            </label>
            <div class="rounded-md border border-slate-200 bg-slate-50 px-4 py-4">
                <div class="flex flex-wrap items-center justify-between gap-3">
                    <div>
                        <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="loan.otp.code" text="OTP Code" /></div>
                        <p class="mt-2 text-sm text-slate-600"><spring:message code="loan.otp.codeHelp" text="The code is sent using the station OTP delivery policy." /></p>
                    </div>
                    <button id="requestApplicantSubmitOtp" type="button" class="app-btn btn-primary otp-request-button inline-flex items-center justify-center gap-2">
                        <span class="otp-button-spinner hidden"></span>
                        <span class="otp-button-label"><spring:message code="loan.otp.sendCode" text="Send OTP Code" /></span>
                    </button>
                </div>
                <div id="applicantSubmitOtpFeedback" data-auto-scroll-message="true" class="mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
                <div class="mt-3">
                    <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="loan.otp.code" text="OTP Code" /></label>
                    <input id="applicantSubmitOtpCode"
                           type="text"
                           name="applicantSignatureOtpCode"
                           inputmode="numeric"
                           maxlength="6"
                           autocomplete="one-time-code"
                           data-otp-hidden="true"
                           data-otp-label="Applicant OTP code"
                           class="w-full rounded-lg border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                           placeholder="123456" />
                    <div id="applicantSubmitOtpLiveStatus" class="mt-3 hidden items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                        <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
                        <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                        </svg>
                        <span data-otp-text><spring:message code="loan.otp.checking" text="Checking code..." /></span>
                    </div>
                </div>
            </div>
            <button id="submitApplicantToReview"
                    type="submit"
                    class="app-btn btn-approve action-button-disabled w-full"
                    disabled>
                <c:out value="${loanFinalSubmitLabel}" />
            </button>
        </form>
    </div>
</c:if>

<c:if test="${canForfeitApplication}">
    <div id="loanForfeitModal" class="app-modal-overlay" aria-hidden="true">
        <div class="app-modal-panel app-modal-panel--compact" role="dialog" aria-modal="true" aria-labelledby="loanForfeitTitle">
            <div class="app-modal-header">
                <div>
                    <p class="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500"><spring:message code="loan.forfeit.otpConfirmation" text="OTP Confirmation" /></p>
                    <h2 id="loanForfeitTitle" class="mt-2 text-2xl font-semibold text-sacco-ink"><spring:message code="loan.forfeit.title" text="Forfeit Application" /></h2>
                </div>
                <button type="button" id="closeForfeitModal" class="app-modal-close" aria-label="<spring:message code='loan.forfeit.close' text='Close forfeit confirmation' />">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                        <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                    </svg>
                </button>
            </div>
            <form action="/app/loan-applications/${app.id}/forfeit"
                  method="post"
                  class="app-modal-body space-y-4"
                  data-confirm-eyebrow="Confirm Forfeiture"
                  data-confirm-title="Forfeit Application"
                  data-confirm-message="Forfeit this application? The review will stop, this application will be recorded as forfeited, and the configured waiting-period policy may block a new application until the waiting period expires."
                  data-confirm-proceed="Forfeit Application">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <p class="text-base leading-7 text-slate-700">
                    <spring:message code="loan.forfeit.help" text="This will stop the review of this application. Request an OTP, then enter it to confirm." />
                </p>
                <c:if test="${forfeitWaitDays gt 0}">
                    <div class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-900">
                        <c:choose>
                            <c:when test="${forfeitWaitDays eq 1}">
                                <spring:message code="loan.forfeit.waitNotice.one" text="After forfeiting, you must wait 1 day before you can apply for another loan." />
                            </c:when>
                            <c:otherwise>
                                <spring:message code="loan.forfeit.waitNotice.many" text="After forfeiting, you must wait {0} days before you can apply for another loan." arguments="${forfeitWaitDays}" />
                            </c:otherwise>
                        </c:choose>
                    </div>
                </c:if>
                <div class="rounded-xl border border-slate-200 bg-slate-50 px-4 py-4">
                    <div class="flex flex-wrap items-center justify-between gap-3">
                        <div>
                            <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="loan.forfeit.verificationCode" text="Verification Code" /></div>
                            <p class="mt-2 text-sm text-slate-600"><spring:message code="loan.forfeit.codeHelp" text="The code is sent to the email on your member profile." /></p>
                        </div>
                        <button type="button" id="requestForfeitOtp" class="app-btn btn-primary otp-request-button inline-flex items-center justify-center gap-2">
                            <span class="otp-button-spinner hidden"></span>
                            <span class="otp-button-label"><spring:message code="loan.otp.sendCode" text="Send OTP Code" /></span>
                        </button>
                    </div>
                    <div id="forfeitOtpFeedback" class="mt-3 hidden rounded-lg border px-4 py-3 text-sm"></div>
                    <div class="mt-3">
                        <label class="mb-1 block text-sm font-medium text-slate-700"><spring:message code="loan.otp.code" text="OTP Code" /></label>
                        <input id="forfeitOtpCode"
                               type="text"
                               name="forfeitOtpCode"
                               inputmode="numeric"
                               maxlength="6"
                               autocomplete="one-time-code"
                               data-otp-hidden="true" data-otp-label="Forfeit OTP code"
                               class="w-full rounded-lg border border-slate-300 px-3 py-3 tracking-[0.3em] focus:border-sacco-blue focus:outline-none"
                               placeholder="123456" />
                    </div>
                </div>
                <div class="app-modal-actions">
                    <button type="button" id="cancelForfeitModal" class="app-btn btn-neutral"><spring:message code="common.cancel" text="Cancel" /></button>
                    <button type="submit" id="submitForfeitApplication" class="app-btn btn-reject action-button-disabled" disabled><spring:message code="loan.forfeit.title" text="Forfeit Application" /></button>
                </div>
            </form>
        </div>
    </div>
</c:if>

<%@ include file="../fragments/confirm-modal.jspf" %>

<spring:message code="loan.otp.sendCode" text="Send OTP Code" var="sendOtpLabel" />
<spring:message code="loan.otp.sending" text="Sending..." var="sendingOtpLabel" />
<spring:message code="loan.otp.sent" text="OTP Sent" var="otpSentLabel" />
<spring:message code="loan.otp.unableSend" text="Unable to send the OTP code right now." var="unableSendOtpLabel" />
<spring:message code="loan.otp.sentEmail" text="We sent an OTP code to your email." var="sentOtpEmailLabel" />
<spring:message code="loan.otp.invalid" text="The OTP code is invalid." var="invalidOtpLabel" />
<script>
    (function () {
        const csrfToken = "${_csrf.token}";
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

        (function bindApplicantSubmitOtpCard() {
            const openButton = document.getElementById("openApplicantSubmitOtp");
            const card = document.getElementById("loanSubmitOtpCard");
            if (!openButton || !card) {
                return;
            }
            const requestButton = document.getElementById("requestApplicantSubmitOtp");
            const feedback = document.getElementById("applicantSubmitOtpFeedback");
            const input = document.getElementById("applicantSubmitOtpCode");
            const liveStatus = document.getElementById("applicantSubmitOtpLiveStatus");
            const submitButton = document.getElementById("submitApplicantToReview");
            const spinner = liveStatus?.querySelector("[data-otp-spinner]");
            const tick = liveStatus?.querySelector("[data-otp-tick]");
            const statusText = liveStatus?.querySelector("[data-otp-text]");
            let otpRequested = false;
            let verifiedCode = "";
            let activeVerification = 0;

            function setFeedback(type, message) {
                if (!feedback) {
                    return;
                }
                feedback.classList.remove("hidden", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-sacco-brown/30", "bg-[#f7efe9]", "text-sacco-brown");
                if (type === "success") {
                    feedback.classList.add("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                } else {
                    feedback.classList.add("border-sacco-brown/30", "bg-[#f7efe9]", "text-sacco-brown");
                }
                feedback.textContent = message;
            }

            function setSubmitEnabled(enabled) {
                if (!submitButton) {
                    return;
                }
                submitButton.disabled = !enabled;
                submitButton.classList.toggle("action-button-disabled", !enabled);
            }

            function renderHidden() {
                if (!liveStatus || !spinner || !tick || !statusText) {
                    return;
                }
                liveStatus.classList.add("hidden");
                liveStatus.classList.remove("flex", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-rose-200", "bg-rose-50", "text-rose-700");
                liveStatus.classList.add("border-slate-200", "bg-white", "text-slate-600");
                spinner.classList.remove("hidden");
                tick.classList.add("hidden");
                statusText.textContent = "Checking code...";
            }

            function renderPending() {
                if (!liveStatus || !spinner || !tick || !statusText) {
                    return;
                }
                liveStatus.classList.remove("hidden", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-rose-200", "bg-rose-50", "text-rose-700");
                liveStatus.classList.add("flex", "border-slate-200", "bg-white", "text-slate-600");
                spinner.classList.remove("hidden");
                tick.classList.add("hidden");
                statusText.textContent = "Verifying code...";
            }

            function renderVerified() {
                if (!liveStatus || !spinner || !tick || !statusText) {
                    return;
                }
                liveStatus.classList.remove("hidden", "border-slate-200", "bg-white", "text-slate-600", "border-rose-200", "bg-rose-50", "text-rose-700");
                liveStatus.classList.add("flex", "border-emerald-200", "bg-emerald-50", "text-emerald-700");
                spinner.classList.add("hidden");
                tick.classList.remove("hidden");
                tick.classList.remove("otp-checkmark-pop");
                void tick.offsetWidth;
                tick.classList.add("otp-checkmark-pop");
                statusText.textContent = "Verified";
            }

            function renderInvalid(message) {
                if (!liveStatus || !spinner || !tick || !statusText) {
                    return;
                }
                liveStatus.classList.remove("hidden", "border-slate-200", "bg-white", "text-slate-600", "border-emerald-200", "bg-emerald-50", "text-emerald-700");
                liveStatus.classList.add("flex", "border-rose-200", "bg-rose-50", "text-rose-700");
                spinner.classList.add("hidden");
                tick.classList.add("hidden");
                statusText.textContent = message || "${invalidOtpLabel}";
            }

            async function verifyOtp(code) {
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
                    throw new Error(payload.message || "${invalidOtpLabel}");
                }
                return payload;
            }

            function renderSubmitState() {
                if (!input) {
                    return;
                }
                input.value = (input.value || "").replace(/\D/g, "").slice(0, 6);
                const code = input.value;
                if (!otpRequested || !code) {
                    activeVerification += 1;
                    verifiedCode = "";
                    setSubmitEnabled(false);
                    renderHidden();
                    return;
                }
                if (!/^\d{6}$/.test(code)) {
                    activeVerification += 1;
                    verifiedCode = "";
                    setSubmitEnabled(false);
                    renderHidden();
                    return;
                }
                if (verifiedCode === code) {
                    setSubmitEnabled(true);
                    renderVerified();
                    return;
                }
                const requestId = ++activeVerification;
                verifiedCode = "";
                setSubmitEnabled(false);
                renderPending();
                verifyOtp(code)
                    .then(function () {
                        if (requestId !== activeVerification || input.value !== code) {
                            return;
                        }
                        verifiedCode = code;
                        renderVerified();
                        setSubmitEnabled(true);
                        submitButton?.focus({ preventScroll: true });
                    })
                    .catch(function (error) {
                        if (requestId !== activeVerification || input.value !== code) {
                            return;
                        }
                        verifiedCode = "";
                        setSubmitEnabled(false);
                        renderInvalid(error && error.message ? error.message : "${invalidOtpLabel}");
                    });
            }

            openButton.addEventListener("click", function () {
                card.classList.remove("hidden");
                card.scrollIntoView({ behavior: "smooth", block: "start" });
                requestButton?.focus({ preventScroll: true });
            });

            input?.addEventListener("input", renderSubmitState);
            renderSubmitState();

            requestButton?.addEventListener("click", async function () {
                otpRequested = false;
                activeVerification += 1;
                verifiedCode = "";
                setSubmitEnabled(false);
                renderHidden();
                setOtpButtonState(requestButton, "loading", "${sendOtpLabel}", "${sendingOtpLabel}", "${otpSentLabel}");
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
                    if (!response.ok || payload.valid === false) {
                        throw new Error(payload.message || "${unableSendOtpLabel}");
                    }
                    otpRequested = true;
                    setFeedback("success", payload.message || "${sentOtpEmailLabel}");
                    setOtpButtonState(requestButton, "sent", "${sendOtpLabel}", "${sendingOtpLabel}", "${otpSentLabel}");
                    window.SaccosOtp?.startCooldown(requestButton, payload, { idle: "${sendOtpLabel}" });
                    window.SaccosOtp?.focusBoxes(input);
                    renderSubmitState();
                } catch (error) {
                    setFeedback("error", error.message || "${unableSendOtpLabel}");
                    setOtpButtonState(requestButton, "idle", "${sendOtpLabel}", "${sendingOtpLabel}", "${otpSentLabel}");
                }
            });
        })();

        (function bindForfeitModal() {
            const modal = document.getElementById("loanForfeitModal");
            if (!modal) {
                return;
            }
            const openButton = document.getElementById("openForfeitModal");
            const closeButton = document.getElementById("closeForfeitModal");
            const cancelButton = document.getElementById("cancelForfeitModal");
            const requestButton = document.getElementById("requestForfeitOtp");
            const feedback = document.getElementById("forfeitOtpFeedback");
            const input = document.getElementById("forfeitOtpCode");
            const submitButton = document.getElementById("submitForfeitApplication");
            let otpRequested = false;

            function closeModal() {
                modal.classList.remove("is-open");
                modal.setAttribute("aria-hidden", "true");
                document.body.classList.remove("overflow-hidden");
            }

            function openModal() {
                modal.classList.add("is-open");
                modal.setAttribute("aria-hidden", "false");
                document.body.classList.add("overflow-hidden");
                requestButton?.focus({ preventScroll: true });
            }

            function setFeedback(type, message) {
                if (!feedback) {
                    return;
                }
                feedback.classList.remove("hidden", "border-emerald-200", "bg-emerald-50", "text-emerald-700", "border-sacco-brown/30", "bg-[#f7efe9]", "text-sacco-brown");
                if (type === "success") {
                    feedback.classList.add("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                } else {
                    feedback.classList.add("border-sacco-brown/30", "bg-[#f7efe9]", "text-sacco-brown");
                }
                feedback.textContent = message;
            }

            function renderSubmitState() {
                if (!input || !submitButton) {
                    return;
                }
                input.value = (input.value || "").replace(/\D/g, "").slice(0, 6);
                const enabled = otpRequested && /^\d{6}$/.test(input.value);
                submitButton.disabled = !enabled;
                submitButton.classList.toggle("action-button-disabled", !enabled);
            }

            openButton?.addEventListener("click", openModal);
            [closeButton, cancelButton].forEach(function (button) {
                button?.addEventListener("click", closeModal);
            });
            modal.addEventListener("click", function (event) {
                if (event.target === modal) {
                    closeModal();
                }
            });
            input?.addEventListener("input", renderSubmitState);
            requestButton?.addEventListener("click", async function () {
                otpRequested = false;
                renderSubmitState();
                setOtpButtonState(requestButton, "loading", "${sendOtpLabel}", "${sendingOtpLabel}", "${otpSentLabel}");
                try {
                    const response = await fetch("/app/loan-applications/request-forfeit-otp", {
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
                    if (!response.ok || payload.valid === false) {
                        throw new Error(payload.message || "${unableSendOtpLabel}");
                    }
                    otpRequested = true;
                    setFeedback("success", payload.message || "${sentOtpEmailLabel}");
                    setOtpButtonState(requestButton, "sent", "${sendOtpLabel}", "${sendingOtpLabel}", "${otpSentLabel}");
                    window.SaccosOtp?.startCooldown(requestButton, payload, { idle: "${sendOtpLabel}" });
                    window.SaccosOtp?.focusBoxes(input);
                    renderSubmitState();
                } catch (error) {
                    setFeedback("error", error.message || "${unableSendOtpLabel}");
                    setOtpButtonState(requestButton, "idle", "${sendOtpLabel}", "${sendingOtpLabel}", "${otpSentLabel}");
                }
            });
        })();

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

<%@ include file="../fragments/live-account-status-hydration.jspf" %>
<%@ include file="../fragments/loan-export-modal.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
