<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>
<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="archives.breadcrumb" text="Member Workspace / Archives" /></p>
    <h1 class="erp-page-title"><spring:message code="archives.title" text="Archives" /></h1>
</div>

<c:choose>
    <c:when test="${archiveSection eq 'guarantors'}">
        <section id="guarantor-archive-section" class="erp-panel overflow-hidden">
            <div class="border-b border-slate-200 px-4 py-4">
                <div class="flex flex-wrap items-center justify-between gap-3">
                    <div>
                        <p class="text-sm font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="archives.guarantee.title" text="Guarantee Archive" /></p>
                        <p class="mt-2 text-sm text-slate-600"><spring:message code="archives.guarantee.subtitle" text="Search by loan ID and filter archived guarantor decisions." /></p>
                    </div>
                    <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">${fn:length(guarantorArchives)} <spring:message code="common.shown" text="shown" /></span>
                </div>
                <form action="/app/archives" method="get" class="erp-filter-form mt-4 grid gap-3 md:grid-cols-[minmax(0,1.2fr)_220px_auto]">
                    <input type="hidden" name="section" value="guarantors" />
                    <label class="block">
                        <span class="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-slate-500"><spring:message code="archives.loanIdSearch" text="Loan ID Search" /></span>
                        <input type="text"
                               name="guarantorArchiveQuery"
                               value="${guarantorArchiveQuery}"
                               placeholder="<spring:message code='common.searchLoanId' text='Search loan ID' />"
                               class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800 focus:border-cyan-500 focus:outline-none" />
                    </label>
                    <label class="block">
                        <span class="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-slate-500"><spring:message code="common.filter" text="Filter" /></span>
                        <select name="guarantorArchiveFilter" class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800 focus:border-cyan-500 focus:outline-none">
                            <option value="ALL" ${guarantorArchiveFilter eq 'ALL' ? 'selected' : ''}><spring:message code="review.allDecisions" text="All decisions" /></option>
                            <option value="APPROVED" ${guarantorArchiveFilter eq 'APPROVED' ? 'selected' : ''}><spring:message code="review.approved" text="Approved" /></option>
                            <option value="REJECTED" ${guarantorArchiveFilter eq 'REJECTED' ? 'selected' : ''}><spring:message code="review.rejected" text="Rejected" /></option>
                            <option value="EXPIRED" ${guarantorArchiveFilter eq 'EXPIRED' ? 'selected' : ''}><spring:message code="common.expired" text="Expired" /></option>
                        </select>
                    </label>
                    <div class="flex items-end gap-2">
                        <button type="submit" class="app-btn btn-primary"><spring:message code="common.apply" text="Apply" /></button>
                        <a href="/app/archives?section=guarantors" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
                    </div>
                </form>
            </div>
            <div class="erp-table-wrap erp-table-scroll border-0 shadow-none">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th><spring:message code="archives.loanReference" text="Loan Reference" /></th>
                        <th><spring:message code="common.applicant" text="Applicant" /></th>
                        <th><spring:message code="loan.type" text="Loan Type" /></th>
                        <th><spring:message code="common.amount" text="Amount" /></th>
                        <th><spring:message code="review.decision" text="Decision" /></th>
                        <th><spring:message code="loan.date" text="Date" /></th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${guarantorArchives}" var="req">
                        <tr>
                            <td>${fn:substring(req.loanApplicationId, 0, 8)}</td>
                            <td>${guaranteeNames[req.loanApplicationId]}</td>
                            <td>
                                <c:if test="${not empty guaranteeLoanTypes[req.loanApplicationId]}">
                                    <spring:message code="loan.type.${guaranteeLoanTypes[req.loanApplicationId]}" />
                                </c:if>
                            </td>
                            <td>${guaranteeLoanAmountLabels[req.loanApplicationId]}</td>
                            <td>${req.status}</td>
                            <td>
                                <c:choose>
                                <c:when test="${not empty req.decidedAt}">${fn:replace(fn:substring(req.decidedAt, 0, 16), 'T', ' ')}</c:when>
                                <c:otherwise>${fn:replace(fn:substring(req.createdAt, 0, 16), 'T', ' ')}</c:otherwise>
                            </c:choose>
                            </td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty guarantorArchives}">
                        <tr>
                            <td colspan="6"><spring:message code="archives.guarantee.empty" text="No archived guarantor decisions match this filter." /></td>
                        </tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </section>
    </c:when>
    <c:otherwise>
        <section id="loan-archive-section" class="erp-panel overflow-hidden">
            <div class="border-b border-slate-200 px-4 py-4">
                <div class="flex flex-wrap items-center justify-between gap-3">
                    <div>
                        <p class="text-sm font-semibold uppercase tracking-[0.18em] text-slate-500"><spring:message code="archives.loan.title" text="Loan Archive" /></p>
                        <p class="mt-2 text-sm text-slate-600"><spring:message code="archives.loan.subtitle" text="Search by loan ID and filter archived loan outcomes." /></p>
                    </div>
                    <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">${fn:length(archives)} <spring:message code="common.shown" text="shown" /></span>
                </div>
                <form action="/app/archives" method="get" class="erp-filter-form mt-4 grid gap-3 md:grid-cols-[minmax(0,1.2fr)_220px_auto]">
                    <input type="hidden" name="section" value="loans" />
                    <label class="block">
                        <span class="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-slate-500"><spring:message code="archives.loanIdSearch" text="Loan ID Search" /></span>
                        <input type="text"
                               name="loanArchiveQuery"
                               value="${loanArchiveQuery}"
                               placeholder="<spring:message code='common.searchLoanId' text='Search loan ID' />"
                               class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800 focus:border-cyan-500 focus:outline-none" />
                    </label>
                    <label class="block">
                        <span class="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-slate-500"><spring:message code="common.filter" text="Filter" /></span>
                        <select name="loanArchiveFilter" class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800 focus:border-cyan-500 focus:outline-none">
                            <option value="ALL" ${loanArchiveFilter eq 'ALL' ? 'selected' : ''}><spring:message code="archives.loan.allArchived" text="All archived loans" /></option>
                            <option value="DISBURSED" ${loanArchiveFilter eq 'DISBURSED' ? 'selected' : ''}><spring:message code="archives.loan.disbursedOnly" text="Disbursed only" /></option>
                            <option value="DEFAULTED" ${loanArchiveFilter eq 'DEFAULTED' ? 'selected' : ''}><spring:message code="archives.loan.defaultedOnly" text="Defaulted only" /></option>
                            <option value="PAID" ${loanArchiveFilter eq 'PAID' ? 'selected' : ''}><spring:message code="archives.loan.paidOnly" text="Paid only" /></option>
                            <option value="REJECTED" ${loanArchiveFilter eq 'REJECTED' ? 'selected' : ''}><spring:message code="archives.loan.rejectedOnly" text="Rejected only" /></option>
                        </select>
                    </label>
                    <div class="flex items-end gap-2">
                        <button type="submit" class="app-btn btn-primary"><spring:message code="common.apply" text="Apply" /></button>
                        <a href="/app/archives?section=loans" class="app-btn btn-neutral"><spring:message code="common.reset" text="Reset" /></a>
                    </div>
                </form>
            </div>
            <div class="erp-table-wrap erp-table-scroll border-0 shadow-none">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th><spring:message code="loan.applicationId" text="Loan Application ID" /></th>
                        <th><spring:message code="loan.loanId" text="Loan ID" /></th>
                        <th><spring:message code="loan.type" text="Loan Type" /></th>
                        <th><spring:message code="common.amount" text="Amount" /></th>
                        <th><spring:message code="common.status" text="Status" /></th>
                        <th><spring:message code="loan.date" text="Date" /></th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${archives}" var="app">
                        <tr>
                            <td>${app.applicationNumber}</td>
                            <td><c:out value="${empty app.loanId ? '-' : app.loanId}" /></td>
                            <td><spring:message code="loan.type.${app.loanType}" text="${app.loanType}" /></td>
                            <td><fmt:formatNumber value="${app.amount}" minFractionDigits="0" maxFractionDigits="2" /></td>
                            <td><spring:message code="loan.status.${app.status}" text="${app.status}" /></td>
                            <td>
                                <c:choose>
                                    <c:when test="${app.status eq 'PAID' and not empty app.paidAt}">${fn:replace(fn:substring(app.paidAt, 0, 16), 'T', ' ')}</c:when>
                                    <c:otherwise>${fn:replace(fn:substring(app.updatedAt, 0, 16), 'T', ' ')}</c:otherwise>
                                </c:choose>
                            </td>
                            <td><a href="/app/loan-applications/${app.id}" class="app-btn btn-primary"><spring:message code="common.view" /></a></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty archives}">
                        <tr>
                            <td colspan="7">No archived loans match this filter.</td>
                        </tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </section>
    </c:otherwise>
</c:choose>

<c:if test="${archivePage.totalPages gt 1}">
    <nav class="mt-4 flex items-center justify-between gap-3" aria-label="Archive pages">
        <c:choose>
            <c:when test="${not archivePage.first}">
                <c:url var="archivePreviousUrl" value="/app/archives">
                    <c:param name="section" value="${archiveSection}" />
                    <c:param name="page" value="${archivePage.number - 1}" />
                    <c:param name="loanArchiveQuery" value="${loanArchiveQuery}" />
                    <c:param name="loanArchiveFilter" value="${loanArchiveFilter}" />
                    <c:param name="guarantorArchiveQuery" value="${guarantorArchiveQuery}" />
                    <c:param name="guarantorArchiveFilter" value="${guarantorArchiveFilter}" />
                </c:url>
                <a class="app-btn btn-neutral" href="${archivePreviousUrl}">Previous</a>
            </c:when>
            <c:otherwise><span></span></c:otherwise>
        </c:choose>
        <span class="text-sm font-semibold text-slate-600">Page ${archivePage.number + 1} of ${archivePage.totalPages}</span>
        <c:if test="${not archivePage.last}">
            <c:url var="archiveNextUrl" value="/app/archives">
                <c:param name="section" value="${archiveSection}" />
                <c:param name="page" value="${archivePage.number + 1}" />
                <c:param name="loanArchiveQuery" value="${loanArchiveQuery}" />
                <c:param name="loanArchiveFilter" value="${loanArchiveFilter}" />
                <c:param name="guarantorArchiveQuery" value="${guarantorArchiveQuery}" />
                <c:param name="guarantorArchiveFilter" value="${guarantorArchiveFilter}" />
            </c:url>
            <a class="app-btn btn-neutral" href="${archiveNextUrl}">Next</a>
        </c:if>
    </nav>
</c:if>

<script>
    (() => {
        document.querySelectorAll("form[data-confirm-title]").forEach((form) => {
            form.addEventListener("submit", () => {
                if (form.dataset.confirmTitle) {
                    return;
                }
                const submitButton = form.querySelector("button[type='submit']");
                if (!submitButton || submitButton.disabled) {
                    return;
                }
                submitButton.disabled = true;
                submitButton.classList.add("action-button-disabled");
            });
        });
    })();
</script>

<%@ include file="../fragments/confirm-modal.jspf" %>

<%@ include file="../fragments/footer.jspf" %>
