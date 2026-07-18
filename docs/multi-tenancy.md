# Multi-Tenancy

## The Problem

Imagine a SaaS school management app. Three schools share the same application and the same database:

- School Alpha (`alpha`)
- School Beta (`beta`)
- School Gamma (`gamma`)

All schools have students in the same `students` table. When School Alpha calls `GET /api/students`, it must see **only its own students** — never Beta's or Gamma's.

Without `@FlashMultiTenant`, you'd have to manually:
1. Add `WHERE schoolId = 'alpha'` to every query
2. Check on every PUT/DELETE that the student belongs to the requesting school
3. Force the schoolId on every CREATE

Forget just once → data leaks between schools.

**`@FlashMultiTenant` eliminates this entirely.** You declare which field identifies the owner, and FlashAPI guarantees isolation across all operations automatically.

---

## Quick Start

### 1. Add a tenant field to your entity

```java
@Entity
@FlashEntity
@FlashMultiTenant(field = "schoolId")
public class Student {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String schoolId;  // FlashAPI manages this field automatically
    private String name;
    private String grade;
}
```

### 2. Send the tenant header with each request

Every HTTP request must identify "who is asking" via a header (default: `X-Tenant-Id`):

```bash
# School Alpha creates a student
curl -X POST http://localhost:8080/api/students \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: alpha" \
  -d '{"name": "Alice", "grade": "3rd"}'

# School Beta tries to list students — sees nothing from Alpha
curl http://localhost:8080/api/students \
  -H "X-Tenant-Id: beta"
# → {"data": [], "meta": {"totalElements": 0, ...}}
```

That's it. One annotation, and FlashAPI guarantees:

| Action | What FlashAPI does |
|--------|-------------------|
| `GET /api/students` | Returns **only** students where `schoolId` matches the header |
| `POST /api/students` | Forces `schoolId` to the header value (ignores any value in the body) |
| `GET /api/students/{id}` from another school | Returns **404** (not 403 — doesn't even reveal the student exists) |
| `PUT` or `DELETE` from another school | Returns **404** — impossible to modify another school's data |
| Request without the tenant header | Returns **400** "Tenant context required" |

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

## Tenant Resolution — Where Does the Tenant Come From?

In production, no one types `X-Tenant-Id` manually. Your frontend or auth system sends it. FlashAPI supports multiple strategies:

### Default: HTTP header

The simplest approach — your frontend includes the tenant in every request:

```yaml
flashapi:
  tenant:
    header-name: X-Tenant-Id    # change this to match your header name
```

### Custom: implement TenantResolver

For real-world apps, you'll extract the tenant from a JWT, a subdomain, or a database. Create a bean that implements `TenantResolver`:

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

FlashAPI auto-detects your bean and uses it instead of the default header resolver. You don't need to configure anything else.

### Subdomain-based resolution

If your app uses subdomains like `alpha.myapp.com`, `beta.myapp.com`:

```java
@Component
public class SubdomainTenantResolver implements TenantResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        String host = request.getServerName(); // "alpha.myapp.com"
        int dot = host.indexOf('.');
        return dot > 0 ? host.substring(0, dot) : null; // "alpha"
    }
}
```

---

## Behavior Details

Let's say School Alpha creates a student "Alice", and School Beta tries to access her:

### CREATE — tenant is forced, never trusted from the body

```bash
# School Alpha creates a student
# Even if the body sends schoolId: "beta", FlashAPI forces "alpha" from the header
curl -X POST http://localhost:8080/api/students \
  -H "X-Tenant-Id: alpha" \
  -H "Content-Type: application/json" \
  -d '{"name": "Alice", "grade": "3rd", "schoolId": "beta"}'

# Result in database: schoolId = "alpha" (from header, body value ignored)
```

This prevents malicious clients from injecting data into another tenant's space.

### LIST — automatic filtering, no manual WHERE clause

```bash
# School Alpha sees only its students
curl http://localhost:8080/api/students -H "X-Tenant-Id: alpha"
# → returns Alice and other Alpha students

# School Beta sees nothing from Alpha
curl http://localhost:8080/api/students -H "X-Tenant-Id: beta"
# → {"data": [], "meta": {"totalElements": 0}}
```

Under the hood, FlashAPI adds:
```sql
SELECT * FROM students WHERE school_id = 'alpha' AND ...
```

### GET / UPDATE / DELETE — cross-tenant access returns 404

```bash
# Alice (id=1) belongs to Alpha. Beta tries to access her:
curl http://localhost:8080/api/students/1 -H "X-Tenant-Id: beta"
# → 404 Not Found
```

**Why 404 and not 403?** If FlashAPI returned 403 ("access denied"), it would confirm that student #1 exists — leaking information to Beta. Returning 404 reveals nothing about other tenants' data.

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
