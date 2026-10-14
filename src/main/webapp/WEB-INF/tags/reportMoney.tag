<%@ tag pageEncoding="UTF-8" body-content="empty" %>
<%@ attribute name="value" required="false" type="java.lang.Number" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<c:choose><c:when test="${value ne null}"><fmt:formatNumber value="${value}" pattern="#,##0.00;(#,##0.00)" /></c:when><c:otherwise><spring:message code="financial.report.unknown" /></c:otherwise></c:choose>
