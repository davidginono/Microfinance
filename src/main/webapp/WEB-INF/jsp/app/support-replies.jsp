<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Member Workspace / Support / Replies</p>
    <h1 class="erp-page-title">Support Replies</h1>
    <p class="erp-page-subtitle">Read replies and broadcasts from your station admin.</p>
</div>

<section class="erp-panel">
    <div class="erp-panel-header flex flex-wrap items-center justify-between gap-3">
        <p class="erp-panel-title">Replies From Station Admin</p>
        <form action="/app/support/replies/mark-all-read" method="post">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <button type="submit" class="app-btn btn-neutral">Mark All Read</button>
        </form>
    </div>
    <div class="erp-panel-body">
        <div class="erp-table-wrap overflow-x-auto">
            <table class="erp-table">
                <thead>
                <tr><th>Subject</th><th>Message</th><th>Source</th><th>Date</th></tr>
                </thead>
                <tbody>
                <c:forEach items="${replies}" var="item">
                    <tr id="notification-${item.id}" class="${highlightNotificationId eq item.id ? 'bg-amber-50' : (item.unread ? 'bg-sacco-blue/5' : 'bg-white')}">
                        <td class="px-3 py-2 font-semibold text-slate-900">
                            <a href="/app/support/replies/${item.id}/open" class="hover:text-sacco-blue">
                                ${item.subject}
                                <c:if test="${item.unread}">
                                    <span class="ml-2 rounded-full bg-red-500 px-2 py-0.5 text-[11px] font-bold uppercase text-white">Unread</span>
                                </c:if>
                            </a>
                        </td>
                        <td class="px-3 py-2"><a href="/app/support/replies/${item.id}/open" class="block hover:text-sacco-blue">${item.message}</a></td>
                        <td class="px-3 py-2">${item.source}</td>
                        <td class="px-3 py-2 whitespace-nowrap">${item.createdAtLabel}</td>
                    </tr>
                </c:forEach>
                <c:if test="${empty replies}">
                    <tr><td colspan="4" class="px-3 py-3 text-slate-500">No replies yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</section>

<section class="erp-panel">
    <div class="erp-panel-header"><p class="erp-panel-title">Station Admin Broadcasts</p></div>
    <div class="erp-panel-body">
        <div class="erp-table-wrap overflow-x-auto">
            <table class="erp-table">
                <thead>
                <tr><th>Subject</th><th>Message</th><th>Source</th><th>Date</th></tr>
                </thead>
                <tbody>
                <c:forEach items="${broadcasts}" var="item">
                    <tr id="notification-${item.id}" class="${highlightNotificationId eq item.id ? 'bg-amber-50' : (item.unread ? 'bg-sacco-blue/5' : 'bg-white')}">
                        <td class="px-3 py-2 font-semibold text-slate-900">
                            <a href="/app/support/replies/${item.id}/open" class="hover:text-sacco-blue">
                                ${item.subject}
                                <c:if test="${item.unread}">
                                    <span class="ml-2 rounded-full bg-red-500 px-2 py-0.5 text-[11px] font-bold uppercase text-white">Unread</span>
                                </c:if>
                            </a>
                        </td>
                        <td class="px-3 py-2"><a href="/app/support/replies/${item.id}/open" class="block hover:text-sacco-blue">${item.message}</a></td>
                        <td class="px-3 py-2">${item.source}</td>
                        <td class="px-3 py-2 whitespace-nowrap">${item.createdAtLabel}</td>
                    </tr>
                </c:forEach>
                <c:if test="${empty broadcasts}">
                    <tr><td colspan="4" class="px-3 py-3 text-slate-500">No broadcasts yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
