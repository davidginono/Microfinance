<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Notifications</p>
    <h1 class="erp-page-title">Notifications</h1>
    <p class="erp-page-subtitle">Review routed incident notifications.</p>
</div>

<div class="mb-3 flex items-center justify-end">
    <form action="/admin/messages/mark-all-read" method="post" class="m-0">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <button type="submit" class="app-btn btn-primary">Mark all read</button>
    </form>
</div>

<section class="erp-form-wrap">
    <form action="/admin/incidents" method="get" class="admin-filter-form grid gap-3 md:grid-cols-3">
        <div>
            <label class="mb-1 block text-sm font-semibold text-slate-700">Status</label>
            <select name="status" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
                <option value="">All statuses</option>
                <c:forEach items="${incidentStatuses}" var="item">
                    <option value="${item}" ${selectedStatus eq item.name() ? 'selected' : ''}>${item}</option>
                </c:forEach>
            </select>
        </div>
        <div>
            <label class="mb-1 block text-sm font-semibold text-slate-700">Severity</label>
            <select name="severity" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
                <option value="">All severities</option>
                <c:forEach items="${incidentSeverities}" var="item">
                    <option value="${item}" ${selectedSeverity eq item.name() ? 'selected' : ''}>${item}</option>
                </c:forEach>
            </select>
        </div>
        <div class="flex items-end gap-2">
            <button type="submit" class="app-btn btn-primary">Apply Filters</button>
            <a href="/admin/incidents" class="app-btn btn-neutral">Clear</a>
        </div>
    </form>
</section>

<section class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr><th>Subject</th><th>Category</th><th>Severity</th><th>Status</th><th>Created</th><th>Action</th></tr>
        </thead>
        <tbody>
        <c:forEach items="${incidents}" var="incident">
            <tr>
                <td class="px-3 py-2">
                    <div class="font-semibold text-slate-900">${incident.subject}</div>
                    <div class="text-xs text-slate-500">${incident.source}</div>
                </td>
                <td class="px-3 py-2">${incident.category}</td>
                <td class="px-3 py-2">${incident.severity}</td>
                <td class="px-3 py-2">${incident.status}</td>
                <td class="px-3 py-2">${incident.createdAt}</td>
                <td class="px-3 py-2"><a href="/admin/incidents/${incident.id}" class="app-btn btn-primary">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty incidents}">
            <tr><td colspan="6" class="px-3 py-4 text-slate-500">No incidents match the current filters.</td></tr>
        </c:if>
        </tbody>
    </table>
</section>

<%@ include file="../fragments/footer.jspf" %>
