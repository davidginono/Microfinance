<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Event Log</p>
    <h1 class="erp-page-title">Event Log</h1>
    <p class="erp-page-subtitle">Review the recorded actions taken across the system for visibility, troubleshooting, and operational follow-up.</p>
</div>
<div class="mb-3">
    <a href="/admin/dashboard" class="app-btn btn-neutral">Back To Dashboard</a>
</div>
<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr>
            <th>Action</th>
            <th>Entity</th>
            <th>Reference</th>
            <th>Actor</th>
            <th>Date</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${entries}" var="entry">
            <tr>
                <td class="px-3 py-2 font-semibold text-slate-900">${entry.displayAction}</td>
                <td class="px-3 py-2">${entry.displayEntityType}</td>
                <td class="px-3 py-2">${entry.shortEntityReference}</td>
                <td class="px-3 py-2">${entry.actorReferenceLabel}</td>
                <td class="px-3 py-2 whitespace-nowrap">${entry.createdAtLabel}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty entries}">
            <tr><td colspan="5" class="px-3 py-3 text-slate-500">No events recorded yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
