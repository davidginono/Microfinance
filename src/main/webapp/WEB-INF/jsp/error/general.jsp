<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${errorTitle}</title>
    <link rel="icon" type="image/png" href="<c:url value='/images/computer-resources-logo.png' />" />
    <style>
        :root {
            --page-bg: #eef2f5;
            --panel-bg: #ffffff;
            --panel-border: #d7dde3;
            --text-main: #1f2937;
            --text-muted: #64748b;
            --accent: #11b2c3;
        }
        * {
            box-sizing: border-box;
        }
        body {
            margin: 0;
            min-height: 100vh;
            background: linear-gradient(180deg, #eef7fa 0%, var(--page-bg) 28%, var(--page-bg) 100%);
            color: var(--text-main);
            font-family: Manrope, ui-sans-serif, system-ui;
        }
        .error-shell {
            width: min(100%, 64rem);
            margin: 0 auto;
            padding: 2.5rem 1rem 3rem;
        }
        .error-topbar {
            display: flex;
            align-items: center;
            gap: 0.9rem;
            padding: 1rem 1.15rem;
            border: 1px solid rgba(255,255,255,0.34);
            border-radius: 1rem;
            background: linear-gradient(180deg, #19c6d8 0%, var(--accent) 100%);
            color: #fff;
            box-shadow: 0 16px 42px -28px rgba(15, 23, 42, 0.38);
        }
        .error-badge {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 3rem;
            height: 3rem;
            border-radius: 0.9rem;
            background: rgba(255,255,255,0.96);
            color: #1f2937;
            font-weight: 800;
            letter-spacing: 0.14em;
        }
        .error-brand-title {
            margin: 0;
            font-family: Sora, ui-sans-serif, system-ui;
            font-size: 1.2rem;
            font-weight: 700;
        }
        .error-brand-subtitle {
            margin: 0.18rem 0 0;
            font-size: 0.82rem;
            font-weight: 700;
            letter-spacing: 0.16em;
            text-transform: uppercase;
            color: rgba(255,255,255,0.88);
        }
        .error-card {
            margin-top: 1.25rem;
            padding: 1.4rem;
            border: 1px solid var(--panel-border);
            border-radius: 1rem;
            background: var(--panel-bg);
            box-shadow: 0 18px 42px -34px rgba(15, 23, 42, 0.28);
        }
        .error-status {
            display: inline-flex;
            align-items: center;
            gap: 0.55rem;
            padding: 0.45rem 0.75rem;
            border-radius: 999px;
            background: #e0f2fe;
            color: #0f4c81;
            font-size: 0.82rem;
            font-weight: 800;
            letter-spacing: 0.08em;
            text-transform: uppercase;
        }
        .error-title {
            margin: 0.9rem 0 0;
            font-family: Sora, ui-sans-serif, system-ui;
            font-size: clamp(1.8rem, 3.5vw, 2.45rem);
            line-height: 1.1;
        }
        .error-summary {
            margin: 0.75rem 0 0;
            font-size: 1.02rem;
            line-height: 1.6;
            color: var(--text-main);
        }
        .error-message {
            margin: 0.7rem 0 0;
            font-size: 0.96rem;
            line-height: 1.6;
            color: var(--text-muted);
        }
        .error-meta {
            margin-top: 1rem;
            padding: 0.95rem 1rem;
            border-radius: 0.8rem;
            background: #f8fafc;
            color: #475569;
            font-size: 0.9rem;
        }
        .error-actions {
            display: flex;
            flex-wrap: wrap;
            gap: 0.75rem;
            margin-top: 1.2rem;
        }
        .error-btn {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            padding: 0.78rem 1.15rem;
            border: 1px solid #cbd5e1;
            border-radius: 0.7rem;
            background: #ffffff;
            color: #334155;
            font-size: 0.92rem;
            font-weight: 700;
            text-decoration: none;
        }
        .error-btn-primary {
            background: #eff6ff;
            border-color: #93c5fd;
            color: #1d4ed8;
        }
    </style>
</head>
<body>
<div class="error-shell">
    <div class="error-topbar">
        <div class="error-badge">IA</div>
        <div>
            <p class="error-brand-title">IAA SACCOs LTD</p>
            <p class="error-brand-subtitle">Loan Management System</p>
        </div>
    </div>

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
