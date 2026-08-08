package io.github.hackermanme.flashapi.health;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Health check endpoints for production monitoring.
 * Provides /health (liveness) and /ready (readiness) for Kubernetes and load balancers.
 */
@RestController
@ConditionalOnProperty(name = "flashapi.health.enabled", havingValue = "true", matchIfMissing = true)
public class HealthCheckController {

    private final long startTime = System.currentTimeMillis();
    private volatile boolean ready = false;
    private final Map<String, Supplier<Boolean>> checks = new ConcurrentHashMap<>();
    private final DataSource dataSource;

    public HealthCheckController(DataSource dataSource) {
        this.dataSource = dataSource;
        registerDefaultChecks();
    }

    private void registerDefaultChecks() {
        // Database connectivity check
        checks.put("database", () -> {
            try (Connection conn = dataSource.getConnection()) {
                return conn.isValid(1); // 1 second timeout
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * Mark the application as ready to receive traffic.
     * Called automatically after context refresh.
     */
    public void markReady() {
        this.ready = true;
    }

    /**
     * Liveness probe — is the application running?
     * GET /health
     */
    @GetMapping("/health")
    public Map<String, Object> liveness() {
        long uptime = (System.currentTimeMillis() - startTime) / 1000;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("uptime", uptime);
        return response;
    }

    /**
     * Readiness probe — is the application ready to serve traffic?
     * GET /ready
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        if (!ready) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "not_ready");
            response.put("reason", "Application still initializing");
            return ResponseEntity.status(503).body(response);
        }

        java.util.List<String> failedChecks = new java.util.ArrayList<>();
        for (Map.Entry<String, Supplier<Boolean>> entry : checks.entrySet()) {
            try {
                if (!entry.getValue().get()) {
                    failedChecks.add(entry.getKey());
                }
            } catch (Exception e) {
                failedChecks.add(entry.getKey() + ": " + e.getMessage());
            }
        }

        if (!failedChecks.isEmpty()) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "not_ready");
            response.put("failed_checks", failedChecks);
            return ResponseEntity.status(503).body(response);
        }

        long uptime = (System.currentTimeMillis() - startTime) / 1000;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ready");
        response.put("uptime", uptime);
        response.put("checks_passed", checks.size());
        return ResponseEntity.ok(response);
    }
}
