<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<%!
private static long dashboardLongValue(Object value) {
    if (value instanceof Number) {
        return ((Number) value).longValue();
    }
    if (value == null) {
        return 0L;
    }
    try {
        return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException ex) {
        return 0L;
    }
}

private static String esc(Object value) {
    if (value == null) {
        return "";
    }
    return String.valueOf(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
}

private static double[] pointOnCircle(double centerX, double centerY, double radius, double angleDegrees) {
    double radians = Math.toRadians(angleDegrees - 90d);
    return new double[] {
        centerX + (radius * Math.cos(radians)),
        centerY + (radius * Math.sin(radians))
    };
}

private static String pieSlicePath(double centerX, double centerY, double radius, double startAngle, double endAngle) {
    double[] start = pointOnCircle(centerX, centerY, radius, startAngle);
    double[] end = pointOnCircle(centerX, centerY, radius, endAngle);
    int largeArcFlag = endAngle - startAngle > 180d ? 1 : 0;
    return "M " + centerX + " " + centerY
        + " L " + start[0] + " " + start[1]
        + " A " + radius + " " + radius + " 0 " + largeArcFlag + " 1 " + end[0] + " " + end[1]
        + " Z";
}

%>

<%
List<Map<String, Object>> statusRows = (List<Map<String, Object>>) request.getAttribute("dashboardStatusChartRows");
List<Map<String, Object>> pieRows = new ArrayList<Map<String, Object>>();
long totalStatusCount = 0L;

if (statusRows != null) {
    for (Map<String, Object> row : statusRows) {
        long count = dashboardLongValue(row == null ? null : row.get("count"));
        if (row == null || count <= 0L) {
            continue;
        }
        pieRows.add(row);
        totalStatusCount += count;
    }
}
request.setAttribute("dashboardChartTotalCount", totalStatusCount);

double chartWidth = 920d;
double chartHeight = 480d;
double chartCenterX = 380d;
double chartCenterY = 240d;
double chartRadius = 160d;
StringBuilder pieMarkup = new StringBuilder();

if (!pieRows.isEmpty() && totalStatusCount > 0L) {
    if (pieRows.size() == 1) {
        Map<String, Object> row = pieRows.get(0);
        long count = dashboardLongValue(row.get("count"));
        String label = esc(row.get("label"));
        String color = esc(row.get("color"));
        double angle = 35d;
        double[] lineStart = pointOnCircle(chartCenterX, chartCenterY, chartRadius, angle);
        double[] lineBend = pointOnCircle(chartCenterX, chartCenterY, chartRadius + 34d, angle);
        double labelX = Math.min(chartWidth - 42d, lineBend[0] + 110d);
        double lineEndX = labelX - 10d;
        pieMarkup.append("<g class='manager-pie-slice-group' data-slice-id='slice-0'>");
        pieMarkup.append("<circle class='manager-pie-slice' cx='").append(chartCenterX).append("' cy='").append(chartCenterY)
            .append("' r='").append(chartRadius).append("' fill='").append(color).append("' data-label='")
            .append(label).append("' data-count='").append(count).append("'></circle>");
        pieMarkup.append("<path class='manager-pie-line' d='M ").append(lineStart[0]).append(" ").append(lineStart[1])
            .append(" L ").append(lineBend[0]).append(" ").append(lineBend[1])
            .append(" L ").append(lineEndX).append(" ").append(lineBend[1])
            .append("' stroke='").append(color).append("' stroke-width='2' fill='none' stroke-linecap='round'/>");
        pieMarkup.append("<text class='manager-pie-label' x='").append(labelX).append("' y='").append(lineBend[1] - 8d)
            .append("' text-anchor='start'>")
            .append(label).append("</text>");
        pieMarkup.append("<text class='manager-pie-subtext' x='").append(labelX).append("' y='").append(lineBend[1] + 14d)
            .append("' text-anchor='start'>").append(count).append(" application(s)</text>");
        pieMarkup.append("</g>");
    } else {
        double startAngle = 0d;
        int sliceIndex = 0;
        for (Map<String, Object> row : pieRows) {
            long count = dashboardLongValue(row.get("count"));
            double sweep = (count * 360d) / (double) totalStatusCount;
            double endAngle = startAngle + sweep;
            double midAngle = startAngle + (sweep / 2d);
            double[] lineStart = pointOnCircle(chartCenterX, chartCenterY, chartRadius, midAngle);
            double[] lineBend = pointOnCircle(chartCenterX, chartCenterY, chartRadius + 34d, midAngle);
            boolean rightSide = lineBend[0] >= chartCenterX;
            double labelX = rightSide
                ? Math.min(chartWidth - 42d, lineBend[0] + 110d)
                : Math.max(42d, lineBend[0] - 110d);
            double lineEndX = rightSide ? labelX - 10d : labelX + 10d;
            String textAnchor = rightSide ? "start" : "end";
            String label = esc(row.get("label"));
            String color = esc(row.get("color"));

            pieMarkup.append("<g class='manager-pie-slice-group' data-slice-id='slice-").append(sliceIndex).append("'>");
            pieMarkup.append("<path class='manager-pie-slice' d='")
                .append(pieSlicePath(chartCenterX, chartCenterY, chartRadius, startAngle, endAngle))
                .append("' fill='").append(color).append("' stroke='#ffffff' stroke-width='3' data-label='")
                .append(label).append("' data-count='").append(count).append("'></path>");
            pieMarkup.append("<path class='manager-pie-line' d='M ").append(lineStart[0]).append(" ").append(lineStart[1])
                .append(" L ").append(lineBend[0]).append(" ").append(lineBend[1])
                .append(" L ").append(lineEndX).append(" ").append(lineBend[1])
                .append("' stroke='").append(color).append("' stroke-width='2' fill='none' stroke-linecap='round'/>");
            pieMarkup.append("<text class='manager-pie-label' x='").append(labelX).append("' y='").append(lineBend[1] - 8d)
                .append("' text-anchor='").append(textAnchor).append("'>")
                .append(label).append("</text>");
            pieMarkup.append("<text class='manager-pie-subtext' x='").append(labelX).append("' y='").append(lineBend[1] + 14d)
                .append("' text-anchor='").append(textAnchor).append("'>").append(count).append(" application(s)</text>");
            pieMarkup.append("</g>");
            startAngle = endAngle;
            sliceIndex++;
        }
    }
}
%>


<spring:message code="manager.dashboard.breadcrumb" text="Manager Panel / Dashboard" var="dashboardDefaultBreadcrumb" />
<spring:message code="manager.dashboard.title" text="Manager Dashboard" var="dashboardDefaultTitle" />
<spring:message code="loan.status.READY_FOR_MANAGER" text="On Review By Manager" var="dashboardDefaultQueueLabel" />
<spring:message code="manager.dashboard.queueWaiting" text="Queue waiting" var="dashboardDefaultQueueFooter" />
<spring:message code="manager.dashboard.statusPieTitle" text="Application Status Pie Chart" var="dashboardDefaultChartTitle" />
<c:set var="dashboardBreadcrumbValue" value="${empty dashboardBreadcrumb ? dashboardDefaultBreadcrumb : dashboardBreadcrumb}" />
<c:set var="dashboardPageTitleValue" value="${empty dashboardPageTitle ? dashboardDefaultTitle : dashboardPageTitle}" />
<c:set var="dashboardQueueLabelValue" value="${empty dashboardQueueLabel ? dashboardDefaultQueueLabel : dashboardQueueLabel}" />
<c:set var="dashboardQueueValueValue" value="${empty dashboardQueueValue ? dashboardOnReviewByManagerLoans : dashboardQueueValue}" />
<c:set var="dashboardQueueFooterLabelValue" value="${empty dashboardQueueFooterLabel ? dashboardDefaultQueueFooter : dashboardQueueFooterLabel}" />
<c:set var="dashboardDetailBasePathValue" value="${empty dashboardDetailBasePath ? '/manager/loan-applications' : dashboardDetailBasePath}" />
<c:set var="dashboardChartTitleValue" value="${empty dashboardChartTitle ? dashboardDefaultChartTitle : dashboardChartTitle}" />
<c:set var="dashboardQueueHrefValue" value="${dashboardDetailBasePathValue}" />
<c:choose>
    <c:when test="${fn:startsWith(dashboardDetailBasePathValue, '/board/')}">
        <c:set var="dashboardQueueHrefValue" value="/board/queue" />
    </c:when>
    <c:when test="${fn:startsWith(dashboardDetailBasePathValue, '/chairperson/')}">
        <c:set var="dashboardQueueHrefValue" value="/chairperson/queue" />
    </c:when>
    <c:when test="${fn:startsWith(dashboardDetailBasePathValue, '/credit-committee/')}">
        <c:set var="dashboardQueueHrefValue" value="/credit-committee/queue" />
    </c:when>
    <c:when test="${fn:startsWith(dashboardDetailBasePathValue, '/loan-officer/')}">
        <c:set var="dashboardQueueHrefValue" value="/loan-officer/queue" />
    </c:when>
</c:choose>
<c:set var="dashboardTotalDisbursedHrefValue" value="#recentDisbursements" />
<c:set var="dashboardActiveDisbursedHrefValue" value="#recentDisbursements" />
<c:set var="dashboardDefaultedHrefValue" value="#StationLoanStatusChart" />
<c:choose>
    <c:when test="${dashboardDetailBasePathValue eq '/manager/loan-applications'}">
        <c:set var="dashboardTotalDisbursedHrefValue" value="/manager/archive?filter=DISBURSED" />
        <c:set var="dashboardActiveDisbursedHrefValue" value="/manager/archive?filter=DISBURSED" />
    </c:when>
    <c:when test="${dashboardDetailBasePathValue eq '/disbursement/loan-applications'}">
        <c:set var="dashboardTotalDisbursedHrefValue" value="/disbursement/archive" />
        <c:set var="dashboardActiveDisbursedHrefValue" value="/disbursement/archive?filter=DISBURSED" />
        <c:set var="dashboardDefaultedHrefValue" value="/disbursement/archive?filter=DEFAULTED" />
    </c:when>
</c:choose>
<fmt:formatNumber value="${dashboardDisbursementYear}" groupingUsed="false" var="dashboardDisbursementYearLabel" />

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">${dashboardBreadcrumbValue}</p>
    <h1 class="erp-page-title">${dashboardPageTitleValue}</h1>
</div>

<section class="erp-stat-grid">
    <a href="${dashboardTotalDisbursedHrefValue}" class="erp-stat-card erp-stat-card-interactive erp-stat-green block no-underline" aria-label="Open total disbursed loans">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label"><spring:message code="manager.dashboard.totalDisbursed" text="Total Loans Disbursed" /></p>
                <p class="erp-stat-value">${dashboardTotalDisbursedLoans}</p>
            </div>
        </div>
        <div class="erp-stat-footer"><span>${dashboardDisbursementYearLabel}</span></div>
    </a>
    <a href="${dashboardActiveDisbursedHrefValue}" class="erp-stat-card erp-stat-card-interactive erp-stat-blue block no-underline" aria-label="Open active disbursed loans">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label"><spring:message code="manager.dashboard.activeDisbursed" text="Active Disbursed" /></p>
                <p class="erp-stat-value">${dashboardActiveDisbursedLoans}</p>
            </div>
        </div>
        <div class="erp-stat-footer"><span><spring:message code="manager.dashboard.currentlyOngoing" text="Currently ongoing" /></span></div>
    </a>
    <a href="${dashboardQueueHrefValue}" class="erp-stat-card erp-stat-card-interactive erp-stat-amber block no-underline" aria-label="Open ${dashboardQueueLabelValue} queue">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label">${dashboardQueueLabelValue}</p>
                <p class="erp-stat-value">${dashboardQueueValueValue}</p>
            </div>
        </div>
        <div class="erp-stat-footer"><span>${dashboardQueueFooterLabelValue}</span></div>
    </a>
    <a href="${dashboardDefaultedHrefValue}" class="erp-stat-card erp-stat-card-interactive erp-stat-red block no-underline" aria-label="Open defaulted loans">
        <div class="erp-stat-main">
            <div>
                <p class="erp-stat-label"><spring:message code="archive.defaultedLoans" text="Defaulted Loans" /></p>
                <p class="erp-stat-value">${dashboardDefaultedLoans}</p>
            </div>
        </div>
        <div class="erp-stat-footer"><span><spring:message code="manager.dashboard.currentYear" text="Current year" /></span></div>
    </a>
</section>

<section id="StationLoanStatusChart" class="erp-panel">
    <div class="erp-panel-header">
        <div>
            <p class="erp-panel-title">${dashboardChartTitleValue}</p>
        </div>
    </div>
    <div class="erp-panel-body">
        <c:choose>
            <c:when test="${dashboardChartTotalCount gt 0}">
                <div id="managerPieWrap" class="relative border border-slate-200 bg-white p-4 sm:p-6">
                    <svg id="managerPieChart" viewBox="0 0 920 480" class="manager-pie-chart mx-auto block w-full max-w-[68rem]" role="img" aria-label="Application status pie chart">
                        <rect x="0" y="0" width="920" height="480" rx="16" fill="#ffffff"></rect>
                        <%= pieMarkup.toString() %>
                    </svg>
                    <div id="managerPieTooltip" class="manager-pie-tooltip hidden" aria-hidden="true">
                        <div id="managerPieTooltipTitle" class="manager-pie-tooltip-title"></div>
                        <div id="managerPieTooltipMeta" class="manager-pie-tooltip-meta"></div>
                    </div>
                </div>
            </c:when>
            <c:otherwise>
                <div class="erp-section text-center text-sm text-slate-500"><spring:message code="manager.dashboard.noStatusChart" text="No application statuses to chart yet." /></div>
            </c:otherwise>
        </c:choose>
    </div>
</section>

<script>
    (() => {
        const pieChart = document.getElementById("managerPieChart");
        const pieWrap = document.getElementById("managerPieWrap");
        const tooltip = document.getElementById("managerPieTooltip");
        const tooltipTitle = document.getElementById("managerPieTooltipTitle");
        const tooltipMeta = document.getElementById("managerPieTooltipMeta");
        if (!pieChart) {
            return;
        }

        const groups = Array.from(pieChart.querySelectorAll(".manager-pie-slice-group"));
        if (!groups.length) {
            return;
        }

        function clearState() {
            pieChart.classList.remove("is-hovering");
            groups.forEach((group) => group.classList.remove("is-active"));
            if (tooltip) {
                tooltip.classList.add("hidden");
                tooltip.classList.remove("is-visible");
            }
        }

        function updateTooltipPosition(event) {
            if (!pieWrap || !tooltip || tooltip.classList.contains("hidden")) {
                return;
            }
            const wrapRect = pieWrap.getBoundingClientRect();
            const tooltipRect = tooltip.getBoundingClientRect();
            let left = event.clientX - wrapRect.left + 16;
            let top = event.clientY - wrapRect.top - tooltipRect.height - 14;

            if (left + tooltipRect.width > wrapRect.width - 12) {
                left = wrapRect.width - tooltipRect.width - 12;
            }
            if (left < 12) {
                left = 12;
            }
            if (top < 12) {
                top = event.clientY - wrapRect.top + 18;
            }
            tooltip.style.left = left + "px";
            tooltip.style.top = top + "px";
        }

        groups.forEach((group) => {
            const slice = group.querySelector(".manager-pie-slice");
            group.addEventListener("mouseenter", (event) => {
                pieChart.classList.add("is-hovering");
                groups.forEach((item) => item.classList.toggle("is-active", item === group));
                if (slice && tooltip && tooltipTitle && tooltipMeta) {
                    tooltipTitle.textContent = slice.getAttribute("data-label") || "";
                    tooltipMeta.textContent = (slice.getAttribute("data-count") || "0") + " application(s)";
                    tooltip.classList.remove("hidden");
                    requestAnimationFrame(() => tooltip.classList.add("is-visible"));
                    updateTooltipPosition(event);
                }
            });
            group.addEventListener("mousemove", updateTooltipPosition);
            group.addEventListener("mouseleave", clearState);
        });

        pieChart.addEventListener("mouseleave", clearState);
    })();
</script>

<section id="recentDisbursements" class="erp-panel mt-4">
    <div class="erp-panel-header">
        <div>
            <p class="erp-panel-title"><spring:message code="manager.dashboard.recentDisbursements" text="Recent Disbursements" /></p>
        </div>
    </div>
    <div class="erp-panel-body">
        <div class="manager-recent-disbursements-scroll erp-table-scroll erp-table-scroll-sm space-y-3">
            <c:forEach items="${dashboardDisbursementRows}" var="loan">
                <a href="${dashboardDetailBasePathValue}/${loan.id}" class="block rounded border border-slate-200 bg-white p-3 transition hover:border-slate-300 hover:bg-slate-50">
                    <div class="flex items-start justify-between gap-3">
                        <div>
                            <p class="font-semibold text-slate-900">${loan.applicantName}</p>
                            <p class="mt-1 text-sm text-slate-600">${loan.loanTypeLabel}</p>
                        </div>
                        <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">
                            ${loan.statusLabel}
                        </span>
                    </div>
                    <div class="mt-3 flex flex-wrap gap-3 text-sm text-slate-600">
                        <span><spring:message code="loan.applicationId" text="Loan Application ID" />: ${loan.shortId}</span>
                        <c:if test="${not empty loan.loanId}">
                            <span><spring:message code="loan.loanId" text="Loan ID" />: ${loan.loanId}</span>
                        </c:if>
                        <span><spring:message code="common.amount" text="Amount" />: <fmt:formatNumber value="${loan.amount}" minFractionDigits="0" maxFractionDigits="2" /></span>
                        <span><spring:message code="analytics.disbursed" text="Disbursed" />: ${loan.disbursementDate}</span>
                    </div>
                </a>
            </c:forEach>
            <c:if test="${empty dashboardDisbursementRows}">
                <div class="erp-section-muted">
                    <p class="text-slate-600"><spring:message code="manager.dashboard.noRecentDisbursements" arguments="${dashboardRecentDisbursementDays}" text="No loans were disbursed in the recent period." /></p>
                </div>
            </c:if>
        </div>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
