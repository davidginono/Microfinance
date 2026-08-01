<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.minorAdmins.breadcrumb" text="Admin Tools / SACCO Registration / SACCOS Admins Registration" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.minorAdmins.title" text="SACCOS Admins Registration" /></h1>
    <p class="erp-page-subtitle"><spring:message code="admin.minorAdmins.subtitle" text="Register SACCOS Admin accounts under the correct station." /></p>
</div>

<c:if test="${not empty createdMinorAdminStaffNumber}">
    <div class="mb-4 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-emerald-800" aria-live="polite">
        <p class="text-xs font-semibold uppercase tracking-wide">Created SACCOS Admin Staff Number</p>
        <p class="mt-1 text-sm font-semibold">${createdMinorAdminStaffNumber}</p>
    </div>
</c:if>

<c:choose>
    <c:when test="${empty registeredSaccos}">
        <div class="erp-panel overflow-hidden">
            <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6">
                <p class="erp-panel-title"><spring:message code="admin.minorAdmins.registerSaccoFirst" text="Register A SACCO First" /></p>
            </div>
            <div class="erp-panel-body space-y-4">
                <p class="text-sm text-slate-600"><spring:message code="admin.minorAdmins.registerSaccoFirstHelp" text="SACCOS Admin accounts need a SACCO and station assignment. Register the SACCO from the sidebar first, then return here to register the account." /></p>
            </div>
        </div>
    </c:when>
    <c:otherwise>
        <div class="erp-panel overflow-hidden">
            <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6">
                <div>
                    <p class="erp-widget-title"><spring:message code="admin.minorAdmins.registrationForm" text="Registration Form" /></p>
                    <h2 class="mt-1 text-xl font-bold text-sacco-ink"><spring:message code="admin.minorAdmins.create" text="Create SACCOS Admin" /></h2>
                    <p class="mt-2 text-sm text-slate-500"><spring:message code="admin.minorAdmins.createHelp" text="Assign the account to one SACCO and one active station. They will receive an email to set their password." /></p>
                </div>
            </div>

            <form action="/admin/saccos/minor-admins" method="post" class="erp-panel-body grid gap-4 md:grid-cols-2 xl:grid-cols-3" data-minor-admin-form>
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <spring:message code="common.sacco" text="SACCO" />
                    <select id="minorAdminSaccoSelect" name="saccoId" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                        <c:forEach items="${registeredSaccos}" var="sacco" varStatus="status">
                            <c:set var="stationList" value="" />
                            <c:forEach items="${sacco.stationIds}" var="stationId" varStatus="stationStatus">
                                <c:set var="stationList" value="${stationList}${stationStatus.first ? '' : ','}${stationId}" />
                            </c:forEach>
                            <option value="${sacco.saccoId}"
                                    data-stations="${stationList}"
                                    ${status.first ? 'selected' : ''}>
                                ${sacco.saccoId} - ${sacco.saccoName}
                            </option>
                        </c:forEach>
                    </select>
                </label>

                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <spring:message code="admin.saccoRegistry.station" text="Station" />
                    <select id="minorAdminStationSelect" name="stationId" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800"></select>
                </label>

                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <spring:message code="member.fullName" text="Full Name" />
                    <input name="fullName" type="text" required autocapitalize="characters" spellcheck="false" data-uppercase-input class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm uppercase text-slate-800" />
                </label>

                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <spring:message code="register.member.email" text="Email" />
                    <input name="email" type="email" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" />
                </label>

                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    <spring:message code="register.member.phone" text="Phone" />
                    <input name="phone" type="tel" inputmode="numeric" pattern="255[0-9]{9}" minlength="12" maxlength="12" placeholder="255712345678" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" />
                    <span class="mt-1 block text-[11px] font-medium normal-case tracking-normal text-slate-500"><spring:message code="admin.minorAdmins.phoneHelp" text="Use Tanzania format: 255 followed by 9 digits, for example 255746359369. Do not start with 0." /></span>
                </label>

                <div class="md:col-span-2 xl:col-span-3 flex flex-wrap items-center justify-end gap-3">
                    <button type="submit" class="app-btn btn-launch"><spring:message code="admin.minorAdmins.register" text="Register SACCOS Admin" /></button>
                </div>
            </form>
        </div>
    </c:otherwise>
</c:choose>

<div class="erp-panel overflow-hidden">
    <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6">
        <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            <div>
                <p class="erp-panel-title"><spring:message code="admin.minorAdmins.registered" text="Registered SACCOS Admins" /></p>
                <p class="mt-1 text-sm text-slate-500"><spring:message code="admin.minorAdmins.manageHelp" text="Use Users &amp; Roles for status changes or broader role updates." /></p>
            </div>
            <a href="/admin/users" class="app-btn btn-neutral"><spring:message code="admin.minorAdmins.manageUsers" text="Manage In Users &amp; Roles" /></a>
        </div>
    </div>

<div class="erp-table-wrap overflow-x-auto" data-aws-table-region data-loading-label="Loading results...">
        <table class="erp-table min-w-[1120px]">
            <thead>
            <tr>
                <th><spring:message code="admin.users.user" text="User" /></th>
                <th><spring:message code="register.member.email" text="Email" /></th>
                <th><spring:message code="register.member.phone" text="Phone" /></th>
                <th><spring:message code="common.sacco" text="SACCO" /></th>
                <th><spring:message code="admin.saccoRegistry.station" text="Station" /></th>
                <th><spring:message code="admin.minorAdmins.activation" text="Activation" /></th>
                <th class="text-right"><spring:message code="common.actions" text="Actions" /></th>
            </tr>
            </thead>
            <tbody>
            <c:choose>
                <c:when test="${empty minorAdmins}">
                    <tr>
                        <td colspan="7" class="text-slate-500"><spring:message code="admin.minorAdmins.empty" text="No SACCOS Admins have been registered yet." /></td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <c:forEach items="${minorAdmins}" var="minorAdmin">
                        <tr>
                            <td class="align-top">
                                <div class="font-semibold text-slate-900">${minorAdmin.fullName}</div>
                                <div class="text-xs text-slate-500">${minorAdmin.loginId}</div>
                            </td>
                            <td class="align-top whitespace-nowrap">${minorAdmin.email}</td>
                            <td class="align-top whitespace-nowrap">
                                <div><c:choose><c:when test="${empty minorAdmin.phone}"><spring:message code="common.notSet" text="Not set" /></c:when><c:otherwise>${minorAdmin.phone}</c:otherwise></c:choose></div>
                                <c:choose>
                                    <c:when test="${minorAdmin.phoneVerified}">
                                        <div class="mt-1 text-[11px] font-semibold text-emerald-700"><spring:message code="admin.minorAdmins.smsReady" text="Ready for SMS alerts" /></div>
                                    </c:when>
                                    <c:otherwise>
                                        <div class="mt-1 text-[11px] font-semibold text-amber-700"><spring:message code="admin.minorAdmins.phoneVerificationRequired" text="Phone verification required" /></div>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <c:set var="minorAdminSaccoName" value="${registeredSaccoNamesById[minorAdmin.saccoId]}" />
                            <td class="align-top whitespace-nowrap">
                                <c:choose>
                                    <c:when test="${not empty minorAdminSaccoName}"><c:out value="${minorAdminSaccoName}" /></c:when>
                                    <c:otherwise>${minorAdmin.saccoId}</c:otherwise>
                                </c:choose>
                            </td>
                            <td class="align-top whitespace-nowrap"><c:choose><c:when test="${empty minorAdmin.stationId}"><spring:message code="common.notSet" text="Not set" /></c:when><c:otherwise>${minorAdmin.stationId}</c:otherwise></c:choose></td>
                            <td class="align-top whitespace-nowrap">
                                <c:choose>
                                    <c:when test="${minorAdmin.invitationState == 'ACTIVE'}">
                                        <span class="inline-flex items-center rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-800"><spring:message code="admin.dashboard.active" text="Active" /></span>
                                    </c:when>
                                    <c:when test="${minorAdmin.invitationState == 'INVITED'}">
                                        <span class="inline-flex items-center rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800"><spring:message code="admin.minorAdmins.awaitingActivation" text="Awaiting activation" /></span>
                                        <c:if test="${not empty minorAdmin.invitationExpiresAt}">
                                            <div class="text-[11px] text-slate-500"><spring:message code="admin.minorAdmins.expires" text="expires" /> ${minorAdmin.invitationExpiresAt}</div>
                                        </c:if>
                                    </c:when>
                                    <c:when test="${minorAdmin.invitationState == 'EXPIRED'}">
                                        <span class="inline-flex items-center rounded-full bg-rose-100 px-2.5 py-0.5 text-xs font-semibold text-rose-800"><spring:message code="admin.minorAdmins.linkExpired" text="Link expired" /></span>
                                    </c:when>
                                    <c:otherwise>
                                        <span class="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-semibold text-slate-700">${minorAdmin.invitationState}</span>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td class="align-top whitespace-nowrap text-right">
                                <div class="inline-flex flex-wrap justify-end gap-2">
                                    <button type="button"
                                            class="app-btn btn-neutral px-3 py-2 text-[0.74rem]"
                                            data-minor-admin-modal-open="edit-${minorAdmin.accountId}">
                                        <spring:message code="common.edit" text="Edit" />
                                    </button>
                                <c:choose>
                                    <c:when test="${minorAdmin.status == 'INVITED'}">
                                            <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}/resend-invite" method="post" class="inline">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                                <button type="submit" class="app-btn btn-primary px-3 py-2 text-[0.74rem]"><spring:message code="common.resend" text="Resend" /></button>
                                            </form>
                                            <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}/revoke-invite" method="post" class="inline" onsubmit="return confirm('<spring:message code='admin.minorAdmins.confirmRevoke' text='Revoke this invitation? The account will be marked inactive.' javaScriptEscape='true' />');">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                                <button type="submit" class="app-btn btn-reject px-3 py-2 text-[0.74rem]"><spring:message code="common.revoke" text="Revoke" /></button>
                                            </form>
                                    </c:when>
                                    <c:when test="${minorAdmin.status == 'ACTIVE'}">
                                            <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}/deactivate" method="post" class="inline" onsubmit="return confirm('<spring:message code='admin.minorAdmins.confirmDeactivate' text='Deactivate this SACCOS Admin? They will no longer be able to sign in.' javaScriptEscape='true' />');">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                                <button type="submit" class="app-btn btn-reject px-3 py-2 text-[0.74rem]"><spring:message code="common.deactivate" text="Deactivate" /></button>
                                            </form>
                                    </c:when>
                                    <c:otherwise>
                                            <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}/reinvite" method="post" class="inline">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                                <button type="submit" class="app-btn btn-primary px-3 py-2 text-[0.74rem]"><spring:message code="common.reinvite" text="Re-invite" /></button>
                                            </form>
                                            <button type="button"
                                                    class="app-btn btn-reject px-3 py-2 text-[0.74rem]"
                                                    data-minor-admin-modal-open="delete-${minorAdmin.accountId}">
                                                <spring:message code="common.delete" text="Delete" />
                                            </button>
                                    </c:otherwise>
                                </c:choose>
                                </div>
                            </td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>
    </div>
</div>

<c:forEach items="${minorAdmins}" var="minorAdmin">
    <div class="app-modal-overlay hidden" data-minor-admin-modal="edit-${minorAdmin.accountId}">
        <div class="app-modal-panel app-modal-panel--compact">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <p class="erp-panel-title"><spring:message code="admin.minorAdmins.edit" text="Edit SACCOS Admin" /></p>
                        <p class="mt-2 text-sm text-slate-500"><spring:message code="admin.minorAdmins.editHelp" text="Update the assigned workspace and contact details." /></p>
                    </div>
                    <button type="button" class="app-modal-close" data-minor-admin-modal-close="edit-${minorAdmin.accountId}" aria-label="<spring:message code='common.close' text='Close' />">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                </div>
                <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}" method="post" class="app-modal-body space-y-4" data-minor-admin-edit-form data-minor-admin-form>
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

                    <label class="block text-sm font-semibold text-slate-700">
                        <spring:message code="common.sacco" text="SACCO" />
                        <select name="saccoId"
                                required
                                data-edit-sacco-select
                                class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800">
                            <c:forEach items="${registeredSaccos}" var="sacco">
                                <c:set var="editStationList" value="" />
                                <c:forEach items="${sacco.stationIds}" var="stationId" varStatus="stationStatus">
                                    <c:set var="editStationList" value="${editStationList}${stationStatus.first ? '' : ','}${stationId}" />
                                </c:forEach>
                                <option value="${sacco.saccoId}"
                                        data-stations="${editStationList}"
                                        ${sacco.saccoId eq minorAdmin.saccoId ? 'selected' : ''}>
                                    ${sacco.saccoId} - ${sacco.saccoName}
                                </option>
                            </c:forEach>
                        </select>
                    </label>

                    <label class="block text-sm font-semibold text-slate-700">
                        <spring:message code="admin.saccoRegistry.station" text="Station" />
                        <select name="stationId"
                                required
                                data-edit-station-select
                                data-current-station="${minorAdmin.stationId}"
                                class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800"></select>
                    </label>

                    <label class="block text-sm font-semibold text-slate-700">
                        <spring:message code="admin.users.userId" text="User ID" />
                        <input type="text" readonly aria-readonly="true" class="mt-1 w-full rounded border border-slate-300 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-600" value="${minorAdmin.loginId}" />
                    </label>

                    <label class="block text-sm font-semibold text-slate-700">
                        <spring:message code="member.fullName" text="Full Name" />
                        <input name="fullName" type="text" required autocapitalize="characters" spellcheck="false" data-uppercase-input class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm uppercase text-slate-800" value="${minorAdmin.fullName}" />
                    </label>

                    <label class="block text-sm font-semibold text-slate-700">
                        <spring:message code="register.member.email" text="Email" />
                        <input name="email" type="email" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" value="${minorAdmin.email}" />
                    </label>

                    <label class="block text-sm font-semibold text-slate-700">
                        <spring:message code="register.member.phone" text="Phone" />
                        <input name="phone" type="tel" inputmode="numeric" pattern="255[0-9]{9}" minlength="12" maxlength="12" placeholder="255712345678" class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" value="${minorAdmin.phone}" />
                        <span class="mt-1 block text-[11px] font-medium text-slate-500"><spring:message code="admin.minorAdmins.phoneHelp" text="Use Tanzania format: 255 followed by 9 digits, for example 255746359369. Do not start with 0." /></span>
                    </label>

                    <div class="app-modal-actions">
                        <button type="button" class="app-btn btn-neutral" data-minor-admin-modal-close="edit-${minorAdmin.accountId}"><spring:message code="common.cancel" text="Cancel" /></button>
                        <button type="submit" class="app-btn btn-primary"><spring:message code="admin.saccoRegistry.saveChanges" text="Save Changes" /></button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</c:forEach>

<c:forEach items="${minorAdmins}" var="minorAdmin">
    <c:if test="${minorAdmin.status != 'ACTIVE' and minorAdmin.status != 'INVITED'}">
        <div class="app-modal-overlay hidden" data-minor-admin-modal="delete-${minorAdmin.accountId}">
            <div class="app-modal-panel app-modal-panel--compact">
                <div class="app-modal-scroll">
                    <div class="app-modal-header">
                        <div>
                            <p class="erp-panel-title"><spring:message code="common.delete" text="Delete" /> ${minorAdmin.fullName}</p>
                            <p class="mt-2 text-sm text-slate-500">This permanently removes the inactive SACCOS Admin record and its activation data.</p>
                        </div>
                        <button type="button" class="app-modal-close" data-minor-admin-modal-close="delete-${minorAdmin.accountId}" aria-label="<spring:message code='common.close' text='Close' />">
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                                <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                            </svg>
                        </button>
                    </div>
                    <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}/delete" method="post" class="app-modal-body space-y-4" data-minor-admin-form>
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                        <div class="rounded border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">
                            Type <span class="font-semibold">delete ${minorAdmin.fullName}</span> to confirm.
                        </div>
                        <label class="block text-sm font-semibold text-slate-700">
                            Confirmation
                            <input name="confirmation" type="text" required autocomplete="off" class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" />
                        </label>
                        <div class="app-modal-actions">
                            <button type="button" class="app-btn btn-neutral" data-minor-admin-modal-close="delete-${minorAdmin.accountId}"><spring:message code="common.cancel" text="Cancel" /></button>
                            <button type="submit" class="app-btn btn-reject"><spring:message code="common.delete" text="Delete" /></button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    </c:if>
</c:forEach>

<script>
    (function () {
        const saccoSelect = document.getElementById("minorAdminSaccoSelect");
        const stationSelect = document.getElementById("minorAdminStationSelect");

        function syncStations(sourceSelect, targetSelect, preferredValue) {
            if (!sourceSelect || !targetSelect) {
                return;
            }
            const selectedOption = sourceSelect.options[sourceSelect.selectedIndex];
            const stations = selectedOption && selectedOption.dataset.stations
                ? selectedOption.dataset.stations.split(",").map((station) => station.trim()).filter(Boolean)
                : [];
            const currentValue = preferredValue || targetSelect.value;
            targetSelect.innerHTML = "";

            stations.forEach((stationId, index) => {
                const option = document.createElement("option");
                option.value = stationId;
                option.textContent = stationId;
                if (stationId === currentValue || (!currentValue && index === 0)) {
                    option.selected = true;
                }
                targetSelect.appendChild(option);
            });

            if (!targetSelect.options.length) {
                const option = document.createElement("option");
                option.value = "";
                option.textContent = "<spring:message code='admin.minorAdmins.noActiveStations' text='No active stations available' javaScriptEscape='true' />";
                option.selected = true;
                targetSelect.appendChild(option);
            }
        }

        if (saccoSelect && stationSelect) {
            saccoSelect.addEventListener("change", function () {
                syncStations(saccoSelect, stationSelect);
            });
            syncStations(saccoSelect, stationSelect);
        }

        document.querySelectorAll('[data-minor-admin-modal]').forEach((modal) => {
            modal.style.position = 'fixed';
            modal.style.inset = '0';
            modal.style.zIndex = '90';
            document.body.appendChild(modal);
        });

        function closeMinorAdminModal() {
            document.querySelectorAll('[data-minor-admin-modal]').forEach((modal) => {
                modal.classList.add('hidden');
                modal.classList.remove('is-open');
            });
            document.body.classList.remove('overflow-hidden');
        }

        document.querySelectorAll('[data-minor-admin-modal-open]').forEach((button) => {
            button.addEventListener('click', function () {
                const key = button.getAttribute('data-minor-admin-modal-open');
                const modal = document.querySelector('[data-minor-admin-modal="' + key + '"]');
                if (modal) {
                    modal.classList.remove('hidden');
                    modal.classList.add('is-open');
                    document.body.classList.add('overflow-hidden');
                }
            });
        });

        document.querySelectorAll('[data-minor-admin-modal-close]').forEach((button) => {
            button.addEventListener('click', closeMinorAdminModal);
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeMinorAdminModal();
            }
        });

        document.querySelectorAll('[data-minor-admin-edit-form]').forEach((form) => {
            const editSaccoSelect = form.querySelector('[data-edit-sacco-select]');
            const editStationSelect = form.querySelector('[data-edit-station-select]');
            const preferredStation = editStationSelect ? editStationSelect.getAttribute('data-current-station') : '';
            if (!editSaccoSelect || !editStationSelect) {
                return;
            }
            editSaccoSelect.addEventListener('change', function () {
                syncStations(editSaccoSelect, editStationSelect);
            });
            syncStations(editSaccoSelect, editStationSelect, preferredStation);
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
