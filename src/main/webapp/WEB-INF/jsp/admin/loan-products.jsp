<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Loan Product Controls</p>
    <h1 class="erp-page-title">Loan Product Controls</h1>
    <p class="erp-page-subtitle">Maintain legacy product control values where needed without changing the core workflow implementation.</p>
</div>
<div class="erp-table-wrap overflow-x-auto">
    <table class="erp-table">
        <thead>
        <tr><th>Loan Type</th><th>Current Guarantors</th><th>Current Ratio</th><th>Active</th><th>Update</th></tr>
        </thead>
        <tbody>
        <c:forEach items="${products}" var="product">
            <tr>
                <td class="px-3 py-2 font-semibold text-slate-900">${product.loanType}</td>
                <td class="px-3 py-2">${product.guarantorsRequired}</td>
                <td class="px-3 py-2">${product.maxLoanSavingsRatio}</td>
                <td class="px-3 py-2">${product.active}</td>
                <td class="px-3 py-2">
                    <form action="/admin/loan-products/${product.id}" method="post" class="flex flex-wrap items-center gap-2">
                        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                        <input name="guarantorsRequired" type="number" class="w-20 border border-slate-300 px-2 py-2" value="${product.guarantorsRequired}" />
                        <input name="maxLoanSavingsRatio" type="number" step="0.0001" class="w-28 border border-slate-300 px-2 py-2" value="${product.maxLoanSavingsRatio}" />
                        <label class="inline-flex items-center gap-2 text-sm text-slate-700"><input name="active" type="checkbox" value="true" ${product.active ? 'checked' : ''} /> Active</label>
                        <button type="submit" class="app-btn btn-primary">Save</button>
                    </form>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</div>

<%@ include file="../fragments/footer.jspf" %>
