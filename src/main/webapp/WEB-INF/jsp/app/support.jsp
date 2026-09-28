<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="support.member.breadcrumb" text="Borrower Workspace / Support" /></p>
    <h1 class="erp-page-title"><spring:message code="support.member.title" text="Contact Branch Admin" /></h1>
</div>

<section class="erp-panel max-w-4xl overflow-hidden">
    <div class="erp-panel-header">
        <p class="erp-panel-title"><spring:message code="support.member.request" text="Support Request" /></p>
    </div>
    <form action="/app/support" method="post" class="erp-panel-body space-y-4">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <label class="block text-sm font-semibold text-slate-700">
            <spring:message code="common.subject" text="Subject" />
            <input name="subject" class="mt-1 w-full rounded border border-slate-300 px-3 py-3 text-sm text-slate-800 focus:border-sacco-blue focus:outline-none" placeholder="<spring:message code='support.member.subjectPlaceholder' text='Example: Unable to submit my loan application' />" required />
        </label>
        <label class="block text-sm font-semibold text-slate-700">
            <spring:message code="common.message" text="Message" />
            <textarea name="message" rows="7" class="mt-1 w-full rounded border border-slate-300 px-3 py-3 text-sm text-slate-800 focus:border-sacco-blue focus:outline-none" placeholder="<spring:message code='support.member.messagePlaceholder' text='Describe the problem clearly. Include the page and what happened.' />" required></textarea>
        </label>
        <div class="flex justify-end">
            <button type="submit" class="app-btn btn-primary"><spring:message code="support.member.send" text="Send To Branch Admin" /></button>
        </div>
    </form>
</section>

<%@ include file="../fragments/footer.jspf" %>
