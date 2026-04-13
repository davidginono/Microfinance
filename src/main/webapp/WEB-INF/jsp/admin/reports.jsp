<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Reports</p>
    <h1 class="erp-page-title">Admin Reports</h1>
    <p class="erp-page-subtitle">Review filtered workflow volume, rejection counts, and product mix from the administrative reporting view.</p>
</div>

<section class="erp-form-wrap">
    <form action="/admin/reports/filter" method="get" class="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
        <div>
            <label class="mb-1 block text-sm font-semibold text-slate-700">Status</label>
            <select name="status" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
                <option value="">All statuses</option>
                <c:forEach items="${loanStatuses}" var="statusItem">
                    <option value="${statusItem}" ${reports.statusFilter eq statusItem.name() ? 'selected' : ''}>${statusItem}</option>
                </c:forEach>
            </select>
        </div>
        <div>
            <label class="mb-1 block text-sm font-semibold text-slate-700">Loan Type</label>
            <select name="loanType" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
                <option value="">All loan types</option>
                <c:forEach items="${loanTypes}" var="loanTypeItem">
                    <option value="${loanTypeItem}" ${reports.loanTypeFilter eq loanTypeItem.name() ? 'selected' : ''}>${loanTypeItem}</option>
                </c:forEach>
            </select>
        </div>
        <div>
            <label class="mb-1 block text-sm font-semibold text-slate-700">From</label>
            <input type="date" name="dateFrom" value="${reports.dateFrom}" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" />
        </div>
        <div>
            <label class="mb-1 block text-sm font-semibold text-slate-700">To</label>
            <input type="date" name="dateTo" value="${reports.dateTo}" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" />
        </div>
        <div class="flex items-end gap-2">
            <button type="submit" class="app-btn btn-primary">Apply</button>
            <a href="/admin/reports" class="app-btn btn-neutral">Reset</a>
        </div>
    </form>
</section>

<section class="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
    <div class="erp-section">
        <p class="text-sm font-semibold uppercase tracking-wide text-slate-500">Pending Guarantor Requests</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">${reports.pendingGuarantorRequests}</p>
    </div>
    <div class="erp-section">
        <p class="text-sm font-semibold uppercase tracking-wide text-slate-500">Manager Rejections</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">${reports.rejectedByManager}</p>
    </div>
    <div class="erp-section">
        <p class="text-sm font-semibold uppercase tracking-wide text-slate-500">Final Approved</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">${reports.finalApproved}</p>
    </div>
    <div class="erp-section">
        <p class="text-sm font-semibold uppercase tracking-wide text-slate-500">Manager Decisions</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">${reports.managerDecisionCount}</p>
    </div>
    <div class="erp-section">
        <p class="text-sm font-semibold uppercase tracking-wide text-slate-500">Filtered Applications</p>
        <p class="mt-2 text-3xl font-bold text-slate-900">${reports.totalFilteredApplications}</p>
    </div>
</section>

<section class="grid gap-4 xl:grid-cols-2">
    <div class="erp-panel">
        <div class="erp-panel-header"><p class="erp-panel-title">Applications By Status</p></div>
        <div class="erp-panel-body">
        <div class="mb-4 space-y-3">
            <c:forEach items="${reports.statusChart}" var="item">
                <div>
                    <div class="mb-1 flex items-center justify-between text-sm">
                        <span class="font-semibold text-slate-800">${item.label}</span>
                        <span class="text-slate-500">${item.count} (${item.percent}%)</span>
                    </div>
                    <div class="h-3 rounded-full bg-slate-100">
                        <div class="h-3 rounded-full bg-sacco-blue" style="width:${item.percent}%"></div>
                    </div>
                </div>
            </c:forEach>
        </div>
        <table class="erp-table">
            <thead><tr><th>Status</th><th>Count</th></tr></thead>
            <tbody>
            <c:forEach items="${reports.applicationsByStatus}" var="entry">
                <tr><td class="px-3 py-2">${entry.key}</td><td class="px-3 py-2">${entry.value}</td></tr>
            </c:forEach>
            </tbody>
        </table>
        </div>
    </div>

    <div class="erp-panel">
        <div class="erp-panel-header"><p class="erp-panel-title">Applications By Loan Type</p></div>
        <div class="erp-panel-body">
        <div class="mb-4 space-y-3">
            <c:forEach items="${reports.typeChart}" var="item">
                <div>
                    <div class="mb-1 flex items-center justify-between text-sm">
                        <span class="font-semibold text-slate-800">${item.label}</span>
                        <span class="text-slate-500">${item.count} (${item.percent}%)</span>
                    </div>
                    <div class="h-3 rounded-full bg-slate-100">
                        <div class="h-3 rounded-full bg-sacco-green" style="width:${item.percent}%"></div>
                    </div>
                </div>
            </c:forEach>
        </div>
        <table class="erp-table">
            <thead><tr><th>Loan Type</th><th>Count</th></tr></thead>
            <tbody>
            <c:forEach items="${reports.applicationsByType}" var="entry">
                <tr><td class="px-3 py-2">${entry.key}</td><td class="px-3 py-2">${entry.value}</td></tr>
            </c:forEach>
            </tbody>
        </table>
        </div>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
