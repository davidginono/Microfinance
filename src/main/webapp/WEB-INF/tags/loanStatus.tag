<%@ tag pageEncoding="UTF-8" body-content="empty" trimDirectiveWhitespaces="true" %>
<%@ attribute name="status" required="true" type="java.lang.String" %>
<%@ attribute name="reviewGroup" required="false" type="java.lang.String" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<span data-resource-status="<c:out value='${status}' />"><c:choose>
    <c:when test="${status eq 'SUBMITTED' and not empty reviewGroup}">
        <spring:message code="userGroup.${reviewGroup}" var="reviewGroupName" />
        <spring:message code="recording.status.onReviewBy" arguments="${reviewGroupName}" htmlEscape="true" />
    </c:when>
    <c:otherwise><spring:message code="recording.status.${status}" text="${status}" htmlEscape="true" /></c:otherwise>
</c:choose></span>
