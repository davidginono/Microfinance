<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <meta name="theme-color" content="#101820" />
    <title>Loan Application Portal</title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-mark-light-green.png?v=20260826-clean' />" />
    <link rel="preload" href="<c:url value='/fonts/open-sans/open-sans-400.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="<c:url value='/fonts/open-sans/open-sans-700.woff2' />" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="<c:url value='/images/loan-portal-landing-hero.png' />" as="image" type="image/png" />
    <link rel="stylesheet" href="<c:url value='/css/open-sans.css?v=20260805-cloudscape-type-v2' />" />
    <link rel="stylesheet" href="<c:url value='/css/aws-auth.css?v=20260827-auth-notice-width-v1' />" />
    <link rel="stylesheet" href="<c:url value='/css/landing.css?v=20260924-landing-v1' />" />
</head>
<body class="landing-page">
<main class="landing-hero">
    <div class="landing-hero__shade" aria-hidden="true"></div>
    <section class="landing-panel" aria-labelledby="landingTitle">
        <img class="landing-panel__mark"
             src="<c:url value='/images/icons/tabler-file-invoice-orange.svg' />"
             alt=""
             width="120"
             height="120" />
        <h1 id="landingTitle">Loan Application Portal</h1>
        <p class="landing-panel__intro">Apply for loans, track your application, and manage your account easily, securely, and conveniently.</p>

        <div class="landing-actions" aria-label="Account access">
            <div class="landing-action">
                <a class="landing-action__button landing-action__button--primary" href="<c:url value='/login' />">
                    <img src="<c:url value='/images/icons/tabler-login-white.svg' />" alt="" width="28" height="28" />
                    <span>Login</span>
                    <img class="landing-action__arrow" src="<c:url value='/images/icons/tabler-chevron-right-white.svg' />" alt="" width="26" height="26" />
                </a>
                <p>Access your existing account</p>
            </div>

            <div class="landing-action">
                <a class="landing-action__button landing-action__button--secondary" href="<c:url value='/register/member' />">
                    <img src="<c:url value='/images/icons/tabler-user-plus-white.svg' />" alt="" width="28" height="28" />
                    <span>Register</span>
                    <img class="landing-action__arrow" src="<c:url value='/images/icons/tabler-chevron-right-white.svg' />" alt="" width="26" height="26" />
                </a>
                <p>Create a new account</p>
            </div>
        </div>
    </section>
</main>
</body>
</html>
