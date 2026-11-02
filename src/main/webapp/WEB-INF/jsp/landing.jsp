<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<!DOCTYPE html>
<html lang="${pageContext.response.locale.language}">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <meta name="theme-color" content="#232f3e" />
    <title><spring:message code="app.title" /></title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-mark-light-green.png' />" />
    <link rel="preload" href="<c:url value='/fonts/amazon-ember/AmazonEmber_W_Rg.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="<c:url value='/fonts/amazon-ember/AmazonEmber_W_Bd.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="stylesheet" href="<c:url value='/css/amazon-ember.css' />" />
    <link rel="stylesheet" href="<c:url value='/css/shell.css' />" />
    <link rel="stylesheet" href="<c:url value='/css/landing.css' />" />
</head>
<body class="landing-page">
<a class="landing-skip" href="#main-content"><spring:message code="home.skip" /></a>
<header class="landing-topbar">
    <div class="landing-width landing-topbar__content">
        <a class="landing-brand" href="<c:url value='/' />" aria-label="<spring:message code='home.title' />">
            <span>microfinance</span>
            <svg viewBox="0 0 128 9" fill="none" aria-hidden="true" focusable="false">
                <path d="M2 2.5C32 9 79 9 111 3.5" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />
                <path d="m115 4 3 3 8-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
        </a>
        <nav class="landing-language" aria-label="<spring:message code='portal.language' />">
            <a href="<c:url value='/'><c:param name='lang' value='en' /></c:url>" lang="en" hreflang="en" ${pageContext.response.locale.language eq 'en' ? 'aria-current="true"' : ''}>English</a>
            <a href="<c:url value='/'><c:param name='lang' value='sw' /></c:url>" lang="sw" hreflang="sw" ${pageContext.response.locale.language eq 'sw' ? 'aria-current="true"' : ''}>Kiswahili</a>
        </nav>
    </div>
</header>
<main id="main-content" tabindex="-1">
    <section class="landing-hero" aria-labelledby="landingTitle">
        <div class="landing-width landing-hero__content">
            <div class="landing-intro">
                <h1 id="landingTitle"><spring:message code="home.heading" /></h1>
                <p><spring:message code="home.intro" /></p>
                <div class="landing-actions">
                    <a class="app-btn btn-launch" href="<c:url value='/login' />"><spring:message code="home.login" /></a>
                    <a class="app-btn btn-neutral" href="<c:url value='/register/member' />"><spring:message code="home.createAccount" /></a>
                </div>
            </div>
            <aside class="landing-portal" aria-labelledby="portalTitle">
                <h2 id="portalTitle"><spring:message code="home.portal" /></h2>
                <p><spring:message code="home.portalIntro" /></p>
                <div class="landing-portal__links">
                    <a href="<c:url value='/client-portal/register' />"><spring:message code="home.registerProfile" /><span aria-hidden="true">&#8594;</span></a>
                    <a href="<c:url value='/client-portal' />"><spring:message code="home.openPortal" /><span aria-hidden="true">&#8594;</span></a>
                </div>
            </aside>
        </div>
    </section>
</main>
<footer class="landing-footer landing-width">
    <h2 id="staffTitle"><spring:message code="home.staff" /></h2>
    <nav aria-labelledby="staffTitle">
        <a href="<c:url value='/login'><c:param name='tab' value='staff' /></c:url>"><spring:message code="home.staffLogin" /></a>
        <a href="<c:url value='/system-admin/login' />"><spring:message code="home.systemAdmin" /></a>
    </nav>
</footer>
</body>
</html>
