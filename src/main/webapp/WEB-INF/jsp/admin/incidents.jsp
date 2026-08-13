<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.incidents.breadcrumb" text="Admin Tools / Incidents" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.incidents.title" text="Incidents" /></h1>
</div>

<form action="/admin/incidents" method="get" class="admin-filter-form admin-filter-bar aws-filter-toolbar" data-aws-filter-toolbar>
        <c:if test="${superAdminScopeFilters}">
            <div class="admin-filter-field min-w-0">
                <label class="mb-1 block text-sm font-semibold text-slate-700">SACCO</label>
                <select id="incidentSaccoFilter" name="saccoId" class="w-full border border-slate-300 px-3 py-2.5 focus:border-sacco-blue focus:outline-none">
                    <option value="">All SACCOs</option>
                    <c:forEach items="${registeredSaccos}" var="sacco">
                        <option value="${sacco.saccoId}" ${selectedSaccoId eq sacco.saccoId ? 'selected' : ''}>${sacco.saccoName}</option>
                    </c:forEach>
                </select>
            </div>
            <div class="admin-filter-field min-w-0">
                <label class="mb-1 block text-sm font-semibold text-slate-700">Station</label>
                <select id="incidentStationFilter" name="stationId" data-selected-station="${selectedStationId}" class="w-full border border-slate-300 px-3 py-2.5 focus:border-sacco-blue focus:outline-none">
                    <option value="">All stations</option>
                    <c:forEach items="${selectedStationOptions}" var="station">
                        <option value="${station}" ${selectedStationId eq station ? 'selected' : ''}>${station}</option>
                    </c:forEach>
                </select>
                <div id="incidentStationTemplate" hidden>
                    <c:forEach items="${registeredSaccos}" var="sacco">
                        <c:forEach items="${sacco.stationIds}" var="station">
                            <span data-station-option data-sacco="${sacco.saccoId}" data-station="${station}"></span>
                        </c:forEach>
                    </c:forEach>
                </div>
            </div>
        </c:if>
        <div class="admin-filter-field min-w-0">
            <label class="mb-1 block text-sm font-semibold text-slate-700">Status</label>
            <select name="status" class="w-full border border-slate-300 px-3 py-2.5 focus:border-sacco-blue focus:outline-none">
                <option value="">All statuses</option>
                <c:forEach items="${incidentStatuses}" var="item">
                    <option value="${item}" ${selectedStatus eq item.name() ? 'selected' : ''}>${item}</option>
                </c:forEach>
            </select>
        </div>
        <div class="admin-filter-actions flex flex-wrap items-end gap-2">
            <button type="submit" class="app-btn btn-primary">Apply Filters</button>
            <a href="/admin/incidents" class="app-btn btn-neutral">Clear</a>
        </div>
    </form>

<section class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results...">
    <div class="erp-table-scroll">
    <table class="erp-table">
        <thead>
        <tr><th>Subject</th><th>Category</th><th>Status</th><th>Created</th><th>Action</th></tr>
        </thead>
        <tbody>
        <c:forEach items="${incidents}" var="incident">
            <tr>
                <td class="px-3 py-2">
                    <div class="font-semibold text-slate-900">${incident.subject}</div>
                    <div class="text-xs text-slate-500">${incident.source}</div>
                </td>
                <td class="px-3 py-2">${incident.category}</td>
                <td class="px-3 py-2">${incident.status}</td>
                <td class="px-3 py-2">${incident.createdAt}</td>
                <td class="px-3 py-2"><a href="/admin/incidents/${incident.id}" class="app-btn btn-primary">Open</a></td>
            </tr>
        </c:forEach>
        <c:if test="${empty incidents}">
            <tr><td colspan="5" class="px-3 py-4 text-slate-500">No incidents match the current filters.</td></tr>
        </c:if>
        </tbody>
    </table>
                </div>
</section>

<c:if test="${isPlatformAdminIdentity}">
    <section class="grid gap-4 xl:grid-cols-2">
        <div class="erp-form-wrap">
            <h5 class="erp-panel-title">Reply To SACCOS Admin</h5>
            <form action="/admin/incidents/reply-minor-admin" method="post" class="mt-4 space-y-4">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <div>
                    <label class="mb-1 block text-sm font-semibold text-slate-700">Recipient</label>
                    <select name="memberId" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required>
                        <option value="">Select SACCOS admin</option>
                        <c:forEach items="${minorAdmins}" var="admin">
                            <option value="${admin.accountId}">${admin.loginId} - ${admin.fullName} (${admin.saccoId} / ${admin.stationId})</option>
                        </c:forEach>
                    </select>
                </div>
                <div>
                    <label class="mb-1 block text-sm font-semibold text-slate-700">Subject</label>
                    <input name="subject" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required />
                </div>
                <div>
                    <label class="mb-1 block text-sm font-semibold text-slate-700">Message</label>
                    <textarea name="message" rows="5" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required></textarea>
        </div>
                <button type="submit" class="app-btn btn-primary">Send Reply</button>
            </form>
                </div>

        <div class="erp-form-wrap">
            <h5 class="erp-panel-title">Broadcast To SACCOS Admins</h5>
            <form action="/admin/incidents/broadcast-minor-admins" method="post" class="mt-4 space-y-4">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <div>
                    <label class="mb-1 block text-sm font-semibold text-slate-700">Subject</label>
                    <input name="subject" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required />
                </div>
                <div>
                    <label class="mb-1 block text-sm font-semibold text-slate-700">Message</label>
                    <textarea name="message" rows="6" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" required></textarea>
        </div>
                <button type="submit" class="app-btn btn-primary">Send Broadcast</button>
            </form>
        </div>
    </section>
</c:if>

<c:if test="${superAdminScopeFilters}">
    <script>
        (function () {
            const saccoFilter = document.getElementById('incidentSaccoFilter');
            const stationFilter = document.getElementById('incidentStationFilter');
            const stationTemplate = document.getElementById('incidentStationTemplate');
            if (!saccoFilter || !stationFilter || !stationTemplate) {
                return;
            }

            function refreshStations() {
                const selectedSacco = saccoFilter.value;
                const selectedStation = stationFilter.dataset.selectedStation || '';
                stationFilter.innerHTML = '';

                const allOption = document.createElement('option');
                allOption.value = '';
                allOption.textContent = selectedSacco ? 'All stations' : 'Select a SACCO first';
                stationFilter.appendChild(allOption);

                stationTemplate.querySelectorAll('[data-station-option]')
                    .forEach(option => {
                        if (option.dataset.sacco !== selectedSacco) {
                            return;
                        }
                        const next = document.createElement('option');
                        next.value = option.dataset.station;
                        next.textContent = option.dataset.station;
                        next.selected = option.dataset.station === selectedStation;
                        stationFilter.appendChild(next);
                    });
                stationFilter.disabled = !selectedSacco;
                stationFilter.dispatchEvent(new Event('change', { bubbles: true }));
            }

            saccoFilter.addEventListener('change', function () {
                stationFilter.dataset.selectedStation = '';
                refreshStations();
            });
            refreshStations();
        })();
    </script>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
