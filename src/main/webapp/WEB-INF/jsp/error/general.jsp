<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${errorTitle}</title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-mark-light-green.png?v=20260820' />" />
    <link rel="preload" href="<c:url value='/fonts/open-sans/open-sans-400.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="<c:url value='/fonts/open-sans/open-sans-700.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="stylesheet" href="<c:url value='/css/open-sans.css?v=20260805-cloudscape-type-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/console-components.css?v=20260809-loan-detail-action-v33' />" />
    <link rel="stylesheet" href="<c:url value='/css/aws-auth.css?v=20260812-navbar-v6' />" />
</head>
<body class="aws-auth-shell">
<div class="error-shell">
    <section class="error-card">
        <div class="error-status">Status ${errorStatus}</div>
        <h1 class="error-title">${errorTitle}</h1>
        <p class="error-summary">${errorSummary}</p>
        <p class="error-message">${errorMessage}</p>

        <c:if test="${not empty errorPath}">
            <div class="error-meta">
                Request path: <strong>${errorPath}</strong>
            </div>
        </c:if>

        <div class="error-actions">
            <a href="/login" class="error-btn error-btn-primary">Go To Login</a>
            <a href="javascript:history.back()" class="error-btn">Go Back</a>
            <a href="/" class="error-btn">Open Home</a>
        </div>
    </section>
</div>
</body>
</html>
