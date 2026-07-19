# Dashboard

FlashAPI includes a built-in monitoring dashboard that provides real-time visibility into your API operations. No external tools required — just enable it and open the URL.

---

## Quick Start

### 1. Enable the dashboard

**application.yml:**

```yaml
flashapi:
  dashboard:
    enabled: true
```

**application.properties:**

```properties
flashapi.dashboard.enabled=true
```

### 2. Open the dashboard

Navigate to: [http://localhost:8080/api/dashboard](http://localhost:8080/api/dashboard)

That's it. The dashboard auto-discovers all registered entities, their features (webhooks, audit, soft-delete, rate-limit, multi-tenant), and starts collecting metrics immediately.

---

## Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `flashapi.dashboard.enabled` | boolean | `false` | Enable/disable the dashboard |
| `flashapi.dashboard.path` | String | `/api/dashboard` | URL path for the dashboard |
| `flashapi.dashboard.role` | String | `ADMIN` | Required role when Spring Security is present |

### application.yml

```yaml
flashapi:
  dashboard:
    enabled: true
    path: /api/dashboard
    role: ADMIN
```

---

## Security

The dashboard adapts to your security setup:

### Without Spring Security (development)

Dashboard is **accessible without authentication**. Convenient for local development.

### With Spring Security (production)

Dashboard requires the configured role. By default, only users with `ROLE_ADMIN` authority can access it.

```properties
# Change the required role
flashapi.dashboard.role=SUPER_ADMIN
```

The check looks for either `ROLE_ADMIN` or `ADMIN` in the user's authorities — both conventions work.

If a non-authorized user tries to access the dashboard:
- HTML page → `403 Access Denied`
- JSON endpoint → `{"error": "Access denied. Required role: ADMIN"}`

---

## Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/dashboard` | HTML dashboard (auto-refreshes every 5s) |
| `GET /api/dashboard/metrics.json` | Raw metrics as JSON |

---

## Metrics Collected

### Per Entity
- Total operations count
- Operations by type (CREATE, READ, UPDATE, DELETE, SEARCH, EXPORT, BULK)
- Feature flags: soft-delete, webhook, audit, rate-limit, multi-tenant

### Global Totals
- Total operations across all entities
- Breakdown by operation type
- Server uptime

### Webhooks
- Total sent successfully
- Total failed (after all retries exhausted)
- Total retries attempted
- Configured target URLs

### Recent Activity
- Last 100 operations with timestamp, operation type, entity name, entity ID, and status
- Displayed in real-time on the dashboard

---

## JSON Response Format

`GET /api/dashboard/metrics.json` returns:

```json
{
  "generatedAt": "2026-07-19T15:30:00Z",
  "uptimeSeconds": 3600,
  "entities": {
    "Eleve": {
      "name": "Eleve",
      "count": 342,
      "softDelete": true,
      "auditEnabled": true,
      "webhookEnabled": true,
      "rateLimited": false,
      "multiTenant": false,
      "operations": {
        "CREATE": 120,
        "READ": 180,
        "UPDATE": 30,
        "DELETE": 12
      }
    }
  },
  "totals": {
    "creates": 342,
    "reads": 1204,
    "updates": 210,
    "deletes": 67,
    "searches": 185,
    "exports": 12,
    "bulkOps": 5,
    "total": 2025
  },
  "webhooks": {
    "sent": 518,
    "failed": 3,
    "retries": 12,
    "targetUrls": ["http://localhost:9090/webhooks"]
  },
  "recentEvents": [
    {
      "timestamp": "2026-07-19T15:29:58Z",
      "operation": "CREATE",
      "entity": "Eleve",
      "entityId": "42",
      "status": "OK"
    }
  ]
}
```

---

## Dashboard UI

The built-in HTML dashboard shows:

- **Server status** — uptime, auto-refresh indicator
- **Summary cards** — total operations, entities registered, webhook stats, reads/searches
- **Entities table** — each entity with operation count and feature badges (soft-delete, webhook, audit, rate-limit, multi-tenant)
- **Recent activity feed** — live stream of operations with color-coded operation types

The UI auto-refreshes every 5 seconds. No manual reload needed.

---

## Auto-Discovery

The dashboard automatically detects:

- All entities annotated with `@FlashEntity`
- Which entities have `@FlashWebhook` → shows "webhook" badge
- Which entities have `auditEnabled = true` → shows "audit" badge
- Which entities have `softDelete = true` → shows "soft-delete" badge
- Which entities have rate limiting → shows "rate-limit" badge
- Which entities are multi-tenant → shows "multi-tenant" badge

When you add `@FlashWebhook` to a new entity and restart, the dashboard picks it up automatically. No dashboard configuration change needed.

---

## Performance Impact

Minimal. The metrics collector uses:
- `ConcurrentHashMap` with `AtomicLong` counters — no locks, no contention
- `ConcurrentLinkedDeque` for recent events — bounded to 100 entries
- Metrics are computed lazily only when the dashboard is accessed

The collector adds ~1 nanosecond overhead per operation (an atomic increment). It does not affect API response times.

---

## Custom Dashboard Path

```yaml
flashapi:
  dashboard:
    path: /admin/monitoring
```

The dashboard is now at `http://localhost:8080/admin/monitoring`.

---

## Disabling in Production

If you prefer external monitoring tools (Grafana, Datadog) in production:

```yaml
# application-prod.yml
flashapi:
  dashboard:
    enabled: false
```

Or keep it enabled but secured — the role-based access ensures only admins see it.

---

## FAQ

**Q: Does the dashboard persist metrics across restarts?**

No. Metrics are in-memory only. They reset on application restart. For persistent metrics, use Spring Boot Actuator + Prometheus + Grafana.

**Q: Can I use the JSON endpoint for my own monitoring?**

Yes. `GET /api/dashboard/metrics.json` returns structured data you can poll from any tool: Grafana, custom scripts, health checks, etc.

**Q: Does the dashboard work with multiple instances?**

Each instance has its own metrics. The dashboard shows metrics for the instance you're connected to. For aggregated metrics across instances, use a centralized monitoring solution.

**Q: Can I embed the dashboard in an iframe?**

Yes. The `Access-Control-Allow-Origin: *` header on the JSON endpoint allows cross-origin requests. The HTML page can be embedded in any iframe.

**Q: What happens if I add @FlashWebhook to an entity later?**

Restart the application. The dashboard auto-discovers the new annotation and shows the "webhook" badge. Metrics start counting from zero for the new feature.
