<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/modal-shell.jspf" %>
<style>
    .admin-user-edit-panel {
        width: min(100%, 48rem);
    }

    .admin-claim-option {
        min-width: 0;
        align-items: center;
    }

    .admin-claim-text {
        display: block;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 0.76rem;
        line-height: 1.25rem;
    }

    .admin-claim-note {
        margin-left: 0.35rem;
        font-size: 0.68rem;
        font-weight: 500;
        color: #94a3b8;
    }

    @media (min-width: 768px) {
        .admin-claim-text {
            font-size: 0.82rem;
        }
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="admin.users.breadcrumb" text="Admin Tools / Users & Roles" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.users.title" text="Users & Roles" /></h1>
    <p class="erp-page-subtitle"><spring:message code="admin.users.subtitle" text="Manage access and roles for the current workspace station." /></p>
</div>

<div class="erp-toolbar">
    <div class="text-sm text-slate-500"><spring:message code="admin.users.addUserHelp" text="Add Staff Member is for staff accounts only." /></div>
    <button type="button" class="app-btn btn-primary" data-user-modal-open="create-user"><spring:message code="admin.users.addUser" text="Add Staff Member" /></button>
</div>

<div class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div class="max-w-2xl">
                <p class="erp-widget-title"><spring:message code="common.filterViewOptions" text="Filter And View Options" /></p>
                <p class="mt-2 text-sm leading-6 text-slate-500"><spring:message code="admin.users.filterHelp" text="Search users by User ID or name and page the results." /></p>
            </div>
            <div class="rounded-lg border border-cyan-100 bg-cyan-50 px-4 py-3 text-sm text-slate-700">
                <div class="text-xs font-semibold uppercase tracking-[0.22em] text-sacco-blue">Current Slice</div>
                <c:set var="userTotal" value="${usersPage.totalElements}" />
                <c:set var="userSliceStart" value="${userTotal == 0 ? 0 : (usersPage.number * usersPage.size) + 1}" />
                <c:set var="userSliceEndRaw" value="${(usersPage.number * usersPage.size) + users.size()}" />
                <c:set var="userSliceEnd" value="${userTotal == 0 ? 0 : userSliceEndRaw}" />
                <p class="mt-2 text-base font-medium text-slate-700">Showing <span class="font-semibold text-sacco-ink">${userSliceStart}-${userSliceEnd}</span> of <span class="font-semibold text-sacco-ink">${userTotal}</span> users</p>
            </div>
        </div>
    </div>

    <form method="get" action="/admin/users" class="admin-filter-form relative flex flex-row flex-wrap items-end gap-3 px-5 py-5 sm:px-6">
        <label class="block min-w-[10rem] flex-[0_1_12rem]">
            <span class="block text-xs font-semibold uppercase tracking-[0.22em] text-slate-500">Search By</span>
            <select name="searchBy" class="mt-2 w-full rounded border border-slate-300 px-3 text-sm text-slate-800" style="height:3rem;min-height:3rem;max-height:3rem;">
                <option value="userId" ${selectedUserSearchBy eq 'userId' ? 'selected' : ''}>User ID</option>
                <option value="name" ${selectedUserSearchBy eq 'name' ? 'selected' : ''}>Name</option>
            </select>
        </label>

        <label class="block min-w-[16rem] flex-[1_1_20rem]">
            <span class="block text-xs font-semibold uppercase tracking-[0.22em] text-slate-500">Search User</span>
            <input type="text"
                   name="query"
                   value="${selectedUserQuery}"
                   class="mt-2 w-full rounded border border-slate-300 px-3 text-sm text-slate-800"
                   style="height:3rem;min-height:3rem;max-height:3rem;"
                   placeholder="Enter selected User ID or name" />
        </label>

        <label class="block min-w-[10rem] flex-[0_1_12rem]">
            <span class="block text-xs font-semibold uppercase tracking-[0.22em] text-slate-500">Rows Per Page</span>
            <select name="size" class="mt-2 w-full rounded border border-slate-300 px-3 text-sm text-slate-800" style="height:3rem;min-height:3rem;max-height:3rem;">
                <option value="25" ${selectedPageSize == 25 ? 'selected' : ''}>25 rows</option>
                <option value="50" ${selectedPageSize == 50 ? 'selected' : ''}>50 rows</option>
                <option value="100" ${selectedPageSize == 100 ? 'selected' : ''}>100 rows</option>
            </select>
        </label>

        <div class="flex shrink-0 flex-wrap items-end gap-3">
            <button type="submit" class="app-btn btn-primary" style="height:3rem;min-height:3rem;">Search</button>
            <a href="/admin/users" class="app-btn btn-neutral" style="height:3rem;min-height:3rem;">Reset</a>
        </div>
    </form>
</div>

<div class="erp-table-wrap erp-table-scroll">
    <table class="erp-table min-w-[1420px]">
        <thead>
        <tr>
            <th class="px-3 py-2 text-left whitespace-nowrap">User</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">User ID</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Member Number</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Staff Member Number</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Email</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Phone Number</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Current Roles</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Membership</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Current Status</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Update</th>
        </tr>
        </thead>
        <tbody>
        <c:choose>
            <c:when test="${empty users}">
                <tr>
                    <td colspan="10" class="px-4 py-5 text-sm text-slate-500">
                        <c:choose>
                            <c:when test="${not empty selectedUserQuery}">
                                No users matched the selected search for <span class="font-semibold text-slate-700">${selectedUserQuery}</span>. Adjust the search and try again.
                            </c:when>
                            <c:otherwise>
                                No users are available in the current slice.
                            </c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:when>
            <c:otherwise>
                <c:forEach items="${users}" var="user">
                    <tr>
                        <td class="px-3 py-2 align-top">
                            <div class="font-semibold text-slate-900">${user.fullName}</div>
                        </td>
                        <td class="px-3 py-2 align-top whitespace-nowrap font-medium text-slate-700">${user.userIdLabel}</td>
                        <td class="px-3 py-2 align-top whitespace-nowrap">${user.memberNumber}</td>
                        <td class="px-3 py-2 align-top whitespace-nowrap">${user.staffMemberNumber}</td>
                        <td class="px-3 py-2 align-top whitespace-nowrap">${user.email}</td>
                        <td class="px-3 py-2 align-top whitespace-nowrap">${user.phone}</td>
                        <td class="px-3 py-2 align-top whitespace-nowrap">${user.roleSummary}</td>
                        <td class="px-3 py-2 align-top whitespace-nowrap">
                            <span class="inline-flex rounded border px-2 py-1 text-xs font-semibold ${user.membershipLabel eq 'Staff' ? 'border-slate-200 bg-slate-50 text-slate-600' : 'border-cyan-200 bg-cyan-50 text-cyan-800'}">
                                ${user.membershipLabel}
                            </span>
                        </td>
                        <td class="px-3 py-2 align-top whitespace-nowrap">
                            <span class="inline-flex rounded border px-2 py-1 text-xs font-semibold ${user.status eq 'INVITED' ? 'border-amber-200 bg-amber-50 text-amber-700' : user.status eq 'ACTIVE' ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-slate-50 text-slate-600'}">
                                ${user.displayStatus}
                            </span>
                        </td>
                        <td class="px-3 py-2 align-top whitespace-nowrap">
                            <button type="button"
                                    class="app-btn btn-primary"
                                    data-user-modal-open="user-${user.accountId}">
                                Edit
                            </button>
                        </td>
                    </tr>
                </c:forEach>
            </c:otherwise>
        </c:choose>
        </tbody>
    </table>
</div>

<div class="erp-toolbar">
    <div class="text-sm text-slate-500">
        The table is server-paged to keep large user lists manageable.
    </div>
    <c:if test="${usersPage.totalPages > 1}">
        <div class="flex flex-wrap items-center justify-end gap-2">
            <c:set var="userPrevPage" value="${usersPage.number - 1}" />
            <c:set var="userNextPage" value="${usersPage.number + 1}" />
            <c:choose>
                <c:when test="${usersPage.first}">
                    <span class="app-btn btn-neutral pointer-events-none opacity-50">Previous</span>
                </c:when>
                <c:otherwise>
                    <a href="/admin/users?page=${userPrevPage}${usersPaginationQuery}" class="app-btn btn-neutral">Previous</a>
                </c:otherwise>
            </c:choose>

            <span class="rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-600">
                Page ${usersPage.number + 1} of ${usersPage.totalPages}
            </span>

            <c:choose>
                <c:when test="${usersPage.last}">
                    <span class="app-btn btn-neutral pointer-events-none opacity-50">Next</span>
                </c:when>
                <c:otherwise>
                    <a href="/admin/users?page=${userNextPage}${usersPaginationQuery}" class="app-btn btn-neutral">Next</a>
                </c:otherwise>
            </c:choose>
        </div>
    </c:if>
</div>

<div class="app-modal-overlay hidden"
     data-user-modal="create-user">
    <div class="app-modal-panel app-modal-panel--compact">
        <div class="app-modal-scroll">
        <div class="app-modal-header">
            <div>
                <p class="erp-widget-title">Add Staff Member</p>
                <h2 class="mt-1 text-xl font-bold text-sacco-ink">Create Staff Member</h2>
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
                <input type="text" readonly aria-readonly="true" value="${nextGeneratedUserId}" class="mt-1 w-full rounded border border-slate-300 bg-slate-50 px-3 py-2 text-sm font-semibold text-slate-600" />
            </label>

            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                Full Name
                <input name="fullName" type="text" required autocapitalize="characters" spellcheck="false" oninput="this.value = this.value.toUpperCase();" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm uppercase text-slate-800" />
            </label>

            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                Email
                <input name="email" type="email" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" />
            </label>

            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                Phone
                <input name="phone" type="tel" inputmode="numeric" pattern="255[0-9]{9}" minlength="12" maxlength="12" required placeholder="255712345678" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" />
                <span class="mt-1 block text-[11px] font-normal normal-case tracking-normal text-slate-500"><spring:message code="admin.minorAdmins.phoneHelp" text="Use Tanzania format: 255 followed by 9 digits, for example 255746359369. Do not start with 0." /></span>
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
                                <span>${position.displayName}</span>
                            </label>
                        </c:forEach>
                    </div>
                </div>

            <div class="app-modal-actions md:col-span-2">
                <button type="button" class="app-btn btn-neutral" data-user-modal-close="create-user">Cancel</button>
                <button type="submit" class="app-btn btn-primary">Create Staff Member</button>
            </div>
        </form>
        </div>
    </div>
</div>

<c:forEach items="${users}" var="user">
    <div class="app-modal-overlay hidden"
         data-user-modal="user-${user.accountId}">
        <div class="app-modal-panel admin-user-edit-panel">
            <div class="app-modal-scroll">
            <div class="app-modal-header">
                <div>
                    <p class="erp-widget-title">Edit User</p>
                    <h2 class="mt-1 text-xl font-bold text-sacco-ink">${user.fullName}</h2>
                    <p class="mt-1 text-sm text-slate-500">User ID ${user.userIdLabel}</p>
                    <c:choose>
                        <c:when test="${user.memberAccess}">
                            <p class="mt-1 text-xs text-slate-500">Member Number ${user.memberNumber}</p>
                        </c:when>
                        <c:otherwise>
                            <p class="mt-1 text-xs text-slate-500">Staff Member Number ${user.staffMemberNumber}</p>
                        </c:otherwise>
                    </c:choose>
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
                                <span>${position.displayName}</span>
                            </label>
                        </c:forEach>
                    </div>
                </div>

                <div>
                    <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Claims</p>
                    <div class="mt-2 grid gap-2 sm:grid-cols-2">
                        <c:forEach items="${availableClaims}" var="claim">
                            <c:set var="memberOnlyClaimDisabled" value="${(claim eq 'APPLY_LOANS' or claim eq 'APPROVE_GUARANTOR_REQUESTS') and not user.memberAccess}" />
                            <label class="admin-claim-option flex gap-3 overflow-hidden rounded border border-slate-200 bg-slate-50 px-3 py-2 font-medium ${memberOnlyClaimDisabled ? 'text-slate-400' : 'text-slate-700'}">
                                <input type="checkbox"
                                       name="claims"
                                       value="${claim}"
                                       ${user.claims.contains(claim) ? 'checked' : ''}
                                       ${memberOnlyClaimDisabled ? 'disabled' : ''}
                                       class="h-4 w-4 flex-shrink-0 rounded border-slate-300 text-sacco-blue focus:ring-sacco-blue" />
                                <span class="admin-claim-text flex-1" title="${claim}">
                                    ${claim}
                                    <c:if test="${memberOnlyClaimDisabled}">
                                        <span class="admin-claim-note">Members only</span>
                                    </c:if>
                                </span>
                            </label>
                        </c:forEach>
                    </div>
                </div>

                <c:choose>
                    <c:when test="${user.status eq 'INVITED'}">
                        <input type="hidden" name="status" value="INVITED" />
                        <div class="rounded-md border border-amber-200 bg-amber-50 px-3 py-3">
                            <p class="text-xs font-semibold uppercase tracking-wide text-amber-700">Status</p>
                            <p class="mt-1 text-sm font-semibold text-amber-800">${user.displayStatus}</p>
                            <p class="mt-1 text-xs leading-5 text-amber-700">This account becomes active only after the invite form is completed.</p>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
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

        function syncSuperAdminRoleGroup(groupKey) {
            const checkboxes = Array.from(document.querySelectorAll('[data-staff-role-checkbox="' + groupKey + '"]'));
            if (!checkboxes.length) {
                return;
            }
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

        const defaultClaimsByRole = {
            "MANAGER": ["REVIEW_MANAGER_QUEUE"],
            "ACCOUNTANT": ["REVIEW_ACCOUNTANT_QUEUE"],
            "DISBURSEMENT_OFFICER": ["ACCESS_DISBURSEMENT_QUEUE", "DISBURSE_LOAN"],
            "BOARD": ["REVIEW_BOARD_QUEUE"],
            "CHAIRPERSON": ["REVIEW_CHAIRPERSON_QUEUE"],
            "CREDIT_COMMITTEE": ["REVIEW_CREDIT_COMMITTEE_QUEUE"],
            "LOAN_OFFICER": ["REVIEW_LOAN_OFFICER_QUEUE"],
            "ADMIN": ["ACCESS_ADMIN_SETTINGS", "ACCESS_OUTBOX_MONITOR"],
            "MINOR_ADMIN": ["ACCESS_ADMIN_SETTINGS", "ACCESS_OUTBOX_MONITOR"]
        };

        function claimInput(form, claim) {
            return form.querySelector('input[name="claims"][value="' + claim + '"]');
        }

        function checkClaim(form, claim) {
            const input = claimInput(form, claim);
            if (input && !input.disabled) {
                input.checked = true;
            }
        }

        function uncheckClaim(form, claim) {
            const input = claimInput(form, claim);
            if (input && !input.disabled) {
                input.checked = false;
            }
        }

        function checkedRolesForForm(form) {
            return Array.from(form.querySelectorAll('[data-staff-role-checkbox]:checked')).map((input) => input.value);
        }

        function otherCheckedRoleNeedsClaim(form, uncheckedRole, claim) {
            return checkedRolesForForm(form)
                .filter((role) => role !== uncheckedRole)
                .some((role) => (defaultClaimsByRole[role] || []).includes(claim));
        }

        function applyDefaultClaimsForRole(roleCheckbox) {
            const form = roleCheckbox.closest('form');
            if (!form) {
                return;
            }
            (defaultClaimsByRole[roleCheckbox.value] || []).forEach((claim) => {
                if (roleCheckbox.checked) {
                    checkClaim(form, claim);
                } else if (!otherCheckedRoleNeedsClaim(form, roleCheckbox.value, claim)) {
                    uncheckClaim(form, claim);
                }
            });
        }

        const initializedRoleGroups = new Set();
        document.querySelectorAll('[data-staff-role-checkbox]').forEach((checkbox) => {
            const groupKey = checkbox.getAttribute('data-staff-role-checkbox');
            checkbox.addEventListener('change', () => {
                syncSuperAdminRoleGroup(groupKey);
                applyDefaultClaimsForRole(checkbox);
            });
            if (!initializedRoleGroups.has(groupKey)) {
                initializedRoleGroups.add(groupKey);
                syncSuperAdminRoleGroup(groupKey);
            }
        });

        document.querySelectorAll('form').forEach((form) => {
            const disburseLoan = claimInput(form, 'DISBURSE_LOAN');
            const accessQueue = claimInput(form, 'ACCESS_DISBURSEMENT_QUEUE');
            if (!disburseLoan || !accessQueue) {
                return;
            }
            function setDisbursementPair(checked) {
                if (!accessQueue.disabled) {
                    accessQueue.checked = checked;
                }
                if (!disburseLoan.disabled) {
                    disburseLoan.checked = checked;
                }
            }
            function ensureDisbursementSubmitPair() {
                if (disburseLoan.checked && !accessQueue.disabled) {
                    accessQueue.checked = true;
                }
            }
            disburseLoan.addEventListener('change', () => setDisbursementPair(disburseLoan.checked));
            accessQueue.addEventListener('change', () => setDisbursementPair(accessQueue.checked));
            form.addEventListener('submit', ensureDisbursementSubmitPair);
            ensureDisbursementSubmitPair();
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
