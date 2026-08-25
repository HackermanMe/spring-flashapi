# Feature Guard (Record Limits)

FlashAPI can enforce maximum record counts per entity, useful for SaaS plan enforcement, free-tier limits, or quota management.

## Quick Start

```java
@Entity
@FlashEntity
@FeatureGuard(max = 100)
public class Project {
    // Maximum 100 projects can be created
}
```

Any `POST` (create) or `POST /bulk` (bulk create) that would exceed the limit returns HTTP `403 Forbidden`.

## How It Works

- The guard intercepts CREATE operations **before** they execute.
- It counts existing records in the entity table using a `SELECT COUNT(*)` query.
- If the current count + items being created exceeds the limit, the operation is rejected.
- READ, UPDATE, and DELETE operations are never guarded.

## Resolution Priority

Limits are resolved in this order:

1. **`PlanLimitResolver` bean** (dynamic, per-request) -- highest priority
2. **`@FeatureGuard(max)` annotation** (static, per-entity)
3. **No limit** -- if neither is configured, no restriction applies

## Dynamic Limits with PlanLimitResolver

For SaaS scenarios where limits depend on the tenant's plan:

```java
@Bean
public PlanLimitResolver planLimits(SubscriptionService subscriptions) {
    return (entityName, request) -> {
        String tenantId = request.getHeader("X-Tenant-Id");
        Plan plan = subscriptions.getPlan(tenantId);
        return switch (plan) {
            case FREE -> 50;
            case PRO -> 5_000;
            case ENTERPRISE -> -1; // unlimited
        };
    };
}
```

The resolver receives the entity name and the current HTTP request, allowing full flexibility.

Return `-1` to indicate no limit (unlimited).

## Error Response

When a limit is exceeded, the response is:

```http
HTTP/1.1 403 Forbidden
Content-Type: application/json

{
  "status": 403,
  "error": "Record limit exceeded for Project: max 100 allowed",
  "entity": "Project",
  "limit": 100
}
```

## Bulk Operations

Bulk create operations check the limit **before** inserting. The guard verifies that `currentCount + batchSize <= limit`. If the batch would exceed the limit, the entire batch is rejected (no partial insert).

## Configuration

No additional configuration properties are needed. The feature activates automatically when:
- `@FeatureGuard` is present on an entity class, or
- A `PlanLimitResolver` bean is registered in the application context

## Notes

- The count query runs within the current transaction context.
- Multi-tenant setups: if you use `TenantResolver`, the count query respects tenant filters (Hibernate filters or equivalent).
- Performance: the `COUNT(*)` query is lightweight for most table sizes. For very large tables (millions of rows), consider using `PlanLimitResolver` with a cached counter instead.
