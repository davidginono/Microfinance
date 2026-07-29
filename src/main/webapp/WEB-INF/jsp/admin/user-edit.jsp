<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/modal-shell.jspf" %>
<style>
    .admin-user-edit-shell {
        display: grid;
        gap: 1rem;
    }

    .admin-access-matrix {
        --erp-table-height: min(38rem, 68vh);
    }

    .admin-access-matrix .erp-table {
        min-width: max(100%, 64rem);
    }

    .admin-access-matrix input[type="checkbox"]:disabled {
        cursor: not-allowed;
        opacity: 0.35;
    }
</style>

<c:if test="${not empty openUserModalKey}">
    <div hidden data-open-user-modal="${openUserModalKey}"></div>
</c:if>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Users &amp; Roles / Edit User</p>
    <h1 class="erp-page-title">Edit User Access</h1>
    <p class="erp-page-subtitle">Update roles, status, and supported access claims.</p>
</div>

<div class="erp-toolbar">
    <div class="text-sm text-slate-500">
        User ID <span class="font-semibold text-sacco-ink"><c:out value="${user.userIdLabel}" /></span>
    </div>
    <a href="/admin/users" class="app-btn btn-neutral">Back To Users</a>
</div>

<form id="user-access-edit-form"
      action="/admin/users/${user.accountId}"
      method="post"
      class="admin-user-edit-shell"
      data-access-edit-form
      data-member-access="${user.memberAccess}">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

    <section class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6">
            <div class="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                <div>
                    <p class="erp-widget-title">User Details</p>
                    <h2 class="mt-1 text-lg font-bold text-sacco-ink"><c:out value="${user.fullName}" /></h2>
                    <p class="mt-1 text-sm text-slate-500">
                        <c:choose>
                            <c:when test="${user.memberAccess}">
                                Member Number <c:out value="${user.memberNumber}" />
                            </c:when>
                            <c:otherwise>
                                Staff Number <c:out value="${user.staffMemberNumber}" />
                            </c:otherwise>
                        </c:choose>
                    </p>
                </div>
                <div class="flex flex-wrap gap-2">
                    <span class="inline-flex rounded border px-2 py-1 text-xs font-semibold ${user.membershipLabel eq 'Staff' ? 'border-slate-200 bg-white text-slate-600' : 'border-cyan-200 bg-cyan-50 text-cyan-800'}">
                        <c:out value="${user.membershipLabel}" />
                    </span>
                    <span class="inline-flex rounded border px-2 py-1 text-xs font-semibold ${user.status eq 'INVITED' ? 'border-amber-200 bg-amber-50 text-amber-700' : user.status eq 'ACTIVE' ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-white text-slate-600'}">
                        <c:out value="${user.displayStatus}" />
                    </span>
                </div>
            </div>
        </div>
        <div class="grid gap-4 px-5 py-5 text-sm text-slate-700 sm:grid-cols-2 lg:grid-cols-4 sm:px-6">
            <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Email</p>
                <p class="mt-1 font-medium text-slate-800"><c:out value="${user.email}" /></p>
            </div>
            <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Phone Number</p>
                <p class="mt-1 font-medium text-slate-800"><c:out value="${user.phone}" /></p>
            </div>
            <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Current Roles</p>
                <p class="mt-1 font-medium text-slate-800"><c:out value="${user.roleSummary}" /></p>
            </div>
            <div>
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Login ID</p>
                <p class="mt-1 font-medium text-slate-800"><c:out value="${user.loginId}" /></p>
            </div>
        </div>
    </section>

    <section class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6">
            <p class="erp-widget-title">Staff Roles</p>
        </div>
        <div class="grid gap-2 px-5 py-5 sm:grid-cols-2 lg:grid-cols-3 sm:px-6">
            <c:forEach items="${staffPositions}" var="position">
                <label class="flex items-center gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700">
                    <input type="checkbox"
                           name="positions"
                           value="${position}"
                           data-staff-role-checkbox="edit-user"
                           ${user.staffRoles.contains(position) ? 'checked' : ''}
                           class="h-4 w-4 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue" />
                    <span>${position.displayName}</span>
                </label>
            </c:forEach>
        </div>
    </section>

    <section class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6">
            <div class="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                <div>
                    <p class="erp-widget-title">Access Matrix</p>
                    <p class="mt-1 text-sm text-slate-500">Unsupported combinations are shown but cannot be selected.</p>
                </div>
                <button type="button" class="app-btn btn-neutral" data-restore-default-claims>
                    Restore Default Permissions
                </button>
            </div>
        </div>
        <div class="px-5 py-5 sm:px-6">
            <div class="admin-access-matrix erp-table-wrap erp-table-scroll">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th class="whitespace-nowrap">Feature</th>
                        <c:forEach items="${accessActions}" var="action">
                            <th class="whitespace-nowrap text-center">
                                <label class="inline-flex items-center gap-2">
                                    <span>${action.displayName}</span>
                                    <input type="checkbox"
                                           data-access-column-toggle="${action}"
                                           class="h-4 w-4 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue" />
                                </label>
                            </th>
                        </c:forEach>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${accessMatrixRows}" var="row">
                        <tr>
                            <td class="whitespace-nowrap font-semibold text-slate-700">
                                <label class="inline-flex items-center gap-2">
                                    <input type="checkbox"
                                           data-access-row-toggle="${row.feature}"
                                           class="h-4 w-4 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue" />
                                    <span><c:out value="${row.label}" /></span>
                                </label>
                            </td>
                            <c:forEach items="${accessActions}" var="action">
                                <c:set var="matrixClaim" value="${row.claimsByAction[action]}" />
                                <td class="text-center">
                                    <c:choose>
                                        <c:when test="${not empty matrixClaim}">
                                            <input type="checkbox"
                                                   name="claims"
                                                   value="${matrixClaim}"
                                                   data-access-row="${row.feature}"
                                                   data-access-column="${action}"
                                                   data-access-claim="${matrixClaim}"
                                                   title="${matrixClaim.displayName}"
                                                   ${user.claims.contains(matrixClaim) ? 'checked' : ''}
                                                   class="h-4 w-4 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue" />
                                        </c:when>
                                        <c:otherwise>
                                            <input type="checkbox"
                                                   disabled
                                                   data-access-row="${row.feature}"
                                                   data-access-column="${action}"
                                                   title="No supported claim for ${row.label} ${action.displayName}"
                                                   class="h-4 w-4 rounded border-slate-300 text-slate-300" />
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                            </c:forEach>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </div>
    </section>

    <section class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6">
            <p class="erp-widget-title">Account Status</p>
        </div>
        <div class="px-5 py-5 sm:px-6">
            <c:choose>
                <c:when test="${user.status eq 'INVITED'}">
                    <input type="hidden" name="status" value="INVITED" />
                    <div class="rounded-md border border-amber-200 bg-amber-50 px-3 py-3">
                        <p class="text-xs font-semibold uppercase tracking-wide text-amber-700">Status</p>
                        <p class="mt-1 text-sm font-semibold text-amber-800"><c:out value="${user.displayStatus}" /></p>
                        <p class="mt-1 text-xs leading-5 text-amber-700">This account becomes active only after the invite form is completed.</p>
                    </div>
                </c:when>
                <c:otherwise>
                    <label class="block max-w-md text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Status
                        <select name="status" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                            <c:forEach items="${statuses}" var="status">
                                <c:if test="${status ne 'INVITED'}">
                                    <option value="${status}" ${user.status eq status ? 'selected' : ''}>${status}</option>
                                </c:if>
                            </c:forEach>
                        </select>
                    </label>
                </c:otherwise>
            </c:choose>
        </div>
    </section>

    <div class="app-modal-actions">
        <c:if test="${user.status eq 'INVITED'}">
            <button type="submit"
                    class="app-btn btn-reject"
                    formaction="/admin/users/${user.accountId}/cancel-invite"
                    formmethod="post"
                    onclick="return confirm('Cancel this invitation? The staff member will be marked inactive.');">
                Cancel Invite
            </button>
        </c:if>
        <c:if test="${user.canDeleteStaffRecord and canDeleteUsers}">
            <button type="button"
                    class="app-btn btn-reject"
                    data-user-modal-open="delete-user-${user.accountId}">
                Delete
            </button>
        </c:if>
        <a href="/admin/users" class="app-btn btn-neutral">Cancel</a>
        <button type="submit" class="app-btn btn-primary">Save</button>
    </div>
</form>

<c:if test="${user.canDeleteStaffRecord and canDeleteUsers}">
    <div class="app-modal-overlay hidden" data-user-modal="delete-user-${user.accountId}">
        <div class="app-modal-panel app-modal-panel--compact">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-widget-title">Delete Staff Member</p>
                        <h2 class="mt-1 text-xl font-bold text-sacco-ink"><c:out value="${user.fullName}" /></h2>
                        <p class="mt-1 text-sm text-slate-500">This permanently removes the cancelled staff invitation record.</p>
                    </div>
                    <button type="button" class="app-modal-close" data-user-modal-close="delete-user-${user.accountId}" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>

                <form action="/admin/users/${user.accountId}/delete" method="post" class="app-modal-body space-y-4">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <div class="rounded-md border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
                        Type <span class="font-semibold">delete <c:out value="${user.fullName}" /></span> to confirm.
                    </div>
                    <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                        Confirmation
                        <input name="confirmation" type="text" required autocomplete="off" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" />
                    </label>
                    <div class="app-modal-actions">
                        <button type="button" class="app-btn btn-neutral" data-user-modal-close="delete-user-${user.accountId}">Cancel</button>
                        <button type="submit" class="app-btn btn-reject">Delete</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</c:if>

<script type="application/json" id="role-default-claims-json">${roleDefaultClaimsJson}</script>
<script type="application/json" id="member-default-claims-json">${memberDefaultClaimsJson}</script>

<script>
    (() => {
        const body = document.body;
        const form = document.querySelector('[data-access-edit-form]');

        function readJson(id, fallback) {
            const source = document.getElementById(id);
            if (!source) {
                return fallback;
            }
            try {
                return JSON.parse(source.textContent || '');
            } catch (error) {
                return fallback;
            }
        }

        const roleDefaultClaims = readJson('role-default-claims-json', {});
        const memberDefaultClaims = readJson('member-default-claims-json', []);

        document.querySelectorAll('[data-user-modal]').forEach((modal) => {
            modal.style.position = 'fixed';
            modal.style.inset = '0';
            modal.style.zIndex = '90';
            document.body.appendChild(modal);
        });

        function syncSuperAdminRoleGroup(groupKey) {
            const checkboxes = Array.from(document.querySelectorAll('[data-staff-role-checkbox="' + groupKey + '"]'));
            const superAdminCheckbox = checkboxes.find((checkbox) => checkbox.value === 'ADMIN');
            if (!superAdminCheckbox) {
                return;
            }
            checkboxes.forEach((checkbox) => {
                if (superAdminCheckbox.checked && checkbox !== superAdminCheckbox) {
                    checkbox.checked = false;
                }
                checkbox.disabled = Boolean(superAdminCheckbox.checked && checkbox !== superAdminCheckbox);
            });
        }

        const initializedRoleGroups = new Set();
        document.querySelectorAll('[data-staff-role-checkbox]').forEach((checkbox) => {
            const groupKey = checkbox.getAttribute('data-staff-role-checkbox');
            checkbox.addEventListener('change', () => syncSuperAdminRoleGroup(groupKey));
            if (!initializedRoleGroups.has(groupKey)) {
                initializedRoleGroups.add(groupKey);
                syncSuperAdminRoleGroup(groupKey);
            }
        });

        function enabledInputs(root, selector) {
            return Array.from(root.querySelectorAll(selector + ':not(:disabled)'));
        }

        function syncMatrixToggle(toggle, inputs) {
            if (!toggle) {
                return;
            }
            if (!inputs.length) {
                toggle.checked = false;
                toggle.indeterminate = false;
                toggle.disabled = true;
                return;
            }
            const checkedCount = inputs.filter((input) => input.checked).length;
            toggle.disabled = false;
            toggle.checked = checkedCount === inputs.length;
            toggle.indeterminate = checkedCount > 0 && checkedCount < inputs.length;
        }

        function syncMatrixState(editForm) {
            editForm.querySelectorAll('[data-access-row-toggle]').forEach((toggle) => {
                const row = toggle.getAttribute('data-access-row-toggle');
                syncMatrixToggle(toggle, enabledInputs(editForm, '[data-access-row="' + row + '"]'));
            });
            editForm.querySelectorAll('[data-access-column-toggle]').forEach((toggle) => {
                const column = toggle.getAttribute('data-access-column-toggle');
                syncMatrixToggle(toggle, enabledInputs(editForm, '[data-access-column="' + column + '"]'));
            });
        }

        if (form) {
            form.querySelectorAll('[data-access-row-toggle]').forEach((toggle) => {
                toggle.addEventListener('change', () => {
                    const row = toggle.getAttribute('data-access-row-toggle');
                    enabledInputs(form, '[data-access-row="' + row + '"]').forEach((input) => {
                        input.checked = toggle.checked;
                    });
                    syncMatrixState(form);
                });
            });

            form.querySelectorAll('[data-access-column-toggle]').forEach((toggle) => {
                toggle.addEventListener('change', () => {
                    const column = toggle.getAttribute('data-access-column-toggle');
                    enabledInputs(form, '[data-access-column="' + column + '"]').forEach((input) => {
                        input.checked = toggle.checked;
                    });
                    syncMatrixState(form);
                });
            });

            form.querySelectorAll('[data-access-claim]').forEach((input) => {
                input.addEventListener('change', () => syncMatrixState(form));
            });

            const restoreButton = form.querySelector('[data-restore-default-claims]');
            if (restoreButton) {
                restoreButton.addEventListener('click', () => {
                    const defaults = new Set();
                    if (form.getAttribute('data-member-access') === 'true') {
                        memberDefaultClaims.forEach((claim) => defaults.add(claim));
                    }
                    enabledInputs(form, '[data-staff-role-checkbox="edit-user"]').forEach((roleInput) => {
                        if (!roleInput.checked) {
                            return;
                        }
                        (roleDefaultClaims[roleInput.value] || []).forEach((claim) => defaults.add(claim));
                    });
                    form.querySelectorAll('[data-access-claim]').forEach((input) => {
                        input.checked = defaults.has(input.value);
                    });
                    syncMatrixState(form);
                });
            }

            syncMatrixState(form);
        }

        function closeAllUserModals() {
            document.querySelectorAll('[data-user-modal]').forEach((modal) => {
                modal.classList.add('hidden');
                modal.classList.remove('is-open');
                modal.setAttribute('aria-hidden', 'true');
            });
            body.classList.remove('overflow-hidden');
            window.SaccosUiState?.clearOpenModal();
        }

        function openUserModal(key) {
            closeAllUserModals();
            const modal = document.querySelector('[data-user-modal="' + key + '"]');
            if (modal) {
                modal.classList.remove('hidden');
                modal.classList.add('is-open');
                modal.setAttribute('aria-hidden', 'false');
                body.classList.add('overflow-hidden');
                window.SaccosUiState?.rememberOpenModal(modal);
            }
        }

        document.querySelectorAll('[data-user-modal-open]').forEach((button) => {
            button.addEventListener('click', () => openUserModal(button.getAttribute('data-user-modal-open')));
        });

        document.querySelectorAll('[data-user-modal-close]').forEach((button) => {
            button.addEventListener('click', closeAllUserModals);
        });

        const initialModalTarget = document.querySelector('[data-open-user-modal]');
        if (initialModalTarget) {
            openUserModal(initialModalTarget.getAttribute('data-open-user-modal'));
        }
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
