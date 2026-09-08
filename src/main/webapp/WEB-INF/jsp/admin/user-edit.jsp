<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<c:if test="${not empty openUserModalKey}">
    <div hidden data-open-user-modal="${openUserModalKey}"></div>
</c:if>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">Admin Tools / Users &amp; Roles / Edit User</p>
    <h1 class="erp-page-title">Edit User Access</h1>
</div>

<div class="admin-user-edit-context-bar">
    <div class="admin-user-edit-context">
        <span class="admin-user-edit-context__label">User ID</span>
        <span class="admin-user-edit-context__value"><c:out value="${user.userIdLabel}" /></span>
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

    <section class="erp-panel admin-user-edit-panel admin-user-summary-panel">
        <div class="admin-user-summary-header">
            <div class="admin-user-summary-copy">
                <p class="admin-section-kicker">User Details</p>
                <h2 class="admin-user-summary-name"><c:out value="${user.fullName}" /></h2>
                <p class="admin-user-summary-meta">
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
            <div class="admin-user-badge-row" aria-label="User access status">
                <span class="admin-user-badge ${user.membershipLabel eq 'Staff' ? 'is-neutral' : 'is-info'}">
                    <c:out value="${user.membershipLabel}" />
                </span>
                <span class="admin-user-badge ${user.status eq 'INVITED' ? 'is-warning' : user.status eq 'ACTIVE' ? 'is-success' : 'is-neutral'}">
                    <c:out value="${user.displayStatus}" />
                </span>
            </div>
        </div>
        <dl class="admin-user-detail-grid">
            <div class="admin-user-detail-item">
                <dt>Email</dt>
                <dd><c:out value="${user.email}" /></dd>
            </div>
            <div class="admin-user-detail-item">
                <dt>Phone Number</dt>
                <dd><c:out value="${user.phone}" /></dd>
            </div>
            <div class="admin-user-detail-item">
                <dt>Current Roles</dt>
                <dd><c:out value="${user.roleSummary}" /></dd>
            </div>
            <div class="admin-user-detail-item">
                <dt>Login ID</dt>
                <dd><c:out value="${user.loginId}" /></dd>
            </div>
        </dl>
    </section>

    <section class="erp-panel admin-user-edit-panel">
        <div class="admin-user-section-header">
            <h2>Staff Roles</h2>
            <c:if test="${user.memberAccess}">
                <p>Clear all staff roles to keep member access only.</p>
            </c:if>
        </div>
        <div class="admin-role-grid">
            <c:forEach items="${staffPositions}" var="position">
                <label class="admin-role-option">
                    <input type="checkbox"
                           name="positions"
                           value="${position}"
                           data-staff-role-checkbox="edit-user"
                           ${user.staffRoles.contains(position) ? 'checked' : ''}
                           class="admin-cloud-checkbox" />
                    <span>${position.displayName}</span>
                </label>
            </c:forEach>
        </div>
    </section>

    <section class="erp-panel admin-user-edit-panel">
        <div class="admin-user-section-header admin-user-section-header--actions">
            <div>
                <h2>Access Matrix</h2>
                <p>Unsupported combinations remain visible and disabled.</p>
            </div>
            <div class="admin-user-section-actions">
                <button type="button" class="admin-access-matrix-restore app-btn btn-neutral" data-restore-default-claims>
                    Restore Default Permissions
                </button>
            </div>
        </div>
        <div class="admin-user-section-body">
            <div class="admin-access-matrix erp-table-scroll" data-aws-table-region data-loading-label="Loading results...">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th class="admin-matrix-feature-cell">Feature</th>
                        <c:forEach items="${accessActions}" var="action">
                            <th class="admin-matrix-action-cell">
                                <label class="admin-matrix-toggle">
                                    <span>${action.displayName}</span>
                                    <input type="checkbox"
                                           data-access-column-toggle="${action}"
                                           class="admin-cloud-checkbox" />
                                </label>
                            </th>
                        </c:forEach>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${accessMatrixRows}" var="row">
                        <tr>
                            <td class="admin-matrix-feature-cell">
                                <label class="admin-matrix-toggle">
                                    <input type="checkbox"
                                           data-access-row-toggle="${row.feature}"
                                           class="admin-cloud-checkbox" />
                                    <span><c:out value="${row.label}" /></span>
                                </label>
                            </td>
                            <c:forEach items="${accessActions}" var="action">
                                <c:set var="matrixClaim" value="${row.claimsByAction[action]}" />
                                <td class="admin-matrix-action-cell">
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
                                                   class="admin-cloud-checkbox" />
                                        </c:when>
                                        <c:otherwise>
                                            <input type="checkbox"
                                                   disabled
                                                   data-access-row="${row.feature}"
                                                   data-access-column="${action}"
                                                   title="No supported claim for ${row.label} ${action.displayName}"
                                                   class="admin-cloud-checkbox" />
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

    <section class="erp-panel admin-user-edit-panel">
        <div class="admin-user-section-header">
            <h2>Account Status</h2>
        </div>
        <div class="admin-user-section-body">
            <c:choose>
                <c:when test="${user.status eq 'INVITED'}">
                    <input type="hidden" name="status" value="INVITED" />
                    <div class="admin-status-callout is-warning">
                        <p class="admin-section-kicker">Status</p>
                        <p class="admin-status-callout__value"><c:out value="${user.displayStatus}" /></p>
                        <p class="admin-status-callout__text">This account becomes active only after the invite form is completed.</p>
                    </div>
                </c:when>
                <c:otherwise>
                    <label class="fcms-label admin-status-field">
                        Status
                        <select name="status" class="fcms-control">
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

    <div class="admin-user-edit-actions">
        <c:if test="${user.status eq 'INVITED'}">
            <button type="submit"
                    class="app-btn btn-danger"
                    formaction="/admin/users/${user.accountId}/cancel-invite"
                    formmethod="post"
                    onclick="return confirm('Cancel this invitation? The staff member will be marked inactive.');">
                Cancel Invite
            </button>
        </c:if>
        <c:if test="${user.canDeleteStaffRecord and canDeleteUsers}">
            <button type="button"
                    class="app-btn btn-danger"
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
                        <p class="admin-section-kicker">Delete Staff Member</p>
                        <h2 class="admin-modal-title"><c:out value="${user.fullName}" /></h2>
                        <p class="admin-modal-copy">This permanently removes the cancelled staff invitation record.</p>
                    </div>
                    <button type="button" class="app-modal-close" data-user-modal-close="delete-user-${user.accountId}" aria-label="Close modal">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>

                <form action="/admin/users/${user.accountId}/delete" method="post" class="app-modal-body admin-delete-form">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <div class="admin-delete-callout">
                        Type <span class="font-semibold">delete <c:out value="${user.fullName}" /></span> to confirm.
                    </div>
                    <label class="fcms-label">
                        Confirmation
                        <input name="confirmation" type="text" required autocomplete="off" class="fcms-control" />
                    </label>
                    <div class="app-modal-actions">
                        <button type="button" class="app-btn btn-neutral" data-user-modal-close="delete-user-${user.accountId}">Cancel</button>
                        <button type="submit" class="app-btn btn-danger">Delete</button>
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

        function allRoleDefaultClaims() {
            const defaults = new Set();
            Object.keys(roleDefaultClaims).forEach((roleName) => {
                (roleDefaultClaims[roleName] || []).forEach((claim) => defaults.add(claim));
            });
            return defaults;
        }

        function selectedDefaultClaims(editForm) {
            const defaults = new Set();
            if (editForm.getAttribute('data-member-access') === 'true') {
                memberDefaultClaims.forEach((claim) => defaults.add(claim));
            }
            enabledInputs(editForm, '[data-staff-role-checkbox="edit-user"]').forEach((roleInput) => {
                if (!roleInput.checked) {
                    return;
                }
                (roleDefaultClaims[roleInput.value] || []).forEach((claim) => defaults.add(claim));
            });
            return defaults;
        }

        function syncClaimsForSelectedRoles(editForm) {
            const selectedDefaults = selectedDefaultClaims(editForm);
            const roleDefaults = allRoleDefaultClaims();
            editForm.querySelectorAll('[data-access-claim]:not(:disabled)').forEach((input) => {
                if (selectedDefaults.has(input.value)) {
                    input.checked = true;
                } else if (roleDefaults.has(input.value)) {
                    input.checked = false;
                }
            });
            syncMatrixState(editForm);
        }

        if (form) {
            form.querySelectorAll('[data-staff-role-checkbox="edit-user"]').forEach((roleInput) => {
                roleInput.addEventListener('change', () => syncClaimsForSelectedRoles(form));
            });

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
                    const defaults = selectedDefaultClaims(form);
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
