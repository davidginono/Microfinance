<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="guaranteedLoans.breadcrumb" text="Borrower Workspace / Guarantees" /></p>
    <h1 class="erp-page-title"><spring:message code="guaranteedLoans.title" text="Loans I Guarantee" /></h1>
</div>

<section class="erp-table-wrap guaranteed-loans-table" aria-labelledby="guaranteedLoansTableTitle"
         data-aws-table-region data-aws-client-table data-loading-label="Loading results...">
    <div class="app-table-titlebar">
        <div class="app-table-heading">
            <h2 id="guaranteedLoansTableTitle"><spring:message code="guaranteedLoans.activePosition" text="Active Guarantee Position" /></h2>
            <span class="app-table-count">(<fmt:formatNumber value="${fn:length(guaranteedLoans)}" />)</span>
    </div>
        <div class="app-table-toolbar">
            <button type="button" class="app-icon-button btn-neutral" data-aws-table-refresh
                    aria-label="<spring:message code='common.refresh' text='Refresh guaranteed loans' />">
                <i data-lucide="refresh-cw" aria-hidden="true"></i>
            </button>
    </div>
    </div>

    <div class="aws-filter-toolbar guaranteed-loans-filter" data-aws-client-table-toolbar>
        <label class="erp-table-toolbar__search" for="guaranteedLoansSearch">
            <span class="sr-only"><spring:message code="guaranteedLoans.search" text="Find guaranteed loans" /></span>
            <input id="guaranteedLoansSearch" type="search" class="aws-control"
                   data-aws-table-search
                   placeholder="<spring:message code='guaranteedLoans.search' text='Find guaranteed loans' />"
                   autocomplete="off" />
        </label>
    </div>

    <div class="erp-table-scroll" data-view-position-key="Member-guaranteed-loans">
        <table class="erp-table">
            <thead>
            <tr>
                <th class="whitespace-nowrap"><spring:message code="loan.id" text="Loan ID" /></th>
                <th><spring:message code="common.applicant" text="Applicant" /></th>
                <th><spring:message code="reports.loanProduct" text="Loan Product" /></th>
                <th><spring:message code="common.status" text="Status" /></th>
                <th class="text-right whitespace-nowrap"><spring:message code="common.amount" text="Amount" /> (TZS)</th>
                <th class="whitespace-nowrap"><spring:message code="loan.repayment.finalDueDate" text="Final Due Date" /></th>
                <th class="whitespace-nowrap"><spring:message code="loan.repayment.timeLeft" text="Time Left" /></th>
            </tr>
            </thead>
            <tbody>
            <c:forEach items="${guaranteedLoans}" var="row">
                <tr data-aws-table-row>
                    <td class="font-semibold text-sacco-ink whitespace-nowrap">
                        <c:choose>
                            <c:when test="${row.loan ne null and not empty row.loan.loanId}"><c:out value="${row.loan.loanId}" /></c:when>
                            <c:when test="${row.loan ne null}"><c:out value="${row.loan.applicationNumber}" /></c:when>
                            <c:otherwise><c:out value="${row.request.loanApplicationId}" /></c:otherwise>
                        </c:choose>
                    </td>
                    <td><c:out value="${row.applicantName}" /></td>
                    <td><c:out value="${row.loanProductName}" /></td>
                    <td><span class="app-badge loan-status-badge"><c:out value="${row.statusLabel}" /></span></td>
                    <td class="text-right whitespace-nowrap">
                        <c:choose>
                            <c:when test="${row.amount ne null}"><fmt:formatNumber value="${row.amount}" minFractionDigits="2" maxFractionDigits="2" /></c:when>
                            <c:otherwise>-</c:otherwise>
                        </c:choose>
                    </td>
                    <td class="whitespace-nowrap">
                        <c:choose>
                            <c:when test="${row.finalDueDate ne null}"><c:out value="${row.finalDueDate}" /></c:when>
                            <c:otherwise><spring:message code="loan.repayment.notScheduled" text="Not scheduled" /></c:otherwise>
                        </c:choose>
                    </td>
                    <td class="whitespace-nowrap">
                        <c:choose>
                            <c:when test="${row.daysLeft ne null}"><c:out value="${row.daysLeft}" /> <spring:message code="common.days" text="day(s)" /></c:when>
                            <c:otherwise><spring:message code="loan.repayment.notScheduled" text="Not scheduled" /></c:otherwise>
                        </c:choose>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty guaranteedLoans}">
                <tr>
                    <td colspan="7" class="erp-table-empty"><spring:message code="guaranteedLoans.empty" text="No active guaranteed loans yet." /></td>
                </tr>
            </c:if>
            </tbody>
        </table>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
