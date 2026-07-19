package io.github.hackermanme.flashapi.dashboard;

import io.github.hackermanme.flashapi.annotation.FlashWebhook;
import io.github.hackermanme.flashapi.registry.EntityMetadata;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics collector.
 * Records every CRUD/webhook operation and exposes snapshots for the dashboard.
 */
public final class MetricsCollector {

    private static final int MAX_RECENT_EVENTS = 100;

    private final Instant startedAt = Instant.now();
    private final List<EntityMetadata> registry;
    private final List<String> webhookUrls;

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicLong>> entityOps = new ConcurrentHashMap<>();
    private final AtomicLong webhooksSent = new AtomicLong();
    private final AtomicLong webhooksFailed = new AtomicLong();
    private final AtomicLong webhooksRetries = new AtomicLong();
    private final ConcurrentLinkedDeque<FlashMetrics.RecentEvent> recentEvents = new ConcurrentLinkedDeque<>();

    public MetricsCollector(List<EntityMetadata> registry, List<String> webhookUrls) {
        this.registry = registry;
        this.webhookUrls = webhookUrls != null ? webhookUrls : List.of();
        for (EntityMetadata meta : registry) {
            entityOps.put(meta.entityName(), new ConcurrentHashMap<>());
        }
    }

    public void recordOperation(String entityName, String operation) {
        entityOps.computeIfAbsent(entityName, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(operation, k -> new AtomicLong())
                .incrementAndGet();

        addRecentEvent(entityName, operation, null, "OK");
    }

    public void recordOperation(String entityName, String operation, String entityId) {
        entityOps.computeIfAbsent(entityName, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(operation, k -> new AtomicLong())
                .incrementAndGet();

        addRecentEvent(entityName, operation, entityId, "OK");
    }

    public void recordWebhookSent() {
        webhooksSent.incrementAndGet();
    }

    public void recordWebhookFailed() {
        webhooksFailed.incrementAndGet();
    }

    public void recordWebhookRetry() {
        webhooksRetries.incrementAndGet();
    }

    public FlashMetrics snapshot() {
        Instant now = Instant.now();
        long uptime = now.getEpochSecond() - startedAt.getEpochSecond();

        Map<String, FlashMetrics.EntityStats> entityStats = new LinkedHashMap<>();
        long totalCreates = 0, totalReads = 0, totalUpdates = 0, totalDeletes = 0;
        long totalSearches = 0, totalExports = 0, totalBulk = 0;

        for (EntityMetadata meta : registry) {
            var ops = entityOps.getOrDefault(meta.entityName(), new ConcurrentHashMap<>());
            Map<String, Long> opsMap = new LinkedHashMap<>();
            ops.forEach((k, v) -> opsMap.put(k, v.get()));

            boolean hasWebhook = meta.entityClass().isAnnotationPresent(FlashWebhook.class);

            long entityCount = opsMap.values().stream().mapToLong(Long::longValue).sum();

            entityStats.put(meta.entityName(), new FlashMetrics.EntityStats(
                    meta.entityName(),
                    entityCount,
                    meta.softDelete(),
                    meta.auditEnabled(),
                    hasWebhook,
                    meta.rateLimitEnabled(),
                    meta.isMultiTenant(),
                    opsMap
            ));

            totalCreates += opsMap.getOrDefault("CREATE", 0L);
            totalReads += opsMap.getOrDefault("READ", 0L);
            totalUpdates += opsMap.getOrDefault("UPDATE", 0L);
            totalDeletes += opsMap.getOrDefault("DELETE", 0L);
            totalSearches += opsMap.getOrDefault("SEARCH", 0L);
            totalExports += opsMap.getOrDefault("EXPORT", 0L);
            totalBulk += opsMap.getOrDefault("BULK", 0L);
        }

        var totals = new FlashMetrics.OperationTotals(
                totalCreates, totalReads, totalUpdates, totalDeletes,
                totalSearches, totalExports, totalBulk);

        var webhookStats = new FlashMetrics.WebhookStats(
                webhooksSent.get(), webhooksFailed.get(), webhooksRetries.get(), webhookUrls);

        List<FlashMetrics.RecentEvent> recent = new ArrayList<>(recentEvents);

        return new FlashMetrics(now, uptime, entityStats, totals, webhookStats, recent);
    }

    private void addRecentEvent(String entity, String operation, String entityId, String status) {
        recentEvents.addFirst(new FlashMetrics.RecentEvent(
                Instant.now(), operation, entity, entityId, status));
        while (recentEvents.size() > MAX_RECENT_EVENTS) {
            recentEvents.removeLast();
        }
    }
}
