package io.github.hackermanme.flashapi.dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of all FlashAPI metrics at a point in time.
 */
public record FlashMetrics(
        Instant generatedAt,
        long uptimeSeconds,
        Map<String, EntityStats> entities,
        OperationTotals totals,
        WebhookStats webhooks,
        List<RecentEvent> recentEvents
) {

    public record EntityStats(
            String name,
            long count,
            boolean softDelete,
            boolean auditEnabled,
            boolean webhookEnabled,
            boolean rateLimited,
            boolean multiTenant,
            Map<String, Long> operations
    ) {}

    public record OperationTotals(
            long creates,
            long reads,
            long updates,
            long deletes,
            long searches,
            long exports,
            long bulkOps
    ) {
        public long total() {
            return creates + reads + updates + deletes + searches + exports + bulkOps;
        }
    }

    public record WebhookStats(
            long sent,
            long failed,
            long retries,
            List<String> targetUrls
    ) {}

    public record RecentEvent(
            Instant timestamp,
            String operation,
            String entity,
            String entityId,
            String status
    ) {}
}
