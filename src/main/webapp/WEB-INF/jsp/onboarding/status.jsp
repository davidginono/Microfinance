<%@ include file="head.jspf" %>
<h1><spring:message code="kyc.ownTitle"/></h1>
<section class="erp-resource-section"><h2><spring:message code="kyc.ownTitle"/></h2><dl class="erp-detail-facts">
    <div><dt><spring:message code="kyc.field.state"/></dt><dd><spring:message code="kyc.state.${onboardingStatus.state}"/></dd></div>
    <div><dt><spring:message code="kyc.field.due"/></dt><dd><c:out value="${appTime:display(onboardingStatus.reviewDue)}"/></dd></div>
</dl><c:if test="${onboardingStatus.state!='VERIFY'}"><p><spring:message code="kyc.contactBranch"/></p></c:if></section>
<%@ include file="../fragments/footer.jspf" %>
