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
            <%@ include file="fragments/microfinance-wordmark.jspf" %>
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
                <p class="landing-eyebrow"><spring:message code="app.title" /></p>
                <h1 id="landingTitle"><spring:message code="home.hero.title" text="Small loans. Big plans." /></h1>
                <p class="landing-intro__copy"><spring:message code="home.hero.copy" text="Need stock, tools, or a little room to grow? Set up your profile and apply for a loan online." /></p>
                <div class="landing-actions">
                    <a class="app-btn btn-neutral" href="<c:url value='/login' />"><spring:message code="home.login" /></a>
                    <a class="landing-create" href="<c:url value='/register/member' />"><spring:message code="home.createAccount" /></a>
                </div>
            </div>
            <section class="landing-panel landing-demo" aria-labelledby="demoTitle">
                <p class="landing-eyebrow"><spring:message code="home.demo.audience" text="For lending teams" /></p>
                <h2 id="demoTitle"><spring:message code="home.demo.title" text="See it in action" /></h2>
                <p><spring:message code="home.demo.copy" text="See how your team can register clients, review loan requests, and track repayments." /></p>
                <a class="app-btn btn-launch" href="mailto:info@foresight.co.tz?subject=Microfinance%20demo"><spring:message code="home.demo.book" text="Book a demo" /></a>
            </section>
        </div>
    </section>
    <div class="landing-width landing-overview">
        <section aria-labelledby="stepsTitle">
            <h2 id="stepsTitle"><spring:message code="home.steps.title" text="How it works" /></h2>
            <div class="landing-panel landing-flow-card">
              <ol class="landing-flow" role="list">
                <li class="landing-flow-step">
                    <svg viewBox="0 0 96 64" aria-hidden="true" focusable="false">
                        <path d="M22 22H10v32h25m30-42H54m16 12h15v29H61M15 29h8m-8 6h8m54 0h4m-4 6h4" />
                        <rect x="25" y="15" width="49" height="31" rx="2" />
                        <circle cx="38" cy="26" r="4" />
                        <path d="M31 38a7 7 0 0 1 14 0M51 25h16m-16 6h16m-16 6h10" />
                        <path class="landing-art-accent" d="M39 51h19m-4-4 4 4-4 4" />
                    </svg>
                    <h3><spring:message code="home.steps.profile" /></h3>
                    <p><spring:message code="home.steps.profileCopy" /></p>
                </li>
                <li class="landing-flow-step landing-flow-apply">
                    <svg viewBox="0 0 96 64" aria-hidden="true" focusable="false">
                        <path d="M50 56H24V9h33l12 12v11M57 9v12h12M32 23h17m-17 9h20m-20 9h13m-13 8h12M18 16h-4v44h38" />
                        <path class="landing-art-accent" d="m48 43 34-11-11 23-8-8-7 9 1-11-9-2Zm9 2 25-13" />
                    </svg>
                    <h3><spring:message code="home.steps.loan" /></h3>
                    <p><spring:message code="home.steps.loanCopy" /></p>
                </li>
                <li class="landing-flow-step">
                    <svg viewBox="0 0 96 64" aria-hidden="true" focusable="false">
                        <rect x="20" y="12" width="41" height="44" rx="2" />
                        <path d="M30 12V7h20v5m-20 12h21m-21 9h10m-10 9h9m-9 8h13" />
                        <circle cx="61" cy="37" r="13" />
                        <path d="m70 46 12 12M11 21h4m-4 9h4m-4 9h4" />
                        <path class="landing-art-accent" d="m53 40 5-5 4 2 6-6" />
                    </svg>
                    <h3><spring:message code="home.steps.review" /></h3>
                    <p><spring:message code="home.steps.reviewCopy" /></p>
                </li>
                <li class="landing-flow-step landing-flow-track">
                    <svg viewBox="0 0 96 64" aria-hidden="true" focusable="false">
                        <rect x="12" y="10" width="72" height="44" rx="2" />
                        <path d="M12 21h72m-64-5h2m4 0h2m4 0h2m-12 9v21h28m8-15h17m-17 7h17m-17 7h13" />
                        <path class="landing-art-accent" d="m26 40 7-8 6 4 9-8m-6 0h6v6" />
                    </svg>
                    <h3><spring:message code="home.steps.track" /></h3>
                    <p><spring:message code="home.steps.trackCopy" /></p>
                </li>
              </ol>
            </div>
        </section>
        <aside class="landing-panel landing-portal" aria-labelledby="portalTitle">
            <h2 id="portalTitle"><spring:message code="home.portal" /></h2>
            <p><spring:message code="home.portalIntro" /></p>
            <div class="landing-portal__links">
                <a href="<c:url value='/client-portal/register' />"><spring:message code="home.registerProfile" /><span aria-hidden="true">&#8594;</span></a>
                <a href="<c:url value='/client-portal' />"><spring:message code="home.openPortal" /><span aria-hidden="true">&#8594;</span></a>
            </div>
        </aside>
    </div>
</main>
<footer class="landing-footer landing-width">
    <h2 id="staffTitle"><spring:message code="home.staff" /></h2>
    <nav aria-labelledby="staffTitle">
        <a href="<c:url value='/login'><c:param name='tab' value='staff' /></c:url>"><spring:message code="home.staffLogin" /></a>
    </nav>
</footer>
</body>
</html>
