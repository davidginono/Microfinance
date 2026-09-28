<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>

<c:set var="showTopUpAction" value="${canRequestTopUp}" />
<c:if test="${showTopUpAction}">
    <c:url value="/app/loan-applications/new" var="topUpLoanUrl">
        <c:choose>
            <c:when test="${not empty app.loanProductSettingId}">
                <c:param name="loanProductId" value="${app.loanProductSettingId}" />
            </c:when>
            <c:otherwise>
                <c:param name="loanType" value="${app.loanType}" />
            </c:otherwise>
        </c:choose>
        <c:param name="topUpLoanId" value="${app.id}" />
    </c:url>
</c:if>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="loan.view.breadcrumb" text="Borrower Workspace / Application Detail" /></p>
    <h1 class="erp-page-title"><spring:message code="loan.detail" /></h1>
</div>
<div class="loan-application-detail-page">
<c:set var="declarationSaccoName" value="${not empty activeSaccoName ? activeSaccoName : 'your institution'}" />
<c:if test="${app.status eq 'AWAITING_GUARANTORS'}">
    <div class="aws-inline-notice loan-detail-notice" role="status">
        <spring:message code="loan.view.awaitingGuarantors" text="Waiting for guarantor approval. This page auto-refreshes every 1 hour." />
    </div>
    <script>
        setTimeout(function () { window.location.reload(); }, 3600000);
    </script>
</c:if>
<c:if test="${app.status eq 'ALL_GUARANTORS_APPROVED'}">
    <div class="aws-inline-notice aws-inline-notice--success loan-detail-notice" role="status">
        <spring:message code="loan.view.guarantorsApproved" text="All guarantors have approved this application. You can now submit it for review." />
    </div>
</c:if>
<c:if test="${app.status eq 'DISBURSED' and empty app.applicantDisbursementAcknowledgedAt}">
    <div class="aws-inline-notice aws-inline-notice--success loan-detail-notice" role="status">
        <span><spring:message code="loan.disbursement.memberReadyAck" text="Your loan has been disbursed." /></span>
        <form method="post" action="${pageContext.request.contextPath}/app/loan-applications/${app.id}/acknowledge-disbursement" class="m-0">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <input type="hidden" name="returnTo" value="detail" />
            <button type="submit" class="app-btn btn-neutral"><spring:message code="common.acknowledge" text="Acknowledge" /></button>
        </form>
    </div>
</c:if>
<c:if test="${rejectionAcknowledgementRequired}">
    <div class="aws-inline-notice aws-inline-notice--danger loan-detail-notice" role="alert">
        <span><spring:message code="loan.rejection.memberReadyAck" text="This loan application was rejected. Review the decision and acknowledge it to clear it from your progress view." /></span>
        <form method="post" action="${pageContext.request.contextPath}/app/loan-applications/${app.id}/acknowledge-rejection" class="m-0">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <input type="hidden" name="returnTo" value="detail" />
            <button type="submit" class="app-btn btn-neutral"><spring:message code="common.acknowledge" text="Acknowledge" /></button>
        </form>
    </div>
</c:if>
<c:if test="${showTopUpAction or canPrint}">
    <div class="loan-detail-action-row">
        <c:if test="${canPrint}">
            <a href="${pageContext.request.contextPath}/documents/loan-applications/${app.id}/print?signatureMode=signed" data-print-action="true" class="app-btn btn-neutral">
                <span class="loan-document-action-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 9V2h12v7"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><path d="M6 14h12v8H6z"/></svg>
                </span>
                <spring:message code="common.print" text="Print" />
            </a>
            <button type="button" data-aws-action-pin="true" data-loan-export-url="${pageContext.request.contextPath}/documents/loan-applications/${app.id}/print" class="app-btn btn-neutral">
                <span class="loan-document-action-icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M9 15h6"/><path d="M9 18h4"/></svg>
                </span>
                <spring:message code="common.export" text="Export" />
            </button>
        </c:if>
        <c:if test="${showTopUpAction}">
            <a href="${topUpLoanUrl}" class="app-btn btn-launch">
                <spring:message code="loan.actions.topUp" text="Request Loan Top-Up" />
            </a>
        </c:if>
    </div>
</c:if>
<div class="loan-view-hero-summary loan-staff-review-template">
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
                        <span class="app-badge loan-status-badge ${loanStatusBadgeClass}">
                            <spring:message code="loan.status.${app.status}" text="${app.status}" />
                        </span>
                    </div>
                </div>
            </div>
            <div class="mt-4 loan-hero-facts-grid sm:grid-cols-2">
                <div class="loan-hero-inline-fact">
                    <div class="loan-hero-inline-label"><spring:message code="reports.loanProduct" text="Loan Product" /></div>
                    <div class="loan-hero-inline-value"><c:out value="${loanProductName}" /></div>
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
            <div class="loan-staff-kpi-grid">
                <div class="loan-view-summary-card loan-staff-kpi-card">
                    <div class="loan-stat-label"><spring:message code="loan.amount.label" text="Loan Amount" /></div>
                    <div class="loan-stat-value">TSh <fmt:formatNumber value="${app.amount}" minFractionDigits="0" maxFractionDigits="2" /></div>
                </div>
                <div class="loan-view-summary-card loan-staff-kpi-card">
                    <div class="loan-stat-label"><spring:message code="loan.tenor.label" text="Tenor" /></div>
                    <div class="loan-stat-value">${app.tenorMonths} <spring:message code="common.months" text="month(s)" /></div>
                </div>
                <div class="loan-view-summary-card loan-staff-kpi-card">
                    <div class="loan-stat-label"><spring:message code="loan.currentSavings" text="Current Disposable Income" /></div>
                    <div class="loan-stat-value" data-live-account-status-savings>${applicantExternalAccountStatus.savingsLabel}</div>
                </div>
                <div class="loan-view-summary-card loan-staff-kpi-card">
                    <div class="loan-stat-label"><spring:message code="loan.currentShares" text="Current Risk History" /></div>
                    <div class="loan-stat-value" data-live-account-status-shares>${applicantExternalAccountStatus.sharesLabel}</div>
                </div>
            </div>
            <%@ include file="../fragments/live-account-status-message.jspf" %>
        </div>

        <%@ include file="../fragments/loan-application-progress.jspf" %>

    </div>
</div>

<div class="mt-4 space-y-4">
    <c:if test="${not empty decisionFeedback}">
        <div class="aws-inline-notice loan-detail-notice">
            <p class="font-semibold"><spring:message code="review.decisionFeedback" text="Decision Feedback" /></p>
            <div class="mt-2 space-y-2">
                <c:forEach items="${decisionFeedback}" var="feedback">
                    <div>
                        <strong><c:out value="${feedback.role}" />:</strong>
                        <c:out value="${feedback.reason}" />
                    </div>
                </c:forEach>
            </div>
        </div>
    </c:if>
</div>

<c:if test="${app.status eq 'READY_FOR_MANAGER' and not empty pendingManagerStageWithdrawal}">
    <div class="aws-inline-notice aws-inline-notice--warning loan-detail-notice" role="status">
        <spring:message code="loan.view.removalPending" text="Your application removal request is already waiting for the manager's decision." />
    </div>
</c:if>

<c:choose>
    <c:when test="${not empty financialFieldSections}">
        <div class="grid gap-3 md:grid-cols-2">
            <c:forEach items="${financialFieldSections}" var="section">
<div class="erp-table-wrap loan-summary-table" aria-label="${fn:escapeXml(section.key)}" data-aws-table-region data-aws-no-titlebar="true" data-loading-label="Loading results...">
                    <div class="border-b border-slate-200 bg-slate-50 px-3 py-2">
                        <p class="text-xs font-bold uppercase tracking-[0.16em] text-slate-600">${section.key}</p>
                    </div>
                    <div class="erp-table-scroll erp-table-scroll-sm">
                    <table class="erp-table">
                        <thead>
                        <tr>
                            <th><spring:message code="newloan.table.section" text="Section" /></th>
                            <th><spring:message code="newloan.table.value" text="Value" /></th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${section.value}" var="entry">
                            <tr>
                                <td class="px-3 py-2 font-medium text-slate-700">${entry.key}</td>
                                <td class="px-3 py-2">${entry.value}</td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
        </div>
            </c:forEach>
            </div>
    </c:when>
    <c:otherwise>
        <div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
            <div class="app-table-titlebar">
                <div class="app-table-heading"><h2><spring:message code="loan.details" text="Loan Details" /></h2></div>
        </div>
            <div class="erp-table-scroll">
            <table class="erp-table">
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
    </div>
    </c:otherwise>
</c:choose>

<c:if test="${not empty calculatedRepaymentRows}">
<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
        <div class="app-table-titlebar">
            <div class="app-table-heading">
                <h2>
                    <c:choose>
                        <c:when test="${not empty app.disbursementDate}">
                            <spring:message code="loan.repayment.actualSchedule" text="Actual Repayment Schedule" />
                        </c:when>
                        <c:otherwise>
                            <spring:message code="loan.repayment.calculatedSchedule" text="Calculated Repayment Schedule" />
                        </c:otherwise>
                    </c:choose>
                </h2>
            </div>
            </div>
        <div class="erp-table-scroll">
        <table class="erp-table loan-repayment-schedule-table">
            <thead>
                <tr>
                    <th><spring:message code="repayment.pmtNo" text="Pmt No." /></th>
                    <th><spring:message code="repayment.month" text="Month" /></th>
                    <th><spring:message code="repayment.beginningBalance" text="Beginning Balance" /></th>
                    <th><spring:message code="repayment.amountToPay" text="Amount to Pay" /></th>
                    <th><spring:message code="repayment.principal" text="Principal" /></th>
                    <th><spring:message code="repayment.interest" text="Interest" /></th>
                    <th><spring:message code="repayment.endingBalance" text="Ending Balance" /></th>
                </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
                <c:forEach items="${calculatedRepaymentRows}" var="row">
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
        </div>
</c:if>

<c:if test="${app.status eq 'DISBURSED' or app.status eq 'DEFAULTED' or app.status eq 'PAID'}">
    <div id="repayment-plan" class="erp-section"
         data-repayment-schedule-section="true"
         data-repayment-schedule-url="${pageContext.request.contextPath}${repaymentSchedulePath}"
         data-repayment-summary-card-class="loan-repayment-card">
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
            <div class="loan-repayment-grid" data-repayment-summary-grid>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.installmentAmount" text="Installment Amount" /></div>
                    <div class="loan-repayment-card-value" data-repayment-summary-value="Installment Amount">
                        <c:out value="${repaymentSummary['Installment Amount']}" default="Pending update" />
                </div>
                    </div>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.totalInterest" text="Total Interest" /></div>
                    <div class="loan-repayment-card-value" data-repayment-summary-value="Total Interest">
                        <c:out value="${repaymentSummary['Total Interest']}" default="Pending update" />
                </div>
                    </div>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.totalPrincipal" text="Total Principal" /></div>
                    <div class="loan-repayment-card-value" data-repayment-summary-value="Total Principal">
                        <c:out value="${repaymentSummary['Total Principal']}" default="Pending update" />
                </div>
                    </div>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.totalAmount" text="Total Amount" /></div>
                    <div class="loan-repayment-card-value" data-repayment-summary-value="Total Amount">
                        <c:out value="${repaymentSummary['Total Amount']}" default="Pending update" />
                </div>
                    </div>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.frequency" text="Repayment Frequency" /></div>
                    <div class="loan-repayment-card-value" data-repayment-summary-value="Repayment Frequency">
                        <c:out value="${repaymentSummary['Repayment Frequency']}" default="Pending update" />
                </div>
                    </div>
                <div class="loan-repayment-card">
                    <div class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="loan.repayment.firstRepayment" text="First Repayment" /></div>
                    <div class="loan-repayment-card-value" data-repayment-summary-value="First Repayment Date">
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
                    <div class="loan-repayment-card-value" data-repayment-summary-value="Final Due Date">
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
        <details class="loan-detail-disclosure" open>
            <summary class="loan-detail-disclosure__summary">
                <span><spring:message code="loan.repayment.schedule" text="Repayment Schedule" /></span>
                <span class="inline-flex items-center gap-2">
                    <button type="button"
                            class="app-icon-button btn-neutral"
                            data-repayment-schedule-refresh
                            data-loading-label="<spring:message code='loan.repayment.refreshingSchedule' text='Refreshing repayment schedule' />"
                            aria-label="<spring:message code='loan.repayment.refreshSchedule' text='Refresh repayment schedule' />"
                            title="<spring:message code='loan.repayment.refreshSchedule' text='Refresh repayment schedule' />">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <path d="M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5" />
                            <path d="M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5" />
                        </svg>
                    </button>
                    <span class="loan-detail-disclosure__chevron" aria-hidden="true"></span>
                </span>
            </summary>
            <div class="erp-table-wrap rounded-none border-0" data-aws-no-titlebar="true">
                <div class="erp-table-scroll">
                <table class="erp-table loan-repayment-schedule-table">
                    <thead>
                        <tr>
                            <th><spring:message code="repayment.pmtNo" text="Pmt No." /></th>
                            <th><spring:message code="repayment.month" text="Month" /></th>
                            <th><spring:message code="repayment.beginningBalance" text="Beginning Balance" /></th>
                            <th><spring:message code="repayment.amountToPay" text="Amount to Pay" /></th>
                            <th><spring:message code="repayment.principal" text="Principal" /></th>
                            <th><spring:message code="repayment.interest" text="Interest" /></th>
                            <th><spring:message code="repayment.endingBalance" text="Ending Balance" /></th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100" data-repayment-schedule-body>
                        <c:choose>
                            <c:when test="${not empty repaymentRows}">
                                <c:forEach items="${repaymentRows}" var="row">
                                    <tr>
                                        <td class="px-3 py-2 font-medium text-slate-700">${row.pmtNo}</td>
                                        <td class="px-3 py-2">${not empty row.month ? row.month : row.dueDate}</td>
                                        <td class="px-3 py-2">${row.beginningBalance}</td>
                                        <td class="px-3 py-2 font-medium text-slate-900">${not empty row.payment ? row.payment : row.amount}</td>
                                        <td class="px-3 py-2">${not empty row.loanAmount ? row.loanAmount : row.principal}</td>
                                        <td class="px-3 py-2">${row.interest}</td>
                                        <td class="px-3 py-2">${not empty row.endingBalance ? row.endingBalance : row.outstandingBalance}</td>
                                    </tr>
                                </c:forEach>
                            </c:when>
                            <c:otherwise>
                                <tr data-repayment-schedule-empty>
                                    <td colspan="7" class="px-3 py-4 text-center text-sm text-slate-600">
                                        <spring:message code="loan.repayment.schedulePending" text="Repayment schedule will appear after disbursement." />
                                    </td>
                                </tr>
                            </c:otherwise>
                        </c:choose>
                    </tbody>
                </table>
                        </div>
</div>
        </details>
    </div>
</c:if>

<c:if test="${not empty disbursementProofAttachments or app.status eq 'DISBURSED' or app.status eq 'DEFAULTED' or app.status eq 'PAID'}">
    <div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
        <div class="app-table-titlebar">
            <div class="app-table-heading"><h2><spring:message code="loan.attachments.disbursementProof" text="Disbursement Proof" /></h2></div>
</div>
        <div class="erp-table-scroll">
        <table class="erp-table">
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
                    <tr>
                        <td colspan="4" class="px-3 py-3 text-slate-500"><spring:message code="loan.attachments.noDisbursementProof" text="No disbursement proof has been uploaded for this loan yet." /></td>
                    </tr>
                </c:if>
            </tbody>
        </table>
                                    </div>
                                </div>
</c:if>

<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
    <div class="app-table-titlebar">
        <div class="app-table-heading"><h2><spring:message code="loan.attachments.applicationAttachments" text="Application Attachments" /></h2></div>
</div>
    <div class="erp-table-scroll">
    <table class="erp-table">
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
                    <td class="px-3 py-2"><c:out value="${appTime:format(file.uploadedAt)}" /></td>
                    <td class="px-3 py-2"><a href="/documents/loan-applications/${app.id}/attachments/${file.id}"
                            class="app-btn btn-primary" data-download-action="true"><spring:message code="common.download" text="Download" /></a></td>
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
            </div>

<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
    <div class="app-table-titlebar">
        <div class="app-table-heading"><h2><spring:message code="loan.guarantors" /></h2></div>
            </div>
    <div class="erp-table-scroll">
    <table class="erp-table">
        <thead class="bg-slate-50">
            <tr>
                <th class="w-[18%] px-3 py-2 text-left"><spring:message code="loan.guarantor" text="Guarantor" /></th>
                <th class="w-[12%] px-3 py-2 text-left"><spring:message code="member.memberNo" text="Client No" /></th>
                <th class="w-[14%] px-3 py-2 text-left"><spring:message code="loan.status" text="Status" /></th>
                <th class="w-[14%] px-3 py-2 text-left"><spring:message code="loan.date" text="Date" /></th>
                <th class="w-[42%] px-3 py-2 text-left"><spring:message code="loan.removalRequest" text="Removal Request" /></th>
            </tr>
        </thead>
        <tbody class="divide-y divide-slate-100">
            <c:forEach items="${guarantorRequests}" var="req">
                <tr>
                    <td class="px-3 py-2 align-top">
                        <c:choose>
                            <c:when test="${not empty guarantorNames[req.guarantorMemberId]}">
                                ${guarantorNames[req.guarantorMemberId]}</c:when>
                            <c:when test="${not empty req.externalFullName}">${req.externalFullName}</c:when>
                            <c:when test="${not empty req.externalMemberNo}">${req.externalMemberNo}</c:when>
                            <c:when test="${not empty req.guarantorMemberId}">#${fn:substring(req.guarantorMemberId, 0, 8)}</c:when>
                            <c:otherwise>Guarantor</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2 align-top">
                        <c:choose>
                            <c:when test="${not empty guarantorMembersById[req.guarantorMemberId] and not empty guarantorMembersById[req.guarantorMemberId].memberNo}">${guarantorMembersById[req.guarantorMemberId].memberNo}</c:when>
                            <c:when test="${not empty req.externalMemberNo}">${req.externalMemberNo}</c:when>
                            <c:otherwise>-</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="px-3 py-2 align-top">${req.status}</td>
                    <td class="px-3 py-2 align-top">
                        <c:choose>
                            <c:when test="${not empty req.decidedAt}"><c:out value="${appTime:format(req.decidedAt)}" /></c:when>
                            <c:otherwise><c:out value="${appTime:format(req.createdAt)}" /></c:otherwise>
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
                                              data-page-preloader="true"
                                              data-confirm-title="Approve Removal"
                                              data-confirm-message="Approve this request and remove the guarantor from this application?"
                                              data-confirm-proceed="Approve Removal">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                            <button type="submit" class="app-btn btn-primary"><spring:message code="loan.guarantorRemoval.approve" text="Approve Removal" /></button>
                                        </form>
                                        <form action="/app/loan-applications/${app.id}/guarantor-reversal-requests/${pendingUndo.id}/reject"
                                              method="post"
                                              data-page-preloader="true"
                                              data-confirm-title="Keep Guarantor"
                                              data-confirm-message="Decline this request and keep the guarantor on this application?"
                                              data-confirm-proceed="Keep Guarantor">
                                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                            <button type="submit" class="app-btn btn-neutral"><spring:message code="loan.guarantorRemoval.keep" text="Keep Guarantor" /></button>
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
                    </div>

<c:if test="${directOtpGuarantorPanelVisible}">
    <details class="loan-detail-disclosure" open>
        <summary class="loan-detail-disclosure__summary">
            <div class="flex flex-wrap items-center justify-between gap-2">
                <div>
                    <h5 class="erp-panel-title">
                        <spring:message code="loan.guarantorOtp.directTitle" text="Direct OTP approval" />
                    </h5>
                    <p class="mt-1 text-sm text-slate-500">
                        <spring:message code="loan.guarantorOtp.directHelp" text="Enter each guarantor code after they receive it and agree to guarantee this loan." />
                    </p>
                                </div>
                <span class="app-badge loan-selection-counter">
                    <spring:message code="loan.guarantorOtp.pending" text="Pending OTPs" />
                </span>
                            </div>
        </summary>
        <div class="mt-4">
            <div class="mb-3 flex flex-wrap items-center justify-between gap-2 border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">
                <span class="font-semibold text-slate-800">
                    <spring:message code="loan.guarantorOtp.currentStep" text="Current guarantor" />
                </span>
                <span>
                    1 of ${fn:length(directOtpGuarantorRequests)}
                </span>
                        </div>
            <div class="space-y-3">
            <c:forEach items="${directOtpGuarantorRequests}" var="req" varStatus="otpStatus">
                <div class="${otpStatus.first ? '' : 'hidden'} border border-slate-200 bg-white p-4"
                     data-guarantor-direct-otp-card
                     data-verify-url="/app/loan-applications/${app.id}/guarantors/${req.id}/verify-confirmation-otp"
                     data-resend-url="/app/loan-applications/${app.id}/guarantors/${req.id}/request-confirmation-otp-json">
                    <div class="mb-3 flex flex-wrap items-start justify-between gap-2">
                        <div>
                            <div class="text-sm font-semibold text-slate-900">
                                <c:choose>
                                    <c:when test="${not empty guarantorNames[req.guarantorMemberId]}">
                                        ${guarantorNames[req.guarantorMemberId]}
                                    </c:when>
                                    <c:when test="${not empty req.externalFullName}">${req.externalFullName}</c:when>
                                    <c:when test="${not empty req.externalMemberNo}">${req.externalMemberNo}</c:when>
                                    <c:when test="${not empty req.guarantorMemberId}">#${fn:substring(req.guarantorMemberId, 0, 8)}</c:when>
                                    <c:otherwise>Guarantor</c:otherwise>
                                </c:choose>
                </div>
                            <div class="mt-1 text-xs text-slate-500">
                                <spring:message code="member.memberNo" text="Client No" />:
                                <c:choose>
                                    <c:when test="${not empty guarantorMembersById[req.guarantorMemberId] and not empty guarantorMembersById[req.guarantorMemberId].memberNo}">${guarantorMembersById[req.guarantorMemberId].memberNo}</c:when>
                                    <c:when test="${not empty req.externalMemberNo}">${req.externalMemberNo}</c:when>
                                    <c:otherwise>-</c:otherwise>
                                </c:choose>
            </div>
        </div>
                        <button type="button"
                                class="app-btn btn-neutral otp-request-button"
                                data-guarantor-otp-resend>
                            <span class="otp-button-spinner hidden"></span>
                            <span class="otp-button-label"><spring:message code="loan.guarantorOtp.resend" text="Resend code" /></span>
                        </button>
        </div>
                    <form action="/app/loan-applications/${app.id}/guarantors/${req.id}/confirm-with-otp"
                          method="post"
                          class="space-y-3"
                          data-guarantor-otp-confirm-form>
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                        <input id="directGuarantorOtpCode-${req.id}"
                               name="otpCode"
                               type="text"
                               inputmode="numeric"
                               maxlength="6"
                               data-otp-hidden="true"
                               data-guarantor-otp-input
                               data-otp-label="Guarantor OTP code"
                               autocomplete="one-time-code"
                               class="sr-only"
                               placeholder="123456" />
                        <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                            <div class="min-h-[2.5rem]">
                                <div class="hidden border px-3 py-2 text-sm"
                                     data-guarantor-otp-status>
                                    <span data-guarantor-otp-status-text></span>
    </div>
        </div>
                            <button type="submit"
                                    class="app-btn btn-primary action-button-disabled"
                                    data-guarantor-otp-confirm
                                    disabled>
                                <spring:message code="loan.guarantorOtp.confirm" text="Confirm" />
                            </button>
                        </div>
                    </form>
                    </div>
            </c:forEach>
                        </div>
                    </div>
    </details>
</c:if>

<c:set var="requiredDraftGuarantors" value="${empty app.requiredGuarantors ? 0 : app.requiredGuarantors}" />
<c:set var="draftSelectedGuarantorCount" value="${empty draftSelectedGuarantors ? 0 : fn:length(draftSelectedGuarantors)}" />
<c:set var="draftGuarantorSelectionComplete" value="${app.status eq 'DRAFT' and requiredDraftGuarantors gt 0 and draftSelectedGuarantorCount eq requiredDraftGuarantors}" />
<c:set var="showDraftSelectGuarantorAction" value="${app.status eq 'DRAFT' and requiredDraftGuarantors gt 0 and not draftGuarantorSelectionComplete}" />
<c:set var="showEditAction" value="${app.status eq 'DRAFT' or app.status eq 'ALL_GUARANTORS_APPROVED'}" />
<c:set var="showDraftSendToGuarantorsAction" value="${draftGuarantorSelectionComplete}" />
<c:set var="showMemberReversalAction" value="${app.status eq 'AWAITING_GUARANTORS' or app.status eq 'ALL_GUARANTORS_APPROVED' or app.status eq 'READY_FOR_MANAGER'}" />
<c:set var="showDeleteAction" value="${app.status eq 'DRAFT' or app.status eq 'AWAITING_GUARANTORS'}" />
<c:set var="showLoanActionCard" value="${showEditAction or showDraftSelectGuarantorAction or showDraftSendToGuarantorsAction or (showMemberReversalAction and app.status ne 'READY_FOR_MANAGER') or showDeleteAction}" />
<c:set var="canSubmitApprovedGuarantorLoan" value="${app.status eq 'ALL_GUARANTORS_APPROVED' and not pendingGuarantorRemovalRequest}" />
<spring:message code="loan.confirm.cancelSubmission.title" text="Cancel Application" var="cancelSubmissionTitle" />
<spring:message code="loan.confirm.cancelSubmission.message" text="Move this application back to draft so you can keep editing it?" var="cancelSubmissionMessage" />
<spring:message code="loan.confirm.cancelSubmission.proceed" text="Cancel Application" var="cancelSubmissionProceed" />
<spring:message code="loan.confirm.sendToGuarantors.title" text="Send to Guarantors" var="sendToGuarantorsTitle" />
<spring:message code="loan.confirm.sendToGuarantors.message" text="Send this draft to the selected guarantors for approval?" var="sendToGuarantorsMessage" />
<spring:message code="loan.confirm.sendToGuarantors.proceed" text="Send to Guarantors" var="sendToGuarantorsProceed" />
<spring:message code="loan.submit.blockedByGuarantorRemoval" text="Approve the pending guarantor removal request before submitting this application." var="submitBlockedByGuarantorRemovalMessage" />
<spring:message code="loan.confirm.deleteApplication.title" text="Delete Application" var="deleteApplicationTitle" />
<spring:message code="loan.confirm.deleteApplication.message" text="Delete this application completely? This will remove it from your view and from every review queue." var="deleteApplicationMessage" />
<spring:message code="loan.confirm.deleteApplication.proceed" text="Delete Application" var="deleteApplicationProceed" />
<c:if test="${showLoanActionCard}">
    <div class="erp-form-wrap loan-page-bottom-actions">
        <div class="loan-view-action-cluster">
            <c:if test="${showDraftSelectGuarantorAction}">
                <a href="/app/loan-applications/${app.id}/edit?step=3" class="app-btn btn-primary">
                    <spring:message code="loan.actions.selectGuarantor" text="Select Guarantor" />
                </a>
            </c:if>
            <c:if test="${showEditAction and not showDraftSelectGuarantorAction}">
                <a href="/app/loan-applications/${app.id}/edit" class="app-btn btn-neutral">
                    <spring:message code="loan.actions.reEdit" text="Re-edit Application" />
                </a>
            </c:if>
            <c:if test="${showDraftSendToGuarantorsAction}">
                <form action="/app/loan-applications/${app.id}/submit"
                      method="post"
                      data-confirm-title="${sendToGuarantorsTitle}"
                      data-confirm-message="${sendToGuarantorsMessage}"
                      data-confirm-proceed="${sendToGuarantorsProceed}">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <button type="submit" class="app-btn btn-primary">
                        <spring:message code="loan.actions.sendToGuarantors" text="Send to Guarantors" />
                    </button>
                </form>
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
                <c:choose>
                    <c:when test="${pendingGuarantorRemovalRequest}">
                        <button type="button" class="app-btn btn-primary action-button-disabled" disabled>
                            <c:out value="${loanFinalSubmitLabel}" />
                        </button>
                        <p class="basis-full text-sm text-amber-700">
                            <c:out value="${submitBlockedByGuarantorRemovalMessage}" />
                        </p>
                    </c:when>
                    <c:otherwise>
                        <button type="button" id="openApplicantSubmitOtp" class="app-btn btn-primary">
                            <c:out value="${loanFinalSubmitLabel}" />
                        </button>
                    </c:otherwise>
                </c:choose>
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
                </div>
    </div>
</c:if>

<c:if test="${canSubmitApprovedGuarantorLoan}">
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
            <label class="flex items-start gap-3 border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-700">
                <input type="checkbox" name="termsAccepted" value="true" required class="mt-1 h-4 w-4 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue" />
                <span><spring:message code="loan.terms.accept" text="I accept the terms and conditions for this loan application." /></span>
            </label>
            <c:if test="${applicantApprovalOtpEnabled}">
                <div class="border border-slate-200 bg-slate-50 px-4 py-4">
                    <div class="flex flex-wrap items-center justify-between gap-3">
                        <div>
                            <div class="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="loan.otp.code" text="OTP Code" /></div>
                            <p class="mt-2 text-sm text-slate-600"><spring:message code="loan.otp.codeHelp" text="The code is sent using the Branch OTP delivery policy." /></p>
                        </div>
                        <button id="requestApplicantSubmitOtp" type="button" class="app-btn btn-primary otp-request-button inline-flex items-center justify-center gap-2">
                            <span class="otp-button-spinner hidden"></span>
                            <span class="otp-button-label"><spring:message code="loan.otp.sendCode" text="Send OTP Code" /></span>
                        </button>
                    </div>
                    <div id="applicantSubmitOtpFeedback" data-auto-scroll-message="true" class="mt-3 hidden border px-4 py-3 text-sm"></div>
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
                               class="fcms-control loan-otp-input"
                               placeholder="123456" />
                        <div id="applicantSubmitOtpLiveStatus" class="mt-3 hidden items-center gap-2 border border-slate-200 bg-white px-3 py-2 text-sm text-slate-600">
                            <span data-otp-spinner class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-slate-300 border-t-sacco-blue"></span>
                            <svg data-otp-tick class="otp-checkmark-pop hidden h-5 w-5 text-emerald-600" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                <path fill-rule="evenodd" d="M16.704 5.29a1 1 0 010 1.42l-7.25 7.25a1 1 0 01-1.415 0l-3.25-3.25a1 1 0 111.414-1.42l2.543 2.544 6.543-6.544a1 1 0 011.415 0z" clip-rule="evenodd"/>
                            </svg>
                            <span data-otp-text><spring:message code="loan.otp.checking" text="Checking code..." /></span>
                        </div>
                    </div>
                </div>
            </c:if>
            <button id="submitApplicantToReview"
                    type="submit"
                    class="app-btn btn-approve ${applicantApprovalOtpEnabled ? 'action-button-disabled' : ''} loan-submit-to-review-button w-full"
                    ${applicantApprovalOtpEnabled ? 'disabled' : ''}>
                <c:out value="${loanFinalSubmitLabel}" />
            </button>
        </form>
    </div>
</c:if>

</div>

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
                    headers: {"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","Accept":"application/json"
                    },
                    body: new URLSearchParams({"${_csrf.parameterName}": csrfToken,"applicationId":"${app.id}","otpCode": code
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
                const previousOtpRequested = otpRequested;
                activeVerification += 1;
                verifiedCode = "";
                setSubmitEnabled(false);
                renderHidden();
                setOtpButtonState(requestButton, "loading", "${sendOtpLabel}", "${sendingOtpLabel}", "${otpSentLabel}");
                try {
                    const response = await fetch("/app/loan-applications/request-signature-otp", {
                        method: "POST",
                        headers: {"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","Accept":"application/json"
                        },
                        body: new URLSearchParams({"${_csrf.parameterName}": csrfToken,"applicationId":"${app.id}"
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
                    otpRequested = previousOtpRequested;
                    setFeedback("error", error.message || "${unableSendOtpLabel}");
                    setOtpButtonState(requestButton, "idle", "${sendOtpLabel}", "${sendingOtpLabel}", "${otpSentLabel}");
                    renderSubmitState();
                }
            });
        })();

        (function bindDirectGuarantorOtpApproval() {
            const cards = Array.from(document.querySelectorAll("[data-guarantor-direct-otp-card]"));
            if (!cards.length) {
                return;
            }

            function setStatus(status, type, message) {
                if (!status) {
                    return;
                }
                const text = status.querySelector("[data-guarantor-otp-status-text]");
                status.classList.remove("hidden","border-slate-200","bg-white","text-slate-600","border-emerald-200","bg-emerald-50","text-emerald-700","border-rose-200","bg-rose-50","text-rose-700"
                );
                if (type === "success") {
                    status.classList.add("border-emerald-200", "bg-emerald-50", "text-emerald-700");
                } else if (type === "error") {
                    status.classList.add("border-rose-200", "bg-rose-50", "text-rose-700");
                } else {
                    status.classList.add("border-slate-200", "bg-white", "text-slate-600");
                }
                if (text) {
                    text.textContent = message || "";
                }
            }

            function hideStatus(status) {
                if (status) {
                    status.classList.add("hidden");
                }
            }

            function setConfirmEnabled(button, enabled) {
                if (!button) {
                    return;
                }
                button.disabled = !enabled;
                button.classList.toggle("action-button-disabled", !enabled);
            }

            function setResendLimitReached(button) {
                if (!button) {
                    return;
                }
                window.clearInterval(button._otpCooldownTimer);
                window.clearTimeout(button._otpCooldownTimeout);
                button.disabled = true;
                button.classList.remove("is-loading");
                button.classList.add("is-sent");
                const label = button.querySelector(".otp-button-label");
                if (label) {
                    label.textContent = "Resend limit reached";
                }
            }

            function isResendLimitError(error) {
                return Boolean(error && error.message && error.message.indexOf("OTP resend limit reached") >= 0);
            }

            async function postForm(url, values) {
                const response = await fetch(url, {
                    method: "POST",
                    headers: {"Content-Type":"application/x-www-form-urlencoded;charset=UTF-8","Accept":"application/json"
                    },
                    body: new URLSearchParams(values)
                });
                const payload = await response.json();
                if (!response.ok || payload.valid === false) {
                    throw new Error(payload.message || "${invalidOtpLabel}");
                }
                return payload;
            }

            cards.forEach(function (card) {
                const input = card.querySelector("[data-guarantor-otp-input]");
                const confirmButton = card.querySelector("[data-guarantor-otp-confirm]");
                const resendButton = card.querySelector("[data-guarantor-otp-resend]");
                const status = card.querySelector("[data-guarantor-otp-status]");
                let activeVerification = 0;
                let verifiedCode = "";

                function renderForInput() {
                    if (!input) {
                        return;
                    }
                    input.value = (input.value || "").replace(/\D/g, "").slice(0, 6);
                    const code = input.value;
                    if (!/^\d{6}$/.test(code)) {
                        activeVerification += 1;
                        verifiedCode = "";
                        setConfirmEnabled(confirmButton, false);
                        hideStatus(status);
                        return;
                    }
                    if (verifiedCode === code) {
                        setConfirmEnabled(confirmButton, true);
                        setStatus(status, "success", "Verified");
                        return;
                    }
                    const requestId = ++activeVerification;
                    verifiedCode = "";
                    setConfirmEnabled(confirmButton, false);
                    setStatus(status, "pending", "Verifying code...");
                    postForm(card.dataset.verifyUrl, {"${_csrf.parameterName}": csrfToken,"otpCode": code
                    }).then(function () {
                        if (requestId !== activeVerification || input.value !== code) {
                            return;
                        }
                        verifiedCode = code;
                        setStatus(status, "success", "Verified");
                        setConfirmEnabled(confirmButton, true);
                        confirmButton?.focus({ preventScroll: true });
                    }).catch(function (error) {
                        if (requestId !== activeVerification || input.value !== code) {
                            return;
                        }
                        verifiedCode = "";
                        setConfirmEnabled(confirmButton, false);
                        setStatus(status, "error", error && error.message ? error.message : "${invalidOtpLabel}");
                    });
                }

                input?.addEventListener("input", renderForInput);
                renderForInput();

                resendButton?.addEventListener("click", async function () {
                    activeVerification += 1;
                    verifiedCode = "";
                    if (input) {
                        input.value = "";
                        input.dispatchEvent(new Event("input", { bubbles: true }));
                    }
                    setConfirmEnabled(confirmButton, false);
                    setOtpButtonState(resendButton, "loading", "Resend code", "${sendingOtpLabel}", "${otpSentLabel}");
                    try {
                        const payload = await postForm(card.dataset.resendUrl, {"${_csrf.parameterName}": csrfToken
                        });
                        setStatus(status, "success", payload.message || "OTP sent.");
                        setOtpButtonState(resendButton, "sent", "Resend code", "${sendingOtpLabel}", "${otpSentLabel}");
                        window.SaccosOtp?.startCooldown(resendButton, payload, { idle: "Resend code" });
                        window.SaccosOtp?.focusBoxes(input);
                    } catch (error) {
                        setStatus(status, "error", error && error.message ? error.message : "${unableSendOtpLabel}");
                        if (isResendLimitError(error)) {
                            setResendLimitReached(resendButton);
                        } else {
                            setOtpButtonState(resendButton, "idle", "Resend code", "${sendingOtpLabel}", "${otpSentLabel}");
                        }
                    }
                });
            });
        })();


        document.querySelectorAll("form[action$='/submit'], form[data-confirm-title]").forEach(function (form) {
            form.addEventListener("submit", function () {
                if (form.dataset.confirmTitle) {
                    return;
                }
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
<%@ include file="../fragments/repayment-schedule-hydration.jspf" %>
<%@ include file="../fragments/loan-export-modal.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
