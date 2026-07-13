package io.github.hackermanme.flashapi.controller;

import io.github.hackermanme.flashapi.ratelimit.FlashRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

public final class FlashBulkEndpointHandler {

    private final FlashController controller;
    private final String operation;
    private final FlashRateLimiter rateLimiter;

    public FlashBulkEndpointHandler(FlashController controller, String operation, FlashRateLimiter rateLimiter) {
        this.controller = controller;
        this.operation = operation;
        this.rateLimiter = rateLimiter;
    }

    public ResponseEntity<?> handle(
            HttpServletRequest request,
            @RequestBody(required = false) Object body) {

        if (rateLimiter != null && !rateLimiter.isAllowed(controller.getMetadata(), getClientIp(request))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded", "retryAfter", controller.getMetadata().rateLimitWindow()));
        }

        return switch (operation) {
            case "bulkCreate" -> controller.bulkCreate(body);
            case "bulkUpdate" -> controller.bulkUpdate(body);
            case "bulkDelete" -> controller.bulkDelete(body);
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
}
