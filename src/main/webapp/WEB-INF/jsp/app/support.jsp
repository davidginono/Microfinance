<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Member Workspace / Support</p>
    <h1 class="erp-page-title">Contact Station Admin</h1>
    <p class="erp-page-subtitle">Send support messages to your station admin team.</p>
</div>

<section class="erp-panel max-w-4xl overflow-hidden">
    <div class="erp-panel-header">
        <p class="erp-panel-title">Support Request</p>
    </div>
    <form action="/app/support" method="post" class="erp-panel-body space-y-4">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <label class="block text-sm font-semibold text-slate-700">
            Subject
            <input name="subject" class="mt-1 w-full rounded border border-slate-300 px-3 py-3 text-sm text-slate-800 focus:border-sacco-blue focus:outline-none" placeholder="Example: Unable to submit my loan application" required />
        </label>
        <label class="block text-sm font-semibold text-slate-700">
            Message
            <textarea name="message" rows="7" class="mt-1 w-full rounded border border-slate-300 px-3 py-3 text-sm text-slate-800 focus:border-sacco-blue focus:outline-none" placeholder="Describe the problem clearly. Include the page and what happened." required></textarea>
        </label>
        <div class="flex justify-end">
            <button type="submit" class="app-btn btn-primary">Send To Station Admin</button>
        </div>
    </form>
</section>

<%@ include file="../fragments/footer.jspf" %>
