# Multi-Tenancy

FlashAPI supports automatic data isolation between tenants. Each tenant sees only its own data — no cross-tenant leakage, no manual filtering required.

---

## Quick Start

### 1. Add a tenant field to your entity

```java
@Entity
@FlashEntity
@FlashMultiTenant(field = "tenantId")
public class Document {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String content;
    private String tenantId;  // FlashAPI manages this field
}
```

### 2. Send the tenant header with each request

```bash
# Tenant A creates a document
curl -X POST http://localhost:8080/api/documents \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-A" \
  -d '{"title": "Secret Plan", "content": "..."}'

# Tenant B cannot see it
curl http://localhost:8080/api/documents \
  -H "X-Tenant-Id: tenant-B"
# → {"data": [], "meta": {"totalElements": 0, ...}}
```

That's it. FlashAPI automatically:
- Filters all LIST/READ queries by tenant
- Injects the tenant ID on CREATE (overrides any user-supplied value)
- Returns 404 for cross-tenant GET/PUT/DELETE (no existence leak)
- Returns 400 if no tenant context is provided

---

## @FlashMultiTenant Annotation

```java
@FlashMultiTenant(field = "tenantId")
```

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `field` | String | `"tenantId"` | Java field name storing the tenant identifier |

The field must:
- Exist on the entity class
- Be of type `String`
- Be included in `creatableFields` (not `@FlashReadOnly` or `@FlashHidden`)

FlashAPI validates this at startup — fails fast if the field is missing.

---

## Configuration

### application.yml

```yaml
flashapi:
  tenant:
    header-name: X-Tenant-Id    # HTTP header for tenant resolution (default)
```

### application.properties

```properties
flashapi.tenant.header-name=X-Tenant-Id
```

---

## Tenant Resolution

### Default: HTTP header

By default, FlashAPI reads the tenant from the `X-Tenant-Id` header. Change the header name via configuration:

```yaml
flashapi:
  tenant:
    header-name: X-Organization-Id
```

### Custom: implement TenantResolver

For complex scenarios (JWT claims, subdomains, database lookup), implement `TenantResolver`:

```java
@Component
public class JwtTenantResolver implements TenantResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getClaimAsString("org_id");
        }
        return null;
    }
}
```

FlashAPI detects your bean via `@ConditionalOnMissingBean` and uses it instead of the default.

### Subdomain-based resolution

```java
@Component
public class SubdomainTenantResolver implements TenantResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        String host = request.getServerName();
        int dot = host.indexOf('.');
        return dot > 0 ? host.substring(0, dot) : null;
    }
}
```

---

## Behavior Details

### CREATE

The tenant field is **always overwritten** with the current tenant from `TenantContext`:

```bash
# Even if you send tenantId in the body, it's ignored
curl -X POST http://localhost:8080/api/documents \
  -H "X-Tenant-Id: tenant-A" \
  -H "Content-Type: application/json" \
  -d '{"title": "Test", "tenantId": "tenant-B"}'

# Result: tenantId = "tenant-A" (from header, not body)
```

### LIST

All queries are automatically filtered:

```sql
-- What FlashAPI generates for tenant-A:
SELECT * FROM document WHERE tenant_id = 'tenant-A' AND ...
```

### GET by ID

If the entity exists but belongs to a different tenant → `404 Not Found` (not 403).

**Why 404 and not 403?** Returning 403 would confirm the resource exists, leaking information. 404 reveals nothing about other tenants' data.

### UPDATE / DELETE

Same as GET: if the entity doesn't belong to the current tenant → `404 Not Found`.

### Export / Bulk

Tenant filtering applies to all operations including export and bulk:

```bash
# Only exports tenant-A's data
curl "http://localhost:8080/api/documents/export?format=csv" \
  -H "X-Tenant-Id: tenant-A"
```

---

## Missing Tenant Context

If a request reaches a `@FlashMultiTenant` entity without a tenant header:

```json
HTTP 400 Bad Request
{"error": "Tenant context required"}
```

Non-multi-tenant entities are unaffected — they don't require the header.

---

## Combining with Other Features

### With @FlashSecured

```java
@Entity
@FlashEntity
@FlashSecured(roles = "USER")
@FlashMultiTenant(field = "orgId")
public class Project { ... }
```

Pipeline order: **Tenant Resolution → Security Check → Rate Limit → Business Logic**

A request without a tenant header gets `400` before security is checked.

### With Soft Delete

```java
@Entity
@FlashEntity(softDelete = true)
@FlashMultiTenant(field = "tenantId")
public class Task { ... }
```

Soft-deleted items are hidden per tenant. Restore only works for the owning tenant.

### With Audit

Audit entries are NOT tenant-filtered — they record the global history. If you need tenant-scoped audit, use the `tenantId` in the entity to filter the history endpoint results via a custom service.

---

## Examples

### SaaS application with organizations

```java
@Entity
@FlashEntity
@FlashMultiTenant(field = "organizationId")
@FlashSecured(roles = "MEMBER")
public class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String organizationId;
    private BigDecimal amount;
    private String status;
    private LocalDate dueDate;
}
```

```yaml
flashapi:
  tenant:
    header-name: X-Organization-Id
```

### Multi-tenant with JWT

```java
@Component
public class JwtTenantResolver implements TenantResolver {
    @Override
    public String resolve(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getClaimAsString("tenant_id");
        }
        return request.getHeader("X-Tenant-Id");
    }
}
```

### Per-user data isolation

Tenant doesn't have to mean "organization" — it can be a user:

```java
@Entity
@FlashEntity
@FlashMultiTenant(field = "userId")
public class Note {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;
    private String title;
    private String body;
}
```

With a resolver that extracts the user ID from the auth token, each user only sees their own notes.

---

## FAQ

**Q: Is the tenant field hidden from responses?**

No. The tenant field is visible in responses by default. If you want to hide it, add `@FlashHidden`:

```java
@FlashHidden
private String tenantId;
```

But note: if you hide it AND the field is the tenant field, FlashAPI still sets it on create and filters by it — the annotation only affects serialization.

**Q: Can I have multiple tenant fields?**

No. One `@FlashMultiTenant` per entity with one field. For composite tenant keys, use a single concatenated string (e.g., `"org:team"`) and resolve it in a custom `TenantResolver`.

**Q: What if I want some entities tenant-scoped and others shared?**

Only annotate the entities that need isolation. Entities without `@FlashMultiTenant` are global — accessible regardless of the tenant header.

**Q: Does multi-tenancy work with custom services (Level 2)?**

The `TenantContext` ThreadLocal is set before your custom service is called. You can read `TenantContext.get()` in your service. However, FlashAPI does NOT automatically filter your custom queries — that's your responsibility at Level 2.

**Q: Performance impact?**

Minimal. The tenant filter adds one `WHERE` clause to every query. For high-volume tables, add a database index on the tenant field:

```sql
CREATE INDEX idx_document_tenant ON document(tenant_id);
```

**Q: Can a super-admin see all tenants' data?**

Not through FlashAPI's generated endpoints. The tenant context is always enforced. For admin dashboards, create a custom controller (Level 3) that bypasses FlashAPI, or implement a `TenantResolver` that returns `null` for admin users (which skips tenant filtering for non-multi-tenant entities but returns 400 for multi-tenant ones — so you'd need a separate approach).

**Q: What about database-level multi-tenancy (separate schemas)?**

`@FlashMultiTenant` is row-level isolation (discriminator column). For schema-level or database-level isolation, use Spring's `AbstractRoutingDataSource` and exclude FlashAPI from tenant handling — let the datasource routing handle it.
