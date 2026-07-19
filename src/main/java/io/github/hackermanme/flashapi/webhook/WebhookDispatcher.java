package io.github.hackermanme.flashapi.webhook;

import io.github.hackermanme.flashapi.dashboard.MetricsCollector;
import io.github.hackermanme.flashapi.registry.EntityMetadata;
import io.github.hackermanme.flashapi.registry.FieldMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Dispatches webhook events asynchronously to configured URLs.
 * Uses a virtual thread executor for non-blocking I/O.
 * Retries failed deliveries up to the configured maximum.
 */
public final class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final List<String> urls;
    private final int maxRetries;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private volatile MetricsCollector metricsCollector;

    public WebhookDispatcher(List<String> urls, int maxRetries, int timeoutSeconds) {
        this.urls = urls != null ? List.copyOf(urls) : List.of();
        this.maxRetries = maxRetries;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public void setMetricsCollector(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    public void dispatch(EntityMetadata meta, String event, Object entity) {
        if (urls.isEmpty()) return;
        if (!shouldFire(meta, event)) return;

        Map<String, Object> data = serializeEntity(meta, entity);
        String entityId = extractId(meta, entity);

        WebhookEvent payload = new WebhookEvent(
                event, meta.entityName(), entityId, data, Instant.now());

        for (String url : urls) {
            executor.submit(() -> deliver(url, payload));
        }
    }

    private boolean shouldFire(EntityMetadata meta, String event) {
        var annotation = meta.entityClass()
                .getAnnotation(io.github.hackermanme.flashapi.annotation.FlashWebhook.class);
        if (annotation == null) return false;
        return Arrays.asList(annotation.events()).contains(event);
    }

    private void deliver(String url, WebhookEvent payload) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                String body = toJson(payload);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(timeout)
                        .header("Content-Type", "application/json")
                        .header("X-FlashAPI-Event", payload.event())
                        .header("X-FlashAPI-Entity", payload.entity())
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.debug("Webhook delivered: {} {} → {} ({})",
                            payload.event(), payload.entity(), url, response.statusCode());
                    if (metricsCollector != null) metricsCollector.recordWebhookSent();
                    return;
                }

                log.warn("Webhook failed: {} {} → {} (HTTP {}), attempt {}/{}",
                        payload.event(), payload.entity(), url, response.statusCode(),
                        attempt + 1, maxRetries + 1);
            } catch (Exception e) {
                log.warn("Webhook error: {} {} → {}, attempt {}/{}: {}",
                        payload.event(), payload.entity(), url,
                        attempt + 1, maxRetries + 1, e.getMessage());
            }

            if (attempt < maxRetries) {
                if (metricsCollector != null) metricsCollector.recordWebhookRetry();
                try {
                    Thread.sleep(Duration.ofSeconds((long) Math.pow(2, attempt)));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        if (metricsCollector != null) metricsCollector.recordWebhookFailed();
    }

    private Map<String, Object> serializeEntity(EntityMetadata meta, Object entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (FieldMetadata field : meta.visibleFields()) {
            try {
                map.put(field.name(), field.javaField().get(entity));
            } catch (IllegalAccessException e) {
                map.put(field.name(), null);
            }
        }
        return map;
    }

    private String extractId(EntityMetadata meta, Object entity) {
        try {
            Object id = meta.primaryKeyField().javaField().get(entity);
            return id != null ? id.toString() : "null";
        } catch (IllegalAccessException e) {
            return "unknown";
        }
    }

    private String toJson(WebhookEvent payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"event\":\"").append(escape(payload.event())).append("\"");
        sb.append(",\"entity\":\"").append(escape(payload.entity())).append("\"");
        sb.append(",\"entityId\":\"").append(escape(payload.entityId())).append("\"");
        sb.append(",\"timestamp\":\"").append(payload.timestamp().toString()).append("\"");
        sb.append(",\"data\":{");
        sb.append(payload.data().entrySet().stream()
                .map(e -> "\"" + escape(e.getKey()) + "\":" + valueToJson(e.getValue()))
                .collect(Collectors.joining(",")));
        sb.append("}}");
        return sb.toString();
    }

    private String valueToJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return "\"" + escape(value.toString()) + "\"";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
