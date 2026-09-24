package com.sacco.mvp.web;

import jakarta.servlet.http.HttpServletRequest;

public final class WebRequestClassifier {
    private WebRequestClassifier() {
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
