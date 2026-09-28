<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="chairperson.configurations" text="Institution Configurations" /> / <spring:message code="reports.loanProduct" text="Loan Product" /></p>
    <h1 class="erp-page-title"><c:out value="${product.name}" /></h1>
</div>

<section class="erp-panel chairperson-product-summary" data-aws-table-region>
    <div class="chairperson-product-summary-grid">
        <div class="chairperson-product-summary-item chairperson-product-summary-code">
            <div class="chairperson-product-summary-label"><spring:message code="common.code" text="Code" /></div>
            <div class="chairperson-product-code"><c:out value="${product.code}" /></div>
        </div>
        <div class="chairperson-product-summary-item">
            <div class="chairperson-product-summary-label"><spring:message code="common.status" text="Status" /></div>
            <div class="chairperson-product-summary-value"><span class="erp-status-badge"><spring:message code="status.${fn:toLowerCase(product.status)}" text="${product.status}" /></span></div>
        </div>
        <div class="chairperson-product-summary-item">
            <div class="chairperson-product-summary-label"><spring:message code="loan.type" text="Loan Type" /></div>
            <div class="chairperson-product-summary-value"><spring:message code="loan.type.${product.loanType}" text="${fn:replace(product.loanType, '_', ' ')}" /></div>
        </div>
        <div class="chairperson-product-summary-item">
            <div class="chairperson-product-summary-label"><spring:message code="loan.minimumAmount" text="Minimum Amount" /></div>
            <div class="chairperson-product-summary-value"><c:choose><c:when test="${empty product.minimumAmount}"><spring:message code="common.notSet" text="Not set" /></c:when><c:otherwise>TSh <fmt:formatNumber value="${product.minimumAmount}" maxFractionDigits="2" /></c:otherwise></c:choose></div>
        </div>
        <div class="chairperson-product-summary-item">
            <div class="chairperson-product-summary-label"><spring:message code="loan.maximumAmount" text="Maximum Amount" /></div>
            <div class="chairperson-product-summary-value"><c:choose><c:when test="${empty product.maximumAmount}"><spring:message code="common.notSet" text="Not set" /></c:when><c:otherwise>TSh <fmt:formatNumber value="${product.maximumAmount}" maxFractionDigits="2" /></c:otherwise></c:choose></div>
        </div>
        <div class="chairperson-product-summary-item chairperson-product-summary-description">
            <div class="chairperson-product-summary-label"><spring:message code="common.description" text="Description" /></div>
            <div class="chairperson-product-summary-description-value"><c:out value="${product.description}" /></div>
        </div>
    </div>
</section>

<div class="chairperson-product-rules-grid">
    <section class="erp-panel chairperson-config-panel">
        <h2 class="font-semibold"><spring:message code="chairperson.financialRules" text="Financial & Repayment Rules" /></h2>
        <dl class="chairperson-config-list">
            <div class="chairperson-config-row">
                <dt><spring:message code="loan.interestRate" text="Interest rate" /></dt>
                <dd><fmt:formatNumber value="${product.interestRate * 100}" maxFractionDigits="2" />%</dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="loan.interestMethod" text="Interest method" /></dt>
                <dd><c:choose><c:when test="${empty product.interestMethod or product.interestMethod eq '-'}"><spring:message code="common.notSet" text="Not set" /></c:when><c:otherwise><spring:message code="interestMethod.${product.interestMethod}" text="${fn:replace(product.interestMethod, '_', ' ')}" /></c:otherwise></c:choose></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="loan.repaymentPeriod" text="Repayment period" /></dt>
                <dd><c:choose><c:when test="${product.minimumMonths eq product.maximumMonths}"><c:out value="${product.minimumMonths}" /></c:when><c:otherwise><c:out value="${product.minimumMonths}" /> <spring:message code="common.to" text="to" /> <c:out value="${product.maximumMonths}" /></c:otherwise></c:choose> <spring:message code="common.months" text="month(s)" /></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="loan.applicationFee" text="Application fee" /></dt>
                <dd>TSh <fmt:formatNumber value="${product.applicationFee}" maxFractionDigits="2" /></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="loan.processingFee" text="Processing fee" /></dt>
                <dd><fmt:formatNumber value="${product.processingFeeRate * 100}" maxFractionDigits="2" />%</dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="loan.insuranceRate" text="Insurance rate" /></dt>
                <dd><c:choose><c:when test="${empty product.insuranceRate}"><spring:message code="common.notSet" text="Not set" /></c:when><c:otherwise><fmt:formatNumber value="${product.insuranceRate * 100}" maxFractionDigits="2" />%</c:otherwise></c:choose></dd>
            </div>
        </dl>
    </section>

    <section class="erp-panel chairperson-config-panel">
        <h2 class="font-semibold"><spring:message code="chairperson.eligibilityRules" text="Eligibility & Guarantor Rules" /></h2>
        <dl class="chairperson-config-list">
            <div class="chairperson-config-row">
                <dt><spring:message code="loan.requiredGuarantors" text="Required guarantors" /></dt>
                <dd><c:out value="${product.guarantorsRequired}" /></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.savingsCheck" text="Disposable Income-limit check" /></dt>
                <dd><c:choose><c:when test="${product.savingsCheckRequired}"><spring:message code="common.enabled" text="Enabled" /></c:when><c:otherwise><spring:message code="common.disabled" text="Disabled" /></c:otherwise></c:choose></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.configuredSavingsMultiplier" text="Configured Disposable Income multiplier" /></dt>
                <dd><c:choose><c:when test="${empty product.maximumSavingsRatio}"><spring:message code="common.notSet" text="Not set" /></c:when><c:otherwise><fmt:formatNumber value="${product.maximumSavingsRatio}" maxFractionDigits="2" /> <spring:message code="admin.settings.loanProducts.savingsMultiplierSuffix" text="x of Disposable Income" /></c:otherwise></c:choose></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.activeLoanAllowed" text="Application with active loan" /></dt>
                <dd><c:choose><c:when test="${product.activeLoanAllowed}"><spring:message code="common.yes" text="Yes" /></c:when><c:otherwise><spring:message code="common.no" text="No" /></c:otherwise></c:choose></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.freshFinancialData" text="Fresh financial data required" /></dt>
                <dd><c:choose><c:when test="${product.freshFinancialDataRequired}"><spring:message code="common.yes" text="Yes" /></c:when><c:otherwise><spring:message code="common.no" text="No" /></c:otherwise></c:choose></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.applicantAttachmentRequired" text="Applicant attachment required" /></dt>
                <dd><c:choose><c:when test="${product.applicantAttachmentRequired}"><spring:message code="common.yes" text="Yes" /></c:when><c:otherwise><spring:message code="common.no" text="No" /></c:otherwise></c:choose></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.disbursementProofRequired" text="Disbursement proof required" /></dt>
                <dd><c:choose><c:when test="${product.disbursementProofRequired}"><spring:message code="common.yes" text="Yes" /></c:when><c:otherwise><spring:message code="common.no" text="No" /></c:otherwise></c:choose></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.guarantorSavingsCheck" text="Guarantor Disposable Income check" /></dt>
                <dd><c:choose><c:when test="${product.guarantorSavingsCheckRequired}"><spring:message code="common.enabled" text="Enabled" /></c:when><c:otherwise><spring:message code="common.disabled" text="Disabled" /></c:otherwise></c:choose></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.guarantorSavings" text="Guarantor minimum Disposable Income" /></dt>
                <dd><c:choose><c:when test="${product.guarantorSavingsCheckRequired}">TSh <fmt:formatNumber value="${product.guarantorMinimumSavings}" maxFractionDigits="2" /></c:when><c:otherwise><spring:message code="chairperson.notApplied" text="Not applied" /></c:otherwise></c:choose></dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.votingThreshold" text="Committee voting" /></dt>
                <dd><c:choose><c:when test="${product.committeeReviewRequired}"><spring:message code="chairperson.votingSummary" arguments="${product.approvalThreshold},${product.minimumVotes}" text="${product.approvalThreshold} of ${product.minimumVotes} votes required" /></c:when><c:otherwise><spring:message code="chairperson.notApplied" text="Not applied" /></c:otherwise></c:choose></dd>
            </div>
        </dl>
    </section>
</div>

<section class="erp-panel mt-4 overflow-hidden">
    <div class="border-b border-slate-200 px-5 py-4"><h2 class="font-semibold"><spring:message code="admin.settings.approvalFlow" text="Approval Flow" /></h2></div>
    <div class="erp-table-scroll erp-table-scroll-fit">
        <table class="erp-table erp-table--left-headings chairperson-approval-table">
            <colgroup><col class="chairperson-table-order-column" /><col /></colgroup>
            <thead><tr><th scope="col"><spring:message code="common.order" text="Order" /></th><th scope="col"><spring:message code="common.stage" text="Stage" /></th></tr></thead>
            <tbody>
                <c:forEach items="${product.workflow}" var="stage"><tr><td><c:out value="${stage.priority}" /></td><td><spring:message code="workflow.stage.${stage.stage}" text="${stage.label}" /></td></tr></c:forEach>
                <c:if test="${empty product.workflow}"><tr><td colspan="2" class="erp-table-empty"><spring:message code="chairperson.noApprovalStages" text="No review stages are configured." /></td></tr></c:if>
            </tbody>
        </table>
    </div>
</section>

<div class="chairperson-product-tables-grid">
    <section class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 px-5 py-4"><h2 class="font-semibold"><spring:message code="loan.attachments" text="Required Attachments" /></h2></div>
        <div class="erp-table-scroll erp-table-scroll-fit">
            <table class="erp-table erp-table--left-headings chairperson-attachments-table">
                <colgroup><col /><col class="chairperson-table-size-column" /></colgroup>
                <thead><tr><th scope="col"><spring:message code="common.name" text="Name" /></th><th scope="col"><spring:message code="chairperson.maximumFileSize" text="Maximum Size (MB)" /></th></tr></thead>
                <tbody>
                    <c:forEach items="${product.attachments}" var="attachment"><tr><td><c:out value="${attachment.name}" /></td><td><fmt:formatNumber value="${attachment.maxSizeMb}" maxFractionDigits="2" /></td></tr></c:forEach>
                    <c:if test="${empty product.attachments}"><tr><td colspan="2" class="erp-table-empty"><spring:message code="chairperson.noRequiredAttachments" text="No product-specific attachments." /></td></tr></c:if>
                </tbody>
            </table>
        </div>
    </section>

    <section class="erp-panel overflow-hidden">
        <div class="border-b border-slate-200 px-5 py-4"><h2 class="font-semibold"><spring:message code="chairperson.assignedReviewers" text="Assigned Reviewers" /></h2></div>
        <div class="erp-table-scroll erp-table-scroll-md">
            <table class="erp-table erp-table--left-headings chairperson-reviewers-table">
                <colgroup><col class="chairperson-table-stage-column" /><col /><col class="chairperson-table-staff-column" /></colgroup>
                <thead><tr><th scope="col"><spring:message code="common.stage" text="Stage" /></th><th scope="col"><spring:message code="common.reviewer" text="Reviewer" /></th><th scope="col"><spring:message code="staff.number" text="Staff Number" /></th></tr></thead>
                <tbody>
                    <c:forEach items="${product.reviewers}" var="reviewer"><tr><td><spring:message code="workflow.stage.${reviewer.stage}" text="${reviewer.stageLabel}" /></td><td><c:out value="${reviewer.name}" /></td><td><c:out value="${reviewer.staffNumber}" /></td></tr></c:forEach>
                    <c:if test="${empty product.reviewers}"><tr><td colspan="3" class="erp-table-empty"><spring:message code="chairperson.noAssignedReviewers" text="No product-specific reviewers assigned." /></td></tr></c:if>
                </tbody>
            </table>
        </div>
    </section>
</div>

<%@ include file="../fragments/footer.jspf" %>
