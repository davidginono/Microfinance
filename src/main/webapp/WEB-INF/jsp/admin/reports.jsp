<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">Admin Tools / Reports</p>
    <h1 class="erp-page-title">Reporting & Export Architecture</h1>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title">25.1 Report Categories</p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink">Report Categories</h2>
    </div>
<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
        <div class="erp-table-scroll">
        <table class="erp-table">
            <thead class="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <tr>
                    <th class="px-4 py-3">Category</th>
                    <th class="px-4 py-3">Reports</th>
                </tr>
            </thead>
            <tbody class="divide-y divide-slate-200 bg-white text-slate-700">
                <tr>
                    <td class="px-4 py-3 font-semibold text-sacco-ink">Application reports</td>
                    <td class="px-4 py-3">Submitted, approved, rejected, pending</td>
                </tr>
                <tr>
                    <td class="px-4 py-3 font-semibold text-sacco-ink">Workflow reports</td>
                    <td class="px-4 py-3">Manager pending, committee pending, turnaround time</td>
                </tr>
                <tr>
                    <td class="px-4 py-3 font-semibold text-sacco-ink">Guarantor reports</td>
                    <td class="px-4 py-3">Pending consent, accepted, rejected</td>
                </tr>
                <tr>
                    <td class="px-4 py-3 font-semibold text-sacco-ink">Disbursement reports</td>
                    <td class="px-4 py-3">Pending disbursement, details captured, reconciliation</td>
                </tr>
                <tr>
                    <td class="px-4 py-3 font-semibold text-sacco-ink">Audit reports</td>
                    <td class="px-4 py-3">Sensitive action history</td>
                </tr>
            </tbody>
        </table>
    </div>
        </div>
</section>

<section class="grid gap-4 xl:grid-cols-2">
    <div class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <p class="erp-widget-title">25.2 Disbursement Handoff Report</p>
            <h2 class="mt-1 text-xl font-bold text-sacco-ink">Disbursement Handoff Report</h2>
            </div>
        <div class="erp-panel-body space-y-5">
            <div>
                <p class="text-sm font-semibold text-sacco-ink">Purpose</p>
                <p class="mt-1 text-sm text-slate-600">
                    Provide all approved application details required to proceed with disbursement in the financial system.
                </p>
            </div>
            <div>
                <p class="text-sm font-semibold text-sacco-ink">Recommended export formats</p>
                <ul class="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
                    <li>PDF</li>
                </ul>
            </div>
            <div>
                <p class="text-sm font-semibold text-sacco-ink">Report should include</p>
                <ul class="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
                    <li>Loan Application ID</li>
                    <li>Client details</li>
                    <li>Approved product</li>
                    <li>Approved amount and tenure</li>
                    <li>Estimated fee/insurance deductions</li>
                    <li>Guarantor details</li>
                    <li>Approval decision summary</li>
                    <li>Prepared by and date</li>
                </ul>
        </div>
    </div>
        </div>
    <div class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <p class="erp-widget-title">25.3 Reconciliation Report</p>
            <h2 class="mt-1 text-xl font-bold text-sacco-ink">Reconciliation Report</h2>
            </div>
        <div class="erp-panel-body space-y-5">
            <div>
                <p class="text-sm font-semibold text-sacco-ink">Purpose</p>
                <p class="mt-1 text-sm text-slate-600">
                    Identify approved applications not yet completed with Loan ID and disbursement details.
                </p>
            </div>
<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
                <div class="erp-table-scroll">
                <table class="erp-table">
                    <thead class="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                        <tr>
                            <th class="px-4 py-3">Column</th>
                            <th class="px-4 py-3">Description</th>
                        </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-200 bg-white text-slate-700">
                        <tr><td class="px-4 py-3 font-semibold text-sacco-ink">Loan Application ID</td><td class="px-4 py-3">Application reference</td></tr>
                        <tr><td class="px-4 py-3 font-semibold text-sacco-ink">Client Number</td><td class="px-4 py-3">Client identifier</td></tr>
                        <tr><td class="px-4 py-3 font-semibold text-sacco-ink">Approved Amount</td><td class="px-4 py-3">Approved amount</td></tr>
                        <tr><td class="px-4 py-3 font-semibold text-sacco-ink">Handoff Status</td><td class="px-4 py-3">Pending/exported/captured/exception</td></tr>
                        <tr><td class="px-4 py-3 font-semibold text-sacco-ink">Loan ID</td><td class="px-4 py-3">Captured loan identifier</td></tr>
                        <tr><td class="px-4 py-3 font-semibold text-sacco-ink">Disbursement Date</td><td class="px-4 py-3">Captured disbursement date</td></tr>
                        <tr><td class="px-4 py-3 font-semibold text-sacco-ink">Disbursed Amount</td><td class="px-4 py-3">Captured amount</td></tr>
                        <tr><td class="px-4 py-3 font-semibold text-sacco-ink">Difference</td><td class="px-4 py-3">Disbursed amount minus approved amount</td></tr>
                        <tr><td class="px-4 py-3 font-semibold text-sacco-ink">Captured By</td><td class="px-4 py-3">User who captured details</td></tr>
                        <tr><td class="px-4 py-3 font-semibold text-sacco-ink">Remarks</td><td class="px-4 py-3">Notes or exceptions</td></tr>
                    </tbody>
                </table>
        </div>
    </div>
        </div>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
