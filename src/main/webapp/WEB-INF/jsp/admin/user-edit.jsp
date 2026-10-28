<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/admin-workspace-support.jspf" %>


<c:if test="${not empty openUserModalKey}">
    <div hidden data-open-user-modal="${openUserModalKey}"></div>
</c:if>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.workspace.userBreadcrumb" /></p>
    <h1 class="erp-page-title" id="admin-user-edit-title"><c:out value="${user.fullName}" /></h1>
</div>

<template data-resource-details-page></template>

<form id="user-access-edit-form"
      action="/admin/users/${user.accountId}"
      method="post"
      class="admin-user-edit-shell accounting-create-form"
      data-access-edit-form
      data-form-unordered-fields="claims"
      data-member-access="${user.memberAccess}">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

    <section class="erp-panel admin-user-edit-panel admin-user-summary-panel"><div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="admin.workspace.userDetails" /></h2></div><div class="erp-panel-body"><dl class="erp-detail-facts">
<div><dt><spring:message code="admin.workspace.fullName" /></dt><dd><c:out value="${user.fullName}" /></dd></div>
<div><dt><spring:message code="admin.workspace.userId" /></dt><dd><c:out value="${user.userIdLabel}" /></dd></div>
<div><dt><spring:message code="voucher.print.clientNumber" /></dt><dd><c:out value="${user.memberNumber}" /></dd></div>
<div><dt><spring:message code="admin.workspace.staffNumber" /></dt><dd><c:out value="${user.staffMemberNumber}" /></dd></div>
<div><dt><spring:message code="admin.workspace.email" /></dt><dd><c:out value="${user.email}" /></dd></div>
<div><dt><spring:message code="admin.workspace.phone" /></dt><dd><c:out value="${user.phone}" /></dd></div>
<div><dt><spring:message code="admin.workspace.roles" /></dt><dd><c:out value="${user.roleSummary}" /></dd></div>
<div><dt><spring:message code="admin.workspace.loginId" /></dt><dd><c:out value="${user.loginId}" /></dd></div>
<div><dt><spring:message code="admin.workspace.accessType" /></dt><dd><c:out value="${user.membershipLabel}" /></dd></div><div><dt><spring:message code="common.status" /></dt><dd><span data-resource-status="${user.status}"><c:out value="${user.displayStatus}" /></span></dd></div><c:if test="${not empty user.acknowledgementStatus}"><div><dt><spring:message code="admin.workspace.acknowledgement" /></dt><dd><c:out value="${user.acknowledgementStatus}" /></dd></div></c:if></dl></div></section>

    <section id="user-access-roles" class="erp-panel admin-user-edit-panel">
        <div class="admin-user-section-header">
            <h2><spring:message code="admin.workspace.staffRoles" /></h2>
            <c:if test="${user.memberAccess}">
                <p><spring:message code="admin.workspace.clientOnly" /></p>
            </c:if>
        </div>
        <div class="admin-role-grid">
            <c:forEach items="${staffPositions}" var="position">
                <label class="admin-role-option admin-role-card">
                    <input type="checkbox"
                           name="positions"
                           value="${position}"
                           data-staff-role-checkbox="edit-user"
                           ${user.staffRoles.contains(position) ? 'checked' : ''}
                           class="admin-cloud-checkbox" />
                    <span class="admin-role-card-name"><spring:message code="admin.role.${position}" text="${position.displayName}" /></span>
                    <svg fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" class="admin-role-card-image" viewBox="0 0 120 100" aria-hidden="true" focusable="false"><use href="<c:url value='/images/staff-role-icons.svg' />#${position}" /></svg>
                </label>
            </c:forEach>
        </div>
    </section>

    <c:set var="matrixColumns" value="${['VIEW','ADD','UPDATE','DELETE','APPROVE','CONFIGURE','CREATE','VALIDATE','POST','INITIATE','WITHDRAW','HOLD','FORCE_APPROVE','ESCALATED_APPROVE','WAIVE','REJECT','ASSIGN','EXPORT','IMPORT','DISBURSE','REVERSE']}" />
    <section id="user-access-claims" class="erp-panel admin-user-edit-panel erp-table-wrap" data-resource-table="admin-user-claims-matrix" data-table-pagination-mode="external" data-table-page-size="10" data-aws-no-refresh="true" data-access-matrix>
        <div class="app-table-titlebar admin-user-section-header admin-user-section-header--actions">
            <h2><spring:message code="access.matrix.title" /> <span class="app-table-count" data-access-count></span></h2>
            <div class="app-table-toolbar admin-user-section-actions">
                <button type="button" class="admin-access-matrix-restore app-btn btn-neutral" data-restore-default-claims data-table-command><spring:message code="access.matrix.restore" /></button>
                <button type="button" class="app-btn btn-neutral" data-access-discard disabled><spring:message code="access.matrix.discard" /></button>
                <button type="submit" class="app-btn btn-neutral" data-access-save disabled><spring:message code="common.save" /></button>
            </div>
        </div>
        <div class="aws-filter-toolbar resource-table-search"><label><span class="sr-only"><spring:message code="access.matrix.search" /></span><span class="access-matrix-search"><svg viewBox="0 0 16 16" aria-hidden="true" focusable="false"><circle cx="6.5" cy="6.5" r="4.5" /><path d="m10 10 4 4" /></svg><input type="search" class="aws-control" data-access-search placeholder="<spring:message code='access.matrix.search' />" autocomplete="off" /></span></label></div>
        <div class="erp-table-scroll admin-claims-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="editor">
            <thead><tr><th scope="col" data-table-identifier data-access-sort="claim"><spring:message code="access.matrix.permissionClaim" /></th><c:forEach items="${matrixColumns}" var="column"><th scope="col" data-table-column-locked data-access-sort="${column}"><spring:message code="${column=='UPDATE'?'access.matrix.edit':'access.action.'.concat(column)}" /></th></c:forEach></tr></thead>
            <tbody><c:forEach items="${accessMatrixRows}" var="row"><spring:message code="access.feature.${row.feature}" text="${row.label}" var="matrixFeatureLabel" /><c:forEach items="${accessActions}" var="action"><c:set var="matrixClaim" value="${row.claimsByAction[action]}" /><c:if test="${not empty matrixClaim}">
                <spring:message code="access.action.${action}" text="${action.displayName}" var="matrixActionLabel" />
                <tr data-claim-row data-claim-key="${matrixClaim}">
                    <th scope="row"><span class="access-matrix-name"><c:out value="${matrixFeatureLabel}" /> — <c:out value="${matrixActionLabel}" /></span></th>
                    <c:forEach items="${matrixColumns}" var="column"><td data-claim-action="${column}"><c:choose><c:when test="${action.name()==column}"><input type="checkbox" name="claims" value="${matrixClaim}" data-access-claim="${matrixClaim}" data-access-row="${row.feature}" data-access-column="${action}" aria-label="<spring:message code='access.matrix.allow' /> <c:out value='${matrixFeatureLabel}' /> — <c:out value='${matrixActionLabel}' />" ${user.claims.contains(matrixClaim) ? 'checked' : ''} class="admin-cloud-checkbox" /></c:when><c:otherwise><span class="access-matrix-unavailable" aria-disabled="true"><span data-resource-status="STOPPED"><spring:message code="access.matrix.unavailable" /></span></span></c:otherwise></c:choose></td></c:forEach>
                </tr>
            </c:if></c:forEach></c:forEach></tbody>
        </table></div>
        <p data-access-empty hidden role="status"><spring:message code="access.matrix.empty" /></p>
    </section>

    <section id="user-access-status" class="erp-panel admin-user-edit-panel">
        <div class="admin-user-section-header">
            <h2><spring:message code="admin.workspace.accountStatus" /></h2>
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
                    onclick="return confirm('Cancel this invitation? The staff user will be marked inactive.');">
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
        <a href="/admin/users" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a>
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
<script type="application/json" id="Member-default-claims-json">${MemberDefaultClaimsJson}</script>

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
        const MemberDefaultClaims = readJson('Member-default-claims-json', []);

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
        function syncMatrixState(editForm) {
            editForm.querySelector('[data-access-matrix]')?.dispatchEvent(new Event('access-matrix:changed'));
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
                MemberDefaultClaims.forEach((claim) => defaults.add(claim));
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
<script src="<c:url value='/js/access-matrix.js' />" defer></script>

<%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
