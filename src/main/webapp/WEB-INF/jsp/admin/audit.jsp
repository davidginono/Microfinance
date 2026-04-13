<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Audit Log</p>
    <h1 class="erp-page-title">Audit Log</h1>
    <p class="erp-page-subtitle">Inspect recorded system actions with entity and actor references for administrative tracing.</p>
</div>
<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr><th>Action</th><th>Entity</th><th>Entity ID</th><th>Actor</th><th>Date</th></tr>
        </thead>
        <tbody>
        <c:forEach items="${entries}" var="entry">
            <tr>
                <td class="px-3 py-2">${entry.displayAction}</td>
                <td class="px-3 py-2">${entry.displayEntityType}</td>
                <td class="px-3 py-2">${entry.shortEntityReference}</td>
                <td class="px-3 py-2">${entry.actorReferenceLabel}</td>
                <td class="px-3 py-2 whitespace-nowrap">${entry.createdAtLabel}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty entries}">
            <tr><td colspan="5" class="px-3 py-3 text-slate-500">No audit entries yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
