package com.sacco.mvp.config;

import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class EventLogAspect {
    private final AuditService auditService;

    @Around("@annotation(org.springframework.web.bind.annotation.PostMapping) && within(com.sacco.mvp.web..*)")
    public Object logWebPostEvent(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID actorId = currentActorId();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String entityType = signature.getDeclaringType().getSimpleName();
        String action = "WEB_" + toUpperSnakeCase(signature.getMethod().getName());
        try {
            Object result = joinPoint.proceed();
            if (!auditService.hasCurrentRequestAuditMarker()) {
                auditService.log(entityType, null, action, actorId, null, eventState("SUCCESS", null));
            }
            return result;
        } catch (Throwable ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("result", "ERROR");
            error.put("message", ex.getMessage());
            currentPrincipal().ifPresent(principal -> {
                error.put("saccoId", principal.getSaccoId());
                error.put("stationId", principal.getStationId());
            });
            auditService.log(entityType, null, action, actorId, null, error);
            throw ex;
        }
    }

    private Map<String, Object> eventState(String result, String message) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("result", result);
        if (message != null && !message.isBlank()) {
            state.put("message", message);
        }
        currentPrincipal().ifPresent(principal -> {
            state.put("saccoId", principal.getSaccoId());
            state.put("stationId", principal.getStationId());
        });
        return state;
    }

    private String toUpperSnakeCase(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN_ACTION";
        }
        String snake = value
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .replace('-', '_')
            .replace(' ', '_');
        return snake.toUpperCase();
    }

    private UUID currentActorId() {
        return currentPrincipal().map(AppUserPrincipal::getMemberId).orElse(null);
    }

    private java.util.Optional<AppUserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(principal);
    }
}
