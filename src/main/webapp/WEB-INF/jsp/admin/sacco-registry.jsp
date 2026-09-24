<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<spring:message code="admin.saccoRegistry.saccoRegistration" text="SACCO Registration" var="saccoRegistrationLabel" />
<spring:message code="admin.saccoRegistry.stationRegistry" text="Station Registry" var="stationRegistryLabel" />
<spring:message code="admin.saccoRegistry.saccoRegistrationSubtitle" text="Register SACCO workspaces and manage the stations under each SACCO." var="saccoRegistrationSubtitle" />
<spring:message code="admin.saccoRegistry.stationRegistrySubtitle" text="Manage station IDs for your SACCO workspace." var="stationRegistrySubtitle" />
<spring:message code="admin.saccoRegistry.currentSaccoStations" text="Current SACCO Stations" var="currentSaccoStationsLabel" />
<spring:message code="admin.saccoRegistry.editSacco" text="Edit SACCO" var="editSaccoLabel" />
<spring:message code="admin.saccoRegistry.editStations" text="Edit Stations" var="editStationsLabel" />
<spring:message code="admin.saccoRegistry.editSaccoHelp" text="Update the SACCO name and active station IDs." var="editSaccoHelp" />
<spring:message code="admin.saccoRegistry.editStationsHelp" text="Review and update the active station IDs for this SACCO workspace." var="editStationsHelp" />
<spring:message code="admin.saccoRegistry.saveChanges" text="Save Changes" var="saveChangesLabel" />
<spring:message code="admin.saccoRegistry.saveStations" text="Save Stations" var="saveStationsLabel" />

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.tools" text="Admin Tools" /> / ${superAdmin ? saccoRegistrationLabel : stationRegistryLabel}</p>
    <h1 class="erp-page-title">${superAdmin ? saccoRegistrationLabel : stationRegistryLabel}</h1>
</div>

<div class="erp-panel">
    <div class="erp-panel-header">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
                <p class="erp-panel-title">${superAdmin ? saccoRegistrationLabel : currentSaccoStationsLabel}</p>
            </div>
            <c:if test="${superAdmin}">
                <div class="flex flex-wrap gap-2">
                    <button type="button" class="app-btn btn-launch" data-sacco-modal-open="create-sacco"><spring:message code="admin.saccoRegistry.addSacco" text="Add SACCO" /></button>
                </div>
            </c:if>
        </div>
    </div>
    <div class="erp-panel-body">
<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
            <div class="erp-table-scroll">
            <table class="erp-table min-w-[680px]">
                <thead>
                <tr>
                    <th><spring:message code="admin.saccoRegistry.logo" text="Logo" /></th>
                    <th>SACCO ID</th>
                    <th><spring:message code="admin.saccoRegistry.saccoName" text="SACCO Name" /></th>
                    <th><spring:message code="admin.saccoRegistry.access" text="Access" /></th>
                    <th><spring:message code="admin.saccoRegistry.stations" text="Stations" /></th>
                    <c:if test="${superAdmin}">
                        <th><spring:message code="admin.saccoRegistry.details" text="Details" /></th>
                    </c:if>
                    <th><spring:message code="common.action" text="Action" /></th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${registeredSaccos}" var="sacco">
                    <tr>
                        <td class="w-16">
                            <c:choose>
                                <c:when test="${sacco.hasLogo}">
                                    <img src="${sacco.logoUrl}"
                                         alt="${sacco.saccoName} logo"
                                         class="h-10 w-10 border border-slate-200 bg-white object-contain p-1 shadow-sm" />
                                </c:when>
                                <c:otherwise>
                                    <span class="inline-flex h-10 w-10 items-center justify-center border border-dashed border-slate-300 bg-slate-50 text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400">
                                        Logo
                                    </span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td class="font-semibold text-slate-800">${sacco.saccoId}</td>
                        <td>${sacco.saccoName}</td>
                        <td>
                            <span class="inline-flex rounded-full border px-3 py-1 text-xs font-semibold ${sacco.accessBadgeClass}">
                                ${sacco.accessStatusLabel}
                            </span>
                        </td>
                        <td>
                            <div class="space-y-1">
                                <c:forEach items="${sacco.stations}" var="station">
                                    <div>
                                        <p class="font-semibold text-slate-800">${station.stationId}</p>
                                        <p class="text-xs text-slate-500">${station.addressLocationLabel}</p>
                                    </div>
                                </c:forEach>
                            </div>
                        </td>
                        <c:if test="${superAdmin}">
                            <td class="whitespace-nowrap">
                                <a href="/admin/saccos/${sacco.saccoId}?section=overview" class="app-btn btn-neutral"><spring:message code="admin.saccoRegistry.viewDetails" text="View Details" /></a>
                            </td>
                        </c:if>
                        <td class="whitespace-nowrap">
                            <div class="flex flex-wrap gap-2">
                                <c:choose>
                                    <c:when test="${superAdmin}">
                                        <button type="button"
                                                class="app-btn btn-neutral"
                                                data-sacco-modal-open="add-station-${sacco.saccoId}">
                                            <spring:message code="admin.saccoRegistry.addStation" text="Add Station" />
                                        </button>
                                        <button type="button"
                                                class="app-btn btn-primary"
                                                data-sacco-modal-open="edit-${sacco.saccoId}">
                                            ${editSaccoLabel}
                                        </button>
                                        <button type="button"
                                                class="app-btn btn-reject"
                                                data-sacco-modal-open="delete-${sacco.saccoId}">
                                            <spring:message code="common.delete" text="Delete" />
                                        </button>
                                    </c:when>
                                    <c:otherwise>
                                        <button type="button"
                                                class="app-btn btn-primary"
                                                data-sacco-modal-open="logo-${sacco.saccoId}">
                                            Edit Logo
                                        </button>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty registeredSaccos}">
                    <tr>
                        <td colspan="${superAdmin ? 7 : 6}" class="text-slate-500">No SACCOs have been registered yet.</td>
                    </tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>
                </div>

<c:if test="${superAdmin}">
<div class="app-modal-overlay hidden" data-sacco-modal="create-sacco">
    <div class="app-modal-panel app-modal-panel--compact">
        <div class="app-modal-scroll">
            <div class="app-modal-header">
                <div>
                    <p class="erp-panel-title"><spring:message code="admin.saccoRegistry.addSacco" text="Add SACCO" /></p>
                    <p class="mt-2 text-sm text-slate-500"><spring:message code="admin.saccoRegistry.addSaccoHelp" text="Save the SACCO and its station IDs." /></p>
            </div>
                <button type="button" class="app-modal-close" data-sacco-modal-close="create-sacco" aria-label="Close modal">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                    </svg>
                </button>
                </div>
            <form action="/admin/saccos" method="post" enctype="multipart/form-data" class="app-modal-body space-y-4">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <div class="rounded border border-slate-200 bg-slate-50 px-3 py-2.5 text-sm text-slate-600">
                    <span class="block text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">SACCO ID</span>
                    <span class="mt-1 block font-medium text-slate-700">Generated automatically after saving.</span>
                        </div>
                <label class="block text-sm font-semibold text-slate-700">
                    <spring:message code="admin.saccoRegistry.saccoName" text="SACCO Name" />
                    <input name="saccoName" type="text" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" placeholder="e.g. IAA SACCOS LTD" />
                </label>
                <label class="block text-sm font-semibold text-slate-700">
                    Station IDs
                    <textarea name="stationIds" rows="5" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" placeholder="Enter one or more station IDs, separated by commas or new lines.&#10;Example:&#10;STN789&#10;STN790"></textarea>
                </label>
                <div class="sacco-logo-upload-card" data-logo-upload-card>
                    <p class="aws-file-card-kicker">SACCO Logo</p>
                    <div class="sacco-logo-upload-layout">
                        <div data-logo-preview-shell class="sacco-logo-preview-shell is-empty">
                            <img data-logo-preview-image src="" alt="New SACCO logo preview" class="hidden h-full w-full object-contain p-2" />
                            <span data-logo-preview-fallback class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400">Auto</span>
                        </div>
                        <div class="min-w-0 flex-1">
                            <label class="aws-file-picker">
                                <span class="aws-file-picker-label">Optional logo image</span>
                                <span class="aws-file-picker-control">
                                <input name="logoFile"
                                       type="file"
                                       accept="image/png,image/jpeg"
                                       data-logo-file-input
                                       data-file-picker-input
                                       class="aws-file-picker-input" />
                                    <span class="aws-file-picker-button">Choose File</span>
                                    <span class="aws-file-picker-name" data-file-picker-name>No file chosen</span>
                                </span>
                            </label>
                            <button type="button"
                                    data-logo-paste-target
                                    class="aws-file-paste-target">
                                <span class="min-w-0">
                                    <span class="block text-sm font-semibold text-slate-700">Paste image</span>
                                    <span data-logo-paste-hint class="mt-1 block text-xs font-medium text-slate-500">Click here and press Ctrl+V to paste a PNG or JPEG from your clipboard.</span>
                                </span>
                                <span class="aws-file-paste-shortcut">Ctrl+V</span>
                            </button>
                            <span class="aws-file-help">
                                Optional. ${logoUploadPolicy.helpText} If you skip this, the navbar will use the SACCO initials.
                            </span>
                            <span class="aws-file-meta hidden" data-logo-file-meta></span>
                    </div>
                </div>
                </div>
                <div class="app-modal-section text-sm text-slate-600">
                    <spring:message code="admin.saccoRegistry.stationHelp" text="Add at least one station ID for every SACCO. Admin selection and member registration will use these values." />
                </div>
                <div class="app-modal-actions">
                    <button type="button" class="app-btn btn-neutral" data-sacco-modal-close="create-sacco"><spring:message code="common.cancel" text="Cancel" /></button>
                    <button type="submit" class="app-btn btn-primary"><spring:message code="admin.saccoRegistry.saveSacco" text="Save SACCO" /></button>
        </div>
            </form>
    </div>
</div>
                    </div>
</c:if>

<c:if test="${superAdmin}">
<c:forEach items="${registeredSaccos}" var="sacco">
    <div class="app-modal-overlay hidden" data-sacco-modal="add-station-${sacco.saccoId}">
        <div class="app-modal-panel app-modal-panel--compact">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-panel-title"><spring:message code="admin.saccoRegistry.addStation" text="Add Station" /></p>
                        <p class="mt-2 text-sm text-slate-500"><spring:message code="admin.saccoRegistry.addStationHelp" text="Save one new station under this SACCO." /></p>
                </div>
                    <button type="button" class="app-modal-close" data-sacco-modal-close="add-station-${sacco.saccoId}" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                    </div>
                <form action="/admin/saccos/${sacco.saccoId}/stations" method="post" class="app-modal-body space-y-4">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <label class="block text-sm font-semibold text-slate-700">
                        Station ID
                        <input name="stationId" type="text" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" placeholder="Enter the new station ID" />
                    </label>
                    <label class="block text-sm font-semibold text-slate-700">
                        <spring:message code="admin.saccoRegistry.stationAddressLocation" text="Station Address / Location" />
                        <input name="addressLocation" type="text" required maxlength="255" class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" placeholder="e.g. Arusha CBD, Sokoine Road" />
                    </label>
                    <div class="app-modal-actions">
                        <button type="button" class="app-btn btn-neutral" data-sacco-modal-close="add-station-${sacco.saccoId}"><spring:message code="common.cancel" text="Cancel" /></button>
                        <button type="submit" class="app-btn btn-primary"><spring:message code="admin.saccoRegistry.saveStation" text="Save Station" /></button>
            </div>
                </form>
        </div>
    </div>
                    </div>
    <div class="app-modal-overlay hidden" data-sacco-modal="edit-${sacco.saccoId}">
        <div class="app-modal-panel app-modal-panel--compact">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-panel-title">${editSaccoLabel}</p>
                        <p class="mt-2 text-sm text-slate-500">${editSaccoHelp}</p>
                </div>
                    <button type="button" class="app-modal-close" data-sacco-modal-close="edit-${sacco.saccoId}" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                        </div>
                <form action="/admin/saccos/${sacco.saccoId}" method="post" enctype="multipart/form-data" class="app-modal-body space-y-4">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <label class="block text-sm font-semibold text-slate-700">
                        SACCO ID
                        <input type="text" readonly class="mt-1 w-full rounded border border-slate-300 bg-slate-50 px-3 py-2.5 text-sm text-slate-500" value="${sacco.saccoId}" />
                    </label>
                    <label class="block text-sm font-semibold text-slate-700">
                        <spring:message code="admin.saccoRegistry.saccoName" text="SACCO Name" />
                        <input name="saccoName" type="text" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" value="${sacco.saccoName}" />
                    </label>
                    <label class="block text-sm font-semibold text-slate-700">
                        Station IDs
                        <textarea name="stationIds" rows="5" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800">${sacco.stationIdsText}</textarea>
                    </label>
                    <div class="rounded border border-slate-200 bg-slate-50 p-4">
                        <p class="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500"><spring:message code="admin.saccoRegistry.stationLocations" text="Station Locations" /></p>
                        <div class="mt-3 grid gap-3">
                            <c:forEach items="${sacco.stations}" var="station">
                                <label class="block text-sm font-semibold text-slate-700">
                                    ${station.stationId}
                                    <input type="hidden" name="stationAddressIds" value="${station.stationId}" />
                                    <input name="stationAddressLocations" type="text" maxlength="255" class="mt-1 w-full rounded border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800" value="${station.addressLocation}" placeholder="Enter station address or location" />
                                </label>
                            </c:forEach>
                    </div>
                            </div>
                    <div class="sacco-logo-upload-card" data-logo-upload-card>
                        <p class="aws-file-card-kicker">SACCO Logo</p>
                        <div class="sacco-logo-upload-layout">
                            <div data-logo-preview-shell class="sacco-logo-preview-shell">
                                <c:choose>
                                    <c:when test="${sacco.hasLogo}">
                                        <img data-logo-preview-image
                                             src="${sacco.logoUrl}"
                                             alt="${sacco.saccoName} logo"
                                             class="h-full w-full object-contain p-2" />
                                        <span data-logo-preview-fallback class="hidden text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400">None</span>
                                    </c:when>
                                    <c:otherwise>
                                        <img data-logo-preview-image src="" alt="${sacco.saccoName} logo preview" class="hidden h-full w-full object-contain p-2" />
                                        <span data-logo-preview-fallback class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400">None</span>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                            <div class="min-w-0 flex-1">
                                <label class="aws-file-picker">
                                    <span class="aws-file-picker-label">Replace logo image</span>
                                    <span class="aws-file-picker-control">
                                    <input name="logoFile"
                                           type="file"
                                           accept="image/png,image/jpeg"
                                           data-logo-file-input
                                           data-file-picker-input
                                           class="aws-file-picker-input" />
                                        <span class="aws-file-picker-button">Choose File</span>
                                        <span class="aws-file-picker-name" data-file-picker-name>No file chosen</span>
                                    </span>
                                </label>
                                <button type="button"
                                        data-logo-paste-target
                                        class="aws-file-paste-target">
                                    <span class="min-w-0">
                                        <span class="block text-sm font-semibold text-slate-700">Paste replacement image</span>
                                        <span data-logo-paste-hint class="mt-1 block text-xs font-medium text-slate-500">Click here and press Ctrl+V to paste a PNG or JPEG from your clipboard.</span>
                                    </span>
                                    <span class="aws-file-paste-shortcut">Ctrl+V</span>
                                </button>
                                <span class="aws-file-help">
                                    Leave this empty to keep the current logo. Upload or paste a new image to replace it. ${logoUploadPolicy.helpText}
                                </span>
                                <span class="aws-file-meta hidden" data-logo-file-meta></span>
                        </div>
                    </div>
                    </div>
                    <div class="app-modal-actions">
                        <button type="button" class="app-btn btn-neutral" data-sacco-modal-close="edit-${sacco.saccoId}"><spring:message code="common.cancel" text="Cancel" /></button>
                        <c:if test="${sacco.hasLogo}">
                            <button type="submit"
                                    class="app-btn btn-reject"
                                    formaction="/admin/saccos/${sacco.saccoId}/logo/delete"
                                    formmethod="post"
                                    formnovalidate>
                                Remove Logo
                            </button>
                        </c:if>
                        <button type="submit" class="app-btn btn-primary">${saveChangesLabel}</button>
            </div>
                </form>
        </div>
    </div>
                    </div>
    <div class="app-modal-overlay hidden" data-sacco-modal="delete-${sacco.saccoId}">
        <div class="app-modal-panel app-modal-panel--compact">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-panel-title"><spring:message code="common.delete" text="Delete" /> ${sacco.saccoName}</p>
                        <p class="mt-2 text-sm text-slate-500">This permanently removes the SACCO and all related onboarding, member, staff, loan, station, SMS, upload, and workflow data.</p>
                </div>
                    <button type="button" class="app-modal-close" data-sacco-modal-close="delete-${sacco.saccoId}" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                    </div>
                <form action="/admin/saccos/${sacco.saccoId}/delete" method="post" class="app-modal-body space-y-4">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <div class="rounded border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
                        Type <span class="font-semibold">delete ${sacco.saccoName} and all its data</span> to confirm.
                    </div>
                    <label class="block text-sm font-semibold text-slate-700">
                        Confirmation
                        <input name="confirmation" type="text" required autocomplete="off" class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" />
                    </label>
                    <div class="app-modal-actions">
                        <button type="button" class="app-btn btn-neutral" data-sacco-modal-close="delete-${sacco.saccoId}"><spring:message code="common.cancel" text="Cancel" /></button>
                        <button type="submit" class="app-btn btn-reject"><spring:message code="common.delete" text="Delete" /></button>
            </div>
                </form>
        </div>
    </div>
                    </div>
</c:forEach>
</c:if>

<c:if test="${not superAdmin}">
<c:forEach items="${registeredSaccos}" var="sacco">
    <div class="app-modal-overlay hidden" data-sacco-modal="logo-${sacco.saccoId}">
        <div class="app-modal-panel app-modal-panel--compact">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-panel-title">Edit SACCO Logo</p>
                        <p class="mt-2 text-sm text-slate-500">Update the logo shown for ${sacco.saccoName}.</p>
                </div>
                    <button type="button" class="app-modal-close" data-sacco-modal-close="logo-${sacco.saccoId}" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                            </div>
                <form action="/admin/saccos/${sacco.saccoId}/logo" method="post" enctype="multipart/form-data" class="app-modal-body space-y-4">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <div class="sacco-logo-upload-card" data-logo-upload-card>
                        <p class="aws-file-card-kicker">SACCO Logo</p>
                        <div class="sacco-logo-upload-layout">
                            <div data-logo-preview-shell class="sacco-logo-preview-shell">
                                <c:choose>
                                    <c:when test="${sacco.hasLogo}">
                                        <img data-logo-preview-image
                                             src="${sacco.logoUrl}"
                                             alt="${sacco.saccoName} logo"
                                             class="h-full w-full object-contain p-2" />
                                        <span data-logo-preview-fallback class="hidden text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400">None</span>
                                    </c:when>
                                    <c:otherwise>
                                        <img data-logo-preview-image src="" alt="${sacco.saccoName} logo preview" class="hidden h-full w-full object-contain p-2" />
                                        <span data-logo-preview-fallback class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400">None</span>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                            <div class="min-w-0 flex-1">
                                <label class="aws-file-picker">
                                    <span class="aws-file-picker-label">Replace logo image</span>
                                    <span class="aws-file-picker-control">
                                    <input name="logoFile"
                                           type="file"
                                           accept="image/png,image/jpeg"
                                           required
                                           data-logo-file-input
                                           data-file-picker-input
                                           class="aws-file-picker-input" />
                                        <span class="aws-file-picker-button">Choose File</span>
                                        <span class="aws-file-picker-name" data-file-picker-name>No file chosen</span>
                                    </span>
                                </label>
                                <button type="button"
                                        data-logo-paste-target
                                        class="aws-file-paste-target">
                                    <span class="min-w-0">
                                        <span class="block text-sm font-semibold text-slate-700">Paste replacement image</span>
                                        <span data-logo-paste-hint class="mt-1 block text-xs font-medium text-slate-500">Click here and press Ctrl+V to paste a PNG or JPEG from your clipboard.</span>
                                    </span>
                                    <span class="aws-file-paste-shortcut">Ctrl+V</span>
                                </button>
                                <span class="aws-file-help">
                                    ${logoUploadPolicy.helpText}
                                </span>
                                <span class="aws-file-meta hidden" data-logo-file-meta></span>
                        </div>
                    </div>
                    </div>
                    <div class="app-modal-actions">
                        <button type="button" class="app-btn btn-neutral" data-sacco-modal-close="logo-${sacco.saccoId}"><spring:message code="common.cancel" text="Cancel" /></button>
                        <c:if test="${sacco.hasLogo}">
                            <button type="submit"
                                    class="app-btn btn-reject"
                                    formaction="/admin/saccos/${sacco.saccoId}/logo/delete"
                                    formmethod="post"
                                    formnovalidate>
                                Remove Logo
                            </button>
                        </c:if>
                        <button type="submit" class="app-btn btn-primary">Save Logo</button>
            </div>
                </form>
        </div>
    </div>
    </div>
</c:forEach>
</c:if>

<script>
    (function () {
        document.querySelectorAll('[data-sacco-modal]').forEach((modal) => {
            modal.style.position = 'fixed';
            modal.style.inset = '0';
            modal.style.zIndex = '90';
            document.body.appendChild(modal);
        });

        function closeSaccoModal() {
            document.querySelectorAll('[data-sacco-modal]').forEach((modal) => {
                modal.classList.add('hidden');
                modal.classList.remove('is-open');
            });
            document.body.classList.remove('overflow-hidden');
        }

        document.querySelectorAll('[data-sacco-modal-open]').forEach((button) => {
            button.addEventListener('click', function () {
                const key = button.getAttribute('data-sacco-modal-open');
                const modal = document.querySelector('[data-sacco-modal="' + key + '"]');
                if (modal) {
                    modal.classList.remove('hidden');
                    modal.classList.add('is-open');
                    document.body.classList.add('overflow-hidden');
                }
            });
        });

        document.querySelectorAll('[data-sacco-modal-close]').forEach((button) => {
            button.addEventListener('click', closeSaccoModal);
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeSaccoModal();
            }
        });

        function formatLogoFileSize(bytes) {
            if (!Number.isFinite(bytes) || bytes <= 0) {
                return '0 KB';
            }
            if (bytes < 1024 * 1024) {
                return Math.max(1, Math.round(bytes / 1024)) + ' KB';
            }
            return (bytes / (1024 * 1024)).toFixed(1).replace(/\.0$/, '') + ' MB';
        }

        function setLogoFileMeta(meta, text) {
            if (!meta) {
                return;
            }
            if (!text) {
                meta.textContent = '';
                meta.classList.add('hidden');
                return;
            }
            meta.textContent = text;
            meta.classList.remove('hidden');
        }

        function updateLogoPreview(container, file, meta) {
            if (!container || !file || !file.type || file.type.indexOf('image/') !== 0) {
                return;
            }
            const previewImage = container.querySelector('[data-logo-preview-image]');
            const previewFallback = container.querySelector('[data-logo-preview-fallback]');
            if (!previewImage || !previewFallback) {
                return;
            }
            const objectUrl = URL.createObjectURL(file);
            previewImage.src = objectUrl;
            previewImage.classList.remove('hidden');
            previewFallback.classList.add('hidden');
            setLogoFileMeta(meta, 'Checking image details...');
            previewImage.onload = function () {
                setLogoFileMeta(meta, previewImage.naturalWidth + ' x ' + previewImage.naturalHeight + ' px, ' + formatLogoFileSize(file.size));
                URL.revokeObjectURL(objectUrl);
            };
            previewImage.onerror = function () {
                setLogoFileMeta(meta, 'Unable to read image dimensions, ' + formatLogoFileSize(file.size));
                URL.revokeObjectURL(objectUrl);
            };
        }

        function bindLogoUploadCard(card) {
            const fileInput = card.querySelector('[data-logo-file-input]');
            const pasteTarget = card.querySelector('[data-logo-paste-target]');
            const pasteHint = card.querySelector('[data-logo-paste-hint]');
            const previewShell = card.querySelector('[data-logo-preview-shell]');
            const fileMeta = card.querySelector('[data-logo-file-meta]');
            if (!fileInput || !pasteTarget || !previewShell) {
                return;
            }

            fileInput.addEventListener('change', function () {
                if (fileInput.files && fileInput.files[0]) {
                    updateLogoPreview(previewShell, fileInput.files[0], fileMeta);
                    if (pasteHint) {
                        pasteHint.textContent = 'Selected image ready. You can still paste another one to replace it before saving.';
                    }
                } else {
                    setLogoFileMeta(fileMeta, '');
                }
            });

            pasteTarget.addEventListener('paste', function (event) {
                const clipboardItems = event.clipboardData ? Array.from(event.clipboardData.items || []) : [];
                const imageItem = clipboardItems.find(function (item) {
                    return item && item.type && item.type.indexOf('image/') === 0;
                });
                if (!imageItem) {
                    return;
                }
                event.preventDefault();
                const pastedFile = imageItem.getAsFile();
                if (!pastedFile) {
                    return;
                }
                const normalizedName = pastedFile.type === 'image/png' ? 'pasted-logo.png' : 'pasted-logo.jpg';
                const file = new File([pastedFile], normalizedName, { type: pastedFile.type });
                const transfer = new DataTransfer();
                transfer.items.add(file);
                fileInput.files = transfer.files;
                fileInput.dispatchEvent(new Event('change', { bubbles: true }));
                pasteTarget.classList.add('is-ready');
                if (pasteHint) {
                    pasteHint.textContent = 'Pasted image ready. Save the form to apply this logo.';
                }
            });
        }

        document.querySelectorAll('[data-logo-upload-card]').forEach(function (card) {
            if (card.querySelector('[data-logo-file-input]')) {
                bindLogoUploadCard(card);
            }
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
