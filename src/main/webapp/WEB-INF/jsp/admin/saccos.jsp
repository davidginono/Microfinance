<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/modal-shell.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / SACCO Registry</p>
    <h1 class="erp-page-title">SACCO Registry</h1>
    <p class="erp-page-subtitle">Register SACCOs and their stations so registration and admin work stay tied to real SACCO IDs.</p>
</div>

<div class="erp-panel">
    <div class="erp-panel-header">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
                <p class="erp-panel-title">Registered SACCOs</p>
                <p class="mt-1 text-sm text-slate-500">Manage SACCO IDs, names, and station lists from one place.</p>
            </div>
            <button type="button" class="app-btn btn-primary" data-sacco-modal-open="create-sacco">Add SACCO</button>
        </div>
    </div>
    <div class="erp-panel-body">
        <div class="erp-table-wrap overflow-x-auto">
            <table class="erp-table min-w-[680px]">
                <thead>
                <tr>
                    <th>SACCO ID</th>
                    <th>SACCO Name</th>
                    <th>Stations</th>
                    <th>Action</th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${registeredSaccos}" var="sacco">
                    <tr>
                        <td class="font-semibold text-slate-800">${sacco.saccoId}</td>
                        <td>${sacco.saccoName}</td>
                        <td>${sacco.stationIds}</td>
                        <td class="whitespace-nowrap">
                            <button type="button"
                                    class="app-btn btn-primary"
                                    data-sacco-modal-open="edit-${sacco.saccoId}">
                                Edit
                            </button>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty registeredSaccos}">
                    <tr>
                        <td colspan="4" class="text-slate-500">No SACCOs have been registered yet.</td>
                    </tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>

<div class="app-modal-overlay hidden" data-sacco-modal="create-sacco">
    <div class="app-modal-panel app-modal-panel--compact">
        <div class="app-modal-scroll">
            <div class="app-modal-header">
                <div>
                    <p class="erp-panel-title">Add SACCO</p>
                    <p class="mt-2 text-sm text-slate-500">Save the SACCO and its station IDs.</p>
                </div>
                <button type="button" class="app-modal-close" data-sacco-modal-close="create-sacco" aria-label="Close modal">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                    </svg>
                </button>
            </div>
            <form action="/admin/saccos" method="post" class="app-modal-body space-y-4">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <label class="block text-sm font-semibold text-slate-700">
                    SACCO ID
                    <input name="saccoId" type="text" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" placeholder="e.g. SACCO-ARUSHA-001" />
                </label>
                <label class="block text-sm font-semibold text-slate-700">
                    SACCO Name
                    <input name="saccoName" type="text" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" placeholder="e.g. IAA SACCOS LTD" />
                </label>
                <label class="block text-sm font-semibold text-slate-700">
                    Station IDs
                    <textarea name="stationIds" rows="5" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" placeholder="Enter one or more station IDs, separated by commas or new lines.&#10;Example:&#10;STN789&#10;STN790"></textarea>
                </label>
                <div class="app-modal-section text-sm text-slate-600">
                    Add at least one station ID for every SACCO. Admin selection and member registration will use these values.
                </div>
                <div class="app-modal-actions">
                    <button type="button" class="app-btn btn-neutral" data-sacco-modal-close="create-sacco">Cancel</button>
                    <button type="submit" class="app-btn btn-primary">Save SACCO</button>
                </div>
            </form>
        </div>
    </div>
</div>

<c:forEach items="${registeredSaccos}" var="sacco">
    <div class="app-modal-overlay hidden" data-sacco-modal="edit-${sacco.saccoId}">
        <div class="app-modal-panel app-modal-panel--compact">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-panel-title">Edit SACCO</p>
                        <p class="mt-2 text-sm text-slate-500">Update the SACCO name and active station IDs.</p>
                    </div>
                    <button type="button" class="app-modal-close" data-sacco-modal-close="edit-${sacco.saccoId}" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>
                <form action="/admin/saccos/${sacco.saccoId}" method="post" class="app-modal-body space-y-4">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <label class="block text-sm font-semibold text-slate-700">
                        SACCO ID
                        <input type="text" readonly class="mt-1 w-full rounded border border-slate-300 bg-slate-50 px-3 py-2.5 text-sm text-slate-500" value="${sacco.saccoId}" />
                    </label>
                    <label class="block text-sm font-semibold text-slate-700">
                        SACCO Name
                        <input name="saccoName" type="text" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" value="${sacco.saccoName}" />
                    </label>
                    <label class="block text-sm font-semibold text-slate-700">
                        Station IDs
                        <textarea name="stationIds" rows="5" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800">${sacco.stationIdsText}</textarea>
                    </label>
                    <div class="app-modal-section text-sm text-slate-600">
                        Keep at least one station active for this SACCO. Removed station IDs will be disabled from future selection.
                    </div>
                    <div class="app-modal-actions">
                        <button type="button" class="app-btn btn-neutral" data-sacco-modal-close="edit-${sacco.saccoId}">Cancel</button>
                        <button type="submit" class="app-btn btn-primary">Save Changes</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</c:forEach>

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

        document.querySelectorAll('[data-sacco-modal]').forEach((modal) => {
            modal.addEventListener('click', function (event) {
                if (event.target === modal) {
                    closeSaccoModal();
                }
            });
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeSaccoModal();
            }
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
