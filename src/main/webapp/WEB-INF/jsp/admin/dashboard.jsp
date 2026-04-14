<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Dashboard</p>
    <h1 class="erp-page-title">Admin Dashboard</h1>
    <p class="erp-page-subtitle">Supervise user activity, incidents, outbox delivery, and attachment storage from one administration workspace.</p>
</div>

<section class="erp-stat-grid">
    <div class="erp-stat-card erp-stat-blue">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">Members</p>
                <p class="erp-stat-value">${dashboard.totalMembers}</p>
                <p class="erp-stat-meta">Active: ${dashboard.activeMemberCount} | Inactive: ${dashboard.inactiveMemberCount}</p>
            </div>
            <span class="erp-stat-icon">M</span>
        </div>
        <div class="erp-stat-footer"><span>Users in SACCO</span><span>${dashboard.totalMembers}</span></div>
    </div>
    <div class="erp-stat-card erp-stat-green">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">Applications</p>
                <p class="erp-stat-value">${dashboard.totalApplications}</p>
                <p class="erp-stat-meta">On Review By Manager: ${dashboard.onReviewByManagerCount} | Board: ${dashboard.awaitingBoardCount}</p>
            </div>
            <span class="erp-stat-icon">A</span>
        </div>
        <div class="erp-stat-footer"><span>Workflow volume</span><span>${dashboard.totalApplications}</span></div>
    </div>
    <div class="erp-stat-card erp-stat-amber">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">Outbox Failures</p>
                <p class="erp-stat-value">${dashboard.outboxFailedCount}</p>
                <p class="erp-stat-meta">New: ${dashboard.outboxNewCount} | Published: ${dashboard.outboxPublishedCount}</p>
            </div>
            <span class="erp-stat-icon">O</span>
        </div>
        <div class="erp-stat-footer"><span>Needs attention</span><span>${dashboard.outboxFailedCount}</span></div>
    </div>
    <div class="erp-stat-card erp-stat-red">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">Storage</p>
                <c:choose>
                    <c:when test="${dashboard.attachmentStorageReady}">
                        <p class="erp-stat-value">OK</p>
                    </c:when>
                    <c:otherwise>
                        <p class="erp-stat-value">ISSUE</p>
                    </c:otherwise>
                </c:choose>
                <p class="erp-stat-meta">Attachment storage readiness</p>
            </div>
            <span class="erp-stat-icon">S</span>
        </div>
        <div class="erp-stat-footer">
            <span>Health check</span>
            <c:choose>
                <c:when test="${dashboard.attachmentStorageReady}">
                    <span>Stable</span>
                </c:when>
                <c:otherwise>
                    <span>Review</span>
                </c:otherwise>
            </c:choose>
        </div>
    </div>
</section>

<section class="erp-panel">
    <div class="erp-panel-header">
        <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
                <p class="erp-panel-title">Database Utilization</p>
                <p class="mt-1 text-sm text-slate-500">Real-time PostgreSQL storage and connection usage sampled every 15 seconds.</p>
            </div>
            <div class="flex flex-wrap gap-2 text-xs font-semibold">
                <span class="rounded-sm border border-slate-200 bg-white px-3 py-2 text-slate-700">Size: <span id="dbUtilizationSize">-</span></span>
                <span class="rounded-sm border border-slate-200 bg-white px-3 py-2 text-slate-700">Connections: <span id="dbUtilizationConnections">-</span></span>
                <span class="rounded-sm border border-slate-200 bg-white px-3 py-2 text-slate-700">Usage: <span id="dbUtilizationPercent">-</span></span>
                <span class="rounded-sm border border-slate-200 bg-white px-3 py-2 text-slate-700">Updated: <span id="dbUtilizationUpdated">-</span></span>
            </div>
        </div>
    </div>
    <div class="erp-panel-body">
        <div class="overflow-hidden rounded border border-slate-200 bg-slate-50 p-3">
            <canvas id="dbUtilizationChart" class="block w-full" height="340"></canvas>
        </div>
    </div>
</section>

<section class="grid gap-4 xl:grid-cols-2">
    <div class="erp-panel">
        <div class="erp-panel-header">
            <div>
                <p class="erp-panel-title">Recent Events</p>
                <p class="mt-1 text-sm text-slate-500">Last 30 days.</p>
            </div>
        </div>
        <div class="erp-panel-body">
        <div class="space-y-3">
            <c:forEach items="${dashboard.recentAuditEntries}" var="entry">
                <div class="erp-section-muted">
                    <div class="flex items-center justify-between gap-3">
                        <p class="font-semibold text-slate-900">${entry.actionLabel}</p>
                        <span class="text-xs text-slate-500">${entry.sourceLabel}</span>
                    </div>
                    <p class="mt-1 text-xs text-slate-500">${entry.createdAtLabel}</p>
                </div>
            </c:forEach>
            <c:if test="${empty dashboard.recentAuditEntries}">
                <p class="text-slate-500">No events in the last 30 days.</p>
            </c:if>
        </div>
        </div>
    </div>

    <div class="erp-panel">
        <div class="erp-panel-header">
            <div>
                <p class="erp-panel-title">Recent Incidents</p>
                <p class="mt-1 text-sm text-slate-500">Last 30 days.</p>
            </div>
        </div>
        <div class="erp-panel-body">
        <div class="mb-3">
            <a href="/admin/incidents" class="app-btn btn-primary">Open Incidents</a>
        </div>
        <div class="space-y-3">
            <c:forEach items="${dashboard.recentIncidents}" var="incident">
                <a href="/admin/incidents/${incident.id}" class="block rounded border border-slate-200 bg-white p-3 transition hover:border-slate-300 hover:bg-slate-50">
                    <div class="flex items-center justify-between gap-3">
                        <p class="font-semibold text-slate-900">${incident.subject}</p>
                        <div class="flex gap-2 text-xs font-semibold">
                            <span class="rounded-full bg-rose-50 px-3 py-1 text-rose-700">${incident.severity}</span>
                            <span class="rounded-full bg-slate-100 px-3 py-1 text-slate-700">${incident.status}</span>
                        </div>
                    </div>
                    <p class="mt-1 text-sm text-slate-600">${incident.source}</p>
                </a>
            </c:forEach>
            <c:if test="${empty dashboard.recentIncidents}">
                <p class="text-slate-500">No incidents in the last 30 days.</p>
            </c:if>
        </div>
        </div>
    </div>
</section>

<section class="erp-panel">
    <div class="erp-panel-header"><p class="erp-panel-title">Failed Outbox Events</p></div>
    <div class="erp-panel-body">
        <div class="erp-table-wrap overflow-x-auto">
            <table class="erp-table">
                <thead>
                <tr><th>Event</th><th>Aggregate</th><th>Created</th></tr>
                </thead>
                <tbody>
                <c:forEach items="${dashboard.failedOutboxEvents}" var="event">
                    <tr>
                        <td>${event.eventType}</td>
                        <td>${event.aggregateType}</td>
                        <td>${event.createdAt}</td>
                    </tr>
                </c:forEach>
                <c:if test="${empty dashboard.failedOutboxEvents}">
                    <tr><td colspan="3" class="text-slate-500">No failed outbox events.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
</section>

<script>
    (function () {
        const canvas = document.getElementById('dbUtilizationChart');
        if (!canvas) {
            return;
        }
        const ctx = canvas.getContext('2d');
        const sizeEl = document.getElementById('dbUtilizationSize');
        const connectionEl = document.getElementById('dbUtilizationConnections');
        const percentEl = document.getElementById('dbUtilizationPercent');
        const updatedEl = document.getElementById('dbUtilizationUpdated');

        function drawChart(payload) {
            const history = payload && payload.history ? payload.history : [];
            const latest = payload && payload.latest ? payload.latest : null;
            const dpr = window.devicePixelRatio || 1;
            const cssWidth = (canvas.parentElement && canvas.parentElement.clientWidth) || canvas.clientWidth || 900;
            const cssHeight = 340;
            canvas.width = cssWidth * dpr;
            canvas.height = cssHeight * dpr;
            canvas.style.width = cssWidth + 'px';
            canvas.style.height = cssHeight + 'px';
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            ctx.clearRect(0, 0, cssWidth, cssHeight);

            if (!history.length) {
                ctx.fillStyle = '#64748b';
                ctx.font = '14px Manrope, sans-serif';
                ctx.fillText('No database utilization samples yet.', 18, 32);
                return;
            }

            const padding = { top: 36, right: 56, bottom: 54, left: 62 };
            const chartWidth = cssWidth - padding.left - padding.right;
            const chartHeight = cssHeight - padding.top - padding.bottom;
            const sizeValues = history.map(function (item) { return Number(item.sizeMb || 0); });
            const utilizationValues = history.map(function (item) { return Number(item.utilizationPercent || 0); });
            const minSizeRaw = Math.min.apply(null, sizeValues);
            const maxSizeRaw = Math.max.apply(null, sizeValues);
            const minUtilizationRaw = Math.min.apply(null, utilizationValues);
            const maxUtilizationRaw = Math.max.apply(null, utilizationValues);

            const sizePadding = Math.max(0.5, (maxSizeRaw - minSizeRaw) * 0.35);
            const utilPadding = Math.max(2, (maxUtilizationRaw - minUtilizationRaw) * 0.35);

            const minSize = Math.max(0, minSizeRaw - sizePadding);
            const maxSize = Math.max(minSize + 1, maxSizeRaw + sizePadding);
            const minUtilization = Math.max(0, minUtilizationRaw - utilPadding);
            const maxUtilization = Math.max(minUtilization + 5, maxUtilizationRaw + utilPadding);

            function x(index) {
                if (history.length === 1) {
                    return padding.left + chartWidth / 2;
                }
                return padding.left + (chartWidth * index / (history.length - 1));
            }

            function yForSize(value) {
                return padding.top + chartHeight - (((value - minSize) / (maxSize - minSize)) * chartHeight);
            }

            function yForUtilization(value) {
                return padding.top + chartHeight - (((value - minUtilization) / (maxUtilization - minUtilization)) * chartHeight);
            }

            ctx.strokeStyle = '#d7dde3';
            ctx.lineWidth = 1;
            for (let i = 0; i <= 4; i++) {
                const y = padding.top + (chartHeight * i / 4);
                ctx.beginPath();
                ctx.moveTo(padding.left, y);
                ctx.lineTo(padding.left + chartWidth, y);
                ctx.stroke();
            }

            ctx.strokeStyle = '#94a3b8';
            ctx.beginPath();
            ctx.moveTo(padding.left, padding.top);
            ctx.lineTo(padding.left, padding.top + chartHeight);
            ctx.lineTo(padding.left + chartWidth, padding.top + chartHeight);
            ctx.stroke();
            ctx.beginPath();
            ctx.moveTo(padding.left + chartWidth, padding.top);
            ctx.lineTo(padding.left + chartWidth, padding.top + chartHeight);
            ctx.stroke();

            ctx.fillStyle = '#64748b';
            ctx.font = '12px Manrope, sans-serif';
            ctx.textAlign = 'right';
            for (let i = 0; i <= 4; i++) {
                const ratio = 1 - (i / 4);
                const y = padding.top + (chartHeight * i / 4) + 4;
                const sizeTick = minSize + ((maxSize - minSize) * ratio);
                ctx.fillText(sizeTick.toFixed(1) + ' MB', padding.left - 8, y);
            }
            ctx.textAlign = 'left';
            for (let i = 0; i <= 4; i++) {
                const ratio = 1 - (i / 4);
                const y = padding.top + (chartHeight * i / 4) + 4;
                const utilTick = minUtilization + ((maxUtilization - minUtilization) * ratio);
                ctx.fillText(utilTick.toFixed(0) + '%', padding.left + chartWidth + 8, y);
            }

            function drawSeries(values, yFn, strokeColor, fillColor) {
                ctx.beginPath();
                values.forEach(function (value, index) {
                    const px = x(index);
                    const py = yFn(value);
                    if (index === 0) {
                        ctx.moveTo(px, py);
                    } else {
                        ctx.lineTo(px, py);
                    }
                });
                ctx.strokeStyle = strokeColor;
                ctx.lineWidth = 3;
                ctx.stroke();

                values.forEach(function (value, index) {
                    const px = x(index);
                    const py = yFn(value);
                    ctx.beginPath();
                    ctx.fillStyle = fillColor;
                    ctx.arc(px, py, 4, 0, Math.PI * 2);
                    ctx.fill();
                    ctx.strokeStyle = '#ffffff';
                    ctx.lineWidth = 1.5;
                    ctx.stroke();
                });
            }

            drawSeries(sizeValues, yForSize, '#0f766e', '#14b8a6');
            drawSeries(utilizationValues, yForUtilization, '#2563eb', '#60a5fa');

            ctx.textAlign = 'center';
            ctx.fillStyle = '#64748b';
            ctx.font = '11px Manrope, sans-serif';
            const labelStep = Math.max(1, Math.ceil(history.length / 6));
            history.forEach(function (item, index) {
                if (index % labelStep !== 0 && index !== history.length - 1) {
                    return;
                }
                ctx.fillText(item.label, x(index), padding.top + chartHeight + 24);
            });

            ctx.textAlign = 'left';
            ctx.font = '12px Manrope, sans-serif';
            ctx.fillStyle = '#0f766e';
            ctx.fillRect(padding.left, 10, 12, 12);
            ctx.fillStyle = '#334155';
            ctx.fillText('Database Size (MB)', padding.left + 18, 20);
            ctx.fillStyle = '#2563eb';
            ctx.fillRect(padding.left + 180, 10, 12, 12);
            ctx.fillStyle = '#334155';
            ctx.fillText('Connection Utilization (%)', padding.left + 198, 20);

            if (latest) {
                sizeEl.textContent = latest.sizeMb.toFixed(2) + ' MB';
                connectionEl.textContent = latest.activeConnections + ' / ' + latest.maxConnections;
                percentEl.textContent = latest.utilizationPercent.toFixed(2) + '%';
                updatedEl.textContent = latest.capturedAtLabel;
            }
        }

        function loadUtilization() {
            fetch('/admin/dashboard/database-utilization', {
                headers: {
                    'Accept': 'application/json'
                }
            })
                .then(function (response) { return response.json(); })
                .then(drawChart)
                .catch(function () {
                    ctx.clearRect(0, 0, canvas.width, canvas.height);
                    ctx.fillStyle = '#64748b';
                    ctx.font = '14px Manrope, sans-serif';
                    ctx.fillText('Unable to load database utilization metrics right now.', 18, 32);
                });
        }

        loadUtilization();
        window.addEventListener('resize', loadUtilization);
        window.setInterval(loadUtilization, 15000);
    }());
</script>

<%@ include file="../fragments/footer.jspf" %>
