<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Manager Panel / Settings</p>
    <h1 class="erp-page-title">Manager Settings</h1>
    <p class="erp-page-subtitle">Maintain the guarantor, board, and language defaults used across your SACCO review workflow.</p>
</div>
<form method="post" action="/manager/settings" class="erp-form-wrap max-w-2xl space-y-3">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    <label class="block text-sm font-medium text-slate-700">Required Guarantors</label>
    <input type="number" class="w-full rounded-lg border border-slate-300 px-3 py-2" name="requiredGuarantors" value="${settings.requiredGuarantors}" />
    <label class="block text-sm font-medium text-slate-700">Board Size</label>
    <input type="number" class="w-full rounded-lg border border-slate-300 px-3 py-2" name="boardSize" value="${settings.boardSize}" />
    <label class="block text-sm font-medium text-slate-700">Board Quorum</label>
    <input type="number" class="w-full rounded-lg border border-slate-300 px-3 py-2" name="boardQuorum" value="${settings.boardQuorum}" />
    <label class="block text-sm font-medium text-slate-700">Default Language</label>
    <select name="defaultLanguage" class="w-full rounded-lg border border-slate-300 px-3 py-2">
        <option value="en" ${settings.defaultLanguage == 'en' ? 'selected' : ''}>English</option>
        <option value="sw" ${settings.defaultLanguage == 'sw' ? 'selected' : ''}>Kiswahili</option>
    </select>
    <button class="app-btn btn-primary">Save</button>
</form>

<%@ include file="../fragments/footer.jspf" %>

