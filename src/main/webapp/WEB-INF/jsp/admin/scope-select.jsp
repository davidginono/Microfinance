<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / SACCO Workspace</p>
    <h1 class="erp-page-title">Choose A SACCO</h1>
    <p class="erp-page-subtitle">Select the SACCO workspace you want to manage before opening the admin dashboard.</p>
</div>

<section class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4">
        <p class="erp-widget-title">Admin Scope</p>
        <h2 class="mt-1 text-xl font-bold text-sacco-ink">Start In The Right SACCO Workspace</h2>
    </div>
    <form action="/admin/scope" method="post" class="erp-panel-body grid gap-4 md:grid-cols-[minmax(0,1fr)_auto] md:items-end">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <input type="hidden" name="next" value="${nextAdminPath}" />
        <c:if test="${not empty scopeSelection}">
            <input type="hidden" id="adminScopeStationLanding" name="stationId" value="${scopeSelection.stationId}" />
            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                Select SACCO
                <select id="adminScopeLandingSelect"
                        name="saccoId"
                        class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                    <c:forEach items="${scopeSelection.options}" var="option">
                        <option value="${option.saccoId}" ${scopeSelection.saccoId eq option.saccoId ? 'selected' : ''}>${option.saccoId} - ${option.saccoName}</option>
                    </c:forEach>
                </select>
            </label>
        </c:if>
        <div class="flex items-end md:justify-end">
            <button type="submit" class="app-btn btn-primary min-w-[12rem]">Open Dashboard</button>
        </div>
    </form>
</section>

<script>
    (() => {
        const options = ${adminScopeOptionsJson};
        const saccoSelect = document.getElementById("adminScopeLandingSelect");
        const stationInput = document.getElementById("adminScopeStationLanding");
        if (!saccoSelect || !stationInput || !Array.isArray(options)) {
            return;
        }

        const syncStation = function () {
            const selectedSacco = saccoSelect.value;
            const selectedOption = options.find(function (option) { return option.saccoId === selectedSacco; });
            const stations = selectedOption && Array.isArray(selectedOption.stationIds) ? selectedOption.stationIds : [];
            stationInput.value = stations.length ? stations[0] : "";
        };

        saccoSelect.addEventListener("change", syncStation);
        syncStation();
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
