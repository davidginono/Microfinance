<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Member Workspace / Reporting & Export</p>
    <h1 class="erp-page-title">My Reporting & Export</h1>
    <p class="erp-page-subtitle">Review your current and completed loan applications and export the current member loan report.</p>
</div>

<section class="grid gap-4 xl:grid-cols-[1.2fr_1fr]">
    <div class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <p class="erp-widget-title">Report Specification</p>
            <h2 class="mt-1 text-xl font-bold text-sacco-ink">Member Loan Report</h2>
        </div>
        <div class="erp-panel-body space-y-5">
            <div>
                <p class="text-sm font-semibold text-sacco-ink">Owner</p>
                <p class="mt-1 text-sm text-slate-600">${reportOwnerName}</p>
            </div>
            <div>
                <p class="text-sm font-semibold text-sacco-ink">Purpose</p>
                <p class="mt-1 text-sm text-slate-600">
                    Provide a member-facing snapshot of current applications, disbursed loans, completed loans, and ongoing obligations.
                </p>
            </div>
            <div>
                <p class="text-sm font-semibold text-sacco-ink">Recommended export format</p>
                <ul class="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
                    <li>PDF</li>
                    <li>Excel</li>
                    <li>CSV</li>
                </ul>
            </div>
            <div>
                <p class="text-sm font-semibold text-sacco-ink">Report includes</p>
                <ul class="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
                    <li>Loan Application ID</li>
                    <li>Loan ID</li>
                    <li>Member details</li>
                    <li>Loan product type</li>
                    <li>Approved amount and tenure</li>
                    <li>Estimated fee and insurance deductions</li>
                    <li>Guarantor details</li>
                    <li>Approval decision summary</li>
                    <li>Prepared by and date</li>
                </ul>
            </div>
        </div>
    </div>

    <div class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <p class="erp-widget-title">Export Action</p>
            <h2 class="mt-1 text-xl font-bold text-sacco-ink">Download Current Report</h2>
        </div>
        <div class="erp-panel-body">
            <p class="text-sm text-slate-600">
                Export the current member loan report as a detailed PDF, Excel, or CSV document for printing, sharing, or offline review.
            </p>
            <div class="mt-4 flex flex-wrap gap-3">
                <a href="/documents/reports/member-loans.pdf" class="app-btn btn-primary">Download PDF Report</a>
                <a href="/documents/reports/member-loans.xlsx" class="app-btn btn-neutral">Download Excel Report</a>
                <a href="/documents/reports/member-loans.csv" class="app-btn btn-neutral">Download CSV Report</a>
            </div>
        </div>
    </div>
</section>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title">Portfolio Detail</p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink">Detailed Loan Applications</h2>
    </div>
    <div class="erp-panel-body space-y-4">
        <c:forEach items="${reportDetails}" var="detail" varStatus="loop">
            <section class="rounded-xl border border-slate-200 bg-white shadow-sm">
                <div class="border-b border-slate-200 bg-slate-50 px-4 py-3">
                    <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Loan ${loop.index + 1}</p>
                    <h3 class="mt-1 text-lg font-bold text-sacco-ink">${detail.approvedProductLabel}</h3>
                </div>
                <div class="grid gap-4 px-4 py-4 md:grid-cols-2 xl:grid-cols-3">
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Loan Application ID</p>
                        <p class="mt-1 text-sm font-semibold text-slate-900">${detail.loanApplicationIdLabel}</p>
                    </div>
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Loan ID</p>
                        <p class="mt-1 text-sm font-semibold text-slate-900">${detail.loanIdLabel}</p>
                    </div>
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Member Details</p>
                        <p class="mt-1 text-sm text-slate-700">${detail.memberDetailsLabel}</p>
                    </div>
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Approved Amount</p>
                        <p class="mt-1 text-sm font-semibold text-slate-900">${detail.approvedAmountLabel}</p>
                    </div>
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Tenure</p>
                        <p class="mt-1 text-sm text-slate-700">${detail.tenureLabel}</p>
                    </div>
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Status</p>
                        <p class="mt-1 text-sm font-semibold text-slate-900">${detail.statusLabel}</p>
                    </div>
                    <div class="md:col-span-2 xl:col-span-3">
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Estimated Fee / Insurance Deductions</p>
                        <p class="mt-1 text-sm text-slate-700">${detail.deductionSummaryLabel}</p>
                    </div>
                    <div class="md:col-span-2 xl:col-span-3">
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Guarantor Details</p>
                        <p class="mt-1 text-sm text-slate-700">${detail.guarantorDetailsLabel}</p>
                    </div>
                    <div class="md:col-span-2 xl:col-span-3">
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Approval Decision Summary</p>
                        <p class="mt-1 text-sm text-slate-700">${detail.approvalDecisionSummaryLabel}</p>
                    </div>
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Disbursement Date</p>
                        <p class="mt-1 text-sm text-slate-700">${detail.disbursementDateLabel}</p>
                    </div>
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Final Due Date</p>
                        <p class="mt-1 text-sm text-slate-700">${detail.finalDueDateLabel}</p>
                    </div>
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Prepared By and Date</p>
                        <p class="mt-1 text-sm text-slate-700">${detail.preparedByLabel} | ${detail.preparedDateLabel}</p>
                    </div>
                </div>
            </section>
        </c:forEach>
        <c:if test="${empty reportDetails}">
            <div class="rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">
                No loan applications found for this member yet.
            </div>
        </c:if>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
