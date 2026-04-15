<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/modal-shell.jspf" %>

<div class="erp-page-header">
    <p class="erp-breadcrumb">Admin Tools / Settings & Controls</p>
    <h1 class="erp-page-title">Settings & Controls</h1>
    <p class="erp-page-subtitle">Manage product settings, percentages, guarantor rules, and repayment controls without affecting workflow logic.</p>
</div>

<c:if test="${settingsSection eq 'board'}">
    <div class="erp-panel mb-4">
        <div class="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
                <p class="erp-widget-title">Board Review Requirement</p>
                <h2 class="mt-1 text-xl font-bold text-sacco-ink">Required Board Reviewers</h2>
            </div>
        </div>
        <form action="/admin/settings-controls/review-rules" method="post" class="erp-panel-body grid gap-4 md:grid-cols-[1.2fr_1fr_auto] md:items-end">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

            <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                Required Board Members Per Loan Review
                <input name="boardQuorum"
                       type="number"
                       min="1"
                       max="${activeBoardMemberCount}"
                       class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800"
                       value="${settings.boardQuorum}" />
                <span class="mt-2 block text-sm font-normal normal-case tracking-normal text-slate-500">
                    Set how many active board members must review each loan before the board stage can complete.
                </span>
            </label>

            <div class="rounded border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-700">
                Active Board Members: <strong>${activeBoardMemberCount}</strong>
            </div>

            <div class="app-modal-actions !justify-start md:justify-end">
                <button type="submit" class="app-btn btn-primary">Save</button>
            </div>
        </form>
    </div>
</c:if>

<c:if test="${settingsSection eq 'loan'}">
    <div class="grid gap-4 xl:grid-cols-2 2xl:grid-cols-3">
        <c:forEach items="${products}" var="product">
            <c:choose>
                <c:when test="${product.loanType eq 'LOAN_ADVANCE'}"><c:set var="loanTypeLabel" value="Loan Advance" /></c:when>
                <c:when test="${product.loanType eq 'EDUCATION_LOAN'}"><c:set var="loanTypeLabel" value="Education Loan" /></c:when>
                <c:when test="${product.loanType eq 'EMERGENCY_LOAN'}"><c:set var="loanTypeLabel" value="Emergency Loan" /></c:when>
                <c:when test="${product.loanType eq 'DEVELOPMENT_LOAN'}"><c:set var="loanTypeLabel" value="Development Loan" /></c:when>
                <c:otherwise><c:set var="loanTypeLabel" value="${fn:replace(product.loanType, '_', ' ')}" /></c:otherwise>
            </c:choose>

            <section class="erp-panel overflow-hidden">
                <div class="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
                    <div>
                        <p class="erp-widget-title">Loan Product</p>
                        <h2 class="mt-1 text-xl font-bold text-sacco-ink">${loanTypeLabel}</h2>
                        <p class="mt-1 text-sm text-slate-500">Configuration overview for this product.</p>
                    </div>
                    <div class="flex items-center gap-2 sm:pt-1">
                        <span class="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${product.active ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}">
                            ${product.active ? 'Active' : 'Inactive'}
                        </span>
                        <button type="button"
                                class="app-btn btn-primary"
                                data-product-modal-open="product-${product.id}">
                            Edit
                        </button>
                    </div>
                </div>

                <div class="divide-y divide-slate-200">
                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Guarantors</p>
                            <p class="text-sm text-slate-500">Number of guarantors required before review moves forward.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">${product.guarantorsRequired}</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Savings Percentage</p>
                            <p class="text-sm text-slate-500">Maximum percentage of member savings available for this product.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900"><fmt:formatNumber value="${product.maxLoanSavingsRatio * 100}" minFractionDigits="2" maxFractionDigits="2" />%</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Insurance</p>
                            <p class="text-sm text-slate-500">Percentage applied as insurance on the loan amount.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900"><fmt:formatNumber value="${product.insuranceRate * 100}" minFractionDigits="2" maxFractionDigits="2" />%</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Interest</p>
                            <p class="text-sm text-slate-500">Repayment interest percentage applied to this product.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900"><fmt:formatNumber value="${product.interestRate * 100}" minFractionDigits="2" maxFractionDigits="2" />%</p>
                    </div>

                    <div class="flex flex-col gap-1 px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4">
                        <div>
                            <p class="text-sm font-semibold text-sacco-ink">Max Repayment Period</p>
                            <p class="text-sm text-slate-500">Maximum allowed duration for repayment on this product.</p>
                        </div>
                        <p class="text-base font-semibold text-slate-900">${product.maxRepaymentMonths} month(s)</p>
                    </div>
                </div>
            </section>
        </c:forEach>
    </div>
</c:if>

<c:forEach items="${products}" var="product">
    <c:choose>
        <c:when test="${product.loanType eq 'LOAN_ADVANCE'}"><c:set var="loanTypeLabel" value="Loan Advance" /></c:when>
        <c:when test="${product.loanType eq 'EDUCATION_LOAN'}"><c:set var="loanTypeLabel" value="Education Loan" /></c:when>
        <c:when test="${product.loanType eq 'EMERGENCY_LOAN'}"><c:set var="loanTypeLabel" value="Emergency Loan" /></c:when>
        <c:when test="${product.loanType eq 'DEVELOPMENT_LOAN'}"><c:set var="loanTypeLabel" value="Development Loan" /></c:when>
        <c:otherwise><c:set var="loanTypeLabel" value="${fn:replace(product.loanType, '_', ' ')}" /></c:otherwise>
    </c:choose>

    <div class="app-modal-overlay hidden"
         data-product-modal="product-${product.id}">
        <div class="app-modal-panel">
            <div class="app-modal-scroll">
            <div class="app-modal-header">
                <div>
                    <p class="erp-widget-title">Edit Product</p>
                    <h2 class="mt-1 text-xl font-bold text-sacco-ink">${loanTypeLabel}</h2>
                </div>
                <button type="button" class="app-modal-close" data-product-modal-close="product-${product.id}" aria-label="Close modal">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                        <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd"/>
                    </svg>
                </button>
            </div>

            <form action="/admin/settings-controls/${product.id}" method="post" class="app-modal-body grid gap-4 md:grid-cols-2">
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />

                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Guarantors
                    <input name="guarantorsRequired" type="number" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.guarantorsRequired}" />
                </label>

                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Savings Percentage
                    <input name="maxLoanSavingsPercent" type="number" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maxLoanSavingsRatio * 100}" />
                </label>

                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Insurance %
                    <input name="insurancePercent" type="number" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.insuranceRate * 100}" />
                </label>

                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Interest %
                    <input name="interestPercent" type="number" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.interestRate * 100}" />
                </label>

                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                    Max Repayment Period
                    <input name="maxRepaymentMonths" type="number" min="1" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maxRepaymentMonths}" />
                </label>

                <label class="flex items-center gap-2 self-end rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                    <input name="active" type="checkbox" value="true" ${product.active ? 'checked' : ''} />
                    <span>Active</span>
                </label>

                <div class="app-modal-actions md:col-span-2">
                    <button type="button"
                            class="app-btn btn-neutral"
                            data-product-modal-close="product-${product.id}">
                        Cancel
                    </button>
                    <button type="submit" class="app-btn btn-primary">Save</button>
                </div>
            </form>
            </div>
        </div>
    </div>
</c:forEach>

<script>
    (() => {
        const body = document.body;

        document.querySelectorAll('[data-product-modal]').forEach((modal) => {
            modal.style.position = 'fixed';
            modal.style.inset = '0';
            modal.style.zIndex = '90';
            document.body.appendChild(modal);
        });

        function closeAllProductModals() {
            document.querySelectorAll('[data-product-modal]').forEach((modal) => {
                modal.classList.add('hidden');
                modal.classList.remove('is-open');
            });
            body.classList.remove('overflow-hidden');
        }

        document.querySelectorAll('[data-product-modal-open]').forEach((button) => {
            button.addEventListener('click', () => {
                const key = button.getAttribute('data-product-modal-open');
                closeAllProductModals();
                const modal = document.querySelector('[data-product-modal="' + key + '"]');
                if (modal) {
                    modal.classList.remove('hidden');
                    modal.classList.add('is-open');
                    body.classList.add('overflow-hidden');
                }
            });
        });

        document.querySelectorAll('[data-product-modal-close]').forEach((button) => {
            button.addEventListener('click', closeAllProductModals);
        });

        document.querySelectorAll('[data-product-modal]').forEach((modal) => {
            modal.addEventListener('click', (event) => {
                if (event.target === modal) {
                    closeAllProductModals();
                }
            });
        });
    })();
</script>

<%@ include file="../fragments/footer.jspf" %>
