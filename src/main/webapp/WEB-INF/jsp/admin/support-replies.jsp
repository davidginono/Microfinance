<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.support.repliesBreadcrumb" text="Admin Tools / Support / Replies" /></p>
    <h1 class="erp-page-title" id="admin-support-replies-title"><spring:message code="admin.support.repliesTitle" text="Support Replies" /></h1>
</div>

<section class="erp-panel">
    <div class="erp-panel-header flex flex-wrap items-center justify-between gap-3" data-resource-header-source>
        <h2 class="erp-panel-title" data-table-heading-for="admin-support-replies-1"><spring:message code="support.repliesFromSuperAdmin" text="Replies From Super Admin" /></h2>
        <form action="/admin/support/replies/mark-all-read" method="post" data-table-toolbar-for="admin-support-replies-1">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
            <button type="submit" class="app-btn btn-neutral">Mark All Read</button>
        </form>
    </div>
    <div class="erp-panel-body">
<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results..." data-resource-table="admin-support-replies-1">
            <div class="erp-table-scroll">
            <table class="erp-table">
                <thead>
                <tr><th data-table-order="subject"><spring:message code="support.subject" text="Subject" /></th><th data-table-order="message"><spring:message code="support.message" text="Message" /></th><th data-table-order="source"><spring:message code="support.source" text="Source" /></th><th data-table-order="date"><spring:message code="loan.date" text="Date" /></th></tr>
                </thead>
                <tbody>
                <c:forEach items="${replies}" var="item">
                    <tr id="notification-${item.id}" class="${highlightNotificationId eq item.id ? 'bg-amber-50' : (item.unread ? 'bg-sacco-blue/5' : 'bg-white')}">
                        <td class="px-3 py-2 font-semibold text-slate-900">
                            <a href="/admin/support/replies/${item.id}/open" class="hover:text-sacco-blue">
                                ${item.subject}
                                <c:if test="${item.unread}">
                                    <span class="ml-2 rounded-full bg-red-500 px-2 py-0.5 text-[11px] font-bold uppercase text-white">Unread</span>
                                </c:if>
                            </a>
                        </td>
                        <td class="px-3 py-2"><a href="/admin/support/replies/${item.id}/open" class="block hover:text-sacco-blue">${item.message}</a></td>
                        <td class="px-3 py-2">${item.source}</td>
                        <td class="px-3 py-2 whitespace-nowrap">${item.createdAtLabel}</td>
                    </tr>
                </c:forEach>
                <c:if test="${empty replies}">
                    <tr><td colspan="4" class="px-3 py-3 text-slate-500">No replies yet.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
        </div>
</section>

<section class="erp-panel">
    <div class="erp-panel-header" data-resource-header-source><h2 class="erp-panel-title" data-table-heading-for="admin-support-replies-2">Super Admin Broadcasts</h2></div>
    <div class="erp-panel-body">
<div class="erp-table-wrap" data-aws-table-region data-loading-label="Loading results..." data-resource-table="admin-support-replies-2">
            <div class="erp-table-scroll">
            <table class="erp-table">
                <thead>
                <tr><th data-table-order="subject">Subject</th><th data-table-order="message">Message</th><th data-table-order="source">Source</th><th data-table-order="date">Date</th></tr>
                </thead>
                <tbody>
                <c:forEach items="${broadcasts}" var="item">
                    <tr id="notification-${item.id}" class="${highlightNotificationId eq item.id ? 'bg-amber-50' : (item.unread ? 'bg-sacco-blue/5' : 'bg-white')}">
                        <td class="px-3 py-2 font-semibold text-slate-900">
                            <a href="/admin/support/replies/${item.id}/open" class="hover:text-sacco-blue">
                                ${item.subject}
                                <c:if test="${item.unread}">
                                    <span class="ml-2 rounded-full bg-red-500 px-2 py-0.5 text-[11px] font-bold uppercase text-white">Unread</span>
                                </c:if>
                            </a>
                        </td>
                        <td class="px-3 py-2"><a href="/admin/support/replies/${item.id}/open" class="block hover:text-sacco-blue">${item.message}</a></td>
                        <td class="px-3 py-2">${item.source}</td>
                        <td class="px-3 py-2 whitespace-nowrap">${item.createdAtLabel}</td>
                    </tr>
                </c:forEach>
                <c:if test="${empty broadcasts}">
                    <tr><td colspan="4" class="px-3 py-3 text-slate-500">No broadcasts yet.</td></tr>
                </c:if>
                </tbody>
            </table>
    </div>
        </div>
    </div>
</section>

<%@ include file="../fragments/footer.jspf" %>
