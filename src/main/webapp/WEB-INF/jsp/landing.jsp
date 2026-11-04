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
            <div class="landing-feature-grid">
                <article class="landing-panel landing-feature">
                    <svg viewBox="0 0 96 64" fill="none" aria-hidden="true" focusable="false" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="10" y="12" width="76" height="44" rx="4" />
                        <circle class="landing-art-accent" cx="34" cy="28" r="7" />
                        <path class="landing-art-accent" d="M22 47a12 12 0 0 1 24 0" />
                        <path d="M56 25h18m-18 9h18m-18 9h11" />
                    </svg>
                    <h3><spring:message code="home.steps.profile" text="Set up your profile" /></h3>
                    <p><spring:message code="home.steps.profileCopy" text="Tell us a little about yourself and add your documents." /></p>
                </article>
                <article class="landing-panel landing-feature">
                    <svg viewBox="0 0 96 64" fill="none" aria-hidden="true" focusable="false" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M23 55h-7V9h35l11 11v13M51 9v11h11M25 23h15m-15 10h25m-25 10h17" />
                        <circle class="landing-art-accent" cx="66" cy="47" r="13" />
                        <path class="landing-art-accent" d="M60 47l4 4 8-8" />
                    </svg>
                    <h3><spring:message code="home.steps.loan" text="Apply for a loan" /></h3>
                    <p><spring:message code="home.steps.loanCopy" text="Choose a loan that fits your plans and send your request." /></p>
                </article>
                <article class="landing-panel landing-feature">
                    <svg viewBox="0 0 96 64" fill="none" aria-hidden="true" focusable="false" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="12" y="10" width="72" height="44" rx="4" />
                        <path d="M12 22h72m-63-6h1m5 0h1m5 0h1m-10 31h48" />
                        <path class="landing-art-accent" d="m25 40 13-9 12 6 20-9m-7 0h7v7" />
                    </svg>
                    <h3><spring:message code="home.steps.track" text="Check your progress" /></h3>
                    <p><spring:message code="home.steps.trackCopy" text="Log in to see where your request stands and what’s next." /></p>
                </article>
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
