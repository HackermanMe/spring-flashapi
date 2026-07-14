# Webhooks

FlashAPI can notify external systems when data changes. Add `@FlashWebhook` to an entity, configure a URL, and FlashAPI sends an HTTP POST on every create, update, or delete.

---

## Quick Start

### 1. Annotate your entity

```java
@Entity
@FlashEntity
@FlashWebhook
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String status;
    private BigDecimal total;
}
```

### 2. Configure the webhook URL

**application.yml:**

```yaml
flashapi:
  webhook:
    urls:
      - https://hooks.example.com/flashapi
```

**application.properties:**

```properties
flashapi.webhook.urls[0]=https://hooks.example.com/flashapi
```

### 3. Receive events

When an `Order` is created:

```json
POST https://hooks.example.com/flashapi
Content-Type: application/json
X-FlashAPI-Event: CREATE
X-FlashAPI-Entity: Order

{
  "event": "CREATE",
  "entity": "Order",
  "entityId": "42",
  "data": {
    "id": 42,
    "status": "PENDING",
    "total": 199.99
  },
  "timestamp": "2026-07-14T15:30:00Z"
}
```

---

## @FlashWebhook Annotation

```java
@FlashWebhook(events = {"CREATE", "UPDATE", "DELETE"})
```

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `events` | String[] | `{"CREATE", "UPDATE", "DELETE"}` | Events that trigger webhooks |

### Selective events

```java
// Only fire on create and delete, not updates
@FlashWebhook(events = {"CREATE", "DELETE"})
public class Payment { ... }
```

---

## Configuration

### application.yml

```yaml
flashapi:
  webhook:
    urls:                              # List of webhook receiver URLs
      - https://hooks.example.com/a
      - https://hooks.internal.io/b
    max-retries: 3                     # Retry failed deliveries (default: 3)
    timeout-seconds: 10                # HTTP timeout per attempt (default: 10)
```

### application.properties

```properties
flashapi.webhook.urls[0]=https://hooks.example.com/a
flashapi.webhook.urls[1]=https://hooks.internal.io/b
flashapi.webhook.max-retries=3
flashapi.webhook.timeout-seconds=10
```

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `flashapi.webhook.urls` | List<String> | `[]` (empty = disabled) | URLs to POST events to |
| `flashapi.webhook.max-retries` | int | `3` | Max retry attempts on failure |
| `flashapi.webhook.timeout-seconds` | int | `10` | HTTP connect/read timeout per attempt |

---

## Payload Format

```json
{
  "event": "CREATE|UPDATE|DELETE",
  "entity": "EntityClassName",
  "entityId": "primaryKeyValue",
  "data": { ... },
  "timestamp": "ISO-8601 instant"
}
```

### Fields

| Field | Type | Description |
|-------|------|-------------|
| `event` | String | `CREATE`, `UPDATE`, or `DELETE` |
| `entity` | String | Simple class name of the entity |
| `entityId` | String | Primary key as string (any type) |
| `data` | Object | All visible fields (respects @FlashHidden/@FlashWriteOnly) |
| `timestamp` | String | ISO-8601 UTC timestamp of the event |

### HTTP Headers

| Header | Value |
|--------|-------|
| `Content-Type` | `application/json` |
| `X-FlashAPI-Event` | Event type (CREATE, UPDATE, DELETE) |
| `X-FlashAPI-Entity` | Entity name |

---

## Delivery Semantics

### Asynchronous

Webhooks fire **after** the database transaction commits, in a separate virtual thread. The API response is never delayed by webhook delivery.

### At-least-once

FlashAPI retries on failure with exponential backoff. A successful delivery (HTTP 2xx) stops retries. This means receivers may get duplicate events — design your handlers to be idempotent.

### Retry schedule (default: 3 retries)

| Attempt | Delay |
|---------|-------|
| 1 | Immediate |
| 2 | 1 second |
| 3 | 2 seconds |
| 4 | 4 seconds |

After all retries fail, the event is logged as a warning and dropped.

### Failure handling

- HTTP 2xx → success, stop
- HTTP 4xx/5xx → retry
- Connection timeout → retry
- DNS failure → retry

---

## Multiple URLs

All configured URLs receive every event. Each URL is delivered independently:

```yaml
flashapi:
  webhook:
    urls:
      - https://analytics.internal/events
      - https://slack-bot.internal/notifications
      - https://audit-system.internal/log
```

If one URL fails, the others still receive their events.

---

## Examples

### Slack notification on order creation

```yaml
flashapi:
  webhook:
    urls:
      - https://hooks.slack.com/services/T00/B00/xxxx
```

Your Slack incoming webhook receives the JSON payload. Use a middleware (AWS Lambda, Cloudflare Worker) to format it into a Slack message if needed.

### Sync to external search index

```java
@Entity
@FlashEntity
@FlashWebhook(events = {"CREATE", "UPDATE", "DELETE"})
public class Product { ... }
```

Your search indexing service receives every change and updates Elasticsearch/Algolia/Meilisearch in near-real-time.

### Audit to external system

```java
@Entity
@FlashEntity
@FlashWebhook(events = {"UPDATE", "DELETE"})
public class SensitiveRecord { ... }
```

Only fire on modifications, not creation.

---

## Custom WebhookDispatcher

Replace the default dispatcher to add signing, filtering, or queue-based delivery:

```java
@Component
public class SignedWebhookDispatcher extends WebhookDispatcher {

    public SignedWebhookDispatcher() {
        super(List.of(), 0, 10); // URLs managed internally
    }

    @Override
    public void dispatch(EntityMetadata meta, String event, Object entity) {
        // Add HMAC signature, push to SQS, etc.
    }
}
```

FlashAPI detects your bean via `@ConditionalOnMissingBean`.

---

## Combining with Other Features

### With Multi-Tenancy

Webhook payloads include the `tenantId` field in `data` (if visible). Your receiver can route events by tenant.

### With Soft Delete

A soft delete fires a `DELETE` event. A restore does NOT fire a webhook (it's mapped to UPDATE internally, and `@FlashWebhook` checks the annotation's `events` list).

### With @FlashSecured

Webhooks fire regardless of who triggered the operation — they're a server-side concern. Authorization only affects whether the request reaches the business logic.

---

## FAQ

**Q: What happens if no URLs are configured?**

Nothing. The dispatcher is a no-op when `urls` is empty. `@FlashWebhook` has no effect without at least one URL.

**Q: Can I configure different URLs per entity?**

Not via properties. All entities share the same URL list. Use the `X-FlashAPI-Entity` header in your receiver to route by entity, or implement a custom `WebhookDispatcher`.

**Q: Are webhooks transactional?**

No. Webhooks fire after the transaction commits. If the transaction rolls back, no webhook is sent. However, if the webhook delivery fails, the database change is NOT rolled back — webhooks are fire-and-forget.

**Q: Can I verify webhook authenticity?**

Not built-in. Implement a custom `WebhookDispatcher` that adds an HMAC signature header. Your receiver verifies the signature.

**Q: What about ordering?**

Events for the same entity are dispatched in order within a single JVM. But with multiple instances, ordering is not guaranteed across nodes. Use the `timestamp` field for ordering at the receiver.

**Q: Performance impact?**

Minimal. Webhooks use virtual threads and never block the request. The only overhead is serializing the entity to JSON (nanoseconds for typical entities).

**Q: Can I disable webhooks temporarily?**

Remove all URLs from the configuration:

```yaml
flashapi:
  webhook:
    urls: []
```

Or set the list to empty in environment variables. No code change needed.
