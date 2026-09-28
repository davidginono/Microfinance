<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.users.breadcrumb" text="Admin Tools / Users &amp; Roles" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.users.title" text="Users &amp; Roles" /></h1>
</div>

<c:if test="${not empty createdStaffUserId}">
    <div class="admin-created-user-id mb-4" aria-live="polite">
        <p class="text-xs font-semibold uppercase tracking-wide">Created Staff User ID</p>
        <p class="mt-1 text-sm font-semibold"><c:out value="${createdStaffUserId}" /></p>
        <c:if test="${not empty createdStaffMemberNumber}">
            <p class="mt-1 text-xs">Staff Number <c:out value="${createdStaffMemberNumber}" /></p>
        </c:if>
    </div>
</c:if>

<c:set var="userTotal" value="${usersPage.totalElements}" />
<c:set var="userSliceStart" value="${userTotal == 0 ? 0 : (usersPage.number * usersPage.size) + 1}" />
<c:set var="userSliceEndRaw" value="${(usersPage.number * usersPage.size) + users.size()}" />
<c:set var="userSliceEnd" value="${userTotal == 0 ? 0 : userSliceEndRaw}" />
<section class="erp-table-wrap admin-register-shell admin-users-register" aria-label="Users and roles results">
    <div class="app-table-titlebar">
        <div class="app-table-heading">
            <h2>Users &amp; Roles results</h2>
        </div>
        <div class="app-table-toolbar">
            <span class="admin-register-count">Showing ${userSliceStart}-${userSliceEnd} of ${userTotal}</span>
            <button type="button" class="app-btn btn-launch" data-user-modal-open="create-user"><spring:message code="admin.users.addUser" text="Add Staff Member" /></button>
        </div>
    </div>

    <form method="get" action="/admin/users" class="admin-filter-form aws-filter-toolbar admin-users-filter-form" data-aws-filter-toolbar>
        <label class="admin-users-filter-field admin-users-filter-field--type">
            <span class="sr-only">Search By</span>
            <select name="searchBy" class="fcms-control w-full" aria-label="Search by">
                <option value="userId" ${selectedUserSearchBy eq 'userId' ? 'selected' : ''}>User ID</option>
                <option value="name" ${selectedUserSearchBy eq 'name' ? 'selected' : ''}>Name</option>
            </select>
        </label>

        <label class="admin-users-filter-field admin-users-filter-field--query">
            <span class="sr-only">Search User</span>
            <input type="search"
                   name="query"
                   value="${selectedUserQuery}"
                   class="fcms-control w-full"
                   placeholder="Enter selected User ID or name"
                   title="Enter selected User ID or name" />
        </label>

        <label class="admin-users-filter-field admin-users-filter-field--rows">
            <span class="sr-only">Rows Per Page</span>
            <select name="size" class="fcms-control w-full" aria-label="Rows per page">
                <option value="25" ${selectedPageSize == 25 ? 'selected' : ''}>25 rows</option>
                <option value="50" ${selectedPageSize == 50 ? 'selected' : ''}>50 rows</option>
                <option value="100" ${selectedPageSize == 100 ? 'selected' : ''}>100 rows</option>
            </select>
        </label>

        <div class="admin-users-filter-actions">
            <button type="submit" class="app-btn btn-primary">Search</button>
            <a href="/admin/users" class="app-btn btn-neutral">Reset</a>
        </div>
    </form>
    <div class="erp-table-scroll" data-aws-table-region data-loading-label="Loading results...">
    <table class="erp-table min-w-[1420px]">
        <thead>
        <tr>
            <th class="px-3 py-2 text-left whitespace-nowrap">User ID</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Client Number</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Staff Number</th>
            <th class="px-3 py-2 text-left whitespace-nowrap">Names</th>
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
                                No users matched the selected search for <span class="font-semibold text-slate-700"><c:out value="${selectedUserQuery}" /></span>. Adjust the search and try again.
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
                        <td class="px-3 py-2 align-top whitespace-nowrap font-medium text-slate-700"><c:out value="${user.userIdLabel}" /></td>
                        <td class="px-3 py-2 align-top whitespace-nowrap"><c:out value="${user.memberNumber}" /></td>
                        <td class="px-3 py-2 align-top whitespace-nowrap"><c:out value="${user.staffMemberNumber}" /></td>
                        <td class="px-3 py-2 align-top">
                            <div class="font-semibold text-slate-900"><c:out value="${user.fullName}" /></div>
                        </td>
                        <td class="px-3 py-2 align-top whitespace-nowrap"><c:out value="${user.email}" /></td>
                        <td class="px-3 py-2 align-top whitespace-nowrap"><c:out value="${user.phone}" /></td>
                        <td class="px-3 py-2 align-top whitespace-nowrap"><c:out value="${user.roleSummary}" /></td>
                        <td class="px-3 py-2 align-top whitespace-nowrap">
                            <span class="inline-flex rounded border px-2 py-1 text-xs font-semibold ${user.membershipLabel eq 'Staff' ? 'border-slate-200 bg-slate-50 text-slate-600' : 'border-cyan-200 bg-cyan-50 text-cyan-800'}">
                                <c:out value="${user.membershipLabel}" />
                            </span>
                        </td>
                        <td class="px-3 py-2 align-top whitespace-nowrap">
                            <span class="inline-flex rounded border px-2 py-1 text-xs font-semibold ${user.status eq 'INVITED' ? 'border-amber-200 bg-amber-50 text-amber-700' : user.status eq 'ACTIVE' ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-slate-200 bg-slate-50 text-slate-600'}">
                                <c:out value="${user.displayStatus}" />
                            </span>
                            <c:if test="${not empty user.acknowledgementStatus}">
                                <div class="text-xs mt-1"><c:out value="${user.acknowledgementStatus}" /></div>
                            </c:if>
                        </td>
                        <td class="px-3 py-2 align-top whitespace-nowrap">
                            <c:choose>
                                <c:when test="${canEditUsers}">
                                    <a href="/admin/users/${user.accountId}/edit" class="app-btn btn-primary">Edit</a>
                                </c:when>
                                <c:otherwise>
                                    <span class="app-btn btn-neutral pointer-events-none opacity-50">View Only</span>
                                </c:otherwise>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
            </c:otherwise>
        </c:choose>
        </tbody>
    </table>
    </div>
</section>

<div class="erp-toolbar">
    <div class="text-sm text-slate-500">
        Server-paged list.
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

<div class="app-modal-overlay hidden" data-user-modal="create-user">
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
                    Full Name
                    <input name="fullName" type="text" required autocapitalize="characters" spellcheck="false" data-uppercase-input class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm uppercase text-slate-800" />
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
                    <button type="submit" class="app-btn btn-launch">Create Staff Member</button>
                </div>
            </form>
        </div>
    </div>
</div>

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
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
