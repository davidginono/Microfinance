<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb"><spring:message code="manager.notifications.breadcrumb" text="Manager Panel / Notifications" /></p>
    <h1 class="erp-page-title"><spring:message code="notifications.title" text="Notifications" /></h1>
    <p class="erp-page-subtitle"><spring:message code="manager.notifications.subtitle" text="Workflow updates and alerts for the manager queue in one place." /></p>
</div>
<div class="mb-3 flex items-center justify-end">
    <form action="/manager/notifications/mark-all-read" method="post" class="m-0">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <button type="submit" class="app-btn btn-primary"><spring:message code="notifications.markAllReadLong" text="Mark all as read" /></button>
    </form>
</div>
<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr>
            <th class="px-3 py-2 text-left"><spring:message code="common.subject" text="Subject" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="common.message" text="Message" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="notifications.source" text="Source" /></th>
            <th class="px-3 py-2 text-left"><spring:message code="notifications.type" text="Type" /></th>
            <th class="min-w-[11rem] whitespace-nowrap px-3 py-2 text-left"><spring:message code="loan.date" text="Date" /></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${notifications}" var="n">
            <tr id="notification-${n.id}" class="${highlightNotificationId eq n.id ? 'bg-amber-50' : (n.unread ? 'bg-sacco-blue/5' : 'bg-white')}">
                <td class="px-3 py-3 align-top font-semibold text-slate-800">
                    <a href="/manager/notifications/${n.id}/open" class="block hover:text-sacco-blue">
                        ${n.subject}
                        <c:if test="${n.unread}">
                            <span class="ml-2 rounded-full bg-red-500 px-2 py-0.5 text-[11px] font-bold uppercase text-white"><spring:message code="notifications.unread" text="Unread" /></span>
                        </c:if>
                    </a>
                </td>
                <td class="px-3 py-3 align-top">
                    <a href="/manager/notifications/${n.id}/open" class="block max-w-[38rem] leading-relaxed text-slate-800 hover:text-sacco-blue">${n.message}</a>
                    <c:if test="${not empty n.detailItems}">
                        <ul class="mt-2 space-y-1 text-sm text-slate-500">
                            <c:forEach items="${n.detailItems}" var="detail">
                                <li>${detail}</li>
                            </c:forEach>
                        </ul>
                    </c:if>
                </td>
                <td class="px-3 py-3 align-top">${n.source}</td>
                <td class="px-3 py-3 align-top">${n.type}</td>
                <td class="min-w-[11rem] whitespace-nowrap px-3 py-3 align-top">${n.createdAtLabel}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty notifications}">
            <tr><td colspan="5" class="px-3 py-3 text-slate-500"><spring:message code="notifications.empty" text="No notifications yet." /></td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
