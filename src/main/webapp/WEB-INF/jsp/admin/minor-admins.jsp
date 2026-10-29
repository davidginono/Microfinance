<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<c:set var="suppressToastMessages" value="${createMode}" />

<c:if test="${createMode}">
<template data-resource-form-page></template>
<spring:message code="${changeAdmin ? 'admin.minorAdmins.changeAdmin' : 'admin.minorAdmins.create'}" var="adminFormTitle" />
<spring:message code="admin.minorAdmins.confirmChange" var="changeAdminConfirmation" />
<nav class="erp-page-path"><a href="/admin/saccos/minor-admins"><spring:message code="admin.minorAdmins.registered" /></a><span>/</span><span aria-current="page"><c:out value="${adminFormTitle}" /></span></nav>
<div class="erp-page-header" data-aws-page-header>
    <h1 class="erp-page-title" id="admin-minor-admins-title"><c:out value="${adminFormTitle}" /></h1>
</div>
</c:if>
<c:if test="${not createMode}">
<nav class="erp-page-path"><a href="/admin/dashboard"><spring:message code="admin.tools" /></a><span>/</span><span aria-current="page"><spring:message code="admin.minorAdmins.registered" /></span></nav>
</c:if>
<%@ include file="../fragments/alerts.jspf" %>

<c:if test="${createMode}">
<c:choose>
    <c:when test="${empty registeredSaccos}">
        <div class="erp-panel overflow-hidden">
            <div class="border-b border-slate-200 bg-slate-50 px-5 py-4 sm:px-6">
                <p class="erp-panel-title"><spring:message code="admin.minorAdmins.registerSaccoFirst" text="Register An Institution First" /></p>
            </div>
            <div class="erp-panel-body space-y-4">
                <p class="text-sm text-slate-600"><spring:message code="admin.minorAdmins.registerSaccoFirstHelp" text="Institution admin accounts need an institution and branch assignment. Register the institution from the sidebar first, then return here to register the account." /></p>
            </div>
        </div>
    </c:when>
    <c:otherwise>
            <form action="${changeAdmin ? '/admin/saccos/minor-admins/change' : '/admin/saccos/minor-admins'}" method="post" class="minor-admin-create-form" data-minor-admin-form
                  data-confirm-title="${fn:escapeXml(adminFormTitle)}" data-confirm-proceed="${fn:escapeXml(adminFormTitle)}"
                  data-confirm-message="${changeAdmin ? fn:escapeXml(changeAdminConfirmation) : ''}">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <section class="erp-panel">
                <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="admin.create.adminDetails" /></h2></div>
                <div class="erp-panel-body minor-admin-create-grid">
                    <label class="block">
                        <spring:message code="common.sacco" text="Institution" />
                        <select id="minorAdminSaccoSelect" name="saccoId" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                            <c:forEach items="${registeredSaccos}" var="sacco" varStatus="status">
                                <c:set var="stationList" value="" />
                                <c:forEach items="${sacco.stationIds}" var="stationId" varStatus="stationStatus">
                                    <c:set var="stationList" value="${stationList}${stationStatus.first ? '' : ','}${stationId}" />
                                </c:forEach>
                                <option value="${sacco.saccoId}"
                                        data-stations="${stationList}"
                                        ${registrationForm.saccoId eq sacco.saccoId or (empty registrationForm.saccoId and status.first) ? 'selected' : ''}>
                                    <c:out value="${sacco.saccoName}" />
                                </option>
                            </c:forEach>
                        </select>
                    </label>

                    <label class="block">
                        <spring:message code="admin.saccoRegistry.station" text="Branch" />
                        <select id="minorAdminStationSelect" name="stationId" data-current-station="${fn:escapeXml(registrationForm.stationId)}" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800"></select>
                    </label>

                    <label class="block">
                        <spring:message code="member.fullName" text="Full Name" />
                        <input name="fullName" type="text" value="${fn:escapeXml(registrationForm.fullName)}" required autocapitalize="characters" spellcheck="false" data-uppercase-input class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm uppercase text-slate-800" />
                    </label>

                    <label class="block">
                        <spring:message code="register.member.email" text="Email" />
                        <input name="email" type="email" value="${fn:escapeXml(registrationForm.email)}" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" />
                    </label>

                    <label class="block">
                        <spring:message code="register.member.phone" text="Phone" />
                        <input name="phone" type="tel" value="${fn:escapeXml(registrationForm.phone)}" inputmode="numeric" pattern="255[0-9]{9}" minlength="12" maxlength="12" placeholder="255712345678" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" />
                        <span class="mt-1 block text-sm text-slate-500"><spring:message code="admin.minorAdmins.phoneHelp" text="Use Tanzania format: 255 followed by 9 digits, for example 255746359369. Do not start with 0." /></span>
                    </label>
                </div>
                </section>
                <c:if test="${not empty error}"><div class="console-message" data-message-type="error" role="alert" tabindex="-1" data-workspace-form-error><c:out value="${error}" /></div></c:if>
                <div class="minor-admin-create-actions" data-aws-action-pin="true">
                    <a href="/admin/saccos/minor-admins" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a>
                    <c:choose><c:when test="${changeAdmin}">
                    <button type="submit"
                            class="app-btn btn-launch">
                        <spring:message code="admin.minorAdmins.changeAdmin" text="Change Institution Admin" />
                    </button>
                    </c:when><c:otherwise>
                    <button type="submit" class="app-btn btn-launch"><spring:message code="admin.minorAdmins.register" text="Register Institution Admin" /></button>
                    </c:otherwise></c:choose>
                </div>
            </form>
    </c:otherwise>
</c:choose>
</c:if>

<c:if test="${not createMode}">
<section class="erp-table-wrap" data-aws-table-region data-resource-table="admin-minor-admins-1" data-table-selection="single" data-resource-split>
    <div class="app-table-titlebar">
        <h2 class="erp-panel-title" id="admin-minor-admins-title"><spring:message code="admin.minorAdmins.registered" /></h2>
        <div class="app-table-toolbar">
            <sec:authorize access="@access.has(principal, 'USER_ACCESS_CREATE') and @access.has(principal, 'USER_ACCESS_UPDATE')"><a href="/admin/saccos/minor-admins/change" class="app-btn btn-neutral"><spring:message code="admin.minorAdmins.changeAdmin" /></a></sec:authorize>
            <sec:authorize access="@access.has(principal, 'USER_ACCESS_CREATE')"><a href="/admin/saccos/minor-admins/new" class="app-btn btn-launch"><spring:message code="admin.minorAdmins.create" /></a></sec:authorize>
        </div>
    </div>
        <div class="erp-table-scroll">
        <table class="erp-table min-w-[1120px]">
            <thead>
            <tr>
                <th data-table-order="name"><spring:message code="admin.users.user" text="User" /></th>
                <th data-table-order="email"><spring:message code="register.member.email" text="Email" /></th>
                <th data-table-order="phone"><spring:message code="register.member.phone" text="Phone" /></th>
                <th data-table-order="institution"><spring:message code="common.sacco" text="Institution" /></th>
                <th data-table-order="branch"><spring:message code="admin.saccoRegistry.station" text="Branch" /></th>
                <th data-table-order="activation"><spring:message code="admin.minorAdmins.activation" text="Activation" /></th>
                <th data-table-actions-header><spring:message code="common.actions" text="Actions" /></th>
            </tr>
            </thead>
            <tbody>
            <c:choose>
                <c:when test="${empty minorAdmins}">
                    <tr>
                        <td colspan="7" class="minor-admin-empty"><p><spring:message code="admin.minorAdmins.empty" text="No Institution Admins have been registered yet." /></p></td>
                    </tr>
                </c:when>
                <c:otherwise>
                    <c:forEach items="${minorAdmins}" var="minorAdmin">
                        <tr>
                            <td class="align-top">
                                <div class="font-semibold text-slate-900"><c:out value="${minorAdmin.fullName}" /></div>
                                <div class="text-slate-500"><c:out value="${minorAdmin.loginId}" /></div>
                            </td>
                            <td class="align-top whitespace-nowrap"><c:out value="${minorAdmin.email}" /></td>
                            <td class="align-top whitespace-nowrap">
                                <div><c:choose><c:when test="${empty minorAdmin.phone}"><spring:message code="common.notSet" text="Not set" /></c:when><c:otherwise><c:out value="${minorAdmin.phone}" /></c:otherwise></c:choose></div>
                                <c:choose>
                                    <c:when test="${minorAdmin.phoneVerified}">
                                        <div class="mt-1"><span data-resource-status="VALIDATED"><spring:message code="admin.minorAdmins.smsReady" text="Ready for SMS alerts" /></span></div>
                                    </c:when>
                                    <c:otherwise>
                                        <div class="mt-1"><span data-resource-status="NOT_VALIDATED"><spring:message code="admin.minorAdmins.phoneVerificationRequired" text="Phone verification required" /></span></div>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <c:set var="minorAdminSaccoName" value="${registeredSaccoNamesById[minorAdmin.saccoId]}" />
                            <td class="align-top whitespace-nowrap">
                                <c:choose>
                                    <c:when test="${not empty minorAdminSaccoName}"><c:out value="${minorAdminSaccoName}" /></c:when>
                                    <c:otherwise><c:out value="${minorAdmin.saccoId}" /></c:otherwise>
                                </c:choose>
                            </td>
                            <td class="align-top whitespace-nowrap"><c:choose><c:when test="${empty minorAdmin.stationId}"><spring:message code="common.notSet" text="Not set" /></c:when><c:otherwise><c:out value="${minorAdmin.stationId}" /></c:otherwise></c:choose></td>
                            <td class="align-top whitespace-nowrap">
                                <c:choose>
                                    <c:when test="${minorAdmin.invitationState == 'ACTIVE'}">
                                        <span data-resource-status="ACTIVE"><spring:message code="admin.dashboard.active" text="Active" /></span>
                                    </c:when>
                                    <c:when test="${minorAdmin.invitationState == 'INVITED'}">
                                        <span data-resource-status="INVITED"><spring:message code="admin.minorAdmins.awaitingActivation" text="Awaiting activation" /></span>
                                        <c:if test="${not empty minorAdmin.invitationExpiresAt}">
                                            <div class="text-slate-500"><spring:message code="admin.minorAdmins.expires" text="expires" /> <c:out value="${appTime:format(minorAdmin.invitationExpiresAt)}" /></div>
                                        </c:if>
                                    </c:when>
                                    <c:when test="${minorAdmin.invitationState == 'EXPIRED'}">
                                        <span data-resource-status="WARNING"><spring:message code="admin.minorAdmins.linkExpired" text="Link expired" /></span>
                                    </c:when>
                                    <c:otherwise>
                                        <span data-resource-status="${minorAdmin.invitationState}"><c:out value="${minorAdmin.invitationState}" /></span>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <sec:authorize access="@access.has(principal, 'USER_ACCESS_UPDATE')">
                                    <button type="button" class="app-btn btn-neutral" data-minor-admin-modal-open="edit-${minorAdmin.accountId}"><spring:message code="common.edit" /></button>
                                    <c:choose>
                                        <c:when test="${minorAdmin.status == 'INVITED'}">
                                            <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}/resend-invite" method="post">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                                <button type="submit" class="app-btn btn-neutral"><spring:message code="common.resend" /></button>
                                            </form>
                                            <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}/revoke-invite" method="post" data-confirm-message="<spring:message code='admin.minorAdmins.confirmRevoke' htmlEscape='true' />" data-confirm-title="<spring:message code='common.revoke' />" data-confirm-proceed="<spring:message code='common.revoke' />">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                                <button type="submit" class="app-btn btn-reject"><spring:message code="common.revoke" /></button>
                                            </form>
                                        </c:when>
                                        <c:when test="${minorAdmin.status == 'ACTIVE'}">
                                            <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}/deactivate" method="post" data-confirm-message="<spring:message code='admin.minorAdmins.confirmDeactivate' htmlEscape='true' />" data-confirm-title="<spring:message code='common.deactivate' />" data-confirm-proceed="<spring:message code='common.deactivate' />">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                                <button type="submit" class="app-btn btn-reject"><spring:message code="common.deactivate" /></button>
                                            </form>
                                        </c:when>
                                        <c:otherwise>
                                            <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}/reinvite" method="post">
                                                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                                <button type="submit" class="app-btn btn-neutral"><spring:message code="common.reinvite" /></button>
                                            </form>
                                        </c:otherwise>
                                    </c:choose>
                                </sec:authorize>
                                <sec:authorize access="@access.has(principal, 'USER_ACCESS_DELETE')">
                                    <c:if test="${minorAdmin.status != 'ACTIVE' and minorAdmin.status != 'INVITED'}">
                                        <button type="button" class="app-btn btn-reject" data-minor-admin-modal-open="delete-${minorAdmin.accountId}"><spring:message code="admin.minorAdmins.delete" /></button>
                                    </c:if>
                                </sec:authorize>
                            </td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>
    </div>
</section>

<sec:authorize access="@access.has(principal, 'USER_ACCESS_UPDATE')">
<c:forEach items="${minorAdmins}" var="minorAdmin">
    <div class="app-modal-overlay hidden" data-minor-admin-modal="edit-${minorAdmin.accountId}">
        <div class="app-modal-panel app-modal-panel--compact" role="dialog" aria-modal="true" aria-labelledby="minor-admin-edit-${minorAdmin.accountId}">
            <div class="app-modal-scroll">
                <div class="app-modal-header">
                    <div>
                        <h2 class="erp-panel-title" id="minor-admin-edit-${minorAdmin.accountId}"><spring:message code="admin.minorAdmins.edit" /></h2>
                </div>
                    <button type="button" class="app-modal-close" data-minor-admin-modal-close="edit-${minorAdmin.accountId}" aria-label="<spring:message code='common.close' text='Close' />">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                        </svg>
                    </button>
                    </div>
                <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}" method="post" class="app-modal-body space-y-4" data-minor-admin-edit-form data-minor-admin-form>
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

                    <label class="block">
                        <spring:message code="common.sacco" text="Institution" />
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
                                    <c:out value="${sacco.saccoName}" />
                                </option>
                            </c:forEach>
                        </select>
                    </label>

                    <label class="block">
                        <spring:message code="admin.saccoRegistry.station" text="Branch" />
                        <select name="stationId"
                                required
                                data-edit-station-select
                                data-current-station="${minorAdmin.stationId}"
                                class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800"></select>
                    </label>

                    <label class="block">
                        <spring:message code="admin.users.userId" text="User ID" />
                        <input type="text" readonly aria-readonly="true" class="mt-1 w-full rounded border border-slate-300 bg-slate-50 px-3 py-2.5 text-sm font-semibold text-slate-600" value="${fn:escapeXml(minorAdmin.loginId)}" />
                    </label>

                    <label class="block">
                        <spring:message code="member.fullName" text="Full Name" />
                        <input name="fullName" type="text" required autocapitalize="characters" spellcheck="false" data-uppercase-input class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm uppercase text-slate-800" value="${fn:escapeXml(minorAdmin.fullName)}" />
                    </label>

                    <label class="block">
                        <spring:message code="register.member.email" text="Email" />
                        <input name="email" type="email" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" value="${fn:escapeXml(minorAdmin.email)}" />
                    </label>

                    <label class="block">
                        <spring:message code="register.member.phone" text="Phone" />
                        <input name="phone" type="tel" inputmode="numeric" pattern="255[0-9]{9}" minlength="12" maxlength="12" placeholder="255712345678" class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" value="${fn:escapeXml(minorAdmin.phone)}" />
                        <span class="mt-1 block text-sm text-slate-500"><spring:message code="admin.minorAdmins.phoneHelp" text="Use Tanzania format: 255 followed by 9 digits, for example 255746359369. Do not start with 0." /></span>
                    </label>

                    <div class="app-modal-actions">
                        <button type="button" class="app-btn btn-neutral" data-minor-admin-modal-close="edit-${minorAdmin.accountId}"><spring:message code="common.cancel" text="Cancel" /></button>
                        <button type="submit" class="app-btn btn-launch"><spring:message code="admin.saccoRegistry.saveChanges" text="Save Changes" /></button>
            </div>
                </form>
        </div>
    </div>
                        </div>
</c:forEach>
</sec:authorize>

<sec:authorize access="@access.has(principal, 'USER_ACCESS_DELETE')">
<c:forEach items="${minorAdmins}" var="minorAdmin">
    <c:if test="${minorAdmin.status != 'ACTIVE' and minorAdmin.status != 'INVITED'}">
        <div class="app-modal-overlay hidden" data-minor-admin-modal="delete-${minorAdmin.accountId}">
            <div class="app-modal-panel app-modal-panel--compact" role="dialog" aria-modal="true" aria-labelledby="minor-admin-delete-${minorAdmin.accountId}">
                <div class="app-modal-scroll">
                    <div class="app-modal-header">
                        <div>
                            <h2 class="erp-panel-title" id="minor-admin-delete-${minorAdmin.accountId}"><spring:message code="admin.minorAdmins.delete" /> <c:out value="${minorAdmin.fullName}" /></h2>
                    </div>
                        <button type="button" class="app-modal-close" data-minor-admin-modal-close="delete-${minorAdmin.accountId}" aria-label="<spring:message code='common.close' text='Close' />">
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                                <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                            </svg>
                        </button>
                        </div>
                    <form action="/admin/saccos/minor-admins/${minorAdmin.accountId}/delete" method="post" class="app-modal-body space-y-4" data-minor-admin-form>
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                        <div class="console-message" data-message-type="warning">
                            <p><spring:message code="admin.minorAdmins.deleteWarning" /></p>
                            <p><spring:message code="admin.minorAdmins.deletePrompt" htmlEscape="true"><spring:argument value="delete ${minorAdmin.fullName}" /></spring:message></p>
                        </div>
                        <label class="block">
                            <spring:message code="admin.minorAdmins.confirmation" />
                            <input name="confirmation" type="text" required autocomplete="off" class="mt-1 w-full rounded border border-slate-300 px-3 py-2.5 text-sm text-slate-800" />
                        </label>
                        <div class="app-modal-actions">
                            <button type="button" class="app-btn btn-neutral" data-minor-admin-modal-close="delete-${minorAdmin.accountId}"><spring:message code="common.cancel" text="Cancel" /></button>
                            <button type="submit" class="app-btn btn-reject"><spring:message code="admin.minorAdmins.delete" /></button>
                </div>
                    </form>
            </div>
        </div>
        </div>
    </c:if>
</c:forEach>
</sec:authorize>

</c:if>
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
                option.textContent = "<spring:message code='admin.minorAdmins.noActiveStations' text='No active branches available' javaScriptEscape='true' />";
                option.selected = true;
                targetSelect.appendChild(option);
            }
        }

        if (saccoSelect && stationSelect) {
            saccoSelect.addEventListener("change", function () {
                syncStations(saccoSelect, stationSelect);
            });
            syncStations(saccoSelect, stationSelect, stationSelect.dataset.currentStation);
        }

        document.querySelectorAll('[data-minor-admin-modal]').forEach((modal) => {
            modal.style.position = 'fixed';
            modal.style.inset = '0';
            modal.style.zIndex = '90';
            document.body.appendChild(modal);
        });

        function closeMinorAdminModal(force) {
            const modal=document.querySelector('[data-minor-admin-modal].is-open');
            if(force!==true && modal && !document.dispatchEvent(new CustomEvent('resource:before-close',{cancelable:true,detail:{modal,close:()=>closeMinorAdminModal(true)}})))return;
            document.querySelectorAll('[data-minor-admin-modal]').forEach((modal) => {
                modal.classList.add('hidden');
                modal.classList.remove('is-open');
            });
            document.body.classList.remove('overflow-hidden');
        }

        document.addEventListener("click", (event) => {
            const button=event.target.closest("[data-minor-admin-modal-open]");
            if (!button || button.disabled) return;
                const key = button.getAttribute('data-minor-admin-modal-open');
                const modal = document.querySelector('[data-minor-admin-modal="' + key + '"]');
                if (modal) {
                    modal.classList.remove('hidden');
                    modal.classList.add('is-open');
                    document.body.classList.add('overflow-hidden');
                }
        });

        document.querySelectorAll('[data-minor-admin-modal-close]').forEach((button) => {
            button.addEventListener('click', closeMinorAdminModal);
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && !event.defaultPrevented) {
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

<%@ include file="../fragments/confirm-modal.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
