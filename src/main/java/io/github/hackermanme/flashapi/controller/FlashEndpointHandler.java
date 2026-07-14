package io.github.hackermanme.flashapi.controller;

import io.github.hackermanme.flashapi.ratelimit.FlashRateLimiter;
import io.github.hackermanme.flashapi.registry.CrudOperation;
import io.github.hackermanme.flashapi.security.SecurityEvaluator;
import io.github.hackermanme.flashapi.security.SecurityResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.Map;

/**
 * Bridge between Spring MVC's handler infrastructure and FlashController.
 * Each instance is bound to a single operation on a single entity.
 * Spring MVC resolves method arguments via annotations on handle().
 */
public final class FlashEndpointHandler {

    private final FlashController controller;
    private final String operation;
    private final FlashRateLimiter rateLimiter;
    private final SecurityEvaluator securityEvaluator;

    public FlashEndpointHandler(FlashController controller, String operation,
                                FlashRateLimiter rateLimiter, SecurityEvaluator securityEvaluator) {
        this.controller = controller;
        this.operation = operation;
        this.rateLimiter = rateLimiter;
        this.securityEvaluator = securityEvaluator;
    }

    public ResponseEntity<?> handle(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) Map<String, String> params,
            @RequestBody(required = false) Map<String, Object> body) throws IOException {

        if (securityEvaluator != null) {
            CrudOperation crudOp = toCrudOperation(operation);
            if (crudOp != null) {
                SecurityResult result = securityEvaluator.evaluate(controller.getMetadata(), crudOp);
                if (result == SecurityResult.UNAUTHENTICATED) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", "Authentication required"));
                }
                if (result == SecurityResult.FORBIDDEN) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Access denied"));
                }
            }
        }

        if (rateLimiter != null && !rateLimiter.isAllowed(controller.getMetadata(), getClientIp(request))) {
            response.setIntHeader("Retry-After", controller.getMetadata().rateLimitWindow());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded", "retryAfter", controller.getMetadata().rateLimitWindow()));
        }

        if ("export".equals(operation)) {
            controller.export(params != null ? params : Map.of(), response);
            return null;
        }

        return switch (operation) {
            case "list" -> controller.list(params != null ? params : Map.of());
            case "getById" -> controller.getById(extractId(request), params);
            case "create" -> controller.create(body != null ? body : Map.of());
            case "update" -> controller.update(extractId(request), body != null ? body : Map.of());
            case "delete" -> controller.delete(extractId(request));
            case "restore" -> controller.restore(extractIdBeforeSegment(request, "restore"));
            case "history" -> controller.history(extractIdBeforeSegment(request, "history"));
            default -> ResponseEntity.internalServerError().build();
        };
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Object extractId(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String idStr = uri.substring(uri.lastIndexOf('/') + 1);
        return convertId(idStr);
    }

    private Object extractIdBeforeSegment(HttpServletRequest request, String segment) {
        String uri = request.getRequestURI();
        int segmentIdx = uri.lastIndexOf("/" + segment);
        String beforeSegment = uri.substring(0, segmentIdx);
        String idStr = beforeSegment.substring(beforeSegment.lastIndexOf('/') + 1);
        return convertId(idStr);
    }

    private Object convertId(String raw) {
        Class<?> idType = controller.getMetadata().idType();
        if (idType == Long.class || idType == long.class) return Long.parseLong(raw);
        if (idType == Integer.class || idType == int.class) return Integer.parseInt(raw);
        if (idType == java.util.UUID.class) return java.util.UUID.fromString(raw);
        return raw;
    }

    private CrudOperation toCrudOperation(String op) {
        return switch (op) {
            case "list", "export" -> CrudOperation.LIST;
            case "getById", "history" -> CrudOperation.READ;
            case "create" -> CrudOperation.CREATE;
            case "update", "restore" -> CrudOperation.UPDATE;
            case "delete" -> CrudOperation.DELETE;
            default -> null;
        };
    }
}
