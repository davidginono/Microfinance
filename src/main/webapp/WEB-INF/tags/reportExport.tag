<%@ tag pageEncoding="UTF-8" body-content="empty" %>
<%@ attribute name="format" required="true" type="java.lang.String" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<svg class="report-export-icon" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
    <c:choose>
        <c:when test="${format eq 'print'}">
            <path fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"
                  d="M6 8V3h12v5M6 18H3V8h18v10h-3M6 14h12v7H6zM6 11h2" />
        </c:when>
        <c:when test="${format eq 'pdf'}">
            <path fill="none" stroke="#c91924" stroke-width="2" stroke-linejoin="round"
                  d="M5 2h9l5 5v15H5zM14 2v5h5" />
            <path fill="none" stroke="#c91924" stroke-width="1.4" stroke-linecap="round"
                  d="M8 18c3-3 5-8 3-8s1 6 5 6c2 0 0-3-4-1-4 1-6 5-4 3z" />
        </c:when>
        <c:when test="${format eq 'excel'}">
            <path fill="none" stroke="#107c41" stroke-width="1.5"
                  d="M12 5h10v14H12m4-11h4m-4 4h4m-4 4h4" />
            <path fill="#107c41" d="M2 4l12-2v20L2 20z" />
            <path fill="none" stroke="#fff" stroke-width="1.7" stroke-linecap="round" d="m5.5 8.5 5 7m0-7-5 7" />
        </c:when>
    </c:choose>
</svg><span><spring:message code="management.${format}" /></span>
