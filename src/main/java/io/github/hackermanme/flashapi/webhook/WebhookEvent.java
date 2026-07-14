package io.github.hackermanme.flashapi.webhook;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable payload sent to webhook endpoints.
 */
public record WebhookEvent(
        String event,
        String entity,
        String entityId,
        Map<String, Object> data,
        Instant timestamp
) {}
