<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Member Workspace / Support</p>
    <h1 class="erp-page-title">Contact System Admin</h1>
    <p class="erp-page-subtitle">Report technical issues, account problems, or workflow concerns directly to the admin team.</p>
</div>
<section class="erp-form-wrap max-w-4xl">
    <p class="text-sm text-slate-600">Use this form when something in the system is blocking your work or you need help on a specific application.</p>

    <form action="/app/support" method="post" class="mt-5 space-y-4">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <div>
            <label class="mb-1 block text-sm font-semibold text-slate-700">Subject</label>
            <input name="subject" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" placeholder="Example: Unable to submit my loan application" required />
        </div>
        <div>
            <label class="mb-1 block text-sm font-semibold text-slate-700">Message</label>
            <textarea name="message" rows="6" class="w-full border border-slate-300 px-3 py-3 focus:border-sacco-blue focus:outline-none" placeholder="Describe the problem clearly. Include the page and what happened." required></textarea>
        </div>
        <button type="submit" class="app-btn btn-primary">Send To Admin</button>
    </form>
</section>

<%@ include file="../fragments/footer.jspf" %>
