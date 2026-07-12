# Configuration

FlashAPI is configured via standard Spring Boot properties in `application.yml` or `application.properties`.

## All Properties

```yaml
flashapi:
  base-path: /api
  default-page-size: 20
  max-page-size: 100
  audit:
    enabled: true
    table-name: flash_audit_entry
  soft-delete:
    column-name: deletedAt
```

## Property Reference

### Core

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `flashapi.base-path` | String | `/api` | URL prefix for all generated endpoints |
| `flashapi.default-page-size` | int | `20` | Default number of items per page |
| `flashapi.max-page-size` | int | `100` | Maximum allowed page size (caps the `?size=` param) |

### Audit

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `flashapi.audit.enabled` | boolean | `true` | Global toggle for audit trail |
| `flashapi.audit.table-name` | String | `flash_audit_entry` | JPA table name for audit entries |

### Soft Delete

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `flashapi.soft-delete.column-name` | String | `deletedAt` | Field name used for soft delete timestamps |

## Overriding per entity

Properties provide global defaults. Entity-level annotations override them:

- `@FlashEntity(softDelete = true)` enables soft delete for one entity even if it's not the global default
- `@FlashAudit(enabled = false)` disables audit for one entity even if globally enabled

## Environment-specific configuration

Use Spring profiles for different environments:

```yaml
# application.yml (shared)
flashapi:
  base-path: /api
  audit:
    enabled: true

---
# application-dev.yml
flashapi:
  max-page-size: 1000  # more permissive in dev

---
# application-prod.yml
flashapi:
  max-page-size: 50    # stricter in production
```

## Programmatic configuration

All FlashAPI beans are created with `@ConditionalOnMissingBean`. You can replace any of them:

```java
@Configuration
public class MyFlashConfig {

    @Bean
    public GenericCrudService flashCrudService(EntityManager em,
                                               AuditService audit,
                                               SoftDeleteHandler softDelete) {
        // Return your custom implementation
        return new MyCustomCrudService(em, audit, softDelete);
    }
}
```

FlashAPI detects your bean and backs off from creating its own.
