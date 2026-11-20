<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<div class="accounting-workspace">
    <div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="branches.oversight" /></h1></div>
    <c:if test="${not empty branchError}"><div class="console-message" data-message-type="error" role="alert"><spring:message code="${branchError}" /></div></c:if>
    <c:if test="${not empty branch}">
        <section class="erp-resource-section">
            <h2><spring:message code="branches.branch" /></h2>
            <div class="erp-panel-body space-y-4">
                <form method="get" action="<c:url value='/staff/branches' />" class="flex flex-wrap items-end gap-2">
                    <label class="min-w-0">
                        <span class="sr-only"><spring:message code="branches.branch" /></span>
                        <select name="branch" class="aws-control">
                            <c:if test="${not branchChoices.contains(branch.id)}">
                                <option selected value="<c:out value='${branch.id}' />"><c:out value="${branch.id}" /></option>
                            </c:if>
                            <c:forEach var="choice" items="${branchChoices}">
                                <option value="<c:out value='${choice}' />" ${branch.id eq choice ? 'selected' : ''}><c:out value="${choice}" /></option>
                            </c:forEach>
                        </select>
                    </label>
                    <button type="submit" class="app-btn btn-neutral"><spring:message code="common.view" /></button>
                </form>
                <c:set var="branchState" value="${branch.suspended ? 'STOPPED' : branch.active ? 'ACTIVE' : 'INACTIVE'}" />
                <dl class="erp-detail-facts">
                    <div><dt><spring:message code="branches.location" /></dt><dd><c:out value="${branch.location}" /></dd></div>
                    <div><dt><spring:message code="common.status" /></dt><dd><span data-resource-status="${branchState}"><spring:message code="${branch.suspended ? 'branches.suspended' : branch.active ? 'branches.active' : 'branches.inactive'}" /></span></dd></div>
                </dl>
                <c:if test="${showClients or showLoans or showReports or showFinancial}">
                    <div class="flex flex-wrap gap-2">
                        <c:forTokens items="clients,loans,reports,statements" delims="," var="section">
                            <c:if test="${section eq 'clients' ? showClients : section eq 'loans' ? showLoans : section eq 'reports' ? showReports : showFinancial}">
                                <c:url value="/staff/branches/${section}" var="link"><c:param name="branch" value="${branch.id}" /></c:url>
                                <a class="app-btn btn-neutral" href="<c:out value='${link}' />"><spring:message code="branches.section.${section}" /></a>
                            </c:if>
                        </c:forTokens>
                    </div>
                </c:if>
            </div>
        </section>
    </c:if>
</div>
<%@ include file="../fragments/footer.jspf" %>
