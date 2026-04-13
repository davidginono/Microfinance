<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/modal-shell.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Users & Roles</p>
    <h1 class="erp-page-title">Users & Roles</h1>
    <p class="erp-page-subtitle">Manage member, staff, and staff-and-member access cleanly while keeping role access aligned with each account type.</p>
</div>

<div class="erp-toolbar">
    <div class="text-sm text-slate-500">Use Add User for staff accounts. Members should still come through the registration flow first.</div>
    <button type="button" class="app-btn btn-primary" data-user-modal-open="create-user">Add User</button>
</div>

<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table min-w-[980px]">
        <thead>
        <tr>
            <th class="px-3 py-2 text-left whitespace-nowrap">User</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Email</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Current Roles</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Membership</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Current Status</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Update</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${users}" var="user">
            <tr>
                <td class="px-3 py-2 align-top">
                    <div class="font-semibold text-slate-900">${user.fullName}</div>
                    <div class="text-xs text-slate-500">${user.loginId}</div>
                </td>
                <td class="px-3 py-2 align-top whitespace-nowrap">${user.email}</td>
                <td class="px-3 py-2 align-top whitespace-nowrap">${user.roleSummary}</td>
                <td class="px-3 py-2 align-top whitespace-nowrap">
                    <span class="inline-flex rounded border px-2 py-1 text-xs font-semibold ${user.membershipLabel eq 'Staff' ? 'border-slate-200 bg-slate-50 text-slate-600' : 'border-cyan-200 bg-cyan-50 text-cyan-800'}">
                        ${user.membershipLabel}
                    </span>
                </td>
                <td class="px-3 py-2 align-top whitespace-nowrap">${user.status}</td>
                <td class="px-3 py-2 align-top whitespace-nowrap">
                    <button type="button"
                            class="app-btn btn-primary"
                            data-user-modal-open="user-${user.accountId}">
                        Edit
                    </button>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>

<div class="app-modal-overlay hidden"
     data-user-modal="create-user">
    <div class="app-modal-panel app-modal-panel--compact">
        <div class="app-modal-scroll">
        <div class="app-modal-header">
            <div>
                <p class="erp-widget-title">Add User</p>
                <h2 class="mt-1 text-xl font-bold text-sacco-ink">Create Staff User</h2>
            </div>
            <button type="button" class="app-modal-close" data-user-modal-close="create-user" aria-label="Close modal">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                </svg>
            </button>
        </div>

        <form action="/admin/users" method="post" class="app-modal-body grid gap-4 md:grid-cols-2">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                User ID
                <input name="memberNo" type="text" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="e.g. MGR004 or STAFF001" />
            </label>

            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                Full Name
                <input name="fullName" type="text" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" />
            </label>

            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                Email
                <input name="email" type="email" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" />
            </label>

            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                Phone
                <input name="phone" type="tel" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" />
            </label>

            <div class="md:col-span-2">
                <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Staff Roles</p>
                    <div class="mt-2 grid gap-2 sm:grid-cols-2">
                        <c:forEach items="${staffPositions}" var="position">
                            <label class="flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700">
                                <input type="checkbox"
                                       name="positions"
                                       value="${position}"
                                       data-staff-role-checkbox="create-user"
                                       class="h-4 w-4 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue" />
                                <span>${position}</span>
                            </label>
                        </c:forEach>
                    </div>
                </div>

            <div class="app-modal-section text-sm text-slate-600 md:col-span-2">
                New users added here are <strong>staff</strong> by default. Admin accounts must remain <strong>ADMIN</strong> only. Other staff accounts can hold one or more non-admin staff roles.
            </div>

            <div class="app-modal-actions md:col-span-2">
                <button type="button" class="app-btn btn-neutral" data-user-modal-close="create-user">Cancel</button>
                <button type="submit" class="app-btn btn-primary">Create User</button>
            </div>
        </form>
        </div>
    </div>
</div>

<c:forEach items="${users}" var="user">
    <div class="app-modal-overlay hidden"
         data-user-modal="user-${user.accountId}">
        <div class="app-modal-panel app-modal-panel--compact">
            <div class="app-modal-scroll">
            <div class="app-modal-header">
                <div>
                    <p class="erp-widget-title">Edit User</p>
                    <h2 class="mt-1 text-xl font-bold text-sacco-ink">${user.fullName}</h2>
                    <p class="mt-1 text-sm text-slate-500">${user.loginId}</p>
                </div>
                <button type="button" class="app-modal-close" data-user-modal-close="user-${user.accountId}" aria-label="Close modal">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                        <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                    </svg>
                </button>
            </div>

            <form id="user-form-${user.accountId}" action="/admin/users/${user.accountId}" method="post" class="app-modal-body space-y-4">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <div>
                    <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Staff Roles</p>
                    <div class="mt-2 grid gap-2 sm:grid-cols-2">
                        <c:forEach items="${staffPositions}" var="position">
                            <label class="flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700">
                                <input type="checkbox"
                                       name="positions"
                                       value="${position}"
                                       data-staff-role-checkbox="user-${user.accountId}"
                                       ${user.staffRoles.contains(position) ? 'checked' : ''}
                                       class="h-4 w-4 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue" />
                                <span>${position}</span>
                            </label>
                        </c:forEach>
                    </div>
                </div>

                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Status
                    <select name="status" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                        <c:forEach items="${statuses}" var="status">
                            <option value="${status}" ${user.status eq status ? 'selected' : ''}>${status}</option>
                        </c:forEach>
                    </select>
                </label>

                <div class="app-modal-section text-sm text-slate-600">
                    <c:choose>
                        <c:when test="${user.staffRoles.contains('ADMIN') || user.roleSummary eq 'ADMIN'}">
                            This is an <strong>Admin</strong> account. It must remain <strong>ADMIN</strong> only and cannot be combined with any other staff role.
                        </c:when>
                        <c:when test="${user.membershipLabel ne 'Staff'}">
                            This account remains a registered member. Leave all staff roles unticked to keep it as <strong>Member</strong> only, or tick one or more staff roles to make it <strong>Staff And Member</strong>.
                        </c:when>
                        <c:otherwise>
                            This is a staff account. Select every staff role this user should be allowed to use while keeping the account as <strong>Staff</strong> only.
                        </c:otherwise>
                    </c:choose>
                </div>

                <div class="app-modal-actions">
                    <button type="button"
                            class="app-btn btn-neutral"
                            data-user-modal-close="user-${user.accountId}">
                        Cancel
                    </button>
                    <button type="submit" class="app-btn btn-primary">Save</button>
                </div>
            </form>
            </div>
        </div>
    </div>
</c:forEach>

<script>
    (() => {
        const body = document.body;

        document.querySelectorAll('[data-user-modal]').forEach((modal) => {
            modal.style.position = 'fixed';
            modal.style.inset = '0';
            modal.style.zIndex = '90';
            document.body.appendChild(modal);
        });

        function syncAdminOnlyRoleGroup(groupKey) {
            const checkboxes = Array.from(document.querySelectorAll('[data-staff-role-checkbox="' + groupKey + '"]'));
            if (!checkboxes.length) {
                return;
            }
            const adminCheckbox = checkboxes.find((checkbox) => checkbox.value === 'ADMIN');
            if (!adminCheckbox) {
                return;
            }
            const adminChecked = adminCheckbox.checked;
            checkboxes.forEach((checkbox) => {
                if (checkbox === adminCheckbox) {
                    return;
                }
                if (adminChecked) {
                    checkbox.checked = false;
                }
                checkbox.disabled = adminChecked;
            });
        }

        const initializedRoleGroups = new Set();
        document.querySelectorAll('[data-staff-role-checkbox]').forEach((checkbox) => {
            const groupKey = checkbox.getAttribute('data-staff-role-checkbox');
            checkbox.addEventListener('change', () => syncAdminOnlyRoleGroup(groupKey));
            if (!initializedRoleGroups.has(groupKey)) {
                initializedRoleGroups.add(groupKey);
                syncAdminOnlyRoleGroup(groupKey);
            }
        });

        function closeAllUserModals() {
            document.querySelectorAll('[data-user-modal]').forEach((modal) => {
                modal.classList.add('hidden');
                modal.classList.remove('is-open');
            });
            body.classList.remove('overflow-hidden');
        }

        document.querySelectorAll('[data-user-modal-open]').forEach((button) => {
            button.addEventListener('click', () => {
                const key = button.getAttribute('data-user-modal-open');
                closeAllUserModals();
                const modal = document.querySelector('[data-user-modal="' + key + '"]');
                if (modal) {
                    modal.classList.remove('hidden');
                    modal.classList.add('is-open');
                    body.classList.add('overflow-hidden');
                }
            });
        });

        document.querySelectorAll('[data-user-modal-close]').forEach((button) => {
            button.addEventListener('click', closeAllUserModals);
        });

        document.querySelectorAll('[data-user-modal]').forEach((modal) => {
            modal.addEventListener('click', (event) => {
                if (event.target === modal) {
                    closeAllUserModals();
                }
            });
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
