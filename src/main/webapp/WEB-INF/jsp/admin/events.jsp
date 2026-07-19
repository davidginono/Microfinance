<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<style>
    .admin-filter-bar {
        align-items: flex-end;
        display: flex;
        flex-wrap: nowrap;
        gap: 0.75rem;
        overflow-x: visible;
        width: 100%;
    }
    .admin-filter-field {
        flex: 1 1 0;
        min-width: 0;
        max-width: none;
    }
    .admin-filter-field--date {
        flex: 0.85 1 0;
    }
    .admin-filter-field--actor {
        flex: 1.05 1 0;
    }
    .admin-filter-field--rows {
        flex: 0 0 9.5rem;
    }
    .admin-filter-field select,
    .admin-filter-field input {
        min-height: 2.75rem;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
    }
    .admin-filter-field .neo-select-button {
        min-height: 2.75rem;
        padding: 0.62rem 0.75rem;
    }
    .admin-filter-field--rows .neo-select-button {
        padding-left: 0.9rem;
        padding-right: 0.9rem;
    }
    .admin-filter-field--rows .neo-select-button-text {
        font-size: 0.9rem;
    }
    .admin-filter-field--rows-label {
        white-space: nowrap;
    }
    .admin-filter-actions {
        flex: 0 0 auto;
        white-space: nowrap;
    }
    @media (max-width: 900px) {
        .admin-filter-bar {
            flex-wrap: wrap;
            overflow-x: visible;
        }
        .admin-filter-field,
        .admin-filter-field--date,
        .admin-filter-field--actor,
        .admin-filter-field--rows {
            flex: 1 1 11rem;
            max-width: none;
        }
    }
</style>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="admin.events.breadcrumb" text="Admin Tools / Event Log" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.events.title" text="Event Log" /></h1>
    <p class="erp-page-subtitle"><spring:message code="admin.events.subtitle" text="Review recorded system activity." /></p>
</div>
<section class="erp-form-wrap mb-4">
    <form action="/admin/events" method="get" class="admin-filter-form admin-filter-bar" data-auto-submit-filter>
        <c:if test="${superAdminScopeFilters}">
            <label class="admin-filter-field block min-w-0 text-sm font-semibold text-slate-700">
                SACCO
                <select id="eventSaccoFilter" name="saccoId" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none">
                    <option value="">All SACCOs</option>
                    <c:forEach items="${registeredSaccos}" var="sacco">
                        <option value="${sacco.saccoId}" ${selectedSaccoId eq sacco.saccoId ? 'selected' : ''}>${sacco.saccoName}</option>
                    </c:forEach>
                </select>
            </label>
            <label class="admin-filter-field block min-w-0 text-sm font-semibold text-slate-700">
                Station
                <select id="eventStationFilter" name="stationId" data-selected-station="${selectedStationId}" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none">
                    <option value="">All stations</option>
                    <c:forEach items="${selectedStationOptions}" var="station">
                        <option value="${station}" ${selectedStationId eq station ? 'selected' : ''}>${station}</option>
                    </c:forEach>
                </select>
                <div id="eventStationTemplate" hidden>
                    <c:forEach items="${registeredSaccos}" var="sacco">
                        <c:forEach items="${sacco.stationIds}" var="station">
                            <span data-station-option data-sacco="${sacco.saccoId}" data-station="${station}"></span>
                        </c:forEach>
                    </c:forEach>
                </div>
            </label>
        </c:if>
        <label class="admin-filter-field admin-filter-field--date block min-w-0 text-sm font-semibold text-slate-700">
            Filter From
            <input type="date" name="dateFrom" value="${selectedDateFrom}" class="mt-1 w-full border px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none ${not empty dateFromError ? 'border-rose-300 bg-rose-50' : 'border-slate-300 bg-white'}" />
            <c:if test="${not empty dateFromError}">
                <span class="mt-1 block text-[0.78rem] font-normal normal-case tracking-normal text-rose-600">${dateFromError}</span>
            </c:if>
        </label>
        <label class="admin-filter-field admin-filter-field--date block min-w-0 text-sm font-semibold text-slate-700">
            Filter To
            <input type="date" name="dateTo" value="${selectedDateTo}" class="mt-1 w-full border px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none ${not empty dateToError ? 'border-rose-300 bg-rose-50' : 'border-slate-300 bg-white'}" />
            <c:if test="${not empty dateToError}">
                <span class="mt-1 block text-[0.78rem] font-normal normal-case tracking-normal text-rose-600">${dateToError}</span>
            </c:if>
        </label>
        <label class="admin-filter-field admin-filter-field--actor block min-w-0 text-sm font-semibold text-slate-700">
            Actor / User ID
            <input type="text" name="actorId" value="${selectedActorId}" placeholder="Search actor UUID or prefix" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none" data-auto-submit-on-input />
        </label>
        <label class="admin-filter-field admin-filter-field--rows block min-w-0 text-sm font-semibold text-slate-700">
            <span class="admin-filter-field--rows-label">Rows Per Page</span>
            <select name="size" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-slate-800 focus:border-sacco-blue focus:outline-none">
                <option value="25" ${selectedPageSize eq 25 ? 'selected' : ''}>25 rows</option>
                <option value="50" ${selectedPageSize eq 50 ? 'selected' : ''}>50 rows</option>
                <option value="100" ${selectedPageSize eq 100 ? 'selected' : ''}>100 rows</option>
            </select>
        </label>
        <div class="admin-filter-actions flex flex-wrap items-end gap-2">
            <button type="submit" class="app-btn btn-primary">Apply Filters</button>
            <a href="/admin/events" class="app-btn btn-neutral">Reset</a>
        </div>
    </form>
</section>
<div class="erp-table-wrap erp-table-scroll">
    <table class="erp-table">
        <thead>
        <tr>
            <th>Action</th>
            <c:if test="${superAdminScopeFilters}">
                <th>SACCO</th>
            </c:if>
            <th>Status</th>
            <th>Entity</th>
            <th>Reference</th>
            <th>Actor ID</th>
            <th>Name</th>
            <th>Date</th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${entries}" var="entry">
            <tr>
                <td class="px-3 py-2 font-semibold text-slate-900">${entry.displayAction}</td>
                <c:if test="${superAdminScopeFilters}">
                    <c:set var="entrySaccoName" value="${registeredSaccoNamesById[entry.saccoId]}" />
                    <td class="px-3 py-2">
                        <c:choose>
                            <c:when test="${not empty entrySaccoName}"><c:out value="${entrySaccoName}" /></c:when>
                            <c:otherwise>${entry.saccoReferenceLabel}</c:otherwise>
                        </c:choose>
                    </td>
                </c:if>
                <td class="px-3 py-2">
                    <span class="inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold ${entry.statusBadgeClass}">${entry.displayStatus}</span>
                </td>
                <td class="px-3 py-2">${entry.displayEntityType}</td>
                <td class="px-3 py-2">${entry.shortEntityReference}</td>
                <td class="px-3 py-2">${entry.actorReferenceLabel}</td>
                <c:set var="actorName" value="${actorNamesById[entry.actorMemberIdText]}" />
                <td class="px-3 py-2">
                    <c:choose>
                        <c:when test="${empty entry.actorMemberId}">System</c:when>
                        <c:when test="${not empty actorName}"><c:out value="${actorName}" /></c:when>
                        <c:otherwise>Unknown actor</c:otherwise>
                    </c:choose>
                </td>
                <td class="px-3 py-2 whitespace-nowrap">${entry.createdAtLabel}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty entries}">
            <tr><td colspan="${superAdminScopeFilters ? 8 : 7}" class="px-3 py-3 text-slate-500">No events recorded yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</div>
<c:if test="${entriesPage.totalPages gt 1}">
    <div class="mt-4 flex flex-col gap-3 rounded border border-slate-200 bg-white px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
        <p class="text-sm text-slate-600">Page <span class="font-semibold text-slate-800">${entriesPage.number + 1}</span> of <span class="font-semibold text-slate-800">${entriesPage.totalPages}</span></p>
        <div class="flex flex-wrap items-center gap-2">
            <c:choose>
                <c:when test="${entriesPage.first}">
                    <span class="app-btn btn-neutral opacity-50">Previous</span>
                </c:when>
                <c:otherwise>
                    <a href="/admin/events?page=${entriesPage.number - 1}${eventsPaginationQuery}" class="app-btn btn-neutral">Previous</a>
                </c:otherwise>
            </c:choose>
            <c:choose>
                <c:when test="${entriesPage.last}">
                    <span class="app-btn btn-neutral opacity-50">Next</span>
                </c:when>
                <c:otherwise>
                    <a href="/admin/events?page=${entriesPage.number + 1}${eventsPaginationQuery}" class="app-btn btn-primary">Next</a>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</c:if>

<c:if test="${superAdminScopeFilters}">
    <script>
        (function () {
            const saccoFilter = document.getElementById('eventSaccoFilter');
            const stationFilter = document.getElementById('eventStationFilter');
            const stationTemplate = document.getElementById('eventStationTemplate');
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

<%@ include file="../fragments/auto-submit-filter.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
