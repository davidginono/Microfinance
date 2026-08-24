<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
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
    if ("Accepted Loan".equals(label) || "Approved Loan".equals(label)) {
        return new String[] {"Approved", "Loan"};
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
        .append("' text-anchor='end' fill='#64748B' font-size='11' font-family='Open Sans, Helvetica, Arial, sans-serif'>")
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
            .append("' text-anchor='middle' fill='#172033' font-size='12' font-weight='700' font-family='Open Sans, Helvetica, Arial, sans-serif'>")
            .append(count).append("</text>");

        String[] pieces = splitLabel(label);
        statusLabels.append("<text x='").append((int) Math.round(centerX)).append("' y='")
            .append(statusChartHeight - 38)
            .append("' text-anchor='middle' fill='#475569' font-size='11' font-weight='600' font-family='Open Sans, Helvetica, Arial, sans-serif'>");
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
        .append("' text-anchor='middle' fill='#64748B' font-size='11' font-family='Open Sans, Helvetica, Arial, sans-serif'>")
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
        .append("' fill='#172033' font-size='12' font-weight='700' font-family='Open Sans, Helvetica, Arial, sans-serif'>")
        .append(daysLeft).append(" d</text>");

    loanRowLabels.append("<text x='").append(loanLeftPad - 12).append("' y='").append(y + 18)
        .append("' text-anchor='end' fill='#172033' font-size='12' font-weight='700' font-family='Open Sans, Helvetica, Arial, sans-serif'>")
        .append(esc(loanId)).append("</text>");
    loanRowLabels.append("<text x='").append(loanLeftPad - 12).append("' y='").append(y + 34)
        .append("' text-anchor='end' fill='#64748B' font-size='11' font-family='Open Sans, Helvetica, Arial, sans-serif'>")
        .append(esc(amountLabel)).append("</text>");
}
%>

<section id="memberDashboardContent"
         class="space-y-4"
         data-member-dashboard-content
         data-member-dashboard-progressive="${dashboardProgressive}"
         data-member-dashboard-content-url="${pageContext.request.contextPath}/app/dashboard?full=true">
<div class="erp-page-header" data-aws-page-header>
        <h1 class="erp-page-title text-3xl sm:text-4xl" data-sticky-title-source="true">Member Dashboard</h1>
        <p class="mt-1 text-lg font-semibold text-sacco-ink">
            <spring:message code="dashboard.welcome" text="Welcome" />,
            <c:choose>
                <c:when test="${not empty currentMember and not empty currentMember.fullName}">
                    <c:out value="${currentMember.fullName}" />
                </c:when>
                <c:otherwise>
                    <c:out value="${pageContext.request.userPrincipal.name}" />
                </c:otherwise>
            </c:choose>
        </p>
    </div>

    <c:if test="${not empty pendingStaffAccess}">
        <div class="erp-panel border-cyan-200 bg-cyan-50/70">
            <div class="erp-panel-body flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                    <p class="erp-widget-title text-cyan-900">Staff Access</p>
                    <p class="mt-1 text-sm text-slate-700">
                        Staff Number <span class="font-semibold text-slate-900">${pendingStaffAccess.staffNo}</span>
                        is ready<c:if test="${not empty pendingStaffAccess.roles}"> for ${pendingStaffAccess.roles}</c:if>.
                    </p>
                </div>
                <form action="/app/staff-access/acknowledge" method="post" class="shrink-0">
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <button type="submit" class="app-btn btn-primary">Activate Staff Access</button>
                </form>
            </div>
        </div>
    </c:if>

    <c:choose>
        <c:when test="${dashboardProgressive}">
            <div class="erp-stat-grid" aria-busy="true">
                <div class="erp-stat-card erp-stat-blue">
                    <div class="erp-stat-main">
                        <div>
                            <p class="erp-stat-label"><spring:message code="dashboard.stat.applications.label" /></p>
                            <p class="erp-stat-value">...</p>
                        </div>
                        <span class="erp-stat-icon" aria-hidden="true">...</span>
                    </div>
                </div>
                <div class="erp-stat-card erp-stat-green">
                    <div class="erp-stat-main">
                        <div>
                            <p class="erp-stat-label"><spring:message code="dashboard.stat.activeLoans.label" /></p>
                            <p class="erp-stat-value">...</p>
                        </div>
                        <span class="erp-stat-icon" aria-hidden="true">...</span>
                    </div>
                </div>
                <div class="erp-stat-card erp-stat-amber">
                    <div class="erp-stat-main">
                        <div>
                            <p class="erp-stat-label"><spring:message code="dashboard.stat.guarantorRequests.label" /></p>
                            <p class="erp-stat-value">...</p>
                        </div>
                        <span class="erp-stat-icon" aria-hidden="true">...</span>
                    </div>
                </div>
                <div class="erp-stat-card erp-stat-red">
                    <div class="erp-stat-main">
                        <div>
                            <p class="erp-stat-label"><spring:message code="dashboard.stat.archivedRejections.label" /></p>
                            <p class="erp-stat-value">...</p>
                        </div>
                        <span class="erp-stat-icon" aria-hidden="true">...</span>
                    </div>
                </div>
            </div>

            <section class="erp-panel" data-live-account-status-url="${pageContext.request.contextPath}/app/dashboard/external-account-status">
                <div class="erp-panel-header">
                    <p class="erp-panel-title"><spring:message code="dashboard.panel.financialStatus" /></p>
                </div>
                <div class="erp-panel-body">
                    <h2 class="erp-widget-heading"><spring:message code="dashboard.widget.yourBalances" text="Your Balances" /></h2>
                    <div class="aws-status-summary-grid mt-3">
                        <div class="aws-status-summary-item">
                            <p class="aws-status-summary-label"><spring:message code="dashboard.savings.label" /></p>
                            <p class="aws-status-summary-value" data-live-account-status-savings>${dashboardExternalAccountStatus.savingsLabel}</p>
                        </div>
                        <div class="aws-status-summary-item">
                            <p class="aws-status-summary-label"><spring:message code="dashboard.shares.label" /></p>
                            <p class="aws-status-summary-value" data-live-account-status-shares>${dashboardExternalAccountStatus.sharesLabel}</p>
                        </div>
                    </div>
                </div>
            </section>

            <div class="grid min-w-0 items-start gap-4 xl:grid-cols-2" aria-busy="true">
                <section class="erp-panel min-w-0">
                    <div class="erp-panel-header">
                        <p class="erp-panel-title"><spring:message code="dashboard.currentLoanApplication" text="Current Loan Application" /></p>
                    </div>
                    <div class="erp-panel-body">
                        <div class="erp-section text-center text-sm text-slate-500">Loading application status...</div>
                    </div>
                </section>
                <section class="erp-panel min-w-0 xl:self-start">
                    <div class="erp-panel-header">
                        <p class="erp-panel-title"><spring:message code="dashboard.activeLoans.title" text="Active Loans" /></p>
                    </div>
                    <div class="erp-panel-body">
                        <div class="erp-section text-center text-sm text-slate-500">Loading active loans...</div>
                    </div>
                </section>
            </div>
            <div class="aws-inline-notice aws-inline-notice--danger hidden" data-dashboard-progressive-error hidden>
                <span>Dashboard content could not load. Refresh this page to try again.</span>
            </div>
        </c:when>
        <c:otherwise>
    <div class="erp-stat-grid">
        <a href="/app/loan-applications" class="erp-stat-card erp-stat-card-interactive erp-stat-blue block no-underline">
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label"><spring:message code="dashboard.stat.applications.label" /></p>
                    <p class="erp-stat-value">${totalApplications}</p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M4 3a1 1 0 00-1 1v12a1 1 0 001 1h12a1 1 0 001-1V7.414A1 1 0 0016.707 7L13 3.293A1 1 0 0012.293 3H4z"/></svg>
                </span>
            </div>
        </a>
        <button type="button" class="erp-stat-card erp-stat-card-interactive erp-stat-green block w-full border-0 text-left" data-active-loans-card-trigger>
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label"><spring:message code="dashboard.stat.activeLoans.label" /></p>
                    <p class="erp-stat-value" data-member-active-loans-count>${activeLoanCount}</p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M4 4h12v3H4V4zm0 5h12v7H4V9zm2 2v3h4v-3H6z"/></svg>
                </span>
            </div>
        </button>
        <a href="/app/guarantee-requests" class="erp-stat-card erp-stat-card-interactive erp-stat-amber block no-underline">
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label"><spring:message code="dashboard.stat.guarantorRequests.label" /></p>
                    <p class="erp-stat-value">${pendingGuaranteeApprovals}</p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M10 2a4 4 0 00-4 4v2H5a2 2 0 00-2 2v5a3 3 0 003 3h8a3 3 0 003-3v-5a2 2 0 00-2-2h-1V6a4 4 0 00-4-4z"/></svg>
                </span>
            </div>
        </a>
        <a href="/app/archives?section=loans&loanArchiveFilter=REJECTED" class="erp-stat-card erp-stat-card-interactive erp-stat-red block no-underline">
            <div class="erp-stat-main">
                <div>
                    <p class="erp-stat-label"><spring:message code="dashboard.stat.archivedRejections.label" /></p>
                    <p class="erp-stat-value">${rejectedLoanCount}</p>
                </div>
                <span class="erp-stat-icon">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor"><path d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-10.293l-4 4a1 1 0 01-1.414 0l-2-2 1.414-1.414L9 9.586l3.293-3.293 1.414 1.414z"/></svg>
                </span>
            </div>
        </a>
    </div>

    <section class="erp-panel" data-live-account-status-url="${pageContext.request.contextPath}/app/dashboard/external-account-status">
        <div class="erp-panel-header">
            <p class="erp-panel-title"><spring:message code="dashboard.panel.financialStatus" /></p>
        </div>
        <div class="erp-panel-body">
            <div>
                <div>
                    <h2 class="erp-widget-heading"><spring:message code="dashboard.widget.yourBalances" text="Your Balances" /></h2>
                </div>
            </div>

            <div class="aws-status-summary-grid">
                <div class="aws-status-summary-item">
                    <p class="aws-status-summary-label"><spring:message code="dashboard.savings.label" /></p>
                    <p class="aws-status-summary-value" data-live-account-status-savings>${dashboardExternalAccountStatus.savingsLabel}</p>
                </div>

                <div class="aws-status-summary-item">
                    <p class="aws-status-summary-label"><spring:message code="dashboard.shares.label" /></p>
                    <p class="aws-status-summary-value" data-live-account-status-shares>${dashboardExternalAccountStatus.sharesLabel}</p>
                </div>
            </div>
        </div>
    </section>

    <div class="grid min-w-0 items-start gap-4 xl:grid-cols-2">
        <section class="erp-panel min-w-0">
            <div class="erp-panel-header">
                <p class="erp-panel-title"><spring:message code="dashboard.currentLoanApplication" text="Current Loan Application" /></p>
            </div>
            <div class="erp-panel-body min-w-0">
                <c:choose>
                    <c:when test="${not empty currentWorkflowApplication}">
                        <button type="button"
                                class="member-dashboard-dropdown-trigger aws-disclosure-button"
                                data-dashboard-toggle="currentApplicationStatusPanel"
                                aria-expanded="false"
                                aria-controls="currentApplicationStatusPanel">
                            <span>See Current Application Status</span>
                            <svg class="member-dashboard-dropdown-chevron h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.17l3.71-3.94a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
                            </svg>
                        </button>
                        <div id="currentApplicationStatusPanel" class="member-dashboard-collapsible aws-dashboard-detail-panel" hidden>
                            <div class="member-dashboard-summary-row aws-detail-grid">
                                <div class="member-dashboard-summary-fact">
                                    <span class="member-dashboard-summary-fact-label"><spring:message code="loan.applicationId" text="Loan Application ID" />:</span>
                                    <span class="member-dashboard-summary-fact-value">${currentWorkflowApplicationNumber}</span>
                                </div>
                                <div class="member-dashboard-summary-fact">
                                    <span class="member-dashboard-summary-fact-label"><spring:message code="reports.loanProduct" text="Loan Product" />:</span>
                                    <span class="member-dashboard-summary-fact-value">${currentWorkflowProductName}</span>
                                </div>
                                <c:if test="${not empty currentWorkflowApplicantReason}">
                                    <div class="member-dashboard-summary-fact">
                                        <span class="member-dashboard-summary-fact-label"><spring:message code="loan.purpose" text="Loan Purpose" />:</span>
                                        <span class="member-dashboard-summary-fact-value">${currentWorkflowApplicantReason}</span>
                                    </div>
                                </c:if>
                                <div class="member-dashboard-summary-fact">
                                    <span class="member-dashboard-summary-fact-label"><spring:message code="common.amount" text="Amount" />:</span>
                                    <span class="member-dashboard-summary-fact-value">${currentWorkflowAmountLabel}</span>
                                </div>
                                <div class="member-dashboard-summary-fact">
                                    <span class="member-dashboard-summary-fact-label"><spring:message code="review.currentStatus" text="Current Status" />:</span>
                                    <span class="member-dashboard-summary-fact-value">${currentWorkflowStatusLabel}</span>
                                </div>
                            </div>
                            <c:if test="${not empty currentWorkflowUpdatedAtLabel}">
                                <div class="aws-detail-updated">
                                    Updated ${currentWorkflowUpdatedAtLabel}
                                </div>
                            </c:if>
                            <c:if test="${not empty currentWorkflowApplication and currentWorkflowApplication.status eq 'DISBURSED' and empty currentWorkflowApplication.applicantDisbursementAcknowledgedAt}">
                                <div class="aws-inline-notice aws-inline-notice--success">
                                    <span><spring:message code="loan.disbursement.readyAck" text="This loan has been disbursed." /></span>
                                    <form method="post" action="${pageContext.request.contextPath}/app/loan-applications/${currentWorkflowApplication.id}/acknowledge-disbursement" class="m-0">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                        <input type="hidden" name="returnTo" value="dashboard" />
                                        <button type="submit" class="app-btn btn-neutral"><spring:message code="common.acknowledge" text="Acknowledge" /></button>
                                    </form>
                                </div>
                            </c:if>
                            <c:if test="${currentWorkflowRejectionAcknowledgementRequired}">
                                <div class="aws-inline-notice aws-inline-notice--danger">
                                    <span><spring:message code="loan.rejection.readyAck" text="This loan application was rejected. Acknowledge the decision to clear it from your progress view." /></span>
                                    <form method="post" action="${pageContext.request.contextPath}/app/loan-applications/${currentWorkflowApplication.id}/acknowledge-rejection" class="m-0">
                                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                                        <input type="hidden" name="returnTo" value="dashboard" />
                                        <button type="submit" class="app-btn btn-neutral"><spring:message code="common.acknowledge" text="Acknowledge" /></button>
                                    </form>
                                </div>
                            </c:if>

                            <%@ include file="../fragments/member-application-progress.jspf" %>
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

        <section id="memberActiveLoansPanel" class="erp-panel min-w-0 xl:self-start">
            <div class="erp-panel-header">
                <p class="erp-panel-title"><spring:message code="dashboard.activeLoans.title" text="Active Loans" /></p>
            </div>
            <div class="erp-panel-body min-w-0">
                <button type="button"
                        class="member-dashboard-dropdown-trigger aws-disclosure-button"
                        data-dashboard-toggle="activeLoansTablePanel"
                        aria-expanded="false"
                        aria-controls="activeLoansTablePanel">
                    <span>See Active Loans</span>
                    <svg class="member-dashboard-dropdown-chevron h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                        <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.17l3.71-3.94a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
                    </svg>
                </button>
                <div id="activeLoansTablePanel" class="member-dashboard-collapsible aws-dashboard-detail-panel" hidden>
                        <div class="erp-table-wrap" data-aws-table-region data-aws-no-refresh="true" data-loading-label="Loading active loans..." data-active-loans-url="${pageContext.request.contextPath}/app/dashboard/active-loans">
                            <div class="app-table-titlebar">
                                <div class="app-table-heading">
                                    <h2><spring:message code="dashboard.activeLoans.balancesTitle" text="Loan balances" /></h2>
                                </div>
                                <div class="app-table-toolbar">
                                    <button type="button"
                                            class="app-icon-button btn-neutral"
                                            data-active-loans-refresh
                                            data-refresh-url="${pageContext.request.contextPath}/app/dashboard/active-loans/balances/refresh"
                                            data-csrf-param="${_csrf.parameterName}"
                                            data-csrf-token="${_csrf.token}"
                                            data-success-message="<spring:message code='dashboard.activeLoans.refreshSuccess' text='Loan balances updated.' />"
                                            data-no-data-message="<spring:message code='dashboard.activeLoans.refreshNoData' text='No updated balance was returned. Original outstanding balance remains.' />"
                                            data-error-message="<spring:message code='dashboard.activeLoans.refreshError' text='We could not refresh loan balances right now. Please retry again later.' />"
                                            data-loading-label="<spring:message code='dashboard.activeLoans.refreshing' text='Refreshing balances' />"
                                            aria-label="<spring:message code='dashboard.activeLoans.refresh' text='Refresh loan balances' />"
                                            title="<spring:message code='dashboard.activeLoans.refresh' text='Refresh loan balances' />">
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                            <path d="M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5" />
                                            <path d="M4 13a8.1 8.1 0 0 0 15.5 2M20 20v-5h-5" />
                                        </svg>
                                    </button>
                                </div>
                            </div>
                            <div class="erp-table-scroll">
                            <table class="erp-table member-dashboard-active-loans-table" data-active-loans-table data-page-size="5">
                                <thead>
                                    <tr>
                                        <th class="px-3 py-3 text-left">Date of Disbursement</th>
                                        <th class="px-3 py-3 text-left">Due Date</th>
                                        <th class="px-3 py-3 text-left">Loan ID</th>
                                        <th class="px-3 py-3 text-left">Status</th>
                                        <th class="px-3 py-3 text-left">Loan Product</th>
                                        <th class="px-3 py-3 text-left">Loan Purpose</th>
                                        <th class="px-3 py-3 text-left">Loan Amount</th>
                                        <th class="px-3 py-3 text-left">Paid Amount</th>
                                        <th class="px-3 py-3 text-left">Outstanding Balance</th>
                                        <th class="px-3 py-3 text-left">Repayment Schedule</th>
                                    </tr>
                                </thead>
                                <tbody class="divide-y divide-slate-100" data-active-loans-body>
                                    <c:choose>
                                        <c:when test="${not empty activeLoanChartRows}">
                                            <c:forEach items="${activeLoanChartRows}" var="loanRow">
                                                <tr data-active-loans-row data-loan-application-id="${loanRow.applicationId}">
                                                    <td class="px-3 py-3 text-sm font-semibold text-slate-700">${loanRow.disbursementDate}</td>
                                                    <td class="px-3 py-3 text-sm font-semibold text-slate-700">
                                                        <c:choose>
                                                            <c:when test="${not empty loanRow.finalDueDate}">${loanRow.finalDueDate}</c:when>
                                                            <c:otherwise>-</c:otherwise>
                                                        </c:choose>
                                                    </td>
                                                    <td class="px-3 py-3 text-sm font-bold text-blue-600">${loanRow.loanId}</td>
                                                    <td class="px-3 py-3">
                                                        <span class="inline-flex items-center border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.12em] ${loanRow.repaymentStateClasses}">
                                                            ${loanRow.repaymentStateLabel}
                                                        </span>
                                                    </td>
                                                    <td class="px-3 py-3 text-sm font-semibold text-sacco-ink">${loanRow.loanProductName}</td>
                                                    <td class="px-3 py-3 text-sm font-semibold uppercase text-sacco-ink">
                                                        <c:choose>
                                                            <c:when test="${not empty loanRow.applicantReason}">${loanRow.applicantReason}</c:when>
                                                            <c:otherwise>-</c:otherwise>
                                                        </c:choose>
                                                    </td>
                                                    <td class="px-3 py-3 text-sm font-semibold text-sacco-ink">${loanRow.amountLabel}</td>
                                                    <td class="px-3 py-3 text-sm font-bold text-emerald-600" data-active-loan-paid-amount>${loanRow.paidAmount}</td>
                                                    <td class="px-3 py-3 text-sm font-bold text-blue-600" data-active-loan-current-balance>${loanRow.currentBalance}</td>
                                                    <td class="px-3 py-3">
                                                        <c:choose>
                                                            <c:when test="${loanRow.scheduleAvailable}">
                                                                <c:set var="loanScheduleUrl" value="${not empty loanRow.scheduleDataUrl ? loanRow.scheduleDataUrl : loanRow.scheduleUrl}" />
                                                                <button type="button"
                                                                   class="app-btn btn-neutral inline-flex justify-center whitespace-nowrap px-3 py-2 text-sm"
                                                                   data-open-repayment-schedule
                                                                   data-schedule-url="${pageContext.request.contextPath}${loanScheduleUrl}"
                                                                   data-schedule-title="Repayment Schedule ${loanRow.loanId}">
                                                                    View Schedule
                                                                </button>
                                                            </c:when>
                                                            <c:otherwise>
                                                                <span class="text-sm font-semibold text-slate-500">-</span>
                                                            </c:otherwise>
                                                        </c:choose>
                                                    </td>
                                                </tr>
                                            </c:forEach>
                                        </c:when>
                                        <c:otherwise>
                                            <tr data-active-loans-empty>
                                                <td colspan="10" class="px-3 py-4 text-center text-sm text-slate-600">
                                                    <spring:message code="dashboard.activeLoans.empty.title" />
                                                </td>
                                            </tr>
                                        </c:otherwise>
                                    </c:choose>
                                </tbody>
                            </table>
                        </div>
                        </div>
                        <div class="aws-table-pagination-footer">
                            <p class="font-semibold text-slate-600" data-active-loans-pagination-summary></p>
                            <div class="flex items-center gap-2" data-active-loans-pagination></div>
                        </div>
            </div>
    </div>
        </section>
    </div>
        </c:otherwise>
    </c:choose>
</section>

<script>
    (function () {
        function setExpanded(trigger, expanded) {
            var targetId = trigger.getAttribute('data-dashboard-toggle');
            var target = targetId ? document.getElementById(targetId) : null;
            if (!target) {
                return;
            }
            trigger.setAttribute('aria-expanded', expanded ? 'true' : 'false');
            target.hidden = !expanded;
        }

        function initDashboardDropdowns() {
            var triggers = document.querySelectorAll('[data-dashboard-toggle]');
            Array.prototype.forEach.call(triggers, function (trigger) {
                if (trigger.getAttribute('data-dashboard-toggle-ready') === 'true') {
                    return;
                }
                trigger.setAttribute('data-dashboard-toggle-ready', 'true');
                setExpanded(trigger, trigger.getAttribute('aria-expanded') !== 'false');
                trigger.addEventListener('click', function () {
                    setExpanded(trigger, trigger.getAttribute('aria-expanded') !== 'true');
                });
            });
        }

        function initActiveLoansShortcut() {
            var cardTrigger = document.querySelector('[data-active-loans-card-trigger]');
            var loansPanel = document.getElementById('memberActiveLoansPanel');
            var tableTrigger = document.querySelector('[data-dashboard-toggle="activeLoansTablePanel"]');
            if (!cardTrigger || !loansPanel) {
                return;
            }
            if (cardTrigger.getAttribute('data-active-loans-shortcut-ready') === 'true') {
                return;
            }
            cardTrigger.setAttribute('data-active-loans-shortcut-ready', 'true');
            cardTrigger.addEventListener('click', function () {
                if (tableTrigger) {
                    setExpanded(tableTrigger, true);
                }
                loansPanel.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
        }

        function button(label, disabled, current) {
            var item = document.createElement('button');
            item.type = 'button';
            item.className = 'member-dashboard-pagination-button';
            item.textContent = label;
            item.disabled = disabled;
            if (current) {
                item.setAttribute('aria-current', 'page');
            }
            return item;
        }

        function initActiveLoansPagination() {
            var table = document.querySelector('[data-active-loans-table]');
            if (!table) {
                return;
            }
            if (table.getAttribute('data-active-loans-pagination-ready') === 'true') {
                return;
            }
            table.setAttribute('data-active-loans-pagination-ready', 'true');
            var rows = Array.prototype.slice.call(table.querySelectorAll('[data-active-loans-row]'));
            var pageSize = Number(table.getAttribute('data-page-size')) || 5;
            var totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
            var currentPage = 1;
            var summary = document.querySelector('[data-active-loans-pagination-summary]');
            var pager = document.querySelector('[data-active-loans-pagination]');

            function render() {
                var start = (currentPage - 1) * pageSize;
                var end = Math.min(start + pageSize, rows.length);
                rows.forEach(function (row, index) {
                    row.hidden = index < start || index >= end;
                });
                if (summary) {
                    summary.textContent = rows.length
                        ? 'Showing ' + (start + 1) + ' to ' + end + ' of ' + rows.length + ' loans'
                        : 'Showing 0 loans';
                }
                if (!pager) {
                    return;
                }
                pager.innerHTML = '';
                var previous = button('<', currentPage === 1, false);
                previous.addEventListener('click', function () {
                    currentPage = Math.max(1, currentPage - 1);
                    render();
                });
                pager.appendChild(previous);

                for (var page = 1; page <= totalPages; page += 1) {
                    var pageButton = button(String(page), false, page === currentPage);
                    pageButton.addEventListener('click', (function (pageNumber) {
                        return function () {
                            currentPage = pageNumber;
                            render();
                        };
                    }(page)));
                    pager.appendChild(pageButton);
                }

                var next = button('>', currentPage === totalPages, false);
                next.addEventListener('click', function () {
                    currentPage = Math.min(totalPages, currentPage + 1);
                    render();
                });
                pager.appendChild(next);
            }

            render();
        }

        function showActiveLoanRefreshToast(type, message) {
            if (typeof window.showToast === 'function') {
                window.showToast(type, message);
            }
        }

        function showActiveLoansTableLoading(region, label) {
            if (!region || region.classList.contains('is-loading')) {
                return;
            }
            region.classList.add('is-loading');
            region.setAttribute('aria-busy', 'true');
            region.setAttribute('data-aws-async-table-loading', 'true');
            if (label) {
                region.setAttribute('data-loading-label', label);
            }
            var loader = document.createElement('div');
            loader.className = 'aws-table-loader';
            loader.setAttribute('role', 'status');
            loader.setAttribute('aria-label', region.getAttribute('data-loading-label') || 'Loading results...');
            var spinner = document.createElement('span');
            spinner.className = 'aws-table-loader__spinner';
            spinner.setAttribute('aria-hidden', 'true');
            loader.appendChild(spinner);
            region.appendChild(loader);
        }

        function hideActiveLoansTableLoading(region) {
            if (!region) {
                return;
            }
            region.classList.remove('is-loading');
            region.setAttribute('aria-busy', 'false');
            region.removeAttribute('data-aws-async-table-loading');
            Array.prototype.forEach.call(region.querySelectorAll('.aws-table-loader'), function (loader) {
                loader.remove();
            });
        }

        function escapeHtml(value) {
            return String(value == null ? '' : value)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#39;');
        }

        function valueOrDash(value) {
            var text = String(value == null ? '' : value).trim();
            return text ? text : '-';
        }

        function activeLoansScheduleHtml(row) {
            if (!row || !row.scheduleAvailable || (!row.scheduleDataUrl && !row.scheduleUrl)) {
                return '<span class="text-sm font-semibold text-slate-500">-</span>';
            }
            var contextPath = '${pageContext.request.contextPath}';
            var url = String(row.scheduleDataUrl || row.scheduleUrl || '');
            if (contextPath && url.charAt(0) === '/') {
                url = contextPath + url;
            }
            return [
                '<button type="button" class="app-btn btn-neutral inline-flex justify-center whitespace-nowrap px-3 py-2 text-sm"',
                ' data-open-repayment-schedule data-schedule-url="', escapeHtml(url), '"',
                ' data-schedule-title="Repayment Schedule ', escapeHtml(valueOrDash(row.loanId)), '">',
                'View Schedule',
                '</button>'
            ].join('');
        }

        function activeLoanRowHtml(row) {
            var stateClasses = valueOrDash(row.repaymentStateClasses);
            return [
                '<tr data-active-loans-row data-loan-application-id="', escapeHtml(valueOrDash(row.applicationId)), '">',
                '<td class="px-3 py-3 text-sm font-semibold text-slate-700">', escapeHtml(valueOrDash(row.disbursementDate)), '</td>',
                '<td class="px-3 py-3 text-sm font-semibold text-slate-700">', escapeHtml(valueOrDash(row.finalDueDate)), '</td>',
                '<td class="px-3 py-3 text-sm font-bold text-blue-600">', escapeHtml(valueOrDash(row.loanId)), '</td>',
                '<td class="px-3 py-3"><span class="inline-flex items-center border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.12em] ',
                escapeHtml(stateClasses),
                '">', escapeHtml(valueOrDash(row.repaymentStateLabel)), '</span></td>',
                '<td class="px-3 py-3 text-sm font-semibold text-sacco-ink">', escapeHtml(valueOrDash(row.loanProductName)), '</td>',
                '<td class="px-3 py-3 text-sm font-semibold uppercase text-sacco-ink">', escapeHtml(valueOrDash(row.applicantReason)), '</td>',
                '<td class="px-3 py-3 text-sm font-semibold text-sacco-ink">', escapeHtml(valueOrDash(row.amountLabel)), '</td>',
                '<td class="px-3 py-3 text-sm font-bold text-emerald-600" data-active-loan-paid-amount>', escapeHtml(valueOrDash(row.paidAmount)), '</td>',
                '<td class="px-3 py-3 text-sm font-bold text-blue-600" data-active-loan-current-balance>', escapeHtml(valueOrDash(row.currentBalance)), '</td>',
                '<td class="px-3 py-3">', activeLoansScheduleHtml(row), '</td>',
                '</tr>'
            ].join('');
        }

        function activeLoansEmptyRowHtml() {
            return [
                '<tr data-active-loans-empty>',
                '<td colspan="10" class="px-3 py-4 text-center text-sm text-slate-600">',
                '<spring:message code="dashboard.activeLoans.empty.title" javaScriptEscape="true" />',
                '</td>',
                '</tr>'
            ].join('');
        }

        function renderActiveLoanRows(rows) {
            var table = document.querySelector('[data-active-loans-table]');
            if (!table || !Array.isArray(rows)) {
                return;
            }
            var body = table.querySelector('[data-active-loans-body]');
            if (!body) {
                return;
            }
            body.innerHTML = rows.length ? rows.map(activeLoanRowHtml).join('') : activeLoansEmptyRowHtml();
            table.removeAttribute('data-active-loans-pagination-ready');
            initActiveLoansPagination();
        }

        function renderActiveLoanPayload(payload) {
            if (!payload || !Array.isArray(payload.rows)) {
                return;
            }
            renderActiveLoanRows(payload.rows);
            var countValue = document.querySelector('[data-member-active-loans-count]');
            if (countValue) {
                countValue.textContent = String(typeof payload.count === 'number' ? payload.count : payload.rows.length);
            }
        }

        function initActiveLoansHydration() {
            var region = document.querySelector('[data-active-loans-url]');
            if (!region || region.getAttribute('data-active-loans-hydration-ready') === 'true') {
                return;
            }
            var url = region.getAttribute('data-active-loans-url');
            if (!url) {
                return;
            }
            region.setAttribute('data-active-loans-hydration-ready', 'true');
            showActiveLoansTableLoading(region);
            fetch(url, {
                credentials: 'same-origin',
                headers: {
                    'Accept': 'application/json',
                    'X-Requested-With': 'XMLHttpRequest'
                }
            })
                .then(function (response) {
                    if (!response.ok) {
                        throw new Error('Active loans could not load.');
                    }
                    return response.json();
                })
                .then(renderActiveLoanPayload)
                .catch(function () {
                    region.removeAttribute('data-active-loans-hydration-ready');
                })
                .finally(function () {
                    hideActiveLoansTableLoading(region);
                });
        }

        function initActiveLoansBalanceRefresh() {
            var refreshButton = document.querySelector('[data-active-loans-refresh]');
            if (!refreshButton) {
                return;
            }
            if (refreshButton.getAttribute('data-active-loans-refresh-ready') === 'true') {
                return;
            }
            refreshButton.setAttribute('data-active-loans-refresh-ready', 'true');
            var refreshUrl = refreshButton.getAttribute('data-refresh-url');
            var csrfParam = refreshButton.getAttribute('data-csrf-param');
            var csrfToken = refreshButton.getAttribute('data-csrf-token');
            var originalLabel = refreshButton.getAttribute('aria-label') || 'Refresh loan balances';
            var loadingLabel = refreshButton.getAttribute('data-loading-label') || 'Refreshing balances';
            var errorMessage = refreshButton.getAttribute('data-error-message')
                || 'We could not refresh loan balances right now. Please retry again later.';
            var noDataMessage = refreshButton.getAttribute('data-no-data-message')
                || 'No updated balance was returned. Original outstanding balance remains.';
            var successMessage = refreshButton.getAttribute('data-success-message') || 'Loan balances updated.';
            var region = refreshButton.closest('.erp-table-wrap');
            var originalTableLoadingLabel = region ? region.getAttribute('data-loading-label') : null;

            refreshButton.addEventListener('click', function () {
                if (refreshButton.disabled || !refreshUrl) {
                    return;
                }
                var body = new URLSearchParams();
                if (csrfParam && csrfToken) {
                    body.append(csrfParam, csrfToken);
                }
                refreshButton.disabled = true;
                refreshButton.setAttribute('aria-busy', 'true');
                refreshButton.setAttribute('aria-label', loadingLabel);
                showActiveLoansTableLoading(region, loadingLabel);
                fetch(refreshUrl, {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: {
                        'Accept': 'application/json',
                        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
                        'X-Requested-With': 'XMLHttpRequest'
                    },
                    body: body.toString()
                })
                    .then(function (response) {
                        return response.json().catch(function () {
                            return {};
                        }).then(function (payload) {
                            if (!response.ok && !payload.status) {
                                payload.status = 'ERROR';
                                payload.message = errorMessage;
                            }
                            return payload;
                        });
                    })
                    .then(function (payload) {
                        renderActiveLoanPayload(payload);
                        if (payload.status === 'ERROR') {
                            showActiveLoanRefreshToast('error', payload.message || errorMessage);
                        } else if (payload.status === 'NO_DATA') {
                            showActiveLoanRefreshToast('info', payload.message || noDataMessage);
                        } else if (payload.status === 'PARTIAL') {
                            showActiveLoanRefreshToast('info', payload.message || successMessage);
                        } else {
                            showActiveLoanRefreshToast('success', payload.message || successMessage);
                        }
                    })
                    .catch(function () {
                        showActiveLoanRefreshToast('error', errorMessage);
                    })
                    .finally(function () {
                        refreshButton.disabled = false;
                        refreshButton.setAttribute('aria-busy', 'false');
                        refreshButton.setAttribute('aria-label', originalLabel);
                        if (region && originalTableLoadingLabel) {
                            region.setAttribute('data-loading-label', originalTableLoadingLabel);
                        }
                        hideActiveLoansTableLoading(region);
                    });
            });
        }

        function initMemberDashboard() {
            initDashboardDropdowns();
            initActiveLoansShortcut();
            initActiveLoansPagination();
            initActiveLoansHydration();
            initActiveLoansBalanceRefresh();
        }

        function initProgressiveDashboard() {
            var shell = document.querySelector('[data-member-dashboard-progressive="true"]');
            if (!shell || shell.getAttribute('data-member-dashboard-progressive-ready') === 'true') {
                return;
            }
            var contentUrl = shell.getAttribute('data-member-dashboard-content-url');
            if (!contentUrl) {
                return;
            }
            shell.setAttribute('data-member-dashboard-progressive-ready', 'true');
            fetch(contentUrl, {
                credentials: 'same-origin',
                headers: {
                    'Accept': 'text/html'
                }
            })
                .then(function (response) {
                    if (response.redirected && response.url) {
                        var redirectedUrl = new URL(response.url, window.location.href);
                        if (/^\/(?:login|auth)(?:\/|$)/.test(redirectedUrl.pathname)) {
                            window.location.assign(redirectedUrl.href);
                            return null;
                        }
                    }
                    if (!response.ok) {
                        throw new Error('Dashboard content could not load.');
                    }
                    return response.text();
                })
                .then(function (html) {
                    if (!html) {
                        return;
                    }
                    var parsed = new DOMParser().parseFromString(html, 'text/html');
                    var nextContent = parsed.querySelector('[data-member-dashboard-content]');
                    if (!nextContent) {
                        throw new Error('Dashboard content could not load.');
                    }
                    shell.replaceWith(document.importNode(nextContent, true));
                    var dashboardPath = '${pageContext.request.contextPath}/app/dashboard';
                    if (window.location.pathname === dashboardPath && window.history && window.history.replaceState) {
                        window.history.replaceState(window.history.state, '', dashboardPath);
                    }
                    initMemberDashboard();
                    if (window.SaccosLiveAccountStatus && typeof window.SaccosLiveAccountStatus.hydrateAll === 'function') {
                        window.SaccosLiveAccountStatus.hydrateAll();
                    }
                })
                .catch(function () {
                    var errorBox = shell.querySelector('[data-dashboard-progressive-error]');
                    if (errorBox) {
                        errorBox.hidden = false;
                        errorBox.classList.remove('hidden');
                    }
                    if (typeof window.showToast === 'function') {
                        window.showToast('error', 'Dashboard content could not load. Refresh this page to try again.');
                    }
                });
        }

        window.SaccosMemberDashboard = {
            init: initMemberDashboard
        };
        initMemberDashboard();
        initProgressiveDashboard();
    }());
</script>

<%@ include file="../fragments/live-account-status-hydration.jspf" %>
<%@ include file="../fragments/repayment-schedule-hydration.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
