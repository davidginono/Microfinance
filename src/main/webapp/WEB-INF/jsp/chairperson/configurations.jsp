<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ include file="../fragments/header.jspf" %>
<%@ include file="../fragments/sidebar.jspf" %>

<div class="erp-page-header" data-aws-page-header>
    <p class="erp-breadcrumb"><spring:message code="chairperson.office" text="Chairperson Office" /> / <spring:message code="chairperson.configurations" text="SACCO Configurations" /></p>
    <h1 class="erp-page-title"><spring:message code="chairperson.configurations" text="SACCO Configurations" /></h1>
</div>

<div class="chairperson-config-grid">
    <section class="erp-panel chairperson-config-panel">
        <h2 class="font-semibold text-slate-900"><spring:message code="chairperson.stationPolicies" text="General Applicant Qualifications & Guarantor Policies" /></h2>
        <p class="chairperson-config-context"><spring:message code="common.station" text="Station" />: <c:out value="${configuration.stationId}" /></p>
        <dl class="chairperson-config-list">
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.applicantMaxDefaults" text="Applicant default limit" /></dt>
                <dd>
                    <span class="chairperson-config-value">
                        <c:choose>
                            <c:when test="${empty configuration.policies.applicantMaxDefaults.value or configuration.policies.applicantMaxDefaults.value le 0}"><spring:message code="chairperson.notEnforced" text="Not enforced" /></c:when>
                            <c:otherwise><c:out value="${configuration.policies.applicantMaxDefaults.value}" /> <spring:message code="chairperson.defaultedLoans" text="defaulted loan(s)" /></c:otherwise>
                        </c:choose>
                    </span>
                </dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.guarantorActiveLoan" text="Guarantor with active loan allowed" /></dt>
                <dd>
                    <span class="chairperson-config-value"><c:choose><c:when test="${configuration.policies.guarantorActiveLoanAllowed.value}"><spring:message code="common.yes" text="Yes" /></c:when><c:otherwise><spring:message code="common.no" text="No" /></c:otherwise></c:choose></span>
                </dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.maximumActiveGuarantees" text="Maximum active guarantees" /></dt>
                <dd>
                    <span class="chairperson-config-value">
                        <c:choose>
                            <c:when test="${empty configuration.policies.guarantorMaximumActiveGuarantees.value or configuration.policies.guarantorMaximumActiveGuarantees.value le 0}"><spring:message code="chairperson.noLimit" text="No limit" /></c:when>
                            <c:otherwise><fmt:formatNumber value="${configuration.policies.guarantorMaximumActiveGuarantees.value}" maxFractionDigits="0" /></c:otherwise>
                        </c:choose>
                    </span>
                </dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="chairperson.guarantorMaxDefaults" text="Guarantor default limit" /></dt>
                <dd>
                    <span class="chairperson-config-value">
                        <c:choose>
                            <c:when test="${empty configuration.policies.guarantorMaxDefaults.value or configuration.policies.guarantorMaxDefaults.value le 0}"><spring:message code="chairperson.notEnforced" text="Not enforced" /></c:when>
                            <c:otherwise><c:out value="${configuration.policies.guarantorMaxDefaults.value}" /> <spring:message code="chairperson.defaultedLoans" text="defaulted loan(s)" /></c:otherwise>
                        </c:choose>
                    </span>
                </dd>
            </div>
        </dl>
    </section>

    <section class="erp-panel chairperson-config-panel">
        <h2 class="font-semibold text-slate-900"><spring:message code="chairperson.otpConfiguration" text="OTP Configuration" /></h2>
        <dl class="chairperson-config-list">
            <div class="chairperson-config-row">
                <dt><spring:message code="admin.settings.otp.channel" text="OTP delivery" /></dt>
                <dd>
                    <c:choose>
                        <c:when test="${configuration.otp.deliveryChannel eq 'SMS'}"><spring:message code="admin.settings.otp.smsOnly" text="SMS only" /></c:when>
                        <c:when test="${configuration.otp.deliveryChannel eq 'SMS_WITH_EMAIL_FALLBACK'}"><spring:message code="admin.settings.otp.smsFallback" text="SMS with email fallback" /></c:when>
                        <c:otherwise><spring:message code="admin.settings.otp.email" text="Email" /></c:otherwise>
                    </c:choose>
                </dd>
            </div>
            <div class="chairperson-config-row">
                <dt><spring:message code="admin.settings.otp.requirement" text="OTP requirement" /></dt>
                <dd>
                    <c:choose>
                        <c:when test="${configuration.otp.requirementMode eq 'APPROVAL_ONLY'}"><spring:message code="admin.settings.otp.approvalOnly" text="Approvals only" /></c:when>
                        <c:when test="${configuration.otp.requirementMode eq 'LOGIN_MFA_AND_APPROVAL'}"><spring:message code="admin.settings.otp.loginAndApprovals" text="Login and approvals" /></c:when>
                        <c:otherwise><spring:message code="admin.settings.otp.loginMfaOnly" text="Login MFA only" /></c:otherwise>
                    </c:choose>
                </dd>
            </div>
        </dl>
    </section>
</div>

<section class="erp-panel mt-4 overflow-hidden" data-aws-table-region>
    <div class="border-b border-slate-200 px-5 py-4">
        <h2 class="font-semibold text-slate-900"><spring:message code="menu.admin.settings.loanProducts" text="Loan Products" /></h2>
    </div>
    <form method="get" class="erp-filter-form aws-filter-toolbar" data-aws-filter-toolbar>
        <label class="min-w-0 flex-1 text-xs font-semibold uppercase tracking-wide text-slate-500"><spring:message code="common.search" text="Search" />
            <input type="search" name="search" value="${fn:escapeXml(configuration.search)}" placeholder="<spring:message code='chairperson.products.searchPlaceholder' text='Product code or name' />" class="mt-1 w-full border border-slate-300 bg-white px-3 py-2.5 text-sm" />
        </label>
        <div class="flex gap-2 self-end">
            <c:if test="${not empty configuration.search}"><a class="app-btn btn-neutral" href="${pageContext.request.contextPath}/chairperson/configurations"><spring:message code="common.reset" text="Reset" /></a></c:if>
            <button class="app-btn btn-primary"><spring:message code="common.search" text="Search" /></button>
        </div>
    </form>
    <div class="erp-table-scroll">
        <table class="erp-table erp-table--left-headings chairperson-products-table">
            <thead><tr>
                <th scope="col"><spring:message code="common.code" text="Code" /></th>
                <th scope="col"><spring:message code="common.name" text="Name" /></th>
                <th scope="col"><spring:message code="common.status" text="Status" /></th>
                <th scope="col"><spring:message code="chairperson.amountRange" text="Amount Range" /></th>
                <th scope="col"><spring:message code="admin.settings.approvalFlow" text="Approval Flow" /></th>
                <th scope="col" class="erp-table-action-column"><spring:message code="common.actions" text="Actions" /></th>
            </tr></thead>
            <tbody>
                <c:forEach items="${configuration.products}" var="row">
                    <tr>
                        <td class="chairperson-table-code"><c:out value="${row.code}" /></td>
                        <td><c:out value="${row.name}" /></td>
                        <td><span class="erp-status-badge"><spring:message code="status.${fn:toLowerCase(row.status)}" text="${row.status}" /></span></td>
                        <td>
                            <c:choose>
                                <c:when test="${empty row.minimumAmount or empty row.maximumAmount}"><spring:message code="chairperson.amountRangeNotSet" text="Amount range not set" /></c:when>
                                <c:otherwise>TSh <fmt:formatNumber value="${row.minimumAmount}" maxFractionDigits="2" /> <spring:message code="common.to" text="to" /> <fmt:formatNumber value="${row.maximumAmount}" maxFractionDigits="2" /></c:otherwise>
                            </c:choose>
                        </td>
                        <td class="chairperson-workflow-cell"><c:forEach items="${row.workflow}" var="stage" varStatus="loop"><spring:message code="workflow.stage.${stage.stage}" text="${stage.label}" /><c:if test="${not loop.last}"><span aria-hidden="true"> &gt; </span></c:if></c:forEach></td>
                        <td class="erp-table-action-column"><div class="erp-table-actions"><a class="app-btn btn-neutral" href="${pageContext.request.contextPath}/chairperson/configurations/loan-products/${row.id}"><spring:message code="common.view" text="View" /></a></div></td>
                    </tr>
                </c:forEach>
                <c:if test="${empty configuration.products}"><tr><td colspan="6" class="erp-table-empty"><spring:message code="chairperson.products.empty" text="No loan products were found." /></td></tr></c:if>
            </tbody>
        </table>
    </div>
</section>

<c:if test="${configuration.totalPages gt 1}">
    <div class="mt-4 flex flex-wrap items-center justify-between gap-3">
        <span class="text-sm text-slate-500"><spring:message code="common.page" text="Page" /> ${configuration.page + 1} / ${configuration.totalPages}</span>
        <div class="flex gap-2">
            <c:if test="${not configuration.first}"><c:url var="prevUrl" value="/chairperson/configurations"><c:param name="search" value="${configuration.search}"/><c:param name="page" value="${configuration.page - 1}"/></c:url><a class="app-btn btn-neutral" href="${prevUrl}"><spring:message code="common.previous" text="Previous" /></a></c:if>
            <c:if test="${not configuration.last}"><c:url var="nextUrl" value="/chairperson/configurations"><c:param name="search" value="${configuration.search}"/><c:param name="page" value="${configuration.page + 1}"/></c:url><a class="app-btn btn-primary" href="${nextUrl}"><spring:message code="common.next" text="Next" /></a></c:if>
        </div>
    </div>
</c:if>

<%@ include file="../fragments/footer.jspf" %>
