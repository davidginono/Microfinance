package com.sacco.mvp.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorPageController implements ErrorController {
    @RequestMapping("/error")
    public String error(HttpServletRequest request, Model model) {
        Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return renderError(model, statusCode, requestUri);
    }

    @GetMapping("/error/403")
    public String forbidden(HttpServletRequest request, Model model) {
        String requestUri = originalRequestPath(request);
        return renderError(model, 403, requestUri);
    }

    private String originalRequestPath(HttpServletRequest request) {
        String uri = attributeValue(request, RequestDispatcher.ERROR_REQUEST_URI);
        String query = attributeValue(request, RequestDispatcher.ERROR_QUERY_STRING);
        if (uri == null || uri.isBlank()) {
            uri = attributeValue(request, RequestDispatcher.FORWARD_REQUEST_URI);
            query = attributeValue(request, RequestDispatcher.FORWARD_QUERY_STRING);
        }
        if (uri == null || uri.isBlank()) {
            uri = request.getRequestURI();
            query = request.getQueryString();
        }
        return query == null || query.isBlank() ? uri : uri + "?" + query;
    }

    private String attributeValue(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String text ? text : null;
    }

    private String renderError(Model model, Integer statusCode, String requestUri) {
        HttpStatus status = HttpStatus.resolve(statusCode == null ? 500 : statusCode);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        model.addAttribute("errorStatus", status.value());
        model.addAttribute("errorTitle", resolveTitle(status));
        model.addAttribute("errorSummary", resolveSummary(status));
        model.addAttribute("errorMessage", resolveMessage(status));
        model.addAttribute("errorPath", requestUri == null || requestUri.isBlank() ? "" : requestUri);
        return "error/general";
    }

    private String resolveTitle(HttpStatus status) {
        return switch (status) {
            case FORBIDDEN -> "Access Denied";
            case NOT_FOUND -> "Page Not Found";
            case UNAUTHORIZED -> "Sign In Required";
            default -> "Something Went Wrong";
        };
    }

    private String resolveSummary(HttpStatus status) {
        return switch (status) {
            case FORBIDDEN -> "The action or page you requested is not available in your current session.";
            case NOT_FOUND -> "The page you requested could not be found.";
            case UNAUTHORIZED -> "Your session is not authorized for that action.";
            default -> "The application hit a problem before it could finish the request.";
        };
    }

    private String resolveMessage(HttpStatus status) {
        if (status == HttpStatus.FORBIDDEN) {
            return "Use the available navigation controls inside the application, or sign in again if your session has changed.";
        }
        if (status == HttpStatus.NOT_FOUND) {
            return "Check the address or return to a known page from the navigation menu.";
        }
        return "Try again, or return to the previous page. If the problem continues, contact support.";
    }
}
