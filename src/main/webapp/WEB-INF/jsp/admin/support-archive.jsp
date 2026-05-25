<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="admin.support.archiveBreadcrumb" text="Admin Tools / Support / Sent Archive" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.support.archiveTitle" text="Sent Support Archive" /></h1>
    <p class="erp-page-subtitle"><spring:message code="admin.support.archiveSubtitle" text="Track messages sent to the platform admin." /></p>
</div>

<section class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr><th><spring:message code="support.subject" text="Subject" /></th><th><spring:message code="support.message" text="Message" /></th><th><spring:message code="common.status" text="Status" /></th><th><spring:message code="support.superAdminRead" text="Super Admin Read" /></th><th><spring:message code="support.sent" text="Sent" /></th></tr>
        </thead>
        <tbody>
        <c:forEach items="${supportArchive}" var="item">
            <tr>
                <td class="px-3 py-2 font-semibold text-slate-900">${item.subject}</td>
                <td class="px-3 py-2 text-slate-700">${item.message}</td>
                <td class="px-3 py-2">${item.status}</td>
                <td class="px-3 py-2">
                    <span class="${item.readBySuperAdmin ? 'rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700' : 'rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600'}">${item.readLabel}</span>
                </td>
                <td class="px-3 py-2 whitespace-nowrap">${item.createdAt}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty supportArchive}">
            <tr><td colspan="5" class="px-3 py-3 text-slate-500">No support messages sent yet.</td></tr>
        </c:if>
        </tbody>
    </table>
</section>

<%@ include file="../fragments/footer.jspf" %>
