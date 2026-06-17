<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="support.archive.breadcrumb" text="Member Workspace / Support / Sent Archive" /></p>
    <h1 class="erp-page-title"><spring:message code="support.archive.title" text="Sent Support Archive" /></h1>
    <p class="erp-page-subtitle"><spring:message code="support.archive.subtitle" text="Track messages sent to your station admin." /></p>
</div>

<section class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr><th><spring:message code="common.subject" text="Subject" /></th><th><spring:message code="common.message" text="Message" /></th><th><spring:message code="common.status" text="Status" /></th><th><spring:message code="support.archive.stationAdminRead" text="Station Admin Read" /></th><th><spring:message code="support.archive.sent" text="Sent" /></th></tr>
        </thead>
        <tbody>
        <c:forEach items="${supportArchive}" var="item">
            <tr>
                <td class="px-3 py-2 font-semibold text-slate-900">${item.subject}</td>
                <td class="px-3 py-2 text-slate-700">${item.message}</td>
                <td class="px-3 py-2">${item.status}</td>
                <td class="px-3 py-2">
                    <span class="${item.readBySuperAdmin ? 'rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700' : 'rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600'}">
                        <c:choose>
                            <c:when test="${item.readBySuperAdmin}"><spring:message code="support.archive.readByStationAdmin" text="Read by Station Admin" /></c:when>
                            <c:otherwise><spring:message code="support.archive.notReadYet" text="Not read yet" /></c:otherwise>
                        </c:choose>
                    </span>
                </td>
                <td class="px-3 py-2 whitespace-nowrap">${item.createdAt}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty supportArchive}">
            <tr><td colspan="5" class="px-3 py-3 text-slate-500"><spring:message code="support.archive.empty" text="No support messages sent yet." /></td></tr>
        </c:if>
        </tbody>
    </table>
</section>

<%@ include file="../fragments/footer.jspf" %>
