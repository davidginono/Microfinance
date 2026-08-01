<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">Admin Tools / Incidents / Detail</p>
    <h1 class="erp-page-title">Incident Detail</h1>
    <p class="erp-page-subtitle">Review the full incident payload, reporter context, and resolution notes before updating its status.</p>
</div>

<section class="grid gap-4 overflow-hidden xl:grid-cols-[minmax(0,1.3fr)_minmax(300px,0.9fr)]">
    <div class="min-w-0 erp-section">
        <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">${incident.category}</p>
                <h5 class="mt-1 text-xl font-semibold text-slate-900">${incident.subject}</h5>
                <p class="mt-2 text-sm text-slate-600">${incident.message}</p>
            </div>
            <div class="flex flex-wrap gap-2 text-xs font-semibold">
                <span class="rounded-full bg-slate-100 px-3 py-1 text-slate-700">${incident.status}</span>
            </div>
        </div>
        <dl class="mt-5 grid gap-3 md:grid-cols-2">
            <div class="min-w-0"><dt class="text-xs font-semibold uppercase tracking-wide text-slate-500">Source</dt><dd class="mt-1 break-words text-sm text-slate-800">${incident.source}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-semibold uppercase tracking-wide text-slate-500">Created</dt><dd class="mt-1 break-all text-sm text-slate-800">${incident.createdAt}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-semibold uppercase tracking-wide text-slate-500">Updated</dt><dd class="mt-1 break-all text-sm text-slate-800">${incident.updatedAt}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-semibold uppercase tracking-wide text-slate-500">Resolved</dt><dd class="mt-1 break-all text-sm text-slate-800">${empty incident.resolvedAt ? '-' : incident.resolvedAt}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-semibold uppercase tracking-wide text-slate-500">Reporter</dt><dd class="mt-1 break-all text-sm text-slate-800">${empty incident.reportedByMemberId ? '-' : incident.reportedByMemberId}</dd></div>
            <div class="min-w-0"><dt class="text-xs font-semibold uppercase tracking-wide text-slate-500">Related Notification</dt><dd class="mt-1 break-all text-sm text-slate-800">${empty incident.relatedNotificationId ? '-' : incident.relatedNotificationId}</dd></div>
        </dl>

        <div class="mt-5 erp-section-muted">
            <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Raw Details</p>
            <pre class="mt-2 overflow-x-hidden whitespace-pre-wrap break-all text-sm text-slate-700">${incident.detailsJson}</pre>
        </div>
    </div>

    <div class="min-w-0 erp-form-wrap">
        <h5 class="erp-panel-title">Update Incident</h5>
        <form action="/admin/incidents/${incident.id}" method="post" class="mt-4 space-y-4">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <div>
                <label class="mb-1 block text-sm font-semibold text-slate-700">Resolution Status</label>
                <select name="status" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
                    <c:forEach items="${incidentStatuses}" var="item">
                        <option value="${item}" ${incident.status eq item ? 'selected' : ''}>${item}</option>
                    </c:forEach>
                </select>
            </div>
            <div>
                <label class="mb-1 block text-sm font-semibold text-slate-700">Resolution Note</label>
                <textarea name="resolutionNote" rows="7" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">${incident.resolutionNote}</textarea>
            </div>
            <button type="submit" class="app-btn btn-primary">Save Incident</button>
        </form>
    </div>
</section>

<c:if test="${isPlatformAdminIdentity or incident.source eq 'Member Support'}">
    <section class="grid gap-4 xl:grid-cols-2">
        <div class="erp-form-wrap">
            <h5 class="erp-panel-title">${isPlatformAdminIdentity ? 'Reply To SACCO Admin' : 'Reply To Member'}</h5>
            <form action="/admin/incidents/${incident.id}/reply" method="post" class="mt-4 space-y-4">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <div>
                    <label class="mb-1 block text-sm font-semibold text-slate-700">Subject</label>
                    <input name="subject" value="Re: ${incident.subject}" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required />
                </div>
                <div>
                    <label class="mb-1 block text-sm font-semibold text-slate-700">Message</label>
                    <textarea name="message" rows="6" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required></textarea>
                </div>
                <button type="submit" class="app-btn btn-primary">Send Reply</button>
            </form>
        </div>

        <c:if test="${isPlatformAdminIdentity}">
        <div class="erp-form-wrap">
            <h5 class="erp-panel-title">Broadcast To SACCOS Admins</h5>
            <form action="/admin/incidents/broadcast-minor-admins" method="post" class="mt-4 space-y-4">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <div>
                    <label class="mb-1 block text-sm font-semibold text-slate-700">Subject</label>
                    <input name="subject" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required />
                </div>
                <div>
                    <label class="mb-1 block text-sm font-semibold text-slate-700">Message</label>
                    <textarea name="message" rows="6" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required></textarea>
                </div>
                <button type="submit" class="app-btn btn-primary">Send Broadcast</button>
            </form>
        </div>
        </c:if>
    </section>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
