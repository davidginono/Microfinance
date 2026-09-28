<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="admin.support.breadcrumb" text="Admin Tools / Support" /></p>
    <h1 class="erp-page-title"><spring:message code="admin.support.title" text="Contact Platform Admin" /></h1>
</div>

<section class="erp-panel max-w-4xl overflow-hidden">
    <div class="erp-panel-header">
        <p class="erp-panel-title"><spring:message code="admin.support.request" text="Support Request" /></p>
    </div>
    <form action="/admin/support" method="post" class="erp-panel-body space-y-4">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <label class="block text-sm font-semibold text-slate-700">
            <spring:message code="support.subject" text="Subject" />
            <input name="subject" class="mt-1 w-full rounded border border-slate-300 px-3 py-3 text-sm text-slate-800 focus:border-sacco-blue focus:outline-none" placeholder='<spring:message code="admin.support.subjectPlaceholder" text="Example: Branch users cannot view loan queue" />' required />
        </label>
        <label class="block text-sm font-semibold text-slate-700">
            <spring:message code="support.message" text="Message" />
            <textarea name="message" rows="7" class="mt-1 w-full rounded border border-slate-300 px-3 py-3 text-sm text-slate-800 focus:border-sacco-blue focus:outline-none" placeholder='<spring:message code="admin.support.messagePlaceholder" text="Describe the issue, Branch, page, and expected outcome." />' required></textarea>
        </label>
        <div class="flex justify-end">
            <button type="submit" class="app-btn btn-primary">Send To Platform Admin</button>
        </div>
    </form>
</section>

<%@ include file="../fragments/footer.jspf" %>
