<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Outbox Monitor</p>
    <h1 class="erp-page-title">Outbox Monitor</h1>
    <p class="erp-page-subtitle">Monitor event publication, identify failures, and retry the items that need operational recovery.</p>
</div>
<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr><th>Event Type</th><th>Aggregate</th><th>Status</th><th>Created</th><th>Action</th></tr>
        </thead>
        <tbody>
        <c:forEach items="${events}" var="event">
            <tr>
                <td class="px-3 py-2 font-semibold text-slate-900">${event.eventType}</td>
                <td class="px-3 py-2">${event.aggregateType}<div class="text-xs text-slate-500">${event.aggregateId}</div></td>
                <td class="px-3 py-2">${event.status}</td>
                <td class="px-3 py-2">${event.createdAt}</td>
                <td class="px-3 py-2">
                    <c:if test="${event.status eq 'FAILED'}">
                        <form action="/admin/outbox/${event.id}/retry" method="post">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                            <button type="submit" class="app-btn btn-primary">Retry</button>
                        </form>
                    </c:if>
                    <c:if test="${event.status ne 'FAILED'}">-</c:if>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
