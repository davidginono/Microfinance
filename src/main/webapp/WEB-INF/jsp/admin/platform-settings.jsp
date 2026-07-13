<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Platform Settings</p>
    <h1 class="erp-page-title">Platform Settings</h1>
    <p class="erp-page-subtitle">Manage platform-wide branding rules for SACCO workspaces.</p>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title">Branding</p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink">Logo Upload Rules</h2>
        <p class="mt-1 text-sm text-slate-500">These limits apply to every SACCO logo uploaded from registration or station registry.</p>
    </div>
    <form action="/admin/platform-settings/logo-policy" method="post" class="erp-panel-body">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div class="grid gap-4 lg:grid-cols-2">
            <div class="rounded-md border border-slate-200 bg-slate-50 p-4">
                <p class="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">Resolution</p>
                <div class="mt-3 grid gap-3 sm:grid-cols-2">
                    <label class="block text-sm font-semibold text-slate-700">
                        Minimum width
                        <input name="minWidthPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${brandingSettings.logoMinWidthPx}" />
                    </label>
                    <label class="block text-sm font-semibold text-slate-700">
                        Minimum height
                        <input name="minHeightPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${brandingSettings.logoMinHeightPx}" />
                    </label>
                    <label class="block text-sm font-semibold text-slate-700">
                        Maximum width
                        <input name="maxWidthPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${brandingSettings.logoMaxWidthPx}" />
                    </label>
                    <label class="block text-sm font-semibold text-slate-700">
                        Maximum height
                        <input name="maxHeightPx" type="number" min="32" max="4096" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${brandingSettings.logoMaxHeightPx}" />
                    </label>
                </div>
            </div>
            <div class="rounded-md border border-slate-200 bg-slate-50 p-4">
                <p class="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">File Size</p>
                <div class="mt-3">
                    <label class="block text-sm font-semibold text-slate-700">
                        Maximum file size (KB)
                        <input name="maxFileSizeKb" type="number" min="64" max="5120" required
                               class="mt-1 h-12 w-full rounded border border-slate-300 bg-white px-3 text-sm text-slate-800"
                               value="${brandingSettings.logoMaxFileSizeKb}" />
                    </label>
                </div>
            </div>
        </div>
        <div class="mt-5 relative flex flex-row items-center justify-between gap-3 flex-wrap border-t border-slate-200 pt-5">
            <p class="min-w-[16rem] flex-[1_1_24rem] text-sm text-slate-500">${logoUploadPolicy.helpText}</p>
            <button type="submit" class="app-btn btn-primary shrink-0 min-w-[10rem]" style="height:3rem;">Save Rules</button>
        </div>
    </form>
</section>

<%@ include file="../fragments/footer.jspf" %>
