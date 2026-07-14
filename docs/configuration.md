# Configuration

FlashAPI is configured via standard Spring Boot properties. You can use **either** `application.yml` **or** `application.properties` — both work identically. Use whichever your project already has.

> **Important:** You do NOT need both files. If your project uses `application.properties`, configure FlashAPI there. If it uses `application.yml`, configure it there. FlashAPI reads from the same Spring `Environment` as any other Spring Boot feature.

---

## All Properties (both formats)

### YAML format (`application.yml`)

```yaml
flashapi:
  base-path: /api
  default-page-size: 20
  max-page-size: 100
  audit:
    enabled: true
    table-name: flash_audit_log
  soft-delete:
    column-name: deleted_at
  export:
    max-rows: 0
    reports-path: flashapi/reports
  bulk:
    max-items: 100
  relations:
    max-depth: 1
  openapi:
    enabled: true
    title: FlashAPI
    version: 1.0.0
    description: Auto-generated REST API documentation
    docs-path: /api/docs
```

### Properties format (`application.properties`)

```properties
flashapi.base-path=/api
flashapi.default-page-size=20
flashapi.max-page-size=100
flashapi.audit.enabled=true
flashapi.audit.table-name=flash_audit_log
flashapi.soft-delete.column-name=deleted_at
flashapi.export.max-rows=0
flashapi.export.reports-path=flashapi/reports
flashapi.bulk.max-items=100
flashapi.relations.max-depth=1
flashapi.openapi.enabled=true
flashapi.openapi.title=FlashAPI
flashapi.openapi.version=1.0.0
flashapi.openapi.description=Auto-generated REST API documentation
flashapi.openapi.docs-path=/api/docs
```

---

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
| `flashapi.audit.table-name` | String | `flash_audit_log` | JPA table name for audit entries |

### Soft Delete

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `flashapi.soft-delete.column-name` | String | `deleted_at` | Field name used for soft delete timestamps |

### Export

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `flashapi.export.max-rows` | int | `0` | Maximum rows per export (`0` = unlimited). Truncates and logs a warning when exceeded. |
| `flashapi.export.reports-path` | String | `flashapi/reports` | Classpath path where FlashAPI looks for custom `.jrxml` PDF templates |

### Bulk

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `flashapi.bulk.max-items` | int | `100` | Maximum items per bulk request. Returns HTTP 413 if exceeded. |

### Relations

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `flashapi.relations.max-depth` | int | `1` | Maximum depth for `?expand` recursion. `1` = direct relations only. |

### OpenAPI

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `flashapi.openapi.enabled` | boolean | `true` | Enable/disable auto-generated API documentation |
| `flashapi.openapi.title` | String | `FlashAPI` | Title shown in Swagger UI header |
| `flashapi.openapi.version` | String | `1.0.0` | API version shown in the spec |
| `flashapi.openapi.description` | String | `Auto-generated REST API documentation` | Description in the OpenAPI info block |
| `flashapi.openapi.docs-path` | String | `/api/docs` | URL path where Swagger UI and spec are served |

---

## Overriding per entity

Properties provide global defaults. Entity-level annotations override them:

- `@FlashEntity(softDelete = true)` enables soft delete for one entity even if it's not the global default
- `@FlashAudit(enabled = false)` disables audit for one entity even if globally enabled
- `@FlashEntity(cache = true)` enables caching for one entity without changing the global config
- `@FlashEntity(rateLimit = true)` enables rate limiting for one entity

---

## Environment-specific configuration

Use Spring profiles for different environments.

**YAML (multi-document):**

```yaml
# application.yml (shared defaults)
flashapi:
  base-path: /api
  audit:
    enabled: true

---
spring:
  config:
    activate:
      on-profile: dev

flashapi:
  max-page-size: 1000

---
spring:
  config:
    activate:
      on-profile: prod

flashapi:
  max-page-size: 50
```

**Properties (profile-specific files):**

```properties
# application.properties (shared defaults)
flashapi.base-path=/api
flashapi.audit.enabled=true
```

```properties
# application-dev.properties
flashapi.max-page-size=1000
```

```properties
# application-prod.properties
flashapi.max-page-size=50
```

---

## Programmatic configuration

All FlashAPI beans are created with `@ConditionalOnMissingBean`. You can replace any of them by defining your own bean with the same type:

```java
@Configuration
public class MyFlashConfig {

    @Bean
    public GenericCrudService flashCrudService(EntityManager em,
                                               AuditService audit,
                                               SoftDeleteHandler softDelete) {
        return new MyCustomCrudService(em, audit, softDelete);
    }
}
```

FlashAPI detects your bean and backs off from creating its own.

### Replaceable beans

| Bean type | Purpose | Default implementation |
|-----------|---------|----------------------|
| `GenericCrudService` | Core CRUD logic | Criteria API-based |
| `AuditService` | Audit trail recording | JPA-based |
| `SoftDeleteHandler` | Soft delete logic | Reflection-based |
| `ExportHandler` | CSV/XLSX/PDF export | Streaming exporters |
| `BulkHandler` | Bulk operations | Best-effort per-item |
| `RelationExpander` | Expand relations | Reflection-based |
| `FlashCacheManager` | Response caching | Spring Cache abstraction |
| `FlashRateLimiter` | Rate limiting | In-memory token bucket |
| `ServiceResolver` | Custom service detection | Convention + @FlashService |
| `FlashExceptionHandler` | Error response formatting | Standard JSON errors |

---

## Minimal vs. full configuration

### Minimal (zero config)

FlashAPI works with no configuration at all. Just add the dependency and `@EnableFlashApi`:

```java
@SpringBootApplication
@EnableFlashApi
public class MyApp { }
```

All defaults apply. Endpoints are at `/api/{entity}`.

### Typical production setup

**application.properties:**

```properties
flashapi.base-path=/api/v1
flashapi.default-page-size=25
flashapi.max-page-size=50
flashapi.export.max-rows=10000
flashapi.bulk.max-items=50
flashapi.openapi.title=My Service API
flashapi.openapi.version=2.1.0
```

**application.yml:**

```yaml
flashapi:
  base-path: /api/v1
  default-page-size: 25
  max-page-size: 50
  export:
    max-rows: 10000
  bulk:
    max-items: 50
  openapi:
    title: My Service API
    version: 2.1.0
```

---

## FAQ

**Q: Do I need to create a `application.yml` if I already have `application.properties`?**

No. FlashAPI reads standard Spring Boot properties. Configure it in whatever file your project uses.

**Q: Can I mix `.yml` and `.properties`?**

Yes. Spring Boot supports having both `application.yml` and `application.properties` simultaneously. Properties from both files are merged. But for simplicity, stick to one format.

**Q: What if I don't configure anything?**

Everything works with sensible defaults. The table above shows all default values.

**Q: Can I use environment variables?**

Yes. Spring Boot property binding supports environment variables:

```bash
FLASHAPI_BASE_PATH=/api/v2
FLASHAPI_DEFAULT_PAGE_SIZE=50
FLASHAPI_OPENAPI_TITLE=My API
```

The naming convention is: uppercase, dots replaced by underscores, hyphens removed.

**Q: Can I use command-line arguments?**

Yes:

```bash
java -jar myapp.jar --flashapi.base-path=/api/v2 --flashapi.max-page-size=50
```
