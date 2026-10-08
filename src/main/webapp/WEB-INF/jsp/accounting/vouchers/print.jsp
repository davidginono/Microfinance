<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html><html lang="${pageContext.response.locale.language}"><head><meta charset="UTF-8" /><meta name="viewport" content="width=device-width,initial-scale=1" /><title><c:out value="${voucher.number}" /></title><link rel="stylesheet" href="<c:url value='/css/accounting-vouchers.css' />" /></head><body class="voucher-print"><div class="voucher-print-toolbar"><button type="button" class="app-btn" id="voucher-print-button"><spring:message code="voucher.print" /></button><a href="<c:url value='${base}/${voucher.id}' />"><spring:message code="common.view" /></a></div><%@ include file="document.jspf" %><script src="<c:url value='/js/accounting-voucher-print.js' />" defer></script></body></html>
