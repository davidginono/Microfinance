<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Incidents</p>
    <h1 class="erp-page-title">Incidents</h1>
    <p class="erp-page-subtitle">Review member support incidents, reply to members, and jump into the admin cases that need your attention.</p>
</div>

<section class="erp-form-wrap">
    <form action="/admin/messages" method="get" class="grid gap-3 md:grid-cols-3">
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
            <a href="/admin/messages" class="app-btn btn-neutral">Clear</a>
        </div>
    </form>
</section>

<section class="erp-panel">
    <div class="erp-panel-header"><p class="erp-panel-title">Incident Inbox</p></div>
    <div class="erp-panel-body">
    <div class="erp-table-wrap overflow-x-auto">
        <table class="erp-table">
            <thead>
            <tr><th>Subject</th><th>Message</th><th>Source</th><th>Type</th><th>Severity</th><th>Status</th><th>Incident</th><th>Date</th></tr>
            </thead>
            <tbody>
            <c:forEach items="${messages}" var="item">
                <c:set var="incidentMeta" value="${messageIncidents[item.incidentId]}" />
                <tr id="notification-${item.id}" class="${highlightNotificationId eq item.id ? 'bg-amber-50' : (item.unread ? 'bg-sacco-blue/5' : 'bg-white')}">
                    <td class="px-3 py-2 font-semibold text-slate-900">
                        <a href="/admin/messages/${item.id}/open" class="hover:text-sacco-blue">
                            ${item.subject}
                            <c:if test="${item.unread}">
                                <span class="ml-2 rounded-full bg-red-500 px-2 py-0.5 text-[11px] font-bold uppercase text-white">Unread</span>
                            </c:if>
                        </a>
                    </td>
                    <td class="px-3 py-2">
                        <a href="/admin/messages/${item.id}/open" class="block hover:text-sacco-blue">${item.message}</a>
                        <c:if test="${not empty item.detailItems}">
                            <ul class="mt-2 space-y-1 text-xs text-slate-500">
                                <c:forEach items="${item.detailItems}" var="detail">
                                    <li>${detail}</li>
                                </c:forEach>
                            </ul>
                        </c:if>
                    </td>
                    <td class="px-3 py-2">${item.source}</td>
                    <td class="px-3 py-2">${item.type}</td>
                    <td class="px-3 py-2">${incidentMeta != null ? incidentMeta.severity : '-'}</td>
                    <td class="px-3 py-2">${incidentMeta != null ? incidentMeta.status : '-'}</td>
                    <td class="px-3 py-2">
                        <c:if test="${not empty item.incidentId}">
                            <a href="/admin/incidents/${item.incidentId}" class="app-btn btn-neutral">Open Incident</a>
                        </c:if>
                        <c:if test="${empty item.incidentId}">-</c:if>
                    </td>
                    <td class="px-3 py-2 whitespace-nowrap">${item.createdAtLabel}</td>
                </tr>
            </c:forEach>
            <c:if test="${empty messages}">
                <tr><td colspan="8" class="px-3 py-3 text-slate-500">No member support notifications match the current filters.</td></tr>
            </c:if>
            </tbody>
        </table>
    </div>
    </div>
</section>

<section class="grid gap-4 xl:grid-cols-2">
    <div class="erp-form-wrap">
        <h5 class="erp-panel-title">Reply To Member</h5>
        <form action="/admin/messages/reply" method="post" class="space-y-3">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div>
                <label class="mb-1 block text-sm font-semibold text-slate-700">Recipient</label>
                <select name="memberId" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required>
                    <option value="">Select member</option>
                    <c:forEach items="${members}" var="member">
                        <option value="${member.id}">${member.memberNo} - ${member.fullName} (${member.position})</option>
                    </c:forEach>
                </select>
            </div>
            <div>
                <label class="mb-1 block text-sm font-semibold text-slate-700">Subject</label>
                <input name="subject" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required />
            </div>
            <div>
                <label class="mb-1 block text-sm font-semibold text-slate-700">Message</label>
                <textarea name="message" rows="5" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required></textarea>
            </div>
            <button type="submit" class="app-btn btn-primary">Send Reply</button>
        </form>
    </div>

    <div class="erp-form-wrap">
        <h5 class="erp-panel-title">Broadcast To SACCO Members</h5>
        <form action="/admin/messages/broadcast" method="post" class="space-y-3">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div>
                <label class="mb-1 block text-sm font-semibold text-slate-700">Subject</label>
                <input name="subject" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required />
            </div>
            <div>
                <label class="mb-1 block text-sm font-semibold text-slate-700">Message</label>
                <textarea name="message" rows="6" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required></textarea>
            </div>
            <button type="submit" class="app-btn btn-approve">Send Broadcast</button>
        </form>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
