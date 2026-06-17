<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<style>
    .member-dashboard-flow-shell {
        overflow-x: auto;
        max-width: 100%;
        padding-bottom: 0.45rem;
        overscroll-behavior-x: contain;
    }
    .member-dashboard-flow-track {
        width: fit-content;
        min-width: 100%;
        padding: 0.85rem 0 0.5rem;
    }
    .member-dashboard-flow-list {
        display: flex;
        align-items: flex-start;
    }
    .member-dashboard-flow-step-wrap {
        display: flex;
        min-width: max-content;
    }
    .member-dashboard-flow-step {
        width: 9.35rem;
        flex: 0 0 9.35rem;
        text-align: center;
    }
    .member-dashboard-flow-node-wrap {
        position: relative;
        display: flex;
        justify-content: center;
    }
    .member-dashboard-flow-node {
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 6.25rem;
        height: 6.25rem;
        margin: 0 auto;
        border-width: 3px;
        border-radius: 9999px;
        background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
        box-shadow: inset 0 0 0 1px rgba(255,255,255,0.7);
    }
    .member-dashboard-flow-node--completed {
        border-color: #6ee7a0;
        background: radial-gradient(circle at top, #f4fff7 0%, #e8faee 100%);
        color: #0ea63c;
    }
    .member-dashboard-flow-node--current {
        border-color: #2563eb;
        background: radial-gradient(circle at top, #f8fbff 0%, #eaf3ff 100%);
        color: #2563eb;
    }
    .member-dashboard-flow-node--pending {
        border-color: #d1d5db;
        background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
        color: #6b7280;
    }
    .member-dashboard-flow-check {
        position: absolute;
        top: 0.15rem;
        right: 1rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 2rem;
        height: 2rem;
        border-radius: 9999px;
        background: #10b83c;
        color: #ffffff;
        font-size: 1rem;
        font-weight: 800;
        box-shadow: 0 0 0 3px #ffffff;
    }
    .member-dashboard-flow-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
    }
    .member-dashboard-flow-icon svg {
        width: 2.85rem;
        height: 2.85rem;
        stroke: currentColor;
        fill: none;
        stroke-width: 1.8;
        stroke-linecap: round;
        stroke-linejoin: round;
    }
    .member-dashboard-flow-icon--completed {
        color: #0ea63c;
    }
    .member-dashboard-flow-icon--current {
        color: #2563eb;
    }
    .member-dashboard-flow-icon--pending {
        color: #6b7280;
    }
    .member-dashboard-flow-connector {
        width: 2.7rem;
        flex: 0 0 2.7rem;
        height: 0.25rem;
        border-radius: 9999px;
        margin-top: 3rem;
    }
    .member-dashboard-flow-connector--completed {
        background: #22c55e;
    }
    .member-dashboard-flow-connector--current {
        background: #2563eb;
    }
    .member-dashboard-flow-connector--pending {
        background: #d1d5db;
    }
    .member-dashboard-flow-number {
        margin-top: 1rem;
        font-size: 2.55rem;
        line-height: 1;
        font-weight: 800;
        letter-spacing: -0.04em;
    }
    .member-dashboard-flow-number--completed {
        color: #16a34a;
    }
    .member-dashboard-flow-number--current {
        color: #2563eb;
    }
    .member-dashboard-flow-number--pending {
        color: #4b5563;
    }
    .member-dashboard-flow-label {
        margin-top: 1rem;
        font-size: 0.9rem;
        line-height: 1.2;
        font-weight: 800;
        color: #172033;
        min-height: 2.15rem;
        overflow-wrap: anywhere;
    }
    .member-dashboard-flow-detail {
        margin-top: 0.25rem;
        font-size: 0.72rem;
        line-height: 1.2;
        font-weight: 700;
        color: #64748b;
        min-height: 0.9rem;
        overflow-wrap: anywhere;
    }
    .member-dashboard-flow-text--completed {
        color: #15803d;
    }
    .member-dashboard-flow-text--current {
        color: #2563eb;
    }
    .member-dashboard-flow-text--pending {
        color: #f59e0b;
    }
    .member-dashboard-flow-text--not-started {
        color: #94a3b8;
    }
    .member-dashboard-flow-status {
        margin-top: 0.65rem;
        font-size: 0.88rem;
        line-height: 1.2;
        font-weight: 700;
        min-height: 1.15rem;
        overflow-wrap: anywhere;
    }
    .member-dashboard-flow-meta {
        margin-top: 0.45rem;
        font-size: 0.82rem;
        line-height: 1.2;
        font-weight: 600;
    }
    .member-dashboard-flow-meta--current {
        color: #2563eb;
    }
    .member-dashboard-flow-date {
        margin-top: 0.45rem;
        font-size: 0.82rem;
        line-height: 1.2;
        color: #6b7280;
        min-height: 1.05rem;
    }
    .member-dashboard-flow-legend {
        display: flex;
        flex-wrap: wrap;
        gap: 1.4rem;
    }
    .member-dashboard-flow-legend-item {
        display: inline-flex;
        align-items: center;
        gap: 0.65rem;
        font-size: 0.95rem;
        font-weight: 600;
        color: #334155;
    }
    .member-dashboard-flow-dot {
        width: 1rem;
        height: 1rem;
        border-radius: 9999px;
        display: inline-block;
    }
    .member-dashboard-flow-dot--completed {
        background: #22c55e;
    }
    .member-dashboard-flow-dot--current {
        background: #2563eb;
    }
    .member-dashboard-flow-dot--pending {
        background: #f59e0b;
    }
    .member-dashboard-flow-dot--not-started {
        background: #d1d5db;
    }
    .member-dashboard-flow-summary-grid {
        display: grid;
        gap: 0.75rem;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
    }
    .member-dashboard-flow-summary-grid > div {
        min-width: 0;
    }
    .member-dashboard-mobile-flow {
        display: none;
    }
    .member-dashboard-mobile-step {
        display: grid;
        grid-template-columns: 3.75rem minmax(0, 1fr);
        gap: 0.9rem;
        align-items: flex-start;
    }
    .member-dashboard-mobile-rail {
        display: flex;
        flex-direction: column;
        align-items: center;
    }
    .member-dashboard-mobile-node {
        position: relative;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 3.75rem;
        height: 3.75rem;
        border-width: 3px;
        border-radius: 9999px;
        background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
    }
    .member-dashboard-mobile-line {
        width: 0.2rem;
        min-height: 2.9rem;
        margin-top: 0.45rem;
        border-radius: 9999px;
        background: #d1d5db;
    }
    .member-dashboard-mobile-line--completed {
        background: #22c55e;
    }
    .member-dashboard-mobile-line--current {
        background: #2563eb;
    }
    .member-dashboard-mobile-body {
        min-width: 0;
        padding-top: 0.2rem;
    }
    .member-dashboard-mobile-step-number {
        font-size: 0.8rem;
        line-height: 1;
        font-weight: 800;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: #94a3b8;
    }
    .member-dashboard-mobile-title {
        margin-top: 0.4rem;
        font-size: 1.02rem;
        line-height: 1.25;
        font-weight: 800;
        color: #172033;
        overflow-wrap: anywhere;
    }
    .member-dashboard-mobile-detail {
        margin-top: 0.18rem;
        font-size: 0.82rem;
        line-height: 1.25;
        font-weight: 700;
        color: #64748b;
        overflow-wrap: anywhere;
    }
    .member-dashboard-mobile-status {
        margin-top: 0.45rem;
        font-size: 0.95rem;
        line-height: 1.2;
        font-weight: 700;
    }
    .member-dashboard-mobile-meta {
        margin-top: 0.25rem;
        font-size: 0.84rem;
        line-height: 1.2;
        font-weight: 600;
        color: #2563eb;
    }
    .member-dashboard-mobile-date {
        margin-top: 0.35rem;
        font-size: 0.84rem;
        line-height: 1.25;
        color: #64748b;
    }
    @media (min-width: 1024px) {
        .member-dashboard-flow-shell {
            overflow-x: visible;
        }
        .member-dashboard-flow-track {
            width: 100%;
            min-width: 0;
            padding-top: 0.7rem;
        }
        .member-dashboard-flow-list {
            width: 100%;
            min-width: 0;
            justify-content: space-between;
        }
        .member-dashboard-flow-step-wrap {
            flex: 1 1 0;
            min-width: 0;
            align-items: flex-start;
        }
        .member-dashboard-flow-step {
            width: auto;
            flex: 1 1 0;
            min-width: 0;
        }
        .member-dashboard-flow-connector {
            width: clamp(1rem, 1.6vw, 1.8rem);
            flex: 0 0 clamp(1rem, 1.6vw, 1.8rem);
        }
        .member-dashboard-flow-label {
            font-size: 0.82rem;
        }
        .member-dashboard-flow-status {
            font-size: 0.8rem;
        }
        .member-dashboard-flow-date,
        .member-dashboard-flow-meta,
        .member-dashboard-flow-detail {
            font-size: 0.72rem;
        }
    }
    @media (max-width: 768px) {
        .member-dashboard-mobile-flow {
            display: block;
        }
        .member-dashboard-flow-shell--desktop {
            display: none;
        }
        .member-dashboard-flow-track {
            padding-top: 0.55rem;
        }
        .member-dashboard-flow-step {
            width: 7.25rem;
            flex-basis: 7.25rem;
        }
        .member-dashboard-flow-node {
            width: 4.55rem;
            height: 4.55rem;
        }
        .member-dashboard-flow-icon svg {
            width: 2rem;
            height: 2rem;
        }
        .member-dashboard-flow-connector {
            width: 1.55rem;
            flex-basis: 1.55rem;
            margin-top: 2.15rem;
        }
        .member-dashboard-flow-check {
            right: 0.55rem;
            width: 1.55rem;
            height: 1.55rem;
            font-size: 0.8rem;
        }
        .member-dashboard-flow-number {
            font-size: 2rem;
        }
        .member-dashboard-flow-label {
            font-size: 0.8rem;
            min-height: 1.95rem;
        }
        .member-dashboard-flow-status,
        .member-dashboard-flow-date,
        .member-dashboard-flow-meta,
        .member-dashboard-flow-detail {
            font-size: 0.76rem;
        }
    }
    @media (min-width: 769px) and (max-width: 1023px) {
        .member-dashboard-flow-step {
            width: 8.2rem;
            flex-basis: 8.2rem;
        }
        .member-dashboard-flow-node {
            width: 5.3rem;
            height: 5.3rem;
        }
        .member-dashboard-flow-icon svg {
            width: 2.25rem;
            height: 2.25rem;
        }
        .member-dashboard-flow-connector {
            width: 1.9rem;
            flex-basis: 1.9rem;
            margin-top: 2.55rem;
        }
        .member-dashboard-flow-check {
            right: 0.72rem;
        }
        .member-dashboard-flow-number {
            font-size: 2.25rem;
        }
        .member-dashboard-flow-label {
            font-size: 0.82rem;
        }
    }
</style>

<%!
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

private static String[] splitLabel(String label) {
    if (label == null) {
        return new String[] {""};
    }
    if ("Awaiting Guarantors".equals(label)) {
        return new String[] {"Awaiting", "Guarantors"};
    }
    if ("All Guarantors Approved".equals(label)) {
        return new String[] {"All Guarantors", "Approved"};
    }
    if ("On Review By Manager".equals(label)) {
        return new String[] {"On Review", "By Manager"};
    }
    if ("On Review By Board".equals(label)) {
        return new String[] {"On Review", "By Board"};
    }
    if ("Accepted Loan".equals(label)) {
        return new String[] {"Accepted", "Loan"};
    }
    return new String[] {label};
}

private static long longValue(Object value) {
    if (value instanceof Number) {
        return ((Number) value).longValue();
    }
    if (value == null) {
        return 0L;
    }
    try {
        return Long.parseLong(String.valueOf(value));
    } catch (Exception ex) {
        return 0L;
    }
}
%>

<%
List<Map<String, Object>> statusRows = (List<Map<String, Object>>) request.getAttribute("statusChartRows");
if (statusRows == null) {
    statusRows = new ArrayList<Map<String, Object>>();
}
List<Map<String, Object>> activeLoanRows = (List<Map<String, Object>>) request.getAttribute("activeLoanChartRows");
if (activeLoanRows == null) {
    activeLoanRows = new ArrayList<Map<String, Object>>();
}

int statusChartWidth = 760;
int statusChartHeight = 350;
int statusLeftPad = 58;
int statusRightPad = 20;
int statusTopPad = 20;
int statusBottomPad = 108;
int statusPlotWidth = statusChartWidth - statusLeftPad - statusRightPad;
int statusPlotHeight = statusChartHeight - statusTopPad - statusBottomPad;

long maxStatusCount = 1L;
for (int i = 0; i < statusRows.size(); i++) {
    Map<String, Object> row = statusRows.get(i);
    long count = longValue(row.get("count"));
    if (count > maxStatusCount) {
        maxStatusCount = count;
    }
}
if (maxStatusCount <= 0L) {
    maxStatusCount = 1L;
}

StringBuilder statusGrid = new StringBuilder();
StringBuilder statusYLabels = new StringBuilder();
int statusTicks = 4;
for (int i = 0; i <= statusTicks; i++) {
    double ratio = (double) i / (double) statusTicks;
    int y = statusTopPad + (int) Math.round(statusPlotHeight - (statusPlotHeight * ratio));
    long tickValue = Math.round(maxStatusCount * ratio);
    statusGrid.append("<line x1='").append(statusLeftPad).append("' y1='").append(y)
        .append("' x2='").append(statusChartWidth - statusRightPad).append("' y2='").append(y)
        .append("' stroke='#E2E8F0' stroke-width='1' />");
    statusYLabels.append("<text x='").append(statusLeftPad - 10).append("' y='").append(y + 4)
        .append("' text-anchor='end' fill='#64748B' font-size='11' font-family='Manrope'>")
        .append(tickValue).append("</text>");
}

StringBuilder statusBars = new StringBuilder();
StringBuilder statusLabels = new StringBuilder();
StringBuilder statusValues = new StringBuilder();
if (!statusRows.isEmpty()) {
    double slotWidth = (double) statusPlotWidth / (double) statusRows.size();
    double barWidth = Math.min(62d, Math.max(28d, slotWidth * 0.56d));
    for (int i = 0; i < statusRows.size(); i++) {
        Map<String, Object> row = statusRows.get(i);
        long count = longValue(row.get("count"));
        String label = String.valueOf(row.get("label"));
        String color = String.valueOf(row.get("color"));
        double x = statusLeftPad + (slotWidth * i) + ((slotWidth - barWidth) / 2.0d);
        double barHeight = count <= 0L ? 0d : Math.max(18d, (count / (double) maxStatusCount) * (statusPlotHeight - 10));
        double y = statusTopPad + statusPlotHeight - barHeight;
        double centerX = x + (barWidth / 2.0d);

        statusBars.append("<rect x='").append((int) Math.round(x)).append("' y='").append((int) Math.round(y))
            .append("' width='").append((int) Math.round(barWidth)).append("' height='").append((int) Math.round(barHeight))
            .append("' rx='5' fill='").append(esc(color)).append("'></rect>");
        statusBars.append("<rect class='dashboard-status-hover' x='").append((int) Math.round(x - 6))
            .append("' y='").append(statusTopPad).append("' width='").append((int) Math.round(barWidth + 12))
            .append("' height='").append(statusPlotHeight)
            .append("' fill='transparent' data-label='").append(esc(label))
            .append("' data-value='").append(count).append("'></rect>");

        statusValues.append("<text x='").append((int) Math.round(centerX)).append("' y='")
            .append((int) Math.round((count <= 0L ? statusTopPad + statusPlotHeight + 2 : y) - 10))
            .append("' text-anchor='middle' fill='#172033' font-size='12' font-weight='700' font-family='Manrope'>")
            .append(count).append("</text>");

        String[] pieces = splitLabel(label);
        statusLabels.append("<text x='").append((int) Math.round(centerX)).append("' y='")
            .append(statusChartHeight - 38)
            .append("' text-anchor='middle' fill='#475569' font-size='11' font-weight='600' font-family='Manrope'>");
        for (int j = 0; j < pieces.length; j++) {
            statusLabels.append("<tspan x='").append((int) Math.round(centerX)).append("' dy='")
                .append(j == 0 ? 0 : 14).append("'>").append(esc(pieces[j])).append("</tspan>");
        }
        statusLabels.append("</text>");
    }
}

int loanChartWidth = 720;
int loanChartHeight = Math.max(240, 110 + (activeLoanRows.size() * 72));
int loanLeftPad = 146;
int loanRightPad = 80;
int loanTopPad = 24;
int loanBottomPad = 30;
int loanPlotWidth = loanChartWidth - loanLeftPad - loanRightPad;

long maxLoanDays = 1L;
for (int i = 0; i < activeLoanRows.size(); i++) {
    Map<String, Object> row = activeLoanRows.get(i);
    long daysLeft = longValue(row.get("daysLeft"));
    long totalDays = longValue(row.get("totalDays"));
    long compareValue = Math.max(daysLeft, totalDays);
    if (compareValue > maxLoanDays) {
        maxLoanDays = compareValue;
    }
}
if (maxLoanDays <= 0L) {
    maxLoanDays = 1L;
}

StringBuilder loanBars = new StringBuilder();
StringBuilder loanAxis = new StringBuilder();
StringBuilder loanTickLabels = new StringBuilder();
StringBuilder loanRowLabels = new StringBuilder();

for (int i = 0; i < 5; i++) {
    double ratio = (double) i / 4.0d;
    int x = loanLeftPad + (int) Math.round(loanPlotWidth * ratio);
    long tickValue = Math.round(maxLoanDays * ratio);
    loanAxis.append("<line x1='").append(x).append("' y1='").append(loanTopPad)
        .append("' x2='").append(x).append("' y2='").append(loanChartHeight - loanBottomPad)
        .append("' stroke='#E2E8F0' stroke-width='1' />");
    loanTickLabels.append("<text x='").append(x).append("' y='").append(loanChartHeight - 8)
        .append("' text-anchor='middle' fill='#64748B' font-size='11' font-family='Manrope'>")
        .append(tickValue).append("d</text>");
}

for (int i = 0; i < activeLoanRows.size(); i++) {
    Map<String, Object> row = activeLoanRows.get(i);
    long daysLeft = longValue(row.get("daysLeft"));
    String loanId = String.valueOf(row.get("loanId"));
    String amountLabel = String.valueOf(row.get("amountLabel"));
    String dueDate = row.get("finalDueDate") == null ? "-" : String.valueOf(row.get("finalDueDate"));
    String countdown = String.valueOf(row.get("countdown"));
    int y = loanTopPad + (i * 72);
    int barY = y + 26;
    int barWidth = (int) Math.round((daysLeft / (double) maxLoanDays) * loanPlotWidth);
    if (daysLeft > 0L && barWidth < 10) {
        barWidth = 10;
    }

    loanBars.append("<rect x='").append(loanLeftPad).append("' y='").append(barY)
        .append("' width='").append(loanPlotWidth).append("' height='16' rx='8' fill='#E2E8F0'></rect>");
    loanBars.append("<rect x='").append(loanLeftPad).append("' y='").append(barY)
        .append("' width='").append(barWidth).append("' height='16' rx='8' fill='#2F348D'></rect>");
    loanBars.append("<rect class='dashboard-loan-hover' x='").append(loanLeftPad).append("' y='").append(y + 4)
        .append("' width='").append(loanPlotWidth).append("' height='42' fill='transparent'")
        .append(" data-label='").append(esc(loanId)).append("'")
        .append(" data-amount='").append(esc(amountLabel)).append("'")
        .append(" data-days='").append(daysLeft).append("'")
        .append(" data-due='").append(esc(dueDate)).append("'")
        .append(" data-countdown='").append(esc(countdown)).append("'></rect>");
    loanBars.append("<text x='").append(loanLeftPad + barWidth + 8).append("' y='").append(barY + 12)
        .append("' fill='#172033' font-size='12' font-weight='700' font-family='Manrope'>")
        .append(daysLeft).append(" d</text>");

    loanRowLabels.append("<text x='").append(loanLeftPad - 12).append("' y='").append(y + 18)
        .append("' text-anchor='end' fill='#172033' font-size='12' font-weight='700' font-family='Manrope'>")
        .append(esc(loanId)).append("</text>");
    loanRowLabels.append("<text x='").append(loanLeftPad - 12).append("' y='").append(y + 34)
        .append("' text-anchor='end' fill='#64748B' font-size='11' font-family='Manrope'>")
        .append(esc(amountLabel)).append("</text>");
}
%>

<section class="space-y-4">
    <div class="erp-page-header">
        <p class="erp-breadcrumb"><spring:message code="dashboard.breadcrumb" /></p>
        <h1 class="erp-page-title" data-sticky-title-source="true"><spring:message code="dashboard.title" /></h1>
        <p class="erp-page-heading"><spring:message code="dashboard.welcome" />,
            <c:choose>
                <c:when test="${not empty currentMember and not empty currentMember.fullName}">
                    ${currentMember.fullName}
                </c:when>
                <c:otherwise>
                    <sec:authentication property="principal.username" />
                </c:otherwise>
            </c:choose>
        </p>
        <p class="erp-page-subtitle"><spring:message code="dashboard.subtitle" /></p>
    </div>

    <div class="erp-stat-grid">
        <div class="erp-stat-card erp-stat-blue">
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label"><spring:message code="dashboard.stat.applications.label" /></p>
                    <p class="erp-stat-value">${totalApplications}</p>
                    <p class="erp-stat-meta"><spring:message code="dashboard.stat.applications.meta" /></p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M4 3a1 1 0 00-1 1v12a1 1 0 001 1h12a1 1 0 001-1V7.414A1 1 0 0016.707 7L13 3.293A1 1 0 0012.293 3H4z"/></svg>
                </span>
            </div>
            <div class="erp-stat-footer">
                <span><spring:message code="dashboard.stat.applications.footer" /> ${loansAwaitingDecision}</span>
            </div>
        </div>
        <div class="erp-stat-card erp-stat-green">
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label"><spring:message code="dashboard.stat.activeLoans.label" /></p>
                    <p class="erp-stat-value">${activeLoanCount}</p>
                    <p class="erp-stat-meta"><spring:message code="dashboard.stat.activeLoans.meta" /></p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M4 4h12v3H4V4zm0 5h12v7H4V9zm2 2v3h4v-3H6z"/></svg>
                </span>
            </div>
            <div class="erp-stat-footer">
                <span><spring:message code="dashboard.stat.activeLoans.footer" /> ${archivedApplicationCount}</span>
            </div>
        </div>
        <div class="erp-stat-card erp-stat-amber">
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label"><spring:message code="dashboard.stat.guarantorRequests.label" /></p>
                    <p class="erp-stat-value">${pendingGuaranteeApprovals}</p>
                    <p class="erp-stat-meta"><spring:message code="dashboard.stat.guarantorRequests.meta" /></p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M10 2a4 4 0 00-4 4v2H5a2 2 0 00-2 2v5a3 3 0 003 3h8a3 3 0 003-3v-5a2 2 0 00-2-2h-1V6a4 4 0 00-4-4z"/></svg>
                </span>
            </div>
            <div class="erp-stat-footer">
                <span><spring:message code="dashboard.stat.guarantorRequests.footer" /> ${pendingGuaranteeApprovals}</span>
            </div>
        </div>
        <div class="erp-stat-card erp-stat-red">
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label"><spring:message code="dashboard.stat.archivedRejections.label" /></p>
                    <p class="erp-stat-value">${rejectedLoanCount}</p>
                    <p class="erp-stat-meta"><spring:message code="dashboard.stat.archivedRejections.meta" /></p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-10.293l-4 4a1 1 0 01-1.414 0l-2-2 1.414-1.414L9 9.586l3.293-3.293 1.414 1.414z"/></svg>
                </span>
            </div>
            <div class="erp-stat-footer">
                <span><spring:message code="dashboard.stat.archivedRejections.footer" /> ${currentApplicationCount}</span>
            </div>
        </div>
    </div>

    <section class="erp-panel" data-live-account-status-url="${pageContext.request.contextPath}/app/dashboard/external-account-status">
        <div class="erp-panel-header">
            <p class="erp-panel-title"><spring:message code="dashboard.panel.financialStatus" /></p>
        </div>
        <div class="erp-panel-body">
            <div>
                <div>
                    <p class="erp-widget-title"><spring:message code="dashboard.widget.memberBalances" /></p>
                    <h2 class="erp-widget-heading"><spring:message code="dashboard.widget.savingsSharesOverview" /></h2>
                </div>
            </div>

            <div class="mt-4 grid gap-4 md:grid-cols-2">
                <div class="rounded-md border border-slate-200 bg-white px-4 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500"><spring:message code="dashboard.savings.label" /></p>
                    <p class="mt-2 text-2xl font-bold text-sacco-ink" data-live-account-status-savings>${dashboardExternalAccountStatus.savingsLabel}</p>
                    <p class="mt-2 text-sm text-slate-500"><spring:message code="dashboard.savings.meta" /></p>
                </div>

                <div class="rounded-md border border-slate-200 bg-white px-4 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500"><spring:message code="dashboard.shares.label" /></p>
                    <p class="mt-2 text-2xl font-bold text-sacco-ink" data-live-account-status-shares>${dashboardExternalAccountStatus.sharesLabel}</p>
                    <p class="mt-2 text-sm text-slate-500"><spring:message code="dashboard.shares.meta" /></p>
                </div>
            </div>
            <p class="mt-4 text-sm text-slate-500" data-live-account-status-message>${dashboardExternalAccountStatus.statusMessage}</p>
        </div>
    </section>

    <div class="grid min-w-0 gap-4 xl:grid-cols-[minmax(0,1.12fr)_minmax(0,0.88fr)]">
        <section class="erp-panel min-w-0">
            <div class="erp-panel-header">
                <p class="erp-panel-title"><spring:message code="dashboard.currentLoanApplication" text="Current Loan Application" /></p>
            </div>
            <div class="erp-panel-body min-w-0">
                <div class="erp-toolbar">
                    <div>
                        <p class="erp-widget-title"><spring:message code="dashboard.workflowProgress" text="Workflow Progress" /></p>
                        <h2 class="erp-widget-heading"><spring:message code="dashboard.trackCurrentLoan" text="Track The Status Of Your Current Loan" /></h2>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm font-semibold text-slate-600">
                        ${currentApplicationCount} current record(s)
                    </div>
                </div>

                <c:choose>
                    <c:when test="${not empty currentWorkflowApplication}">
                        <div class="mt-4 min-w-0 overflow-hidden rounded-md border border-slate-200 bg-white p-4 sm:p-5">
                            <div class="member-dashboard-flow-summary-grid">
                                <div class="min-w-0 rounded-md border border-slate-200 bg-slate-50 px-4 py-3">
                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="loan.applicationId" text="Loan Application ID" /></p>
                                    <p class="mt-2 text-sm font-semibold text-sacco-ink">${currentWorkflowApplicationNumber}</p>
                                </div>
                                <div class="min-w-0 rounded-md border border-slate-200 bg-slate-50 px-4 py-3">
                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="reports.loanProduct" text="Loan Product" /></p>
                                    <p class="mt-2 text-sm font-semibold text-sacco-ink">${currentWorkflowProductName}</p>
                                </div>
                                <c:if test="${not empty currentWorkflowApplicantReason}">
                                    <div class="min-w-0 rounded-md border border-slate-200 bg-slate-50 px-4 py-3">
                                        <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="loan.purpose" text="Loan Purpose" /></p>
                                        <p class="mt-2 text-sm font-semibold text-sacco-ink">${currentWorkflowApplicantReason}</p>
                                    </div>
                                </c:if>
                                <div class="min-w-0 rounded-md border border-slate-200 bg-slate-50 px-4 py-3">
                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="common.amount" text="Amount" /></p>
                                    <p class="mt-2 text-sm font-semibold text-sacco-ink">${currentWorkflowAmountLabel}</p>
                                </div>
                                <div class="min-w-0 rounded-md border border-slate-200 bg-slate-50 px-4 py-3">
                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="review.currentStatus" text="Current Status" /></p>
                                    <p class="mt-2 text-sm font-semibold text-sacco-ink">${currentWorkflowStatusLabel}</p>
                                </div>
                            </div>
                            <c:if test="${not empty currentWorkflowUpdatedAtLabel}">
                                <div class="mt-3 rounded border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-semibold text-slate-600">
                                    Updated ${currentWorkflowUpdatedAtLabel}
                                </div>
                            </c:if>
                            <c:if test="${not empty currentWorkflowApplication and currentWorkflowApplication.status eq 'FINAL_APPROVED' and empty currentWorkflowApplication.applicantDisbursementAcknowledgedAt}">
                                <div class="mt-3 flex flex-wrap items-center justify-between gap-3 rounded border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800">
                                    <span><spring:message code="loan.disbursement.readyAck" text="This loan is Final Approved and Disbursed." /></span>
                                    <form method="post" action="${pageContext.request.contextPath}/app/loan-applications/${currentWorkflowApplication.id}/acknowledge-disbursement" class="m-0">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                        <input type="hidden" name="returnTo" value="dashboard" />
                                        <button type="submit" class="app-btn btn-neutral"><spring:message code="common.acknowledge" text="Acknowledge" /></button>
                                    </form>
                                </div>
                            </c:if>

                            <div class="member-dashboard-mobile-flow mt-6 space-y-4">
                                <c:forEach items="${currentWorkflowSteps}" var="step" varStatus="loop">
                                    <div class="member-dashboard-mobile-step">
                                        <div class="member-dashboard-mobile-rail">
                                            <div class="member-dashboard-mobile-node ${step.nodeClasses}">
                                                <span class="member-dashboard-flow-icon ${step.iconClasses}" aria-hidden="true">
                                                    <c:choose>
                                                        <c:when test="${step.iconKey eq 'applicant'}">
                                                            <svg viewBox="0 0 24 24">
                                                                <circle cx="12" cy="8" r="3.2"></circle>
                                                                <path d="M5.5 18.2c1.9-3 4.1-4.4 6.5-4.4s4.6 1.4 6.5 4.4"></path>
                                                            </svg>
                                                        </c:when>
                                                        <c:when test="${step.iconKey eq 'guarantors'}">
                                                            <svg viewBox="0 0 24 24">
                                                                <circle cx="10" cy="8" r="3"></circle>
                                                                <path d="M4.8 17.8c1.6-2.8 3.5-4 5.2-4 1.8 0 3.8 1.2 5.4 4"></path>
                                                                <rect x="14.6" y="12.6" width="4.2" height="5" rx="0.8"></rect>
                                                                <path d="M15.7 12.6v-1.2a1 1 0 011-1h.2a1 1 0 011 1v1.2"></path>
                                                            </svg>
                                                        </c:when>
                                                        <c:when test="${step.iconKey eq 'manager'}">
                                                            <svg viewBox="0 0 24 24">
                                                                <circle cx="12" cy="7.8" r="3"></circle>
                                                                <path d="M6 18.3c1.8-3 4-4.4 6-4.4s4.2 1.4 6 4.4"></path>
                                                                <path d="M12 11.4v3.4"></path>
                                                                <path d="M10.9 15.1L12 16.3l1.1-1.2"></path>
                                                            </svg>
                                                        </c:when>
                                                        <c:when test="${step.iconKey eq 'review'}">
                                                            <svg viewBox="0 0 24 24">
                                                                <circle cx="10" cy="10" r="4.6"></circle>
                                                                <path d="M13.4 13.4L18.2 18.2"></path>
                                                                <path d="M8 10.2l1.4 1.4 2.8-3"></path>
                                                            </svg>
                                                        </c:when>
                                                        <c:when test="${step.iconKey eq 'disbursement'}">
                                                            <svg viewBox="0 0 24 24">
                                                                <path d="M5.5 9.2h13"></path>
                                                                <path d="M7 9.2V7.6h10v1.6"></path>
                                                                <path d="M6.8 9.2v7.2"></path>
                                                                <path d="M17.2 9.2v7.2"></path>
                                                                <path d="M4.8 16.4h14.4"></path>
                                                                <path d="M12 12v2.8"></path>
                                                                <path d="M10.4 13.8L12 15.4l1.6-1.6"></path>
                                                            </svg>
                                                        </c:when>
                                                        <c:otherwise>
                                                            <svg viewBox="0 0 24 24">
                                                                <path d="M4.5 9.4L12 5.8l7.5 3.6"></path>
                                                                <path d="M6.4 10.5v6.6"></path>
                                                                <path d="M10.2 10.5v6.6"></path>
                                                                <path d="M13.8 10.5v6.6"></path>
                                                                <path d="M17.6 10.5v6.6"></path>
                                                                <path d="M4.5 18.2h15"></path>
                                                            </svg>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </span>
                                                <c:if test="${step.stateKey eq 'completed'}">
                                                    <span class="member-dashboard-flow-check">&#10003;</span>
                                                </c:if>
                                            </div>
                                            <c:if test="${not loop.last}">
                                                <div class="member-dashboard-mobile-line ${step.connectorClasses eq 'member-dashboard-flow-connector--completed' ? 'member-dashboard-mobile-line--completed' : (step.connectorClasses eq 'member-dashboard-flow-connector--current' ? 'member-dashboard-mobile-line--current' : '')}"></div>
                                            </c:if>
                                        </div>
                                        <div class="member-dashboard-mobile-body">
                                            <p class="member-dashboard-mobile-step-number">Step ${step.stepNumber}</p>
                                            <p class="member-dashboard-mobile-title">${step.label}</p>
                                            <c:if test="${not empty step.detailLabel}">
                                                <p class="member-dashboard-mobile-detail">${step.detailLabel}</p>
                                            </c:if>
                                            <p class="member-dashboard-mobile-status ${step.textClasses}">${step.stateLabel}</p>
                                            <c:if test="${not empty step.metaLabel}">
                                                <p class="member-dashboard-mobile-meta">${step.metaLabel}</p>
                                            </c:if>
                                            <c:if test="${not empty step.dateLabel}">
                                                <p class="member-dashboard-mobile-date">${step.dateLabel}</p>
                                            </c:if>
                                        </div>
                                    </div>
                                </c:forEach>
                            </div>

                            <div class="member-dashboard-flow-shell member-dashboard-flow-shell--desktop mt-6">
                                <div class="member-dashboard-flow-track">
                                    <div class="member-dashboard-flow-list">
                                        <c:forEach items="${currentWorkflowSteps}" var="step" varStatus="loop">
                                            <div class="member-dashboard-flow-step-wrap">
                                                <div class="member-dashboard-flow-step">
                                                    <div class="member-dashboard-flow-node-wrap">
                                                        <div class="member-dashboard-flow-node ${step.nodeClasses}">
                                                            <span class="member-dashboard-flow-icon ${step.iconClasses}" aria-hidden="true">
                                                                <c:choose>
                                                                    <c:when test="${step.iconKey eq 'applicant'}">
                                                                        <svg viewBox="0 0 24 24">
                                                                            <circle cx="12" cy="8" r="3.2"></circle>
                                                                            <path d="M5.5 18.2c1.9-3 4.1-4.4 6.5-4.4s4.6 1.4 6.5 4.4"></path>
                                                                        </svg>
                                                                    </c:when>
                                                                    <c:when test="${step.iconKey eq 'guarantors'}">
                                                                        <svg viewBox="0 0 24 24">
                                                                            <circle cx="10" cy="8" r="3"></circle>
                                                                            <path d="M4.8 17.8c1.6-2.8 3.5-4 5.2-4 1.8 0 3.8 1.2 5.4 4"></path>
                                                                            <rect x="14.6" y="12.6" width="4.2" height="5" rx="0.8"></rect>
                                                                            <path d="M15.7 12.6v-1.2a1 1 0 011-1h.2a1 1 0 011 1v1.2"></path>
                                                                        </svg>
                                                                    </c:when>
                                                                    <c:when test="${step.iconKey eq 'manager'}">
                                                                        <svg viewBox="0 0 24 24">
                                                                            <circle cx="12" cy="7.8" r="3"></circle>
                                                                            <path d="M6 18.3c1.8-3 4-4.4 6-4.4s4.2 1.4 6 4.4"></path>
                                                                            <path d="M12 11.4v3.4"></path>
                                                                            <path d="M10.9 15.1L12 16.3l1.1-1.2"></path>
                                                                        </svg>
                                                                    </c:when>
                                                                    <c:when test="${step.iconKey eq 'review'}">
                                                                        <svg viewBox="0 0 24 24">
                                                                            <circle cx="10" cy="10" r="4.6"></circle>
                                                                            <path d="M13.4 13.4L18.2 18.2"></path>
                                                                            <path d="M8 10.2l1.4 1.4 2.8-3"></path>
                                                                        </svg>
                                                                    </c:when>
                                                                    <c:when test="${step.iconKey eq 'disbursement'}">
                                                                        <svg viewBox="0 0 24 24">
                                                                            <path d="M5.5 9.2h13"></path>
                                                                            <path d="M7 9.2V7.6h10v1.6"></path>
                                                                            <path d="M6.8 9.2v7.2"></path>
                                                                            <path d="M17.2 9.2v7.2"></path>
                                                                            <path d="M4.8 16.4h14.4"></path>
                                                                            <path d="M12 12v2.8"></path>
                                                                            <path d="M10.4 13.8L12 15.4l1.6-1.6"></path>
                                                                        </svg>
                                                                    </c:when>
                                                                    <c:otherwise>
                                                                        <svg viewBox="0 0 24 24">
                                                                            <path d="M4.5 9.4L12 5.8l7.5 3.6"></path>
                                                                            <path d="M6.4 10.5v6.6"></path>
                                                                            <path d="M10.2 10.5v6.6"></path>
                                                                            <path d="M13.8 10.5v6.6"></path>
                                                                            <path d="M17.6 10.5v6.6"></path>
                                                                            <path d="M4.5 18.2h15"></path>
                                                                        </svg>
                                                                    </c:otherwise>
                                                                </c:choose>
                                                            </span>
                                                        </div>
                                                        <c:if test="${step.stateKey eq 'completed'}">
                                                            <span class="member-dashboard-flow-check">&#10003;</span>
                                                        </c:if>
                                                    </div>
                                                    <p class="member-dashboard-flow-number ${step.numberClasses}">${step.stepNumber}</p>
                                                    <p class="member-dashboard-flow-label">${step.label}</p>
                                                    <c:if test="${not empty step.detailLabel}">
                                                        <p class="member-dashboard-flow-detail">${step.detailLabel}</p>
                                                    </c:if>
                                                    <p class="member-dashboard-flow-status ${step.textClasses}">${step.stateLabel}</p>
                                                    <c:if test="${not empty step.metaLabel}">
                                                        <p class="member-dashboard-flow-meta member-dashboard-flow-meta--current">${step.metaLabel}</p>
                                                    </c:if>
                                                    <c:if test="${not empty step.dateLabel}">
                                                        <p class="member-dashboard-flow-date">${step.dateLabel}</p>
                                                    </c:if>
                                                </div>
                                                <c:if test="${not loop.last}">
                                                    <div class="member-dashboard-flow-connector ${step.connectorClasses}"></div>
                                                </c:if>
                                            </div>
                                        </c:forEach>
                                    </div>
                                </div>
                            </div>

                            <div class="member-dashboard-flow-legend mt-6 border-t border-slate-200 pt-4">
                                <span class="member-dashboard-flow-legend-item">
                                    <span class="member-dashboard-flow-dot member-dashboard-flow-dot--completed"></span>
                                    Completed
                                </span>
                                <span class="member-dashboard-flow-legend-item">
                                    <span class="member-dashboard-flow-dot member-dashboard-flow-dot--current"></span>
                                    In progress
                                </span>
                                <span class="member-dashboard-flow-legend-item">
                                    <span class="member-dashboard-flow-dot member-dashboard-flow-dot--pending"></span>
                                    Pending
                                </span>
                                <span class="member-dashboard-flow-legend-item">
                                    <span class="member-dashboard-flow-dot member-dashboard-flow-dot--not-started"></span>
                                    Not started
                                </span>
                            </div>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="erp-section mt-4 text-center text-sm text-slate-500">
                            There is no current loan application to track right now.
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </section>

        <section class="erp-panel min-w-0 xl:self-start">
            <div class="erp-panel-header">
                <p class="erp-panel-title"><spring:message code="dashboard.activeLoans.title" text="Active Loans" /></p>
            </div>
            <div class="erp-panel-body min-w-0">
                <div class="erp-toolbar gap-3">
                    <div>
                        <p class="erp-widget-title">Repayment Progress</p>
                        <h2 class="erp-widget-heading"><spring:message code="dashboard.widget.timeLeftEachActiveLoan" /></h2>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm font-semibold text-slate-600">
                        ${activeLoanChartCount} <spring:message code="dashboard.chart.activeLoansCount" />
                    </div>
                </div>
                <c:choose>
                    <c:when test="${not empty activeLoanChartRows}">
                        <div class="mt-4 flex flex-wrap gap-4 text-xs font-semibold text-slate-500">
                            <span class="inline-flex items-center gap-2">
                                <span class="inline-block h-3 w-3 rounded-full" style="background:#2F348D;"></span>
                                Remaining time
                            </span>
                            <span class="inline-flex items-center gap-2">
                                <span class="inline-block h-3 w-3 rounded-full" style="background:#E2E8F0;"></span>
                                Time already used
                            </span>
                        </div>
                        <div class="mt-4 grid gap-4 lg:grid-cols-2">
                            <c:forEach items="${activeLoanChartRows}" var="loanRow">
                                <div class="min-w-0 overflow-hidden rounded border border-slate-200 bg-white p-4"
                                     data-repayment-timer="card"
                                     data-start-date="${loanRow.startDate}"
                                     data-final-due-date="${loanRow.finalDueDate}"
                                     data-total-days="${loanRow.totalDays}">
                                    <div class="grid gap-4 xl:grid-cols-[minmax(0,1fr)_152px] xl:items-center">
                                        <div class="min-w-0 overflow-hidden">
                                            <div class="flex flex-wrap items-start justify-between gap-3">
                                                <div>
                                                    <p class="erp-widget-title"><spring:message code="dashboard.repaymentTimer.label" /></p>
                                                    <h3 class="mt-1 text-lg font-bold text-sacco-ink"><spring:message code="dashboard.loanId.prefix" /> ${loanRow.loanId}</h3>
                                                    <p class="mt-1 text-sm font-semibold text-slate-600"><spring:message code="reports.loanProduct" text="Loan Product" />: ${loanRow.loanProductName}</p>
                                                    <c:if test="${not empty loanRow.applicantReason}">
                                                        <p class="mt-1 text-sm font-semibold text-slate-600"><spring:message code="loan.purpose" text="Loan Purpose" />: ${loanRow.applicantReason}</p>
                                                    </c:if>
                                                </div>
                                                <div class="flex flex-wrap items-center justify-end gap-2">
                                                    <span class="inline-flex items-center rounded-md border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] ${loanRow.repaymentStateClasses}">
                                                        <c:if test="${loanRow.repaymentStateCode eq 'PAID'}">&#10003;&nbsp;</c:if>${loanRow.repaymentStateLabel}
                                                    </span>
                                                    <a href="${pageContext.request.contextPath}/app/loan-applications/${loanRow.fullId}#repayment-plan"
                                                       class="app-btn btn-neutral inline-flex justify-center whitespace-nowrap px-3 py-2 text-sm">
                                                        View Repayment Schedule
                                                    </a>
                                                </div>
                                            </div>
                                            <p class="mt-1 text-sm text-slate-500"><spring:message code="dashboard.loanAmount.label" /> ${loanRow.amountLabel}</p>
                                            <div class="mt-4 grid grid-cols-2 gap-x-4 gap-y-3 text-sm text-slate-600">
                                                <div>
                                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.elapsed.label" /></p>
                                                    <p class="mt-1 font-semibold text-sacco-ink" data-repayment-timer="elapsed-days">${loanRow.elapsedDays} day(s)</p>
                                                </div>
                                                <div>
                                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.remaining.label" /></p>
                                                    <p class="mt-1 font-semibold text-sacco-ink" data-repayment-timer="days-left">${loanRow.daysLeft} day(s)</p>
                                                </div>
                                                <div class="col-span-2">
                                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.finalDueDate.label" /></p>
                                                    <p class="mt-1 font-semibold text-sacco-ink">${loanRow.finalDueDate}</p>
                                                </div>
                                            </div>
                                            <div class="mt-4 rounded-md border border-slate-200 bg-slate-50">
                                                <div class="border-b border-slate-200 px-3 py-2">
                                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.paymentSummary.title" /></p>
                                                </div>
                                                <c:choose>
                                                    <c:when test="${loanRow.paymentSummaryAvailable}">
                                                        <div class="grid gap-3 px-3 py-3 sm:grid-cols-2">
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.product.label" /></p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.loanDescription}</p>
                                                            </div>
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.lastPayment.label" /></p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.lastPaymentDate}</p>
                                                            </div>
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.outstanding.label" /></p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.totalOutstanding}</p>
                                                            </div>
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.principalPaid.label" /></p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.totalPrincipalPaid}</p>
                                                            </div>
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.outstandingPrincipal.label" /></p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.outstandingPrincipal}</p>
                                                            </div>
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.outstandingInterest.label" /></p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.outstandingInterest}</p>
                                                            </div>
                                                        </div>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <div class="px-3 py-3 text-sm text-slate-500">
                                                            <spring:message code="dashboard.paymentSummary.pending" />
                                                        </div>
                                                    </c:otherwise>
                                                </c:choose>
                                            </div>
                                        </div>

                                        <div class="mx-auto flex w-full max-w-[152px] flex-col items-center gap-3">
                                            <div class="relative flex h-28 w-28 items-center justify-center rounded-full sm:h-32 sm:w-32"
                                                data-repayment-timer="ring"
                                                 style="background: conic-gradient(#2F348D 0% ${loanRow.remainingPercent}%, #E2E8F0 ${loanRow.remainingPercent}% 100%);">
                                                <div class="flex h-18 w-18 flex-col items-center justify-center rounded-full bg-white text-center shadow-sm sm:h-20 sm:w-20">
                                                    <p class="text-[8px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.remaining.label" /></p>
                                                    <p class="mt-1 font-display text-xl text-sacco-ink sm:text-2xl" data-repayment-timer="remaining-percent">${loanRow.remainingPercent}%</p>
                                                    <p class="mt-1 text-[10px] text-slate-500" data-repayment-timer="countdown">${loanRow.countdown}</p>
                                                </div>
                                            </div>
                                            <div class="w-full rounded border border-slate-200 bg-slate-50 px-3 py-2 text-center">
                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400"><spring:message code="dashboard.repaymentProgress.label" /></p>
                                                <p class="mt-1 text-sm font-semibold text-sacco-ink" data-repayment-timer="progress-text">${loanRow.remainingPercent}% remaining</p>
                                            </div>
                                            <c:if test="${loanRow.canDismiss}">
                                                <form method="post" action="${pageContext.request.contextPath}/app/dashboard/active-loans/${loanRow.fullId}/seen" class="w-full">
                                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                                    <button type="submit" class="app-btn btn-neutral w-full justify-center text-sm">
                                                        <spring:message code="dashboard.seen" />
                                                    </button>
                                                </form>
                                            </c:if>
                                        </div>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="erp-section mt-4 text-center">
                            <c:choose>
                                <c:when test="${activeLoanCount gt 0}">
                                    <p class="font-display text-xl text-sacco-ink"><spring:message code="dashboard.repaymentTimers.empty.title" /></p>
                                    <p class="mt-2 text-sm leading-6 text-slate-500"><spring:message code="dashboard.repaymentTimers.empty.subtitle" /></p>
                                </c:when>
                                <c:otherwise>
                                    <p class="font-display text-xl text-sacco-ink"><spring:message code="dashboard.activeLoans.empty.title" /></p>
                                    <p class="mt-2 text-sm leading-6 text-slate-500"><spring:message code="dashboard.activeLoans.empty.subtitle" /></p>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </section>
    </div>
</section>

<script>
    (function () {
        function parseLocalDate(value) {
            if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
                return null;
            }
            var parts = value.split('-');
            return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
        }

        function formatDayLabel(days) {
            return days + ' day(s)';
        }

        function formatPercent(value) {
            if (!isFinite(value)) {
                return '0%';
            }
            var rounded = Math.max(0, Math.min(100, value));
            if (rounded === 0 || rounded === 100) {
                return Math.round(rounded) + '%';
            }
            return rounded.toFixed(1) + '%';
        }

        function formatCountdown(diffMs) {
            var minuteMs = 60 * 1000;
            var hourMs = 60 * minuteMs;
            var dayMs = 24 * hourMs;

            if (diffMs <= 0) {
                var overdueMs = Math.abs(diffMs);
                var overdueDays = Math.floor(overdueMs / dayMs);
                if (overdueDays >= 1) {
                    return overdueDays === 1 ? 'Overdue by 1 day' : 'Overdue by ' + overdueDays + ' days';
                }
                var overdueHours = Math.max(1, Math.floor(overdueMs / hourMs));
                return overdueHours === 1 ? 'Overdue by 1 hour' : 'Overdue by ' + overdueHours + ' hours';
            }

            var days = Math.floor(diffMs / dayMs);
            var hours = Math.floor((diffMs % dayMs) / hourMs);
            var minutes = Math.floor((diffMs % hourMs) / minuteMs);

            if (days > 1) {
                return days + 'd ' + hours + 'h left';
            }
            if (days === 1) {
                return '1d ' + hours + 'h left';
            }
            if (hours > 0) {
                return hours + 'h ' + minutes + 'm left';
            }
            return Math.max(1, minutes) + 'm left';
        }

        function updateRepaymentTimerCard(card) {
            if (!card) {
                return;
            }

            var startDate = parseLocalDate(card.getAttribute('data-start-date'));
            var finalDueDate = parseLocalDate(card.getAttribute('data-final-due-date'));
            if (!startDate || !finalDueDate) {
                return;
            }

            var now = new Date();
            var startAt = new Date(startDate.getFullYear(), startDate.getMonth(), startDate.getDate(), 0, 0, 0, 0);
            var dueAt = new Date(finalDueDate.getFullYear(), finalDueDate.getMonth(), finalDueDate.getDate(), 23, 59, 59, 999);
            var totalMs = Math.max(dueAt.getTime() - startAt.getTime(), 1);
            var elapsedMs = Math.max(0, Math.min(totalMs, now.getTime() - startAt.getTime()));
            var remainingMs = Math.max(0, dueAt.getTime() - now.getTime());
            var dayMs = 24 * 60 * 60 * 1000;

            var elapsedDays = Math.max(0, Math.floor(elapsedMs / dayMs));
            var daysLeft = Math.max(0, Math.ceil(remainingMs / dayMs));
            var remainingPercent = Math.max(0, Math.min(100, (remainingMs / totalMs) * 100));

            var elapsedEl = card.querySelector('[data-repayment-timer="elapsed-days"]');
            var daysLeftEl = card.querySelector('[data-repayment-timer="days-left"]');
            var percentEl = card.querySelector('[data-repayment-timer="remaining-percent"]');
            var countdownEl = card.querySelector('[data-repayment-timer="countdown"]');
            var progressEl = card.querySelector('[data-repayment-timer="progress-text"]');
            var ringEl = card.querySelector('[data-repayment-timer="ring"]');

            if (elapsedEl) {
                elapsedEl.textContent = formatDayLabel(elapsedDays);
            }
            if (daysLeftEl) {
                daysLeftEl.textContent = formatDayLabel(daysLeft);
            }
            if (percentEl) {
                percentEl.textContent = formatPercent(remainingPercent);
            }
            if (countdownEl) {
                countdownEl.textContent = formatCountdown(dueAt.getTime() - now.getTime());
            }
            if (progressEl) {
                progressEl.textContent = formatPercent(remainingPercent) + ' remaining';
            }
            if (ringEl) {
                ringEl.style.background = 'conic-gradient(#2F348D 0% ' + remainingPercent + '%, #E2E8F0 ' + remainingPercent + '% 100%)';
            }
        }

        function startRepaymentTimers() {
            var timerCards = document.querySelectorAll('[data-repayment-timer="card"]');
            if (!timerCards.length) {
                return;
            }

            function refresh() {
                Array.prototype.forEach.call(timerCards, updateRepaymentTimerCard);
            }

            refresh();
            window.setInterval(refresh, 60000);
        }

        startRepaymentTimers();

    }());
</script>

<%@ include file="../fragments/live-account-status-hydration.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
