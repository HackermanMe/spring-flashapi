package io.github.hackermanme.flashapi.controller;

import io.github.hackermanme.flashapi.ratelimit.FlashRateLimiter;
import io.github.hackermanme.flashapi.registry.CrudOperation;
import io.github.hackermanme.flashapi.security.SecurityEvaluator;
import io.github.hackermanme.flashapi.security.SecurityResult;
import io.github.hackermanme.flashapi.tenant.TenantContext;
import io.github.hackermanme.flashapi.tenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

public final class FlashBulkEndpointHandler {

    private final FlashController controller;
    private final String operation;
    private final FlashRateLimiter rateLimiter;
    private final SecurityEvaluator securityEvaluator;
    private final TenantResolver tenantResolver;

    public FlashBulkEndpointHandler(FlashController controller, String operation,
                                    FlashRateLimiter rateLimiter, SecurityEvaluator securityEvaluator,
                                    TenantResolver tenantResolver) {
        this.controller = controller;
        this.operation = operation;
        this.rateLimiter = rateLimiter;
        this.securityEvaluator = securityEvaluator;
        this.tenantResolver = tenantResolver;
    }

    public ResponseEntity<?> handle(
            HttpServletRequest request,
            @RequestBody(required = false) Object body) {

        if (tenantResolver != null) {
            String tenantId = tenantResolver.resolve(request);
            if (tenantId != null) {
                TenantContext.set(tenantId);
            } else if (controller.getMetadata().isMultiTenant()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Tenant context required"));
            }
        }

        try {
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
                int window = controller.getMetadata().rateLimitWindow();
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .header("X-RateLimit-Limit", String.valueOf(controller.getMetadata().rateLimitRequests()))
                        .header("X-RateLimit-Remaining", "0")
                        .header("X-RateLimit-Reset", String.valueOf(window))
                        .body(Map.of("error", "Rate limit exceeded", "status", 429, "retryAfter", window));
            }

            return switch (operation) {
                case "bulkCreate" -> controller.bulkCreate(body);
                case "bulkUpdate" -> controller.bulkUpdate(body);
                case "bulkDelete" -> controller.bulkDelete(body);
                default -> ResponseEntity.internalServerError().build();
            };
        } finally {
            TenantContext.clear();
        }
    }

    private CrudOperation toCrudOperation(String op) {
        return switch (op) {
            case "bulkCreate" -> CrudOperation.CREATE;
            case "bulkUpdate" -> CrudOperation.UPDATE;
            case "bulkDelete" -> CrudOperation.DELETE;
            default -> null;
        };
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
