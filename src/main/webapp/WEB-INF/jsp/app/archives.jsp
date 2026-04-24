<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/otp-ui-styles.jspf" %>
<div class="erp-page-header">
    <p class="erp-breadcrumb">Member Workspace / Archives</p>
    <h1 class="erp-page-title">Archives</h1>
</div>

<c:choose>
    <c:when test="${archiveSection eq 'guarantors'}">
        <section id="guarantor-archive-section" class="erp-panel overflow-hidden">
            <div class="border-b border-slate-200 px-4 py-4">
                <div class="flex flex-wrap items-center justify-between gap-3">
                    <div>
                        <p class="text-sm font-semibold uppercase tracking-[0.18em] text-slate-500">Guarantee Archive</p>
                        <p class="mt-2 text-sm text-slate-600">Search by loan ID and filter archived guarantor decisions.</p>
                    </div>
                    <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">${fn:length(guarantorArchives)} shown</span>
                </div>
                <form action="/app/archives" method="get" class="erp-filter-form mt-4 grid gap-3 md:grid-cols-[minmax(0,1.2fr)_220px_auto]">
                    <input type="hidden" name="section" value="guarantors" />
                    <label class="block">
                        <span class="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">Loan ID Search</span>
                        <input type="text"
                               name="guarantorArchiveQuery"
                               value="${guarantorArchiveQuery}"
                               placeholder="Search loan ID"
                               class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800 focus:border-cyan-500 focus:outline-none" />
                    </label>
                    <label class="block">
                        <span class="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">Filter</span>
                        <select name="guarantorArchiveFilter" class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800 focus:border-cyan-500 focus:outline-none">
                            <option value="ALL" ${guarantorArchiveFilter eq 'ALL' ? 'selected' : ''}>All decisions</option>
                            <option value="APPROVED" ${guarantorArchiveFilter eq 'APPROVED' ? 'selected' : ''}>Approved</option>
                            <option value="REJECTED" ${guarantorArchiveFilter eq 'REJECTED' ? 'selected' : ''}>Rejected</option>
                            <option value="EXPIRED" ${guarantorArchiveFilter eq 'EXPIRED' ? 'selected' : ''}>Expired</option>
                        </select>
                    </label>
                    <div class="flex items-end gap-2">
                        <button type="submit" class="app-btn btn-primary">Apply</button>
                        <a href="/app/archives?section=guarantors" class="app-btn btn-neutral">Reset</a>
                    </div>
                </form>
            </div>
            <div class="erp-table-wrap overflow-x-auto border-0 shadow-none">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th>Loan Reference</th>
                        <th>Applicant</th>
                        <th>Loan Type</th>
                        <th>Amount</th>
                        <th>Decision</th>
                        <th>Date</th>
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
                            <td>${guaranteeLoanAmounts[req.loanApplicationId]}</td>
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
                            <td colspan="6">No archived guarantor decisions match this filter.</td>
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
                        <p class="text-sm font-semibold uppercase tracking-[0.18em] text-slate-500">Loan Archive</p>
                        <p class="mt-2 text-sm text-slate-600">Search by loan ID and filter archived loan outcomes.</p>
                    </div>
                    <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-700">${fn:length(archives)} shown</span>
                </div>
                <form action="/app/archives" method="get" class="erp-filter-form mt-4 grid gap-3 md:grid-cols-[minmax(0,1.2fr)_220px_auto]">
                    <input type="hidden" name="section" value="loans" />
                    <label class="block">
                        <span class="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">Loan ID Search</span>
                        <input type="text"
                               name="loanArchiveQuery"
                               value="${loanArchiveQuery}"
                               placeholder="Search loan ID"
                               class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800 focus:border-cyan-500 focus:outline-none" />
                    </label>
                    <label class="block">
                        <span class="mb-1 block text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">Filter</span>
                        <select name="loanArchiveFilter" class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm text-slate-800 focus:border-cyan-500 focus:outline-none">
                            <option value="ALL" ${loanArchiveFilter eq 'ALL' ? 'selected' : ''}>All archived loans</option>
                            <option value="DISBURSED" ${loanArchiveFilter eq 'DISBURSED' ? 'selected' : ''}>Disbursed only</option>
                            <option value="DEFAULTED" ${loanArchiveFilter eq 'DEFAULTED' ? 'selected' : ''}>Defaulted only</option>
                            <option value="PAID" ${loanArchiveFilter eq 'PAID' ? 'selected' : ''}>Paid only</option>
                            <option value="REJECTED" ${loanArchiveFilter eq 'REJECTED' ? 'selected' : ''}>Rejected only</option>
                        </select>
                    </label>
                    <div class="flex items-end gap-2">
                        <button type="submit" class="app-btn btn-primary">Apply</button>
                        <a href="/app/archives?section=loans" class="app-btn btn-neutral">Reset</a>
                    </div>
                </form>
            </div>
            <div class="erp-table-wrap overflow-x-auto border-0 shadow-none">
                <table class="erp-table">
                    <thead>
                    <tr>
                        <th>Loan Application ID</th>
                        <th>Loan ID</th>
                        <th>Loan Type</th>
                        <th>Amount</th>
                        <th>Status</th>
                        <th>Archive Note</th>
                        <th>Date</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${archives}" var="app">
                        <tr>
                            <td>${app.applicationNumber}</td>
                            <td><c:out value="${empty app.loanId ? '-' : app.loanId}" /></td>
                            <td><spring:message code="loan.type.${app.loanType}" text="${app.loanType}" /></td>
                            <td>${app.amount}</td>
                            <td><spring:message code="loan.status.${app.status}" text="${app.status}" /></td>
                            <td>
                                <c:choose>
                                    <c:when test="${app.status eq 'FINAL_APPROVED'}">Disbursed loan moved to archive</c:when>
                                    <c:when test="${app.status eq 'DEFAULTED'}">Loan passed the final due date and remains unpaid</c:when>
                                    <c:when test="${app.status eq 'PAID'}">Manager confirmed repayment completed</c:when>
                                    <c:when test="${not empty managerReasons[app.id]}">${managerReasons[app.id]}</c:when>
                                    <c:otherwise><spring:message code="loan.status.${app.status}" text="${app.status}" /></c:otherwise>
                                </c:choose>
                            </td>
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

<script>
    (() => {
        document.querySelectorAll("form[data-confirm-title]").forEach((form) => {
            form.addEventListener("submit", () => {
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
