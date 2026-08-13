<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.incidents.breadcrumb" text="Admin Tools / Incidents" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.incidents.title" text="Incidents" /></h1>
</div>

<section class="erp-form-wrap">
<form action="/admin/messages" method="get" class="admin-filter-form grid gap-3 md:grid-cols-2 aws-filter-toolbar" data-aws-filter-toolbar>
        <div>
            <label class="mb-1 block text-sm font-semibold text-slate-700"><spring:message code="common.status" text="Status" /></label>
            <select name="status" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none">
                <option value=""><spring:message code="common.allStatuses" text="All statuses" /></option>
                <c:forEach items="${incidentStatuses}" var="item">
                    <option value="${item}" ${selectedStatus eq item.name() ? 'selected' : ''}>${item}</option>
                </c:forEach>
            </select>
        </div>
        <div class="flex items-end gap-2">
            <button type="submit" class="app-btn btn-primary"><spring:message code="common.applyFilters" text="Apply Filters" /></button>
            <a href="/admin/messages" class="app-btn btn-neutral"><spring:message code="common.clear" text="Clear" /></a>
        </div>
    </form>
</section>

<section class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
    <div class="erp-table-scroll">
    <table class="erp-table">
        <thead>
        <tr><th>Subject</th><th>Category</th><th>Status</th><th>Created</th><th>Action</th></tr>
        </thead>
        <tbody>
        <c:forEach items="${messages}" var="item">
            <c:set var="incidentMeta" value="${messageIncidents[item.incidentId]}" />
            <tr>
                <td class="px-3 py-2">
                    <div class="font-semibold text-slate-900">${incidentMeta.subject}</div>
                    <div class="text-xs text-slate-500">${incidentMeta.source}</div>
                </td>
                <td class="px-3 py-2">${incidentMeta.category}</td>
                <td class="px-3 py-2">${incidentMeta.status}</td>
                <td class="px-3 py-2">${incidentMeta.createdAt}</td>
                <td class="px-3 py-2"><a href="/admin/messages/${item.id}/open" class="app-btn btn-primary">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty messages}">
            <tr><td colspan="5" class="px-3 py-4 text-slate-500">No incidents match the current filters.</td></tr>
        </c:if>
        </tbody>
    </table>
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
