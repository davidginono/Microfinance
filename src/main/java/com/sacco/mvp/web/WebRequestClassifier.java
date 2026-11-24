package com.sacco.mvp.web;

import jakarta.servlet.http.HttpServletRequest;

public final class WebRequestClassifier {
    private WebRequestClassifier() {
    }

    public static boolean isStatementReportFragment(HttpServletRequest request) {
        if (request == null || !"GET".equals(request.getMethod())
                || !"report".equals(request.getHeader("X-Statement-Fragment"))) return false;
        String path=request.getRequestURI().substring(request.getContextPath().length());
        return path.matches("/reports/financial/statements/[0-9a-fA-F-]{36}(/trial|/ledger/[0-9a-fA-F-]{36})?");
    }

    public static boolean isJsonRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String requestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equalsIgnoreCase(requestedWith)) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.toLowerCase(java.util.Locale.ROOT).contains("application/json");
    }
}
