<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ include file="../../fragments/header.jspf" %><%@ include file="../../fragments/sidebar.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-ledger.css' />" />
<div class="accounting-workspace">
<div class="erp-page-header"><h1 class="erp-page-title"><spring:message code="payroll.nav.${kind}" /></h1></div>
<section class="erp-table-wrap" data-resource-table="payroll-register" data-table-selection="single" data-payroll-register data-base="<c:url value='${base}' />">
    <div class="app-table-titlebar"><h2><spring:message code="payroll.nav.${kind}" /></h2><div class="accounting-actions">
        <button type="button" class="app-btn btn-neutral" data-payroll-open disabled><spring:message code="common.view" /></button>
        <c:if test="${kind=='batches'}"><sec:authorize access="@access.has(principal,'PAYROLL_DEDUCTIONS_EXPORT')"><button type="button" class="app-btn btn-neutral" data-payroll-pdf data-table-command disabled><spring:message code="payroll.pdf" /></button></sec:authorize></c:if>
        <sec:authorize access="@access.has(principal,'PAYROLL_DEDUCTIONS_CREATE')"><a class="app-btn btn-launch" href="<c:url value='${base}/new' />"><spring:message code="payroll.create.${kind}" /></a></sec:authorize>
    </div></div>
    <form method="get" action="<c:url value='${base}' />" class="erp-table-toolbar">
        <label class="erp-table-toolbar__control"><spring:message code="common.search" /><input class="aws-control" name="search" maxlength="100" value="<c:out value='${search}' />" /></label>
        <c:if test="${kind=='batches'}">
            <label class="erp-table-toolbar__control"><spring:message code="payroll.month" /><input class="aws-control" type="month" name="month" value="<c:out value='${month}' />" /></label>
            <label class="erp-table-toolbar__control"><spring:message code="payroll.state" /><select class="aws-control" name="state"><option value=""><spring:message code="payroll.all" /></option><c:forEach items="${['FROZEN','SENT','CANCELLED']}" var="s"><option value="${s}" ${state==s?'selected':''}><spring:message code="payroll.state.${s}" /></option></c:forEach></select></label>
        </c:if>
        <input type="hidden" name="sort" value="<c:out value='${sort}' />" /><input type="hidden" name="direction" value="<c:out value='${direction}' />" />
        <button class="app-btn btn-neutral"><spring:message code="coa.filter" /></button><a class="app-btn btn-neutral" href="<c:url value='${base}' />"><spring:message code="coa.clear" /></a>
    </form>
    <span class="sr-only" role="status" data-payroll-selection data-empty="<spring:message code='coa.selectHint' />" data-selected="<spring:message code='coa.selected' />"></span>
    <c:set var="columns" value="${kind=='employers'?['name','agreement']:kind=='instructions'?['employer','loan','client','employee','limit','from','to','state']:['reference','employer','month','date','total','state']}" />
    <div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><span class="sr-only"><spring:message code="coa.select" /></span></th>
        <c:forEach items="${columns}" var="column"><th data-table-sort-key="${column}" data-table-sort-asc="asc" data-table-sort-desc="desc"><spring:message code="payroll.column.${column}" /></th></c:forEach>
    </tr></thead><tbody>
    <c:forEach items="${records.rows}" var="row"><tr data-payroll-id="${row.id}"><td><input type="checkbox" name="payroll-selection" aria-label="<spring:message code='coa.select' /> <c:out value='${kind=="employers"?row.name:kind=="instructions"?row.loanNumber:row.reference}' />" /></td>
        <c:choose>
            <c:when test="${kind=='employers'}"><td><a class="resource-table-id-link" href="<c:url value='${base}/${row.id}' />"><c:out value="${row.name}" /></a></td><td><c:out value="${row.agreementReference}" /></td></c:when>
            <c:when test="${kind=='instructions'}">
                <td><c:out value="${row.employer}" /></td><td><a class="resource-table-id-link" href="<c:url value='${base}/${row.id}' />"><c:out value="${row.loanNumber}" /></a></td><td><c:out value="${row.client}" /></td><td><c:out value="${row.employeeNumber}" /></td><td><fmt:formatNumber value="${row.monthlyLimit}" minFractionDigits="2" maxFractionDigits="2" /></td><td><c:out value="${appTime:date(row.effectiveFrom)}" /></td><td><c:out value="${appTime:date(row.effectiveTo)}" /></td><td><spring:message code="payroll.instruction.${row.stopped?'stopped':'active'}" /></td>
            </c:when>
            <c:otherwise><td><a class="resource-table-id-link" href="<c:url value='${base}/${row.id}' />"><c:out value="${row.reference}" /></a></td><td><c:out value="${row.employer}" /></td><td><c:out value="${row.month.toString().substring(0,7)}" /></td><td><c:out value="${appTime:date(row.deductionDate)}" /></td><td><fmt:formatNumber value="${row.total}" minFractionDigits="2" maxFractionDigits="2" /></td><td><spring:message code="payroll.state.${row.state}" /></td></c:otherwise>
        </c:choose>
    </tr></c:forEach>
    <c:if test="${empty records.rows}"><tr><td colspan="${columns.size()+1}"><spring:message code="payroll.empty" /></td></tr></c:if>
    </tbody></table></div>
    <div data-table-pagination data-page="${records.page}" data-table-for="payroll-register">
        <c:forEach items="${['previous','next']}" var="move"><c:if test="${move=='previous'?records.page>0:records.hasNext}"><c:url value="${base}" var="pageUrl"><c:param name="search" value="${search}" /><c:param name="state" value="${state}" /><c:param name="month" value="${month}" /><c:param name="sort" value="${sort}" /><c:param name="direction" value="${direction}" /><c:param name="page" value="${records.page+(move=='previous'?-1:1)}" /></c:url><a data-page-direction="${move}" href="<c:out value='${pageUrl}' />"><spring:message code="common.${move}" /></a></c:if></c:forEach>
    </div>
</section></div>
<script src="<c:url value='/js/payroll-deductions.js' />" defer></script><%@ include file="../../fragments/footer.jspf" %>
