<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="../fragments/header.jspf" %>
<template data-resource-details-page></template>
<%@ include file="../fragments/sidebar.jspf" %>

<nav class="erp-page-path">
    <a href="/admin/dashboard"><spring:message code="admin.tools" /></a><span>/</span>
    <a href="/admin/saccos/registry"><spring:message code="admin.saccoRegistry.saccoRegistration" /></a><span>/</span>
    <span aria-current="page"><spring:message code="admin.institutionDetails.title" /></span>
</nav>
<div class="erp-page-header" data-aws-page-header>
    <h1 class="erp-page-title" id="admin-sacco-detail-title"><spring:message code="admin.institutionDetails.title" /></h1>
</div>
<%@ include file="../fragments/alerts.jspf" %>

<div data-institution-details data-detail-persistent
     data-loading="<spring:message code='common.loading' />"
     data-error="<spring:message code='admin.institutionDetails.loadError' />"
     data-retry="<spring:message code='admin.institutionDetails.retry' />">
    <section class="erp-panel mb-4" data-detail-summary>
        <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="admin.institutionDetails.summary" /></h2></div>
        <div class="erp-panel-body">
            <dl class="erp-detail-facts">
                <div><dt><spring:message code="admin.saccoRegistry.saccoName" /></dt><dd><c:out value="${saccoDetail.summary.saccoName}" /></dd></div>
                <div><dt><spring:message code="admin.institutionDetails.id" /></dt><dd><c:out value="${saccoDetail.saccoId}" /></dd></div>
                <div><dt><spring:message code="admin.saccoRegistry.stations" /></dt><dd><c:out value="${saccoDetail.summary.stationListLabel}" /></dd></div>
                <div><dt><spring:message code="admin.saccoRegistry.access" /></dt><dd><span data-resource-status="${saccoDetail.summary.accessStatus}"><spring:message code="admin.institutionDetails.access.${saccoDetail.summary.accessStatus}" /></span></dd></div>
                <div><dt><spring:message code="admin.institutionDetails.paymentDue" /></dt><dd><c:choose><c:when test="${empty saccoDetail.summary.paymentDueDate}"><spring:message code="common.notSet" /></c:when><c:otherwise><c:out value="${saccoDetail.summary.paymentDueDateLabel}" /></c:otherwise></c:choose></dd></div>
                <c:if test="${saccoDetail.summary.accessSuspended}"><div><dt><spring:message code="admin.institutionDetails.suspendedOn" /></dt><dd><c:out value="${saccoDetail.summary.accessSuspendedAtLabel}" /></dd></div></c:if>
                <c:if test="${not empty saccoDetail.summary.accessRestrictionReason}"><div><dt><spring:message code="common.reason" /></dt><dd><c:out value="${saccoDetail.summary.accessRestrictionReason}" /></dd></div></c:if>
            </dl>
        </div>
    </section>

    <section class="erp-panel mb-4">
        <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="admin.institutionDetails.branchScope" /></h2></div>
        <form action="/admin/saccos/${saccoDetail.saccoId}" method="get" class="erp-panel-body institution-details-controls" data-institution-scope>
            <input type="hidden" name="section" value="${selectedSection}" />
            <label for="institution-branch"><spring:message code="admin.saccoRegistry.station" />
                <select id="institution-branch" name="stationId" class="aws-control">
                    <option value="" ${empty saccoDetail.selectedStationId ? 'selected' : ''}><spring:message code="admin.institutionDetails.allBranches" /></option>
                    <c:forEach items="${saccoDetail.stationOptions}" var="station"><option value="<c:out value='${station}' />" ${saccoDetail.selectedStationId eq station ? 'selected' : ''}><c:out value="${station}" /></option></c:forEach>
                </select>
            </label>
            <button type="submit" class="app-btn btn-neutral"><spring:message code="common.apply" /></button>
            <a href="/admin/saccos/${saccoDetail.saccoId}?section=${selectedSection}" class="app-btn btn-neutral" data-institution-reset><spring:message code="common.reset" /></a>
        </form>
    </section>

    <sec:authorize access="@access.has(principal, 'SACCO_REGISTRY_UPDATE')">
        <c:if test="${saccoDetail.stationScoped}">
            <section class="erp-panel mb-4">
                <div class="erp-panel-header"><h2 class="erp-panel-title"><spring:message code="admin.institutionDetails.branchAccess" /></h2></div>
                <c:choose>
                    <c:when test="${saccoDetail.summary.accessSuspended}">
                        <form action="/admin/saccos/${saccoDetail.saccoId}/access/restore" method="post" class="erp-panel-body">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                            <input type="hidden" name="stationId" value="<c:out value='${saccoDetail.selectedStationId}' />" />
                            <button type="submit" class="app-btn btn-launch"><spring:message code="admin.institutionDetails.restore" /></button>
                        </form>
                    </c:when>
                    <c:otherwise>
                        <spring:message code="admin.institutionDetails.confirmTitle" var="suspendTitle" />
                        <spring:message code="admin.institutionDetails.confirmMessage" arguments="${saccoDetail.selectedStationId}" var="suspendMessage" />
                        <spring:message code="admin.institutionDetails.suspend" var="suspendLabel" />
                        <form action="/admin/saccos/${saccoDetail.saccoId}/access/suspend" method="post" class="erp-panel-body institution-details-controls"
                              data-confirm-title="<c:out value='${suspendTitle}' />" data-confirm-message="<c:out value='${suspendMessage}' />" data-confirm-proceed="<c:out value='${suspendLabel}' />">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                            <input type="hidden" name="stationId" value="<c:out value='${saccoDetail.selectedStationId}' />" />
                            <label for="institution-suspend-reason"><spring:message code="common.reason" /><input id="institution-suspend-reason" name="reason" required maxlength="500" class="aws-control" /></label>
                            <label for="institution-payment-due"><spring:message code="admin.institutionDetails.paymentDue" /><input id="institution-payment-due" name="paymentDueDate" type="date" class="aws-control" /></label>
                            <button type="submit" class="app-btn btn-reject"><c:out value="${suspendLabel}" /></button>
                        </form>
                    </c:otherwise>
                </c:choose>
            </section>
        </c:if>
    </sec:authorize>

    <nav class="console-detail-tabs" aria-label="<spring:message code='admin.institutionDetails.sections' />" data-institution-tabs>
        <c:forTokens items="overview,loans,members,financials,sms,audit" delims="," var="tab">
            <c:url value="/admin/saccos/${saccoDetail.saccoId}" var="tabUrl"><c:param name="section" value="${tab}" /><c:if test="${not empty saccoDetail.selectedStationId}"><c:param name="stationId" value="${saccoDetail.selectedStationId}" /></c:if></c:url>
            <a id="institution-tab-${tab}" href="<c:out value='${tabUrl}' />" data-institution-tab="${tab}" role="tab" aria-selected="${selectedSection eq tab}" aria-controls="institution-section" ${selectedSection eq tab ? 'aria-current="page"' : ''}><spring:message code="admin.institutionDetails.tab.${tab}" /></a>
        </c:forTokens>
    </nav>
    <div id="institution-section" data-institution-section="${selectedSection}" role="tabpanel" aria-labelledby="institution-tab-${selectedSection}" tabindex="0">
        <c:choose>
            <c:when test="${selectedSection eq 'loans'}">
                <section class="erp-resource-section mb-4"><h2><spring:message code="admin.institutionDetails.loanPortfolio" /></h2>
                    <dl class="erp-detail-facts">
                        <div><dt><spring:message code="admin.institutionDetails.applications" /></dt><dd><fmt:formatNumber value="${saccoDetail.totalLoanApplicationCount}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.activeLoans" /></dt><dd><fmt:formatNumber value="${saccoDetail.summary.activeLoanCount}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.disbursed" /></dt><dd><c:out value="${saccoDetail.summary.totalDisbursedPrincipalFullLabel}" /></dd></div>
                    </dl>
                </section>
                <section class="erp-table-wrap" data-resource-table="admin-sacco-detail-1">
                    <div class="app-table-titlebar"><h2 class="erp-panel-title"><spring:message code="admin.institutionDetails.loanStatuses" /></h2></div>
                    <div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="local"><thead><tr><th><spring:message code="common.status" /></th><th><spring:message code="admin.institutionDetails.applications" /></th></tr></thead><tbody>
                        <c:forEach items="${saccoDetail.loanStatusCounts}" var="statusCount"><tr><td><span data-resource-status="${statusCount.status}"><spring:message code="admin.institutionDetails.loanStatus.${statusCount.status}" /></span></td><td><fmt:formatNumber value="${statusCount.count}" /></td></tr></c:forEach>
                        <c:if test="${empty saccoDetail.loanStatusCounts}"><tr><td colspan="2"><spring:message code="admin.institutionDetails.noLoans" /></td></tr></c:if>
                    </tbody></table></div>
                </section>
            </c:when>
            <c:when test="${selectedSection eq 'members'}">
                <section class="erp-resource-section mb-4"><h2><spring:message code="admin.institutionDetails.clientsSummary" /></h2>
                    <dl class="erp-detail-facts">
                        <div><dt><spring:message code="admin.institutionDetails.clients" /></dt><dd><fmt:formatNumber value="${saccoDetail.summary.totalMembers}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.activeClients" /></dt><dd><fmt:formatNumber value="${saccoDetail.summary.activeMembers}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.inactiveClients" /></dt><dd><fmt:formatNumber value="${saccoDetail.summary.inactiveMembers}" /></dd></div>
                    </dl>
                </section>
                <section class="erp-table-wrap" data-resource-table="admin-sacco-detail-2" data-table-page-param="memberPage">
                    <div class="app-table-titlebar"><h2 class="erp-panel-title"><spring:message code="admin.institutionDetails.clients" /> <span class="app-table-count">(${saccoMembersPage.totalElements})</span></h2></div>
                    <form method="get" action="/admin/saccos/${saccoDetail.saccoId}" class="aws-filter-toolbar" data-aws-filter-toolbar>
                        <input type="hidden" name="section" value="members" /><input type="hidden" name="stationId" value="<c:out value='${saccoDetail.selectedStationId}' />" />
                        <label><span class="sr-only"><spring:message code="admin.institutionDetails.searchClients" /></span><input type="search" name="memberQuery" value="<c:out value='${selectedMemberQuery}' />" class="aws-control" placeholder="<spring:message code='admin.institutionDetails.searchClients' />" /></label>
                        <label data-institution-page-size><span class="sr-only"><spring:message code="admin.institutionDetails.rows" /></span><select name="memberSize" class="aws-control" aria-label="<spring:message code='admin.institutionDetails.rows' />"><c:forTokens items="25,50,100" delims="," var="size"><option value="${size}" ${selectedMemberPageSize eq size ? 'selected' : ''}>${size}</option></c:forTokens></select></label>
                        <button type="submit" class="app-btn btn-neutral"><spring:message code="common.search" /></button>
                    </form>
                    <div class="erp-table-scroll"><table class="erp-table"><thead><tr>
                        <th><spring:message code="admin.institutionDetails.userId" /></th><th><spring:message code="admin.institutionDetails.clientNumber" /></th><th><spring:message code="admin.institutionDetails.name" /></th><th><spring:message code="admin.institutionDetails.accountType" /></th><th><spring:message code="admin.institutionDetails.roles" /></th><th><spring:message code="common.status" /></th>
                    </tr></thead><tbody>
                        <c:forEach items="${saccoMembers}" var="member"><tr><td><c:out value="${member.userIdLabel}" /></td><td><c:out value="${member.loginId}" /></td><td><c:out value="${member.fullName}" /></td><td><c:out value="${member.membershipLabel}" /></td><td><c:out value="${empty member.roleSummary ? '-' : member.roleSummary}" /></td><td><span data-resource-status="${member.status}"><c:out value="${member.displayStatus}" /></span></td></tr></c:forEach>
                        <c:if test="${empty saccoMembers}"><tr><td colspan="6"><spring:message code="admin.institutionDetails.noClients" /></td></tr></c:if>
                    </tbody></table></div>
                    <div data-table-pagination data-table-for="admin-sacco-detail-2" data-page="${saccoMembersPage.number}">
                        <c:if test="${not saccoMembersPage.first}"><a href="/admin/saccos/${saccoDetail.saccoId}?memberPage=${saccoMembersPage.number - 1}${saccoMembersPaginationQuery}" data-page-direction="previous"><spring:message code="common.previous" /></a></c:if>
                        <c:if test="${not saccoMembersPage.last}"><a href="/admin/saccos/${saccoDetail.saccoId}?memberPage=${saccoMembersPage.number + 1}${saccoMembersPaginationQuery}" data-page-direction="next"><spring:message code="common.next" /></a></c:if>
                    </div>
                </section>
            </c:when>
            <c:when test="${selectedSection eq 'financials'}">
                <div class="console-message mb-4" data-message-type="info"><spring:message code="admin.institutionDetails.estimates" /></div>
                <section class="erp-resource-section"><h2><spring:message code="admin.institutionDetails.tab.financials" /></h2>
                    <dl class="erp-detail-facts">
                        <div><dt><spring:message code="admin.institutionDetails.disbursed" /></dt><dd><c:out value="${saccoDetail.summary.totalDisbursedPrincipalFullLabel}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.exposure" /></dt><dd><c:out value="${saccoDetail.summary.activeExposureFullLabel}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.legacyBalance" /></dt><dd><c:out value="${saccoDetail.summary.totalSavingsFullLabel}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.repaymentProxy" /></dt><dd><c:out value="${saccoDetail.summary.repaymentPercentLabel}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.defaultProxy" /></dt><dd><c:out value="${saccoDetail.summary.defaultPercentLabel}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.liquidityProxy" /></dt><dd><c:out value="${saccoDetail.summary.liquidityRatioLabel}" /></dd></div>
                    </dl>
                </section>
            </c:when>
            <c:when test="${selectedSection eq 'sms'}">
                <c:choose>
                    <c:when test="${saccoDetail.stationScoped}">
                        <section class="erp-panel mb-4">
                            <div class="erp-panel-header flex flex-wrap items-center justify-between gap-3"><h2 class="erp-panel-title"><spring:message code="admin.institutionDetails.smsBalance" /></h2>
                                <sec:authorize access="@access.has(principal, 'SMS_USAGE_VIEW')"><a href="/admin/sms-usage?saccoId=${saccoDetail.saccoId}&amp;stationId=${saccoDetail.selectedStationId}" class="app-btn btn-neutral"><spring:message code="admin.institutionDetails.manageSms" /></a></sec:authorize>
                            </div>
                            <div class="erp-panel-body">
                            <dl class="erp-detail-facts">
                                <div><dt><spring:message code="admin.institutionDetails.availableUnits" /></dt><dd><fmt:formatNumber value="${smsAccount.availableUnits}" /></dd></div>
                                <div><dt><spring:message code="admin.institutionDetails.reserve" /></dt><dd><fmt:formatNumber value="${smsAccount.alertReservedUnits}" /> / 3</dd></div>
                                <div><dt><spring:message code="admin.institutionDetails.baseline" /></dt><dd><fmt:formatNumber value="${smsAccount.warningBaseline}" /></dd></div>
                                <div><dt><spring:message code="common.status" /></dt><dd><span data-resource-status="${smsAccount.status}"><spring:message code="admin.institutionDetails.smsStatus.${smsAccount.status}" /></span></dd></div>
                            </dl></div>
                        </section>
                        <section class="erp-table-wrap" data-resource-table="admin-sacco-detail-3" data-table-page-param="smsHistoryPage">
                            <div class="app-table-titlebar"><h2 class="erp-panel-title"><spring:message code="admin.institutionDetails.smsHistory" /></h2></div>
                            <div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="admin.institutionDetails.time" /></th><th><spring:message code="admin.institutionDetails.event" /></th><th><spring:message code="admin.institutionDetails.outcome" /></th><th><spring:message code="admin.institutionDetails.unitChange" /></th><th><spring:message code="admin.institutionDetails.reference" /></th><th><spring:message code="admin.institutionDetails.note" /></th></tr></thead><tbody>
                                <c:forEach items="${smsHistory.content}" var="entry"><tr><td><c:out value="${appTime:format(entry.createdAt)}" /></td><td><c:out value="${empty entry.eventType ? '-' : entry.eventType}" /></td><td><span data-resource-status="${entry.outcome}"><spring:message code="admin.institutionDetails.smsOutcome.${entry.outcome}" /></span></td><td><fmt:formatNumber value="${entry.unitChange}" /></td><td><c:out value="${empty entry.providerReference ? '-' : entry.providerReference}" /></td><td><c:out value="${empty entry.note ? '-' : entry.note}" /></td></tr></c:forEach>
                                <c:if test="${empty smsHistory.content}"><tr><td colspan="6"><spring:message code="admin.institutionDetails.noSmsHistory" /></td></tr></c:if>
                            </tbody></table></div>
                            <div data-table-pagination data-table-for="admin-sacco-detail-3" data-page="${smsHistory.number}">
                                <c:if test="${not smsHistory.first}"><a href="/admin/saccos/${saccoDetail.saccoId}?section=sms&amp;stationId=${saccoDetail.selectedStationId}&amp;smsHistoryPage=${smsHistory.number - 1}" data-page-direction="previous"><spring:message code="common.previous" /></a></c:if>
                                <c:if test="${not smsHistory.last}"><a href="/admin/saccos/${saccoDetail.saccoId}?section=sms&amp;stationId=${saccoDetail.selectedStationId}&amp;smsHistoryPage=${smsHistory.number + 1}" data-page-direction="next"><spring:message code="common.next" /></a></c:if>
                            </div>
                        </section>
                    </c:when>
                    <c:otherwise>
                        <section class="erp-table-wrap" data-resource-table="admin-sacco-detail-4" data-table-page-param="smsPage" data-table-selection="single">
                            <div class="app-table-titlebar"><h2 class="erp-panel-title"><spring:message code="admin.institutionDetails.smsBalances" /></h2><div class="app-table-toolbar"><sec:authorize access="@access.has(principal, 'SMS_USAGE_VIEW')"><a href="/admin/sms-usage?saccoId=${saccoDetail.saccoId}" class="app-btn btn-neutral"><spring:message code="admin.institutionDetails.manageSms" /></a></sec:authorize></div></div>
                            <div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="admin.saccoRegistry.station" /></th><th><spring:message code="admin.institutionDetails.availableUnits" /></th><th><spring:message code="admin.institutionDetails.reserve" /></th><th><spring:message code="admin.institutionDetails.baseline" /></th><th><spring:message code="common.status" /></th><th data-table-actions-header><spring:message code="common.actions" /></th></tr></thead><tbody>
                                <c:forEach items="${smsAccounts.content}" var="account"><tr>
                                    <c:url value="/admin/saccos/${saccoDetail.saccoId}" var="smsBranchUrl"><c:param name="section" value="sms" /><c:param name="stationId" value="${account.stationId}" /></c:url>
                                    <td><a class="resource-table-id-link" href="<c:out value='${smsBranchUrl}' />"><c:out value="${account.stationId}" /></a></td><td><fmt:formatNumber value="${account.availableUnits}" /></td><td><fmt:formatNumber value="${account.alertReservedUnits}" /> / 3</td><td><fmt:formatNumber value="${account.warningBaseline}" /></td><td><span data-resource-status="${account.status}"><spring:message code="admin.institutionDetails.smsStatus.${account.status}" /></span></td><td><a href="<c:out value='${smsBranchUrl}' />" class="app-btn btn-neutral"><spring:message code="admin.saccoRegistry.viewDetails" /></a></td>
                                </tr></c:forEach>
                                <c:if test="${empty smsAccounts.content}"><tr><td colspan="6"><spring:message code="admin.institutionDetails.noSmsAccounts" /></td></tr></c:if>
                            </tbody></table></div>
                            <div data-table-pagination data-table-for="admin-sacco-detail-4" data-page="${smsAccounts.number}">
                                <c:if test="${not smsAccounts.first}"><a href="/admin/saccos/${saccoDetail.saccoId}?section=sms&amp;smsPage=${smsAccounts.number - 1}" data-page-direction="previous"><spring:message code="common.previous" /></a></c:if>
                                <c:if test="${not smsAccounts.last}"><a href="/admin/saccos/${saccoDetail.saccoId}?section=sms&amp;smsPage=${smsAccounts.number + 1}" data-page-direction="next"><spring:message code="common.next" /></a></c:if>
                            </div>
                        </section>
                    </c:otherwise>
                </c:choose>
            </c:when>
            <c:when test="${selectedSection eq 'audit'}">
                <section class="erp-table-wrap" data-resource-table="admin-sacco-detail-audit">
                    <div class="app-table-titlebar"><h2 class="erp-panel-title"><spring:message code="admin.institutionDetails.auditActivity" /></h2></div>
                    <div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="local"><thead><tr><th><spring:message code="admin.institutionDetails.time" /></th><th><spring:message code="admin.institutionDetails.event" /></th><th><spring:message code="admin.institutionDetails.source" /></th><th><spring:message code="admin.institutionDetails.actor" /></th></tr></thead><tbody>
                        <c:forEach items="${saccoDetail.recentAuditEntries}" var="entry"><tr><td><c:out value="${entry.createdAtLabel}" /></td><td><c:out value="${entry.actionLabel}" /></td><td><c:out value="${entry.sourceLabel}" /></td><td><c:out value="${entry.actorLabel}" /></td></tr></c:forEach>
                        <c:if test="${empty saccoDetail.recentAuditEntries}"><tr><td colspan="4"><spring:message code="admin.institutionDetails.noAudit" /></td></tr></c:if>
                    </tbody></table></div>
                </section>
            </c:when>
            <c:otherwise>
                <section class="erp-resource-section"><h2><spring:message code="admin.institutionDetails.tab.overview" /></h2>
                    <dl class="erp-detail-facts">
                        <div><dt><spring:message code="admin.institutionDetails.clients" /></dt><dd><fmt:formatNumber value="${saccoDetail.summary.totalMembers}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.activeClients" /></dt><dd><fmt:formatNumber value="${saccoDetail.summary.activeMembers}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.inactiveClients" /></dt><dd><fmt:formatNumber value="${saccoDetail.summary.inactiveMembers}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.applications" /></dt><dd><fmt:formatNumber value="${saccoDetail.totalLoanApplicationCount}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.activeLoans" /></dt><dd><fmt:formatNumber value="${saccoDetail.summary.activeLoanCount}" /></dd></div>
                        <div><dt><spring:message code="admin.institutionDetails.disbursed" /></dt><dd><c:out value="${saccoDetail.summary.totalDisbursedPrincipalFullLabel}" /></dd></div>
                    </dl>
                </section>
            </c:otherwise>
        </c:choose>
    </div>
</div>
<script src="<c:url value='/js/institution-details.js' />" defer></script>
<%@ include file="../fragments/confirm-modal.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
