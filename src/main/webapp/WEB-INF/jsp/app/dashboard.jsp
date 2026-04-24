<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

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
        <p class="erp-breadcrumb">Member Workspace / Dashboard</p>
        <h1 class="erp-page-title" data-sticky-title-source="true">Dashboard</h1>
        <p class="erp-page-heading">Welcome,
            <c:choose>
                <c:when test="${not empty currentMember and not empty currentMember.fullName}">
                    ${currentMember.fullName}
                </c:when>
                <c:otherwise>
                    <sec:authentication property="principal.username" />
                </c:otherwise>
            </c:choose>
        </p>
        <p class="erp-page-subtitle">
            Track applications in progress, follow guarantor approvals, and monitor every active loan repayment timeline from one SACCO workspace.
        </p>
    </div>

    <div class="erp-stat-grid">
        <div class="erp-stat-card erp-stat-blue">
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label">Applications</p>
                    <p class="erp-stat-value">${totalApplications}</p>
                    <p class="erp-stat-meta">Applications still in workflow and waiting for a final outcome</p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M4 3a1 1 0 00-1 1v12a1 1 0 001 1h12a1 1 0 001-1V7.414A1 1 0 0016.707 7L13 3.293A1 1 0 0012.293 3H4z"/></svg>
                </span>
            </div>
            <div class="erp-stat-footer">
                <span>Awaiting decision: ${loansAwaitingDecision}</span>
            </div>
        </div>
        <div class="erp-stat-card erp-stat-green">
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label">Active Loans</p>
                    <p class="erp-stat-value">${activeLoanCount}</p>
                    <p class="erp-stat-meta">Accepted loans that are still active and tracked in repayment timelines</p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M4 4h12v3H4V4zm0 5h12v7H4V9zm2 2v3h4v-3H6z"/></svg>
                </span>
            </div>
            <div class="erp-stat-footer">
                <span>Archived: ${archivedApplicationCount}</span>
            </div>
        </div>
        <div class="erp-stat-card erp-stat-amber">
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label">Guarantee Requests</p>
                    <p class="erp-stat-value">${pendingGuaranteeApprovals}</p>
                    <p class="erp-stat-meta">Requests that still need your guarantor decision</p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M10 2a4 4 0 00-4 4v2H5a2 2 0 00-2 2v5a3 3 0 003 3h8a3 3 0 003-3v-5a2 2 0 00-2-2h-1V6a4 4 0 00-4-4z"/></svg>
                </span>
            </div>
            <div class="erp-stat-footer">
                <span>Queue: ${pendingGuaranteeApprovals}</span>
            </div>
        </div>
        <div class="erp-stat-card erp-stat-red">
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label">Archived Rejections</p>
                    <p class="erp-stat-value">${rejectedLoanCount}</p>
                    <p class="erp-stat-meta">Rejected applications moved out of the active workspace and into archives</p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-10.293l-4 4a1 1 0 01-1.414 0l-2-2 1.414-1.414L9 9.586l3.293-3.293 1.414 1.414z"/></svg>
                </span>
            </div>
            <div class="erp-stat-footer">
                <span>Current records: ${currentApplicationCount}</span>
            </div>
        </div>
    </div>

    <section class="erp-panel">
        <div class="erp-panel-header">
            <p class="erp-panel-title">Financial Status</p>
        </div>
        <div class="erp-panel-body">
            <div>
                <div>
                    <p class="erp-widget-title">Member Balances</p>
                    <h2 class="erp-widget-heading">Savings And Shares Overview</h2>
                </div>
            </div>

            <div class="mt-4 grid gap-4 md:grid-cols-2">
                <div class="rounded-md border border-slate-200 bg-white px-4 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">Savings</p>
                    <p class="mt-2 text-2xl font-bold text-sacco-ink">${dashboardExternalAccountStatus.savingsLabel}</p>
                    <p class="mt-2 text-sm text-slate-500">Current savings balance available to this member.</p>
                </div>

                <div class="rounded-md border border-slate-200 bg-white px-4 py-4">
                    <p class="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">Shares</p>
                    <p class="mt-2 text-2xl font-bold text-sacco-ink">${dashboardExternalAccountStatus.sharesLabel}</p>
                    <p class="mt-2 text-sm text-slate-500">Current shares balance recorded for this member.</p>
                </div>
            </div>
        </div>
    </section>

    <div class="grid gap-4 2xl:grid-cols-[minmax(0,1.25fr)_minmax(0,0.95fr)]">
        <section class="erp-panel">
            <div class="erp-panel-header">
                <p class="erp-panel-title">Application Status Bar Graph</p>
            </div>
            <div class="erp-panel-body">
                <div class="erp-toolbar">
                    <div>
                        <p class="erp-widget-title">Workflow Distribution</p>
                        <h2 class="erp-widget-heading">Applications By Status</h2>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm font-semibold text-slate-600">
                        ${currentApplicationCount} current record(s)
                    </div>
                </div>

                <c:choose>
                    <c:when test="${not empty statusChartRows}">
                        <div id="statusChartWrap" class="relative overflow-hidden rounded border border-slate-200 bg-white p-3 sm:p-4">
                            <svg viewBox="0 0 <%= statusChartWidth %> <%= statusChartHeight %>" class="block w-full">
                                <rect x="0" y="0" width="<%= statusChartWidth %>" height="<%= statusChartHeight %>" rx="8" fill="#ffffff"></rect>
                                <%= statusGrid.toString() %>
                                <line x1="<%= statusLeftPad %>" y1="<%= statusTopPad %>" x2="<%= statusLeftPad %>" y2="<%= statusChartHeight - statusBottomPad %>" stroke="#CBD5E1" stroke-width="1.5"></line>
                                <line x1="<%= statusLeftPad %>" y1="<%= statusChartHeight - statusBottomPad %>" x2="<%= statusChartWidth - statusRightPad %>" y2="<%= statusChartHeight - statusBottomPad %>" stroke="#CBD5E1" stroke-width="1.5"></line>
                                <%= statusYLabels.toString() %>
                                <%= statusBars.toString() %>
                                <%= statusValues.toString() %>
                                <%= statusLabels.toString() %>
                            </svg>
                            <div id="statusChartTooltip" class="pointer-events-none absolute hidden rounded border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 shadow-lg">
                                <p id="statusChartTooltipTitle" class="text-sacco-ink"></p>
                                <p id="statusChartTooltipValue" class="mt-1 text-slate-500"></p>
                            </div>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="erp-section text-center text-sm text-slate-500">No active application statuses to chart yet.</div>
                    </c:otherwise>
                </c:choose>
            </div>
        </section>

        <section class="erp-panel 2xl:self-start">
            <div class="erp-panel-header">
                <p class="erp-panel-title">Repayment Timelines</p>
            </div>
            <div class="erp-panel-body">
                <div class="erp-toolbar gap-3">
                    <div>
                        <p class="erp-widget-title">Active Loans Chart</p>
                        <h2 class="erp-widget-heading">Time Left For Each Active Loan</h2>
                    </div>
                    <div class="rounded border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm font-semibold text-slate-600">
                        ${activeLoanChartCount} active loan(s)
                    </div>
                </div>
                <c:choose>
                    <c:when test="${not empty activeLoanChartRows}">
                        <div class="mt-4 grid gap-4 md:grid-cols-2 2xl:grid-cols-1 3xl:grid-cols-2">
                            <c:forEach items="${activeLoanChartRows}" var="loanRow">
                                <div class="rounded border border-slate-200 bg-white p-4"
                                     data-repayment-timer="card"
                                     data-start-date="${loanRow.startDate}"
                                     data-final-due-date="${loanRow.finalDueDate}"
                                     data-total-days="${loanRow.totalDays}">
                                    <div class="grid gap-4 sm:grid-cols-[minmax(0,1fr)_160px] sm:items-center">
                                        <div class="min-w-0">
                                            <div class="flex flex-wrap items-start justify-between gap-3">
                                                <div>
                                                    <p class="erp-widget-title">Repayment Timer</p>
                                                    <h3 class="mt-1 text-lg font-bold text-sacco-ink">Loan ID ${loanRow.loanId}</h3>
                                                </div>
                                                <span class="inline-flex items-center rounded-md border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.16em] ${loanRow.repaymentStateClasses}">
                                                    <c:if test="${loanRow.repaymentStateLabel eq 'Paid'}">&#10003;&nbsp;</c:if>${loanRow.repaymentStateLabel}
                                                </span>
                                            </div>
                                            <p class="mt-1 text-sm text-slate-500">Loan Amount: ${loanRow.amountLabel}</p>
                                            <div class="mt-4 grid grid-cols-2 gap-x-4 gap-y-3 text-sm text-slate-600">
                                                <div>
                                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Elapsed</p>
                                                    <p class="mt-1 font-semibold text-sacco-ink" data-repayment-timer="elapsed-days">${loanRow.elapsedDays} day(s)</p>
                                                </div>
                                                <div>
                                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Remaining</p>
                                                    <p class="mt-1 font-semibold text-sacco-ink" data-repayment-timer="days-left">${loanRow.daysLeft} day(s)</p>
                                                </div>
                                                <div class="col-span-2">
                                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Final Due Date</p>
                                                    <p class="mt-1 font-semibold text-sacco-ink">${loanRow.finalDueDate}</p>
                                                </div>
                                            </div>
                                            <div class="mt-4 rounded-md border border-slate-200 bg-slate-50">
                                                <div class="border-b border-slate-200 px-3 py-2">
                                                    <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Loan Payment Summary</p>
                                                </div>
                                                <c:choose>
                                                    <c:when test="${loanRow.paymentSummaryAvailable}">
                                                        <div class="grid gap-3 px-3 py-3 sm:grid-cols-2">
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Product</p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.loanDescription}</p>
                                                            </div>
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Last Payment</p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.lastPaymentDate}</p>
                                                            </div>
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Outstanding</p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.totalOutstanding}</p>
                                                            </div>
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Principal Paid</p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.totalPrincipalPaid}</p>
                                                            </div>
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Outstanding Principal</p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.outstandingPrincipal}</p>
                                                            </div>
                                                            <div>
                                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Outstanding Interest</p>
                                                                <p class="mt-1 text-sm font-semibold text-sacco-ink">${loanRow.outstandingInterest}</p>
                                                            </div>
                                                        </div>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <div class="px-3 py-3 text-sm text-slate-500">
                                                            Payment summary details will appear after the latest synced repayment record is fetched from memberportal.
                                                        </div>
                                                    </c:otherwise>
                                                </c:choose>
                                            </div>
                                        </div>

                                        <div class="mx-auto flex w-full max-w-[170px] flex-col items-center gap-3">
                                            <div class="relative flex h-28 w-28 items-center justify-center rounded-full sm:h-32 sm:w-32"
                                                 data-repayment-timer="ring"
                                                 style="background: conic-gradient(#E2E8F0 0% ${loanRow.remainingPercent}%, #2F348D ${loanRow.remainingPercent}% 100%);">
                                                <div class="flex h-18 w-18 flex-col items-center justify-center rounded-full bg-white text-center shadow-sm sm:h-20 sm:w-20">
                                                    <p class="text-[8px] font-bold uppercase tracking-[0.18em] text-slate-400">Remaining</p>
                                                    <p class="mt-1 font-display text-xl text-sacco-ink sm:text-2xl" data-repayment-timer="remaining-percent">${loanRow.remainingPercent}%</p>
                                                    <p class="mt-1 text-[10px] text-slate-500" data-repayment-timer="countdown">${loanRow.countdown}</p>
                                                </div>
                                            </div>
                                            <div class="w-full rounded border border-slate-200 bg-slate-50 px-3 py-2 text-center">
                                                <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-slate-400">Repayment Progress</p>
                                                <p class="mt-1 text-sm font-semibold text-sacco-ink" data-repayment-timer="progress-text">${loanRow.remainingPercent}% remaining</p>
                                            </div>
                                            <c:if test="${loanRow.canDismiss}">
                                                <form method="post" action="${pageContext.request.contextPath}/app/dashboard/active-loans/${loanRow.fullId}/seen" class="w-full">
                                                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                                    <button type="submit" class="app-btn btn-neutral w-full justify-center text-sm">
                                                        Seen
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
                                    <p class="font-display text-xl text-sacco-ink">No repayment timers to show right now</p>
                                    <p class="mt-2 text-sm leading-6 text-slate-500">
                                        Expired timelines you have already marked as seen stay hidden here.
                                    </p>
                                </c:when>
                                <c:otherwise>
                                    <p class="font-display text-xl text-sacco-ink">No active loans under repayment</p>
                                    <p class="mt-2 text-sm leading-6 text-slate-500">
                                        Active accepted loans will appear here with their remaining repayment time plotted in a live chart.
                                    </p>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </section>
    </div>

    <div class="erp-content-grid">
        <section class="erp-panel">
            <div class="erp-panel-header">
                <p class="erp-panel-title">Current Applications</p>
            </div>
            <div class="erp-panel-body">
                <div class="erp-table-wrap">
                    <table class="erp-table">
                        <thead>
                        <tr>
                            <th>Loan Application ID</th>
                            <th>Loan Type</th>
                            <th>Amount</th>
                            <th>Status</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${myApplications}" var="app" end="4">
                            <tr>
                                <td>${app.applicationNumber}</td>
                                <td><spring:message code="loan.type.${app.loanType}" text="${app.loanType}" /></td>
                                <td>${app.amount}</td>
                                <td><spring:message code="loan.status.${app.status}" text="${app.status}" /></td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty myApplications}">
                            <tr>
                                <td colspan="4">No current applications yet.</td>
                            </tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
            </div>
        </section>

        <section class="erp-panel">
            <div class="erp-panel-header">
                <p class="erp-panel-title">Guarantor Queue</p>
            </div>
            <div class="erp-panel-body">
                <div class="erp-table-wrap">
                    <table class="erp-table">
                        <thead>
                        <tr>
                            <th>Loan Id</th>
                            <th>Loan Amount</th>
                            <th>Status</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${pendingGuarantees}" var="request" end="4">
                            <tr>
                                <td>${fn:substring(request.loanApplicationId, 0, 8)}</td>
                                <td>${guaranteeLoanAmounts[request.loanApplicationId]}</td>
                                <td>${request.status}</td>
                            </tr>
                        </c:forEach>
                        <c:if test="${empty pendingGuarantees}">
                            <tr>
                                <td colspan="3">No guarantor requests waiting.</td>
                            </tr>
                        </c:if>
                        </tbody>
                    </table>
                </div>
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
                ringEl.style.background = 'conic-gradient(#E2E8F0 0% ' + remainingPercent + '%, #2F348D ' + remainingPercent + '% 100%)';
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

        function bindTooltip(wrapId, selector, tooltipId, titleId, valueRenderer) {
            var wrap = document.getElementById(wrapId);
            var tooltip = document.getElementById(tooltipId);
            var title = document.getElementById(titleId);
            if (!wrap || !tooltip || !title) {
                return;
            }
            var points = wrap.querySelectorAll(selector);
            Array.prototype.forEach.call(points, function (point) {
                point.addEventListener('mouseenter', function () {
                    title.textContent = point.getAttribute('data-label') || '';
                    valueRenderer(point);
                    tooltip.classList.remove('hidden');
                });
                point.addEventListener('mousemove', function (event) {
                    var wrapRect = wrap.getBoundingClientRect();
                    var left = event.clientX - wrapRect.left + 14;
                    var top = event.clientY - wrapRect.top - 14;
                    tooltip.style.left = left + 'px';
                    tooltip.style.top = top + 'px';
                });
                point.addEventListener('mouseleave', function () {
                    tooltip.classList.add('hidden');
                });
            });
        }

        bindTooltip(
            'statusChartWrap',
            '.dashboard-status-hover',
            'statusChartTooltip',
            'statusChartTooltipTitle',
            function (point) {
                document.getElementById('statusChartTooltipValue').textContent =
                    (point.getAttribute('data-value') || '0') + ' application(s)';
            }
        );

        startRepaymentTimers();

    }());
</script>

<%@ include file="../fragments/footer.jspf" %>
