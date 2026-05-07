<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/modal-shell.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / ${superAdmin ? 'SACCO Registry' : 'Station Registry'}</p>
    <h1 class="erp-page-title">${superAdmin ? 'SACCO Registry' : 'Station Registry'}</h1>
    <p class="erp-page-subtitle">${superAdmin ? 'Manage registered SACCO workspaces.' : 'Manage station IDs for your SACCO workspace.'}</p>
</div>

<div class="erp-panel">
    <div class="erp-panel-header">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
                <p class="erp-panel-title">${superAdmin ? 'SACCO Registry' : 'Current SACCO Stations'}</p>
            </div>
            <c:if test="${superAdmin}">
                <button type="button" class="app-btn btn-primary" data-sacco-modal-open="create-sacco">Add SACCO</button>
            </c:if>
            <c:if test="${not superAdmin and not empty registeredSaccos}">
                <button type="button"
                        class="app-btn btn-primary"
                        data-sacco-modal-open="add-station-${registeredSaccos[0].saccoId}">
                    Add Station
                </button>
            </c:if>
        </div>
    </div>
    <div class="erp-panel-body">
        <c:if test="${not superAdmin}">
            <div class="mb-4 rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
                Add a new station from the button above, or use Edit Stations to review the full station list.
            </div>
        </c:if>
        <div class="erp-table-wrap overflow-x-auto">
            <table class="erp-table min-w-[680px]">
                <thead>
                <tr>
                    <th>Logo</th>
                    <th>SACCO ID</th>
                    <th>SACCO Name</th>
                    <th>Stations</th>
                    <c:if test="${superAdmin}">
                        <th>Details</th>
                    </c:if>
                    <th>Action</th>
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
                                         class="h-10 w-10 rounded-md border border-slate-200 bg-white object-contain p-1 shadow-sm" />
                                </c:when>
                                <c:otherwise>
                                    <span class="inline-flex h-10 w-10 items-center justify-center rounded-md border border-dashed border-slate-300 bg-slate-50 text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400">
                                        Logo
                                    </span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td class="font-semibold text-slate-800">${sacco.saccoId}</td>
                        <td>${sacco.saccoName}</td>
                        <td>${sacco.stationIds}</td>
                        <c:if test="${superAdmin}">
                            <td class="whitespace-nowrap">
                                <a href="/admin/saccos/${sacco.saccoId}?section=overview" class="app-btn btn-neutral">View Details</a>
                            </td>
                        </c:if>
                        <td class="whitespace-nowrap">
                            <button type="button"
                                    class="app-btn btn-primary"
                                    data-sacco-modal-open="edit-${sacco.saccoId}">
                                ${superAdmin ? 'Edit' : 'Edit Stations'}
                            </button>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty registeredSaccos}">
                    <tr>
                        <td colspan="${superAdmin ? 6 : 5}" class="text-slate-500">No SACCOs have been registered yet.</td>
                    </tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</div>

<c:if test="${superAdmin}">
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
            <form action="/admin/saccos" method="post" enctype="multipart/form-data" class="app-modal-body space-y-4">
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
                <div class="rounded border border-slate-200 bg-slate-50 p-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">SACCO Logo</p>
                    <div class="mt-3 flex items-start gap-4">
                        <div data-logo-preview-shell class="inline-flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-md border border-dashed border-slate-300 bg-white">
                            <img data-logo-preview-image src="" alt="New SACCO logo preview" class="hidden h-full w-full object-contain p-2" />
                            <span data-logo-preview-fallback class="text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400">Auto</span>
                        </div>
                        <div class="min-w-0 flex-1">
                            <label class="block text-sm font-semibold text-slate-700">
                                Optional logo image
                                <input name="logoFile"
                                       type="file"
                                       accept="image/png,image/jpeg"
                                       data-logo-file-input
                                       class="mt-2 block w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 file:mr-3 file:rounded file:border-0 file:bg-slate-100 file:px-3 file:py-2 file:text-sm file:font-semibold file:text-slate-700" />
                            </label>
                            <button type="button"
                                    data-logo-paste-target
                                    class="mt-3 flex w-full items-center justify-between gap-3 rounded border border-dashed border-slate-300 bg-white px-3 py-2.5 text-left transition hover:border-slate-400 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-sky-200">
                                <span class="min-w-0">
                                    <span class="block text-sm font-semibold text-slate-700">Paste image</span>
                                    <span data-logo-paste-hint class="mt-1 block text-xs font-medium text-slate-500">Click here and press Ctrl+V to paste a PNG or JPEG from your clipboard.</span>
                                </span>
                                <span class="shrink-0 rounded border border-slate-200 bg-slate-50 px-2 py-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Ctrl+V</span>
                            </button>
                            <span class="mt-2 block text-xs font-medium text-slate-500">
                                Optional. Use PNG or JPEG only, between 64x64 and 1024x1024 pixels, up to 1 MB. If you skip this, the navbar will use the SACCO initials.
                            </span>
                        </div>
                    </div>
                </div>
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
</c:if>

<c:if test="${not superAdmin and not empty registeredSaccos}">
<div class="app-modal-overlay hidden" data-sacco-modal="add-station-${registeredSaccos[0].saccoId}">
    <div class="app-modal-panel app-modal-panel--compact">
        <div class="app-modal-scroll">
            <div class="app-modal-header">
                <div>
                    <p class="erp-panel-title">Add Station</p>
                    <p class="mt-2 text-sm text-slate-500">Save one new station ID for this SACCO workspace.</p>
                </div>
                <button type="button" class="app-modal-close" data-sacco-modal-close="add-station-${registeredSaccos[0].saccoId}" aria-label="Close modal">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                    </svg>
                </button>
            </div>
            <form action="/admin/saccos/${registeredSaccos[0].saccoId}/stations" method="post" class="app-modal-body space-y-4">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <label class="block text-sm font-semibold text-slate-700">
                    Station ID
                    <input name="stationId" type="text" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" placeholder="Enter the new station ID" />
                </label>
                <div class="app-modal-actions">
                    <button type="button" class="app-btn btn-neutral" data-sacco-modal-close="add-station-${registeredSaccos[0].saccoId}">Cancel</button>
                    <button type="submit" class="app-btn btn-primary">Save Station</button>
                </div>
            </form>
        </div>
    </div>
</div>
</c:if>

<c:forEach items="${registeredSaccos}" var="sacco">
    <div class="app-modal-overlay hidden" data-sacco-modal="edit-${sacco.saccoId}">
        <div class="app-modal-panel app-modal-panel--compact">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-panel-title">${superAdmin ? 'Edit SACCO' : 'Edit Stations'}</p>
                        <p class="mt-2 text-sm text-slate-500">${superAdmin ? 'Update the SACCO name and active station IDs.' : 'Review and update the active station IDs for this SACCO workspace.'}</p>
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
                    <c:choose>
                        <c:when test="${superAdmin}">
                            <label class="block text-sm font-semibold text-slate-700">
                                SACCO Name
                                <input name="saccoName" type="text" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" value="${sacco.saccoName}" />
                            </label>
                        </c:when>
                        <c:otherwise>
                            <input type="hidden" name="saccoName" value="${sacco.saccoName}" />
                            <label class="block text-sm font-semibold text-slate-700">
                                SACCO Name
                                <input type="text" readonly class="mt-1 w-full rounded border border-slate-300 bg-slate-50 px-3 py-2.5 text-sm text-slate-500" value="${sacco.saccoName}" />
                            </label>
                        </c:otherwise>
                    </c:choose>
                    <label class="block text-sm font-semibold text-slate-700">
                        Station IDs
                        <textarea name="stationIds" rows="5" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800">${sacco.stationIdsText}</textarea>
                    </label>
                    <c:if test="${superAdmin}">
                    <div class="rounded border border-slate-200 bg-slate-50 p-4">
                        <p class="text-xs font-semibold uppercase tracking-[0.14em] text-slate-500">SACCO Logo</p>
                        <div class="mt-3 flex items-start gap-4">
                            <div data-logo-preview-shell class="inline-flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-md border border-slate-200 bg-white shadow-sm">
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
                                <label class="block text-sm font-semibold text-slate-700">
                                    Replace logo image
                                    <input name="logoFile"
                                           type="file"
                                           accept="image/png,image/jpeg"
                                           data-logo-file-input
                                           class="mt-2 block w-full rounded border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 file:mr-3 file:rounded file:border-0 file:bg-slate-100 file:px-3 file:py-2 file:text-sm file:font-semibold file:text-slate-700" />
                                </label>
                                <button type="button"
                                        data-logo-paste-target
                                        class="mt-3 flex w-full items-center justify-between gap-3 rounded border border-dashed border-slate-300 bg-white px-3 py-2.5 text-left transition hover:border-slate-400 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-sky-200">
                                    <span class="min-w-0">
                                        <span class="block text-sm font-semibold text-slate-700">Paste replacement image</span>
                                        <span data-logo-paste-hint class="mt-1 block text-xs font-medium text-slate-500">Click here and press Ctrl+V to paste a PNG or JPEG from your clipboard.</span>
                                    </span>
                                    <span class="shrink-0 rounded border border-slate-200 bg-slate-50 px-2 py-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-500">Ctrl+V</span>
                                </button>
                                <span class="mt-2 block text-xs font-medium text-slate-500">
                                    Leave this empty to keep the current logo. Upload or paste a new PNG or JPEG image between 64x64 and 1024x1024 pixels, up to 1 MB, to replace it.
                                </span>
                            </div>
                        </div>
                    </div>
                    </c:if>
                    <div class="app-modal-actions">
                        <button type="button" class="app-btn btn-neutral" data-sacco-modal-close="edit-${sacco.saccoId}">Cancel</button>
                        <button type="submit" class="app-btn btn-primary">${superAdmin ? 'Save Changes' : 'Save Stations'}</button>
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

        function updateLogoPreview(container, file) {
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
        }

        function bindLogoUploadCard(card) {
            const fileInput = card.querySelector('[data-logo-file-input]');
            const pasteTarget = card.querySelector('[data-logo-paste-target]');
            const pasteHint = card.querySelector('[data-logo-paste-hint]');
            const previewShell = card.querySelector('[data-logo-preview-shell]');
            if (!fileInput || !pasteTarget || !previewShell) {
                return;
            }

            fileInput.addEventListener('change', function () {
                if (fileInput.files && fileInput.files[0]) {
                    updateLogoPreview(previewShell, fileInput.files[0]);
                    if (pasteHint) {
                        pasteHint.textContent = 'Selected image ready. You can still paste another one to replace it before saving.';
                    }
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
                updateLogoPreview(previewShell, file);
                pasteTarget.classList.remove('border-dashed');
                pasteTarget.classList.add('border-emerald-300', 'bg-emerald-50');
                if (pasteHint) {
                    pasteHint.textContent = 'Pasted image ready. Save the form to apply this logo.';
                }
            });
        }

        document.querySelectorAll('.app-modal-body .rounded.border.border-slate-200.bg-slate-50.p-4').forEach(function (card) {
            if (card.querySelector('[data-logo-file-input]')) {
                bindLogoUploadCard(card);
            }
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
