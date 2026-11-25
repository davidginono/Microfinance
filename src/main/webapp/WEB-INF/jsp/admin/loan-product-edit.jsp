<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>
<%@ include file="../fragments/alerts.jspf" %>
<%@ include file="../fragments/admin-workspace-support.jspf" %>


<div class="erp-page-header" data-aws-page-header>
    <div class="admin-page-heading">
    <p class="erp-breadcrumb"><spring:message code="admin.workspace.productBreadcrumb" /></p>
    <h1 class="erp-page-title" id="admin-loan-product-edit-title"><spring:message code="admin.workspace.configureProduct" /></h1>
    </div>

<div class="product-config-page-actions">
    <a href="<c:url value='/admin/settings-controls'><c:param name='section' value='loan' /></c:url>" class="app-btn btn-neutral"><spring:message code="admin.workspace.backProducts" /></a>
    <form action="/admin/settings-controls/loan-products/${product.id}/delete"
          method="post"
          data-confirm-eyebrow="Confirm Deletion"
          data-confirm-title="Delete Loan Product"
          data-confirm-message="Delete this loan product from active settings? Historical loan applications and reports will remain preserved."
          data-confirm-text="Delete product"
          data-confirm-proceed="Delete Product">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
        <button type="submit" class="app-btn btn-reject">Delete Product</button>
    </form>
</div>
</div>

<div class="product-config-workspace"
     data-product-config-workspace
     data-product-config-stage="Step 2 of 2 &middot; focused product"
     data-product-config-storage-key="loan-product-config-${product.id}">
    <section class="product-config-workspace-nav" aria-label="<spring:message code='admin.workspace.productSections' />">
        <div class="product-config-workspace-summary" >
            <p class="erp-widget-title">Selected product</p>
            <h2><spring:message code="resource.details.summary" /></h2><dl class="erp-detail-facts"><div><dt><spring:message code="admin.settings.loanProducts.name" /></dt><dd><c:out value="${product.displayName}" /></dd></div><div><dt><spring:message code="common.description" /></dt><dd><c:out value="${product.displayDescription}" /></dd></div></dl>
            <div class="product-config-workspace-meta">
                <span class="product-config-status
                    ${product.status eq 'ACTIVE' ? 'bg-emerald-50 text-emerald-700' : ''}
                    ${product.status eq 'DRAFT' ? 'bg-amber-50 text-amber-700' : ''}
                    ${product.status eq 'SUSPENDED' ? 'bg-slate-100 text-slate-600' : ''}
                    ${product.status eq 'RETIRED' ? 'bg-rose-50 text-rose-700' : ''}"><span data-resource-status="${product.status}">${product.status}</span></span>
            </div>
        </div>
        <%@ include file="../fragments/loan-product-config-tabs.jspf" %>
    </section>

    <div class="product-config-workspace-main">
        <form id="loanProductConfigForm" action="/admin/settings-controls/${product.id}"
                   method="post"
                   class="product-builder-form accounting-create-form"
                  data-inline-validation-form="loan-settings"
                  data-product-workflow-builder="true"
                  data-product-edit-mode="true"
                  data-workflow-notifications="focused"
                  data-tenant-loan-officer-enabled="${settings.loanOfficerReviewRequired}"
                  data-tenant-board-enabled="${settings.boardReviewRequired}"
                  aria-label="Configure ${fn:escapeXml(product.displayName)}"
                  data-product-config-form>
                <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                <input type="hidden" name="modalKey" value="product-${product.id}" />
                <input type="hidden" name="productCode" value="${fn:escapeXml(product.displayCode)}" data-product-code-field />

                <c:if test="${not empty loanProductFormDraft}">
                    <div hidden data-loan-product-form-draft>
                        <c:forEach items="${loanProductFormDraft}" var="draftField">
                            <c:forEach items="${draftField.value}" var="draftValue">
                                <span data-loan-product-form-value="${fn:escapeXml(draftField.key)}"><c:out value="${draftValue}" /></span>
                            </c:forEach>
                        </c:forEach>
                    </div>
                </c:if>
                <c:if test="${not empty message}">
                    <span hidden data-toast-message="${fn:escapeXml(message)}" data-toast-type="success"></span>
                </c:if>
                <c:if test="${not empty error}">
                    <span hidden data-toast-message="${fn:escapeXml(error)}" data-toast-type="error"></span>
                </c:if>
                <c:if test="${not empty loanSettingsFieldErrors}">
                    <div hidden data-modal-server-errors>
                        <c:forEach items="${loanSettingsFieldErrors}" var="fieldError">
                            <div data-modal-field-error="${fieldError.key}"><c:out value="${fieldError.value}" /></div>
                        </c:forEach>
                    </div>
                </c:if>

                <%@ include file="../fragments/loan-product-config-terms.jspf" %>
                <%@ include file="../fragments/loan-product-config-accounts-documents.jspf" %>

                <div class="product-config-actionbar">
                    <div class="product-config-section-actions" hidden aria-label="Configuration section navigation">
                        <button type="button" class="app-btn btn-neutral" data-product-config-previous disabled>Previous section</button>
                        <button type="button" class="app-btn btn-neutral" data-product-config-next>Next section</button>
                    </div>
                    <div class="product-config-save-actions">
                        <span class="product-config-save-state" hidden data-product-config-save-state data-unsaved-label="<spring:message code='admin.workspace.unsaved' />"></span>
                        <a href="/admin/settings-controls?section=loan" class="app-btn btn-neutral"><spring:message code="common.cancel" /></a>
                        <button type="submit" class="app-btn btn-launch"><spring:message code="admin.workspace.saveProduct" /></button>
                </div>
    </div>
        </form>
        <section class="product-builder-section product-config-step" data-product-config-step="workflow">
            <div class="product-builder-section-header"><h2><spring:message code="admin.settings.workflow.title" /></h2></div>
            <div class="product-builder-section-body" data-product-config-panel hidden>
                <div class="product-guarantor-actions">
                <c:set var="guarantorProduct" value="${product}" /><c:set var="guarantorFormId" value="loanProductConfigForm" />
                <%@ include file="../fragments/loan-product-guarantor-field.jspf" %>
                <sec:authorize access="@access.has(principal,'LOAN_PRODUCTS_UPDATE')"><button type="submit" form="loanProductConfigForm" class="app-btn btn-launch"><spring:message code="admin.workspace.saveProduct" /></button></sec:authorize>
                </div>
                <sec:authorize access="@access.has(principal,'APPROVAL_FLOW_VIEW')">
                <form method="post" action="<c:url value='/admin/settings-controls/loan-products/${product.id}/approval' />" data-workspace-submit="recover" data-product-approval-form>
                    <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}" />
                    <%@ include file="../fragments/loan-product-approval-fields.jspf" %>
                    <sec:authorize access="@access.has(principal,'APPROVAL_FLOW_CONFIGURE')"><div class="accounting-actions product-approval-actions"><button class="app-btn btn-launch"><spring:message code="common.save" /></button></div></sec:authorize>
                </form>
                </sec:authorize>
            </div>
        </section>
</div>
</div>

<spring:message code="role.manager" text="Manager" var="runtimeManager" />
<spring:message code="role.loanOfficer" text="Loan Officer" var="runtimeLoanOfficer" />
<spring:message code="role.chairperson" text="Chairperson" var="runtimeChairperson" />
<spring:message code="role.boardMember" text="Committee Member" var="runtimeBoardMember" />
<spring:message code="role.committee" text="Credit Committee" var="runtimeCommittee" />
<spring:message code="role.accountant" text="Accountant" var="runtimeAccountant" />
<spring:message code="role.disbursementOfficer" text="Disbursement/Teller Officer" var="runtimeDisbursementOfficer" />
<spring:message code="admin.settings.validation.productNameRequired" text="Enter a loan product name." var="validationProductNameRequired" />
<spring:message code="admin.settings.validation.productNameLength" text="Loan product name must be 120 characters or fewer." var="validationProductNameLength" />
<spring:message code="admin.settings.validation.productDescriptionRequired" text="Enter a product description." var="validationProductDescriptionRequired" />
<spring:message code="admin.settings.validation.descriptionLength" text="Keep the description within 500 characters." var="validationDescriptionLength" />
<spring:message code="admin.settings.validation.minimumAmount" text="Minimum amount cannot be negative." var="validationMinimumAmount" />
<spring:message code="admin.settings.validation.maximumAmountPositive" text="Maximum amount must be greater than zero." var="validationMaximumAmountPositive" />
<spring:message code="admin.settings.validation.maximumBelowMinimum" text="Maximum amount cannot be lower than the minimum amount." var="validationMaximumBelowMinimum" />
<spring:message code="admin.settings.validation.savingsPercent" text="Loan Disposable Income multiple must be between 0 and 10x." var="validationsavingsPercent" />
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
<spring:message code="admin.settings.validation.assignBoardReviewer" text="Assign at least one active Committee Member before using this stage." var="validationAssignBoardReviewer" />
<spring:message code="admin.settings.validation.assignCommitteeReviewer" text="Assign at least one active credit committee Member before using this stage." var="validationAssignCommitteeReviewer" />
<spring:message code="admin.settings.validation.committeeAccountantDifferentPriority" text="Committee and Accountant cannot share the same priority slot." var="validationCommitteeAccountantDifferentPriority" />
<spring:message code="admin.settings.validation.assignAccountant" text="Assign at least one active Accountant before using this stage." var="validationAssignAccountant" />
<spring:message code="admin.settings.validation.assignDisbursementOfficer" text="Assign at least one active Disbursement/Teller Officer before requiring this role." var="validationAssignDisbursementOfficer" />
<spring:message code="admin.settings.validation.assignDisbursementClaims" text="Grant both disbursement claims to at least one active staff user before removing this role requirement." var="validationAssignDisbursementClaims" />
<spring:message code="admin.settings.validation.applicationFee" text="Application fee cannot be negative." var="validationApplicationFee" />
<spring:message code="admin.settings.validation.processingFeePercent" text="Loan processing fee percentage cannot be negative." var="validationProcessingFeePercent" />




<%@ include file="../fragments/confirm-modal.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/loan-tenure-config.css' />" /><script src="<c:url value='/js/loan-tenure-config.js' />" defer></script><script src="<c:url value='/js/loan-recording.js' />" defer></script><%@ include file="../accounting/accounting-form-support.jspf" %>
<%@ include file="../fragments/loan-product-editor-script.jspf" %>
<%@ include file="../fragments/footer.jspf" %>
