<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">${notificationBreadcrumb}</p>
    <h1 class="erp-page-title"><spring:message code="notifications.title" text="Notifications" /></h1>
    <p class="erp-page-subtitle">${notificationSubtitle}</p>
</div>
<div class="mb-3 flex items-center justify-end">
    <form action="${reviewBasePath}/notifications/mark-all-read" method="post" class="m-0">
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
            <tr id="notification-${n.id}" data-notification-href="${reviewBasePath}/notifications/${n.id}/open" class="cursor-pointer ${highlightNotificationId eq n.id ? 'bg-amber-50' : (n.unread ? 'bg-sacco-green/5' : 'bg-white')}">
                <td class="px-3 py-3 align-top font-semibold text-slate-800">
                    <a href="${reviewBasePath}/notifications/${n.id}/open" class="flex items-start gap-3 hover:text-sacco-green">
                        <c:if test="${n.unread}">
                            <span class="inline-flex w-16 shrink-0 justify-center rounded-full bg-red-500 px-2 py-0.5 text-[11px] font-bold uppercase text-white"><spring:message code="notifications.unread" text="Unread" /></span>
                        </c:if>
                        <c:if test="${not n.unread}">
                            <span class="w-16 shrink-0" aria-hidden="true"></span>
                        </c:if>
                        <span class="min-w-0">${n.subject}</span>
                    </a>
                </td>
                <td class="px-3 py-3 align-top">
                    <div class="max-w-[38rem] leading-relaxed text-slate-800">${n.message}</div>
                    <c:if test="${not empty n.detailItems}">
                        <ul class="mt-2 space-y-1 text-sm text-slate-500">
                            <c:forEach items="${n.detailItems}" var="detail">
                                <li>${detail}</li>
                            </c:forEach>
                        </ul>
                    </c:if>
                </td>
                <td class="px-3 py-3 align-top">${n.source}</td>
                <td class="px-3 py-3 align-top">${n.typeLabel}</td>
                <td class="min-w-[11rem] whitespace-nowrap px-3 py-3 align-top">${n.createdAtLabel}</td>
            </tr>
        </c:forEach>
        <c:if test="${empty notifications}">
            <tr><td colspan="5" class="px-3 py-3 text-slate-500"><spring:message code="notifications.empty" text="No notifications yet." /></td></tr>
        </c:if>
        </tbody>
    </table>
</div>

<script>
    (() => {
        document.querySelectorAll("[data-notification-href]").forEach((row) => {
            row.addEventListener("click", (event) => {
                if (event.target.closest("a, button, input, select, textarea")) {
                    return;
                }
                window.location.href = row.dataset.notificationHref;
            });
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
