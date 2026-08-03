<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb">Admin Tools / Settings & Controls / Loan Products</p>
    <h1 class="erp-page-title">Configure <c:out value="${product.displayName}" /></h1>
    <p class="erp-page-subtitle">Work through one section at a time. All changes are checked and saved together.</p>
</div>

<div class="mb-4 flex flex-wrap justify-between gap-3">
    <a href="/admin/settings-controls?section=loan" class="app-btn btn-neutral">Back to loan products</a>
    <form action="/admin/settings-controls/loan-products/${product.id}/delete"
          method="post"
          data-confirm-eyebrow="Confirm Deletion"
          data-confirm-title="Delete Loan Product"
          data-confirm-message="Delete this loan product from active settings? Historical loan applications and reports will remain preserved."
          data-confirm-proceed="Delete Product">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <button type="submit" class="app-btn btn-reject">Delete Product</button>
    </form>
</div>

<div class="product-config-workspace"
     data-product-config-workspace
     data-product-config-storage-key="loan-product-config-${product.id}">
    <aside class="product-config-workspace-nav" aria-label="Loan product configuration sections">
        <div class="product-config-workspace-summary">
            <p class="erp-widget-title">Selected loan product</p>
            <h2><c:out value="${product.displayName}" /></h2>
            <div class="product-config-workspace-meta">
                <span><c:out value="${product.displayCode}" /></span>
                <span class="product-config-status
                    ${product.status eq 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : ''}
                    ${product.status eq 'DRAFT' ? 'bg-amber-50 text-amber-700' : ''}
                    ${product.status eq 'SUSPENDED' ? 'bg-slate-100 text-slate-600' : ''}
                    ${product.status eq 'RETIRED' ? 'bg-rose-50 text-rose-700' : ''}">${product.status}</span>
            </div>
            <p>Use the sections below to keep related decisions together.</p>
        </div>
        <div class="product-config-progress" aria-live="polite">
            <span>Configuration progress</span>
            <strong data-product-config-progress>Section 1 of 5</strong>
        </div>
        <nav class="product-config-step-nav">
            <button type="button" data-product-config-nav="identity" aria-current="step">
                <span class="product-config-step-number">1</span>
                <span><strong>Product identity</strong><small>Name, order and status</small></span>
            </button>
            <button type="button" data-product-config-nav="eligibility">
                <span class="product-config-step-number">2</span>
                <span><strong>Eligibility</strong><small>Limits, tenure and savings</small></span>
            </button>
            <button type="button" data-product-config-nav="pricing">
                <span class="product-config-step-number">3</span>
                <span><strong>Repayment & charges</strong><small>Fees and interest method</small></span>
            </button>
            <button type="button" data-product-config-nav="workflow">
                <span class="product-config-step-number">4</span>
                <span><strong>Approval workflow</strong><small>Stages, reviewers and proof</small></span>
            </button>
            <button type="button" data-product-config-nav="preview">
                <span class="product-config-step-number">5</span>
                <span><strong>Review flow</strong><small>Confirm the resolved path</small></span>
            </button>
        </nav>
        <p class="product-config-save-note">Nothing is saved until you select <strong>Save product</strong>.</p>
    </aside>

    <div class="product-config-workspace-main">
        <form action="/admin/settings-controls/${product.id}"
                   method="post"
                   class="product-builder-form"
                  data-inline-validation-form="loan-settings"
                  data-product-workflow-builder="true"
                  data-product-edit-mode="true"
                  data-active-loan-officers="${activeLoanOfficerCount}"
                  data-active-chairpersons="${activeChairpersonCount}"
                  data-active-board-members="${activeBoardMemberCount}"
                  data-active-credit-committee-members="${activeCreditCommitteeMemberCount}"
                  data-active-accountants="${activeAccountantCount}"
                  data-active-disbursement-officers="${activeDisbursementOfficerCount}"
                  data-active-disbursement-claim-holders="${activeDisbursementClaimHolderCount}"
                  data-tenant-loan-officer-enabled="${settings.loanOfficerReviewRequired}"
                  data-tenant-board-enabled="${settings.boardReviewRequired}"
                  data-product-config-form>
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <input type="hidden" name="modalKey" value="product-${product.id}" />
                <input type="hidden" name="productCode" value="${fn:escapeXml(product.displayCode)}" data-product-code-field />
                <input type="hidden" name="workflowStartStage" value="${product.resolvedWorkflowStartStage}" data-workflow-start-stage-field />
                <c:set var="productModalKey" value="product-${product.id}" />

                <c:if test="${openProductModalKey eq productModalKey and not empty message}">
                    <div class="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">${message}</div>
                </c:if>
                <c:if test="${openProductModalKey eq productModalKey and not empty error}">
                    <div class="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm font-medium text-rose-700">${error}</div>
                </c:if>
                <c:if test="${openProductModalKey eq productModalKey and not empty loanSettingsFieldErrors}">
                    <div hidden data-modal-server-errors>
                        <c:forEach items="${loanSettingsFieldErrors}" var="fieldError">
                            <div data-modal-field-error="${fieldError.key}"><c:out value="${fieldError.value}" /></div>
                        </c:forEach>
                    </div>
                </c:if>

                <section class="product-builder-section product-config-step is-active" data-product-config-step="identity">
                    <button type="button"
                            class="product-builder-section-header product-config-step-toggle"
                            aria-expanded="true"
                            aria-controls="product-config-identity"
                            data-product-config-toggle>
                        <span class="product-config-step-copy">
                            <span class="erp-widget-title">01 · <spring:message code="admin.settings.loanProducts.basics" text="Basics" /></span>
                            <span class="product-config-step-title"><spring:message code="admin.settings.loanProducts.identity" text="Product Identity" /></span>
                            <span class="product-config-step-description"><spring:message code="admin.settings.loanProducts.identityHelp" text="Update the member-facing name, display order, and status for this product." /></span>
                        </span>
                        <span class="product-config-step-action" aria-hidden="true">Current</span>
                    </button>
                    <div id="product-config-identity" class="product-builder-section-body product-identity-grid" data-product-config-panel>
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.displayOrder" text="Display Order" />
                            <input name="displayOrder" type="number" min="1" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.resolvedDisplayOrder}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.name" text="Loan Product Name" />
                            <input name="productName" type="text" required maxlength="120" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${fn:escapeXml(product.displayName)}" data-product-name-field />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.status" text="Product Status" />
                            <select name="productStatus" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                <option value="ACTIVE" ${product.status ne 'SUSPENDED' ? 'selected' : ''}><spring:message code="status.active" text="Active" /></option>
                                <option value="SUSPENDED" ${product.status eq 'SUSPENDED' ? 'selected' : ''}><spring:message code="status.suspended" text="Suspended" /></option>
                            </select>
                        </label>

                        <label class="product-identity-description block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="common.description" text="Description" />
                            <textarea name="productDescription" rows="3" required maxlength="500" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder='<spring:message code="admin.settings.loanProducts.descriptionPlaceholder" text="Short description shown to members when choosing this product." />'>${fn:escapeXml(product.productDescription)}</textarea>
                        </label>
                    </div>
                </section>

                <section class="product-builder-section product-config-step" data-product-config-step="eligibility">
                    <button type="button"
                            class="product-builder-section-header product-config-step-toggle"
                            aria-expanded="false"
                            aria-controls="product-config-eligibility"
                            data-product-config-toggle>
                        <span class="product-config-step-copy">
                            <span class="erp-widget-title">02 · <spring:message code="admin.settings.loanProducts.eligibility" text="Eligibility" /></span>
                            <span class="product-config-step-title"><spring:message code="admin.settings.loanProducts.eligibility" text="Eligibility" /></span>
                            <span class="product-config-step-description"><spring:message code="admin.settings.loanProducts.eligibilityHelp" text="Update loan limits and savings coverage for this product." /></span>
                        </span>
                        <span class="product-config-step-action" aria-hidden="true">Open</span>
                    </button>
                    <div id="product-config-eligibility" class="product-builder-section-body product-builder-grid two-up" data-product-config-panel hidden>
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.minimumAmount" text="Minimum Loan Amount" />
                            <input name="minimumAmount" type="text" inputmode="decimal" required data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.minimumAmount}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.maximumAmount" text="Maximum Loan Amount" />
                            <input name="maximumAmount" type="text" inputmode="decimal" data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maximumAmount}" placeholder='<spring:message code="admin.settings.loanProducts.maximumAmountPlaceholder" text="Leave blank if there is no maximum amount" />' />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.minRepaymentMonths" text="Min Repayment Months" />
                            <input name="minRepaymentMonths" type="number" min="1" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.minimumRepaymentMonths}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.maxRepaymentMonths" text="Max Repayment Months" />
                            <input name="maxRepaymentMonths" type="number" min="1" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.maxRepaymentMonths}" />
                        </label>

                        <div class="savings-ratio-grid md:col-span-2" data-savings-ratio-group>
                            <input name="maxLoanSavingsPercent" type="hidden" value="${product.maxLoanSavingsRatio * 100}" data-savings-percent />
                            <div class="savings-ratio-field block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                <spring:message code="admin.settings.loanProducts.savingsMultiple" text="Loan Amount Limit By Savings" />
                                <div class="savings-multiplier-control mt-1">
                                    <input type="number" min="0" max="10" step="0.01" required class="rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" aria-label="Loan savings multiplier" data-savings-multiplier />
                                    <span class="savings-multiplier-label"><spring:message code="admin.settings.loanProducts.savingsMultiplierSuffix" text="x of savings" /></span>
                                </div>
                            </div>
                            <label class="settings-checkbox-card flex items-start gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                <input name="savingsLimitCheckRequired" type="checkbox" value="true" ${product.savingsLimitCheckRequired ? 'checked' : ''} data-savings-limit-check />
                                <span>
                                    <span class="block font-semibold text-slate-800"><spring:message code="admin.settings.loanProducts.savingsLimitCheck" text="Check savings against loan amount" /></span>
                                    <span class="block text-xs font-normal normal-case tracking-normal text-slate-500"><spring:message code="admin.settings.loanProducts.savingsLimitCheckHelp" text="When unchecked, only the product minimum and maximum amount range is enforced." /></span>
                                </span>
                            </label>
                        </div>
                        <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                            <input name="freshFinancialDataRequired" type="checkbox" value="true" ${product.freshFinancialDataRequired ? 'checked' : ''} data-workflow-loaded-financial />
                            <span><spring:message code="admin.settings.workflow.requireLoadedFinancialData" text="Require Loaded Financial Data (Savings & Shares) At Submission" /></span>
                        </label>
                        <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                            <input name="allowApplicationWithActiveLoan" type="checkbox" value="true" ${product.applicationWithActiveLoanAllowed ? 'checked' : ''} />
                            <span><spring:message code="admin.settings.workflow.allowApplicationWithActiveLoan" text="Allow Application With Active Loan" /></span>
                        </label>
                    </div>
                </section>

                <section class="product-builder-section product-config-step" data-product-config-step="pricing">
                    <button type="button"
                            class="product-builder-section-header product-config-step-toggle"
                            aria-expanded="false"
                            aria-controls="product-config-pricing"
                            data-product-config-toggle>
                        <span class="product-config-step-copy">
                            <span class="erp-widget-title">03 · <spring:message code="admin.settings.loanProducts.repayment" text="Repayment" /></span>
                            <span class="product-config-step-title"><spring:message code="admin.settings.loanProducts.repaymentCharges" text="Repayment & Charges" /></span>
                            <span class="product-config-step-description"><spring:message code="admin.settings.loanProducts.repaymentChargesHelp" text="Update pricing, interest treatment, and repayment duration for this product." /></span>
                        </span>
                        <span class="product-config-step-action" aria-hidden="true">Open</span>
                    </button>
                    <div id="product-config-pricing" class="product-builder-section-body product-builder-grid two-up" data-product-config-panel hidden>
                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.applicationFee" text="Application Fee (TZS)" />
                            <input name="applicationFee" type="text" inputmode="decimal" required data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.applicationFee ne null ? product.applicationFee : settings.resolvedApplicationFee}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.insurancePercent" text="Insurance %" />
                            <input name="insurancePercent" type="number" min="0" step="0.01" required data-trim-decimal-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${(product.insuranceRate ne null ? product.insuranceRate : 0) * 100}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.processingFeePercent" text="Loan Processing Fee %" />
                            <input name="processingFeePercent" type="number" min="0" step="0.01" required data-trim-decimal-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${(product.processingFeeRate ne null ? product.processingFeeRate : 0) * 100}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.annualInterestPercent" text="Annual Interest %" />
                            <input name="annualInterestPercent" type="number" min="0" step="0.01" required data-trim-decimal-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${(product.interestRate ne null ? product.interestRate : 0) * 100}" />
                        </label>

                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                            <spring:message code="admin.settings.loanProducts.amountReturnMethod" text="Interest Method" />
                            <select name="interestMethod" required class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800">
                                <option value="FLAT_RATE" ${product.interestMethod eq 'FLAT_RATE' ? 'selected' : ''}><spring:message code="admin.settings.loanProducts.flatRate" text="Flat Rate" /></option>
                                <option value="REDUCING_BALANCE" ${product.interestMethod eq 'REDUCING_BALANCE' ? 'selected' : ''}><spring:message code="admin.settings.loanProducts.reducingBalance" text="Reducing Balance" /></option>
                            </select>
                        </label>
                    </div>
                </section>

                <section class="product-builder-section product-config-step" data-product-config-step="workflow">
                    <button type="button"
                            class="product-builder-section-header product-config-step-toggle"
                            aria-expanded="false"
                            aria-controls="product-config-workflow"
                            data-product-config-toggle>
                        <span class="product-config-step-copy">
                            <span class="erp-widget-title">04 · <spring:message code="admin.settings.workflow.title" text="Approval Workflow" /></span>
                            <span class="product-config-step-title"><spring:message code="admin.settings.workflow.title" text="Approval Workflow" /></span>
                            <span class="product-config-step-description"><spring:message code="admin.settings.workflow.subtitle" text="Configure the approval path for this loan product." /></span>
                        </span>
                        <span class="product-config-step-action" aria-hidden="true">Open</span>
                    </button>
                    <div id="product-config-workflow" class="product-builder-section-body" data-product-config-panel hidden>
                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="admin.settings.workflow.reviewStages" text="Review Stages" /></p>
                            <div class="workflow-table">
                                <div class="workflow-table-head">
                                    <div><spring:message code="admin.settings.workflow.stage" text="Stage" /></div>
                                    <div><spring:message code="admin.settings.workflow.required" text="Required" /></div>
                                    <div><spring:message code="admin.settings.workflow.priority" text="Priority" /></div>
                                    <div><spring:message code="admin.settings.workflow.notes" text="Notes" /></div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.manager" text="Manager" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="managerReviewRequired" type="checkbox" value="true" ${product.managerReviewRequired != false ? 'checked' : ''} data-workflow-manager aria-label="Manager required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="managerPriority" class="workflow-priority-select" data-workflow-manager-priority>
                                            <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedManagerPriority eq priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'><spring:message code="admin.settings.workflow.managerLoanOfficerPriorityNote" text="Priority follows active review roles." /></div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.loanOfficer" text="Loan Officer" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="loanOfficerReviewRequired" type="checkbox" value="true" ${(product.loanOfficerReviewRequired == true || (product.loanOfficerReviewRequired == null && settings.loanOfficerReviewRequired)) ? 'checked' : ''} data-workflow-loan-officer aria-label="Loan Officer required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="loanOfficerPriority" class="workflow-priority-select" data-workflow-loan-officer-priority>
                                            <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedLoanOfficerPriority eq priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <c:choose>
                                            <c:when test="${product.loanOfficerReviewRequired == true || (product.loanOfficerReviewRequired == null && settings.loanOfficerReviewRequired)}">
                                                <spring:message code="admin.settings.workflow.loanOfficerPriorityNote" text="Priority follows active review roles." />
                                            </c:when>
                                            <c:otherwise>
                                                <spring:message code="admin.settings.workflow.enableLoanOfficerNote" text="Enable Loan Officer review in tenant approval flow settings to use this stage." />
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.chairperson" text="Chairperson" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="chairpersonReviewRequired" type="checkbox" value="true" ${product.chairpersonReviewRequired ? 'checked' : ''} data-workflow-chairperson aria-label="Chairperson required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="chairpersonPriority" class="workflow-priority-select" data-workflow-chairperson-priority>
                                            <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedChairpersonPriority == priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.boardMember" text="Board Member" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="boardReviewRequired" type="checkbox" value="true" ${product.boardReviewRequired ? 'checked' : ''} data-workflow-board aria-label="Board Member required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="boardPriority" class="workflow-priority-select" data-workflow-board-priority>
                                            <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedBoardPriority == priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.committee" text="Credit Committee" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="committeeReviewRequired" type="checkbox" value="true" ${product.committeeReviewRequired ? 'checked' : ''} data-workflow-committee aria-label="Committee required" /></label>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="committeePriority" class="workflow-priority-select" data-workflow-committee-priority>
                                            <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedCommitteePriority == priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.accountant" text="Accountant" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="accountantReviewRequired" type="checkbox" value="true" ${product.accountantReviewRequired != false ? 'checked' : ''} data-workflow-accountant aria-label="Accountant required" /></label>
                                        <input type="hidden" name="accountantReviewRequired" value="false" />
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select name="accountantPriority" class="workflow-priority-select" data-workflow-accountant-priority>
                                            <c:forEach begin="1" end="6" var="priorityOption">
                                                <option value="${priorityOption}" ${product.resolvedAccountantPriority == priorityOption ? 'selected' : ''}>${priorityOption}</option>
                                            </c:forEach>
                                        </select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.openPriorityNote" text="Priority follows active review roles." />
                                    </div>
                                </div>
                                <div class="workflow-table-row">
                                    <div class="workflow-table-cell workflow-table-stage" data-label='<spring:message code="admin.settings.workflow.stage" text="Stage" />'><spring:message code="role.disbursementOfficer" text="Disbursement/Teller Officer" /></div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.required" text="Required" />'>
                                        <label class="workflow-checkbox-lock"><input name="disbursementOfficerRequired" type="checkbox" value="true" ${product.disbursementOfficerRequired != false ? 'checked' : ''} data-workflow-disbursement-officer aria-label="Disbursement/Teller Officer required" /></label>
                                        <input type="hidden" name="disbursementOfficerRequired" value="false" />
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.priority" text="Priority" />'>
                                        <select class="workflow-priority-select" disabled><option selected>7</option></select>
                                    </div>
                                    <div class="workflow-table-cell" data-label='<spring:message code="admin.settings.workflow.notes" text="Notes" />'>
                                        <spring:message code="admin.settings.workflow.disbursementOfficerNote" text="Role requirement for final manual release." />
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="role.boardMember" text="Board Member" /></p>
                            <div class="grid gap-3">
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    Board Members Assigned
                                    <input type="search" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="Search board members..." data-board-reviewer-search />
                                </label>
                                <c:set var="assignedBoardReviewerTokens" value="${productBoardReviewerIdTokens[product.id]}" />
                                <div class="grid max-h-56 gap-2 overflow-y-auto overscroll-contain rounded border border-slate-200 bg-slate-50 p-3 sm:max-h-64" data-board-reviewer-list>
                                    <c:forEach items="${boardReviewerOptions}" var="reviewer">
                                        <c:set var="reviewerToken" value="|${reviewer.id}|" />
                                        <label class="settings-checkbox-card flex items-start gap-2 rounded border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700" data-board-reviewer-option data-board-reviewer-text="${fn:toLowerCase(reviewer.fullName)} ${fn:toLowerCase(reviewer.memberNo)}">
                                            <input name="boardReviewerIds" type="checkbox" value="${reviewer.id}" ${fn:contains(assignedBoardReviewerTokens, reviewerToken) ? 'checked' : ''} />
                                            <span>
                                                <span class="block font-semibold text-slate-900">${reviewer.fullName}</span>
                                                <span class="block text-xs text-slate-500">${reviewer.memberNo}</span>
                                            </span>
                                        </label>
                                    </c:forEach>
                                    <c:if test="${empty boardReviewerOptions}">
                                        <div class="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">No active board members are available.</div>
                                    </c:if>
                                </div>
                                <p class="text-xs text-slate-500">Every selected board member will be assigned and notified for this product.</p>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="role.committee" text="Credit Committee" /></p>
                            <div class="grid gap-3">
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    Credit Committee Assigned
                                    <input type="search" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="Search credit committee..." data-credit-committee-reviewer-search />
                                </label>
                                <c:set var="assignedCreditCommitteeReviewerTokens" value="${productCreditCommitteeReviewerIdTokens[product.id]}" />
                                <div class="grid max-h-56 gap-2 overflow-y-auto overscroll-contain rounded border border-slate-200 bg-slate-50 p-3 sm:max-h-64" data-credit-committee-reviewer-list>
                                    <c:forEach items="${creditCommitteeReviewerOptions}" var="reviewer">
                                        <c:set var="reviewerToken" value="|${reviewer.id}|" />
                                        <label class="settings-checkbox-card flex items-start gap-2 rounded border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700" data-credit-committee-reviewer-option data-credit-committee-reviewer-text="${fn:toLowerCase(reviewer.fullName)} ${fn:toLowerCase(reviewer.memberNo)}">
                                            <input name="creditCommitteeReviewerIds" type="checkbox" value="${reviewer.id}" ${fn:contains(assignedCreditCommitteeReviewerTokens, reviewerToken) ? 'checked' : ''} />
                                            <span>
                                                <span class="block font-semibold text-slate-900">${reviewer.fullName}</span>
                                                <span class="block text-xs text-slate-500">${reviewer.memberNo}</span>
                                            </span>
                                        </label>
                                    </c:forEach>
                                    <c:if test="${empty creditCommitteeReviewerOptions}">
                                        <div class="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">No active credit committee members are available.</div>
                                    </c:if>
                                </div>
                                <p class="text-xs text-slate-500">Every selected credit committee member will be assigned and notified for this product.</p>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title">Applicant</p>
                            <div class="workflow-support-grid">
                                <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    <input name="applicantAttachmentRequired" type="checkbox" value="true" ${product.applicantAttachmentRequired ? 'checked' : ''} data-required-attachments-toggle />
                                    <span><spring:message code="admin.settings.requireApplicantAttachment" text="Require applicant attachment while applying" /></span>
                                </label>
                                <input type="hidden" name="applicantAttachmentRequired" value="false" />
                            </div>
                            <div class="mt-3 ${product.applicantAttachmentRequired ? '' : 'hidden '}rounded-md border border-slate-200 bg-white p-3" data-required-attachments-panel>
                                <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
                                    <div>
                                        <p class="text-xs font-semibold uppercase tracking-wide text-slate-500">Required Attachments</p>
                                        <p class="mt-1 text-xs text-slate-500">Create one record for each document the applicant must upload.</p>
                                    </div>
                                    <button type="button" class="app-btn btn-neutral" data-required-attachment-add>Add Name of Required Attachment</button>
                                </div>
                                <div class="hidden rounded-md border border-slate-200 bg-slate-50 p-3" data-required-attachment-form>
                                    <div class="grid gap-3 md:grid-cols-[minmax(0,1fr)_10rem_auto_auto] md:items-end">
                                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                            Attachment Name
                                            <input form="requiredAttachmentCreateForm" name="attachmentName" type="text" maxlength="120" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" placeholder="e.g. Salary slip" />
                                        </label>
                                        <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                            Max MB
                                            <input form="requiredAttachmentCreateForm" name="maxSizeMb" type="number" min="0.01" max="100" step="0.01" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="5.00" />
                                        </label>
                                        <button form="requiredAttachmentCreateForm" type="submit" class="app-btn btn-launch">Create</button>
                                        <button type="button" class="app-btn btn-neutral" data-required-attachment-cancel>Cancel</button>
                                    </div>
                                </div>
                                <div class="erp-table-wrap erp-table-scroll erp-table-scroll-sm mt-4" data-aws-table-region data-loading-label="Loading members...">
                                    <table class="erp-table min-w-[620px]">
                                        <thead>
                                        <tr>
                                            <th class="px-3 py-2 text-left">Name</th>
                                            <th class="px-3 py-2 text-left">Max MB</th>
                                            <th class="px-3 py-2 text-left">Action</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        <c:choose>
                                            <c:when test="${empty requiredAttachments}">
                                                <tr>
                                                    <td colspan="3" class="px-4 py-5 text-sm text-slate-500">No required attachments have been created yet.</td>
                                                </tr>
                                            </c:when>
                                            <c:otherwise>
                                                <c:forEach items="${requiredAttachments}" var="attachmentRequirement">
                                                    <tr>
                                                        <td class="px-3 py-2 font-semibold text-slate-800"><c:out value="${attachmentRequirement.attachmentName}" /></td>
                                                        <td class="px-3 py-2"><fmt:formatNumber value="${attachmentRequirement.maxSizeMb}" minFractionDigits="2" maxFractionDigits="2" /></td>
                                                        <td class="px-3 py-2">
                                                            <button form="deleteRequiredAttachment-${attachmentRequirement.id}" type="submit" class="app-btn btn-reject">Delete</button>
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                            </c:otherwise>
                                        </c:choose>
                                        </tbody>
                                    </table>
                                </div>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="role.disbursementOfficer" text="Disbursement/Teller Officer" /></p>
                            <div class="workflow-support-grid">
                                <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    <input name="disbursementProofRequired" type="checkbox" value="true" ${product.disbursementProofRequired != false ? 'checked' : ''} />
                                    <span><spring:message code="admin.settings.requireProofBeforeDisbursement" text="Require proof attachment before disbursement" /></span>
                                </label>
                                <input type="hidden" name="disbursementProofRequired" value="false" />
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="admin.settings.workflow.guarantorSettings" text="Guarantor Settings" /></p>
                            <div class="workflow-support-grid">
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    <spring:message code="admin.settings.workflow.guarantorsRequired" text="Guarantors Required" />
                                    <input name="guarantorsRequired" type="number" min="0" max="15" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.guarantorsRequired}" data-number-range-max="15" data-workflow-guarantors />
                                </label>
                                <label class="block text-xs font-semibold uppercase tracking-wide text-slate-500">
                                    Minimum Guarantor Savings
                                    <input name="guarantorMinimumSavings" type="text" inputmode="decimal" data-money-input="true" class="mt-1 w-full rounded border border-slate-300 px-3 py-2 text-sm text-slate-800" value="${product.resolvedGuarantorMinimumSavings}" />
                                </label>
                                <label class="settings-checkbox-card flex items-center gap-2 rounded border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                                    <input name="guarantorMinSavingsCheckRequired" type="checkbox" value="true" ${product.guarantorMinSavingsCheckRequired ? 'checked' : ''} />
                                    <span><spring:message code="admin.settings.checkGuarantorSavings" text="Check guarantor minimum savings before selection" /></span>
                                </label>
                            </div>
                        </div>

                        <div class="workflow-subsection">
                            <p class="workflow-subsection-title"><spring:message code="admin.settings.workflow.validation" text="Validation" /></p>
                            <div class="workflow-warning-stack mt-4" data-workflow-warnings></div>
                        </div>
                    </div>
                </section>

                <section class="product-builder-section product-config-step" data-product-config-step="preview">
                    <button type="button"
                            class="product-builder-section-header product-config-step-toggle"
                            aria-expanded="false"
                            aria-controls="product-config-preview"
                            data-product-config-toggle>
                        <span class="product-config-step-copy">
                            <span class="erp-widget-title">05 · <spring:message code="admin.settings.workflow.preview" text="Preview" /></span>
                            <span class="product-config-step-title"><spring:message code="admin.settings.workflow.resolvedFlow" text="Resolved Flow" /></span>
                            <span class="product-config-step-description"><spring:message code="admin.settings.workflow.resolvedFlowHelp" text="The runtime path below reflects what the system would execute with current tenant settings." /></span>
                        </span>
                        <span class="product-config-step-action" aria-hidden="true">Open</span>
                    </button>
                    <div id="product-config-preview" class="product-builder-section-body" data-product-config-panel hidden>
                        <div class="workflow-preview-shell">
                            <p class="text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="admin.settings.workflow.title" text="Approval Workflow" /></p>
                            <div class="mt-3 workflow-preview-runtime" data-workflow-preview-runtime></div>
                        </div>
                    </div>
                </section>

                <div class="product-config-actionbar">
                    <div class="product-config-section-actions" aria-label="Configuration section navigation">
                        <button type="button" class="app-btn btn-neutral" data-product-config-previous disabled>Previous section</button>
                        <button type="button" class="app-btn btn-neutral" data-product-config-next>Next section</button>
                    </div>
                    <div class="product-config-save-actions">
                        <a href="/admin/settings-controls?section=loan" class="app-btn btn-neutral">Cancel</a>
                        <button type="submit" class="app-btn btn-primary">Save product</button>
                    </div>
                </div>
        </form>
    </div>
</div>

<form id="requiredAttachmentCreateForm" action="/admin/settings-controls/loan-products/${product.id}/required-attachments" method="post" class="hidden">
    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
</form>
<c:forEach items="${requiredAttachments}" var="attachmentRequirement">
    <form id="deleteRequiredAttachment-${attachmentRequirement.id}" action="/admin/settings-controls/loan-products/${product.id}/required-attachments/${attachmentRequirement.id}/delete" method="post" class="hidden">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
    </form>
</c:forEach>

<spring:message code="admin.settings.workflow.warning.noReviewStage" text="At least one review or approval stage must be enabled before disbursement." var="warningNoReviewStage" />
<spring:message code="admin.settings.workflow.warning.noLoanOfficers" text="Loan Officer review is enabled for this product, but there are no active Loan Officers assigned yet." var="warningNoLoanOfficers" />
<spring:message code="admin.settings.workflow.warning.managerLoanOfficerSamePriority" text="Manager and Loan Officer cannot share the same priority." var="warningManagerLoanOfficerSamePriority" />
<spring:message code="admin.settings.workflow.warning.duplicatePriorities" text="Each enabled review stage must use a different priority." var="warningDuplicatePriorities" />
<spring:message code="admin.settings.workflow.warning.noBoardReviewers" text="Board Member review is configured, but there are no active board members available for assignment." var="warningNoBoardReviewers" />
<spring:message code="admin.settings.workflow.warning.noChairpersons" text="Chairperson review is configured, but there are no active chairpersons available for assignment." var="warningNoChairpersons" />
<spring:message code="admin.settings.workflow.warning.noCommitteeReviewers" text="Committee review is configured, but there are no active board members available for assignment." var="warningNoCommitteeReviewers" />
<spring:message code="admin.settings.workflow.warning.committeeAccountantSamePriority" text="Committee and Accountant cannot share the same priority slot." var="warningCommitteeAccountantSamePriority" />
<spring:message code="admin.settings.workflow.warning.noAccountants" text="Accountant review is enabled for this product, but there are no active accountants available." var="warningNoAccountants" />
<spring:message code="admin.settings.workflow.warning.noDisbursementOfficers" text="Disbursement/Teller Officer is required, but there are no active Disbursement/Teller Officers assigned yet." var="warningNoDisbursementOfficers" />
<spring:message code="admin.settings.workflow.warning.noDisbursementClaimHolders" text="Disbursement/Teller Officer is optional, but no active staff user has both disbursement claims." var="warningNoDisbursementClaimHolders" />
<spring:message code="admin.settings.workflow.warning.loadedFinancialRequired" text="Members must load current financial data before this product can move into review." var="warningLoadedFinancialRequired" />
<spring:message code="admin.settings.workflow.warning.guarantorPrefix" text="This product waits for" var="warningGuarantorPrefix" />
<spring:message code="admin.settings.workflow.warning.guarantorApproval" text="guarantor approval" var="warningGuarantorApproval" />
<spring:message code="admin.settings.workflow.warning.guarantorApprovals" text="guarantor approvals" var="warningGuarantorApprovals" />
<spring:message code="admin.settings.workflow.warning.guarantorSuffix" text="before the review flow starts." var="warningGuarantorSuffix" />
<spring:message code="admin.settings.workflow.runtime.member" text="Member" var="runtimeMember" />
<spring:message code="admin.settings.workflow.runtime.loadedFinancialData" text="Loaded Financial Data (Savings & Shares)" var="runtimeLoadedFinancialData" />
<spring:message code="admin.settings.workflow.runtime.guarantor" text="Guarantor" var="runtimeGuarantor" />
<spring:message code="admin.settings.workflow.runtime.guarantors" text="Guarantors" var="runtimeGuarantors" />
<spring:message code="admin.settings.workflow.runtime.fixManagerLoanOfficerPriority" text="Fix Priority Slots" var="runtimeFixManagerLoanOfficerPriority" />
<spring:message code="admin.settings.workflow.runtime.disbursementRelease" text="Disbursement Release" var="runtimeDisbursementRelease" />
<spring:message code="role.manager" text="Manager" var="runtimeManager" />
<spring:message code="role.loanOfficer" text="Loan Officer" var="runtimeLoanOfficer" />
<spring:message code="role.chairperson" text="Chairperson" var="runtimeChairperson" />
<spring:message code="role.boardMember" text="Board Member" var="runtimeBoardMember" />
<spring:message code="role.committee" text="Credit Committee" var="runtimeCommittee" />
<spring:message code="role.accountant" text="Accountant" var="runtimeAccountant" />
<spring:message code="role.disbursementOfficer" text="Disbursement/Teller Officer" var="runtimeDisbursementOfficer" />
<spring:message code="admin.settings.validation.displayOrder" text="Display order must be 1 or higher." var="validationDisplayOrder" />
<spring:message code="admin.settings.validation.productNameRequired" text="Enter a loan product name." var="validationProductNameRequired" />
<spring:message code="admin.settings.validation.productNameLength" text="Loan product name must be 120 characters or fewer." var="validationProductNameLength" />
<spring:message code="admin.settings.validation.productDescriptionRequired" text="Enter a product description." var="validationProductDescriptionRequired" />
<spring:message code="admin.settings.validation.descriptionLength" text="Keep the description within 500 characters." var="validationDescriptionLength" />
<spring:message code="admin.settings.validation.minimumAmount" text="Minimum amount cannot be negative." var="validationMinimumAmount" />
<spring:message code="admin.settings.validation.maximumAmountPositive" text="Maximum amount must be greater than zero." var="validationMaximumAmountPositive" />
<spring:message code="admin.settings.validation.maximumBelowMinimum" text="Maximum amount cannot be lower than the minimum amount." var="validationMaximumBelowMinimum" />
<spring:message code="admin.settings.validation.savingsPercent" text="Loan savings multiple must be between 0 and 10x." var="validationSavingsPercent" />
<spring:message code="admin.settings.validation.insurancePercent" text="Insurance percentage cannot be negative." var="validationInsurancePercent" />
<spring:message code="admin.settings.validation.annualInterestPercent" text="Annual interest percentage cannot be negative." var="validationAnnualInterestPercent" />
<spring:message code="admin.settings.validation.minRepaymentMonths" text="Minimum repayment period must be at least 1 month." var="validationMinRepaymentMonths" />
<spring:message code="admin.settings.validation.maxRepaymentMonths" text="Maximum repayment period must be at least 1 month." var="validationMaxRepaymentMonths" />
<spring:message code="admin.settings.validation.maxRepaymentBelowMin" text="Maximum repayment period cannot be lower than the minimum repayment period." var="validationMaxRepaymentBelowMin" />
<spring:message code="admin.settings.validation.guarantorsRequired" text="Guarantors required cannot be negative." var="validationGuarantorsRequired" />
<spring:message code="admin.settings.validation.countRange" text="Enter a number from 0 to 15." var="validationCountRange" />
<spring:message code="admin.settings.validation.committeeCountRange" text="Enter a number from 1 to 15." var="validationCommitteeCountRange" />
<spring:message code="admin.settings.validation.noReviewStage" text="Enable at least one review or approval stage before disbursement." var="validationNoReviewStage" />
<spring:message code="admin.settings.validation.managerPriority" text="Manager priority must be between 1 and 6." var="validationManagerPriority" />
<spring:message code="admin.settings.validation.loanOfficerPriority" text="Loan Officer priority must be between 1 and 6." var="validationLoanOfficerPriority" />
<spring:message code="admin.settings.validation.openPriority" text="Choose an available priority for active review roles." var="validationOpenPriority" />
<spring:message code="admin.settings.validation.managerLoanOfficerDifferentPriority" text="Choose different priorities for Manager and Loan Officer." var="validationManagerLoanOfficerDifferentPriority" />
<spring:message code="admin.settings.validation.assignLoanOfficer" text="Assign at least one active Loan Officer before using this stage." var="validationAssignLoanOfficer" />
<spring:message code="admin.settings.validation.assignChairperson" text="Assign at least one active Chairperson before using this stage." var="validationAssignChairperson" />
<spring:message code="admin.settings.validation.assignBoardReviewer" text="Assign at least one active board member before using this stage." var="validationAssignBoardReviewer" />
<spring:message code="admin.settings.validation.assignCommitteeReviewer" text="Assign at least one active credit committee member before using this stage." var="validationAssignCommitteeReviewer" />
<spring:message code="admin.settings.validation.committeeAccountantDifferentPriority" text="Committee and Accountant cannot share the same priority slot." var="validationCommitteeAccountantDifferentPriority" />
<spring:message code="admin.settings.validation.assignAccountant" text="Assign at least one active Accountant before using this stage." var="validationAssignAccountant" />
<spring:message code="admin.settings.validation.assignDisbursementOfficer" text="Assign at least one active Disbursement/Teller Officer before requiring this role." var="validationAssignDisbursementOfficer" />
<spring:message code="admin.settings.validation.assignDisbursementClaims" text="Grant both disbursement claims to at least one active staff user before removing this role requirement." var="validationAssignDisbursementClaims" />
<spring:message code="admin.settings.validation.applicationFee" text="Application fee cannot be negative." var="validationApplicationFee" />
<spring:message code="admin.settings.validation.processingFeePercent" text="Loan processing fee percentage cannot be negative." var="validationProcessingFeePercent" />
<%@ include file="../fragments/loan-product-workflow-script.jspf" %>



<%@ include file="../fragments/confirm-modal.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
