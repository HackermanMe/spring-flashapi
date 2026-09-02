# Migration Guide

This guide helps you upgrade between major versions of Spring FlashAPI.

---

## v1.x → v2.0.0

### Breaking Changes

#### 1. Soft-delete configuration renamed

The property `flashapi.soft-delete.column-name` has been renamed to `flashapi.soft-delete.attribute-name`.

**Before (v1.x):**
```yaml
flashapi:
  soft-delete:
    column-name: deletedAt
```

**After (v2.0.0):**
```yaml
flashapi:
  soft-delete:
    attribute-name: deletedAt
```

**Why:** The value was always a Java attribute name (e.g., `deletedAt`), not a SQL column name (e.g., `deleted_at`). The old name was misleading.

**Find and replace:**
- `column-name` → `attribute-name` in your `application.yml` or `application.properties`
- `flashapi.soft-delete.column-name` → `flashapi.soft-delete.attribute-name`

### New Features

#### ManyToOne FK resolution

You no longer need explicit FK fields for `@ManyToOne` relations. FlashAPI resolves `categoryId` in the request body automatically.

**Before (v1.x) — explicit FK field required:**
```java
@Column(name = "category_id")
private Long categoryId;

@ManyToOne
@JoinColumn(name = "category_id", insertable = false, updatable = false)
private Category category;
```

**After (v2.0.0) — just the relation:**
```java
@ManyToOne
private Category category;  // Send categoryId in the body, it just works
```

Both patterns still work. The explicit FK field is not deprecated.

#### Typed error responses

All error responses now follow a consistent format:

```json
{
  "status": 404,
  "error": "Entity not found"
}
```

If you have frontend code parsing error responses, it can now rely on the `status` and `error` fields being present on all errors.

#### Field selection

New `?fields=` query parameter for sparse fieldsets:

```
GET /api/products?fields=id,name
```

No migration needed — this is additive.

#### Lifecycle hooks

New annotations for injecting business logic at CRUD events:

```java
@Component
public class ProductHooks {
    @FlashBeforeCreate
    public void validate(Object entity, HttpServletRequest request) {
        // your logic
    }
}
```

No migration needed — this is additive.

#### Relation filters

Filter by related entity fields using dot-notation:

```
GET /api/products?category.id=5
GET /api/products?category.name.contains=Electronics
```

No migration needed — this is additive.

#### Feature guard

Record-count limits via `@FlashEntity(maxRecords = 100)`:

```java
@Entity
@FlashEntity(maxRecords = 100)
public class Product { ... }
```

No migration needed — this is additive.

#### Owner-based access control

Restrict UPDATE/DELETE to the entity owner via `@FlashSecured(ownerField)`:

```java
@FlashSecured(roles = "authenticated", ownerField = "ownerId", ownerAdminRoles = {"ADMIN"})
```

#### Auto-inject current user

Auto-set the authenticated user on CREATE via `@FlashEntity(currentUserField)`:

```java
@FlashEntity(currentUserField = "author")
```

#### Declarative counters

Auto-maintained denormalized counters via `@FlashCounter`:

```java
@FlashCounter(source = PostLike.class, relation = "post")
private int likeCount;
```

#### Principal resolver for ownership and user injection

`ownerField` and `currentUserField` now support a `FlashPrincipalResolver` bean for custom identity extraction. Without it, FlashAPI compares `auth.getName()` with entity field values — which fails silently when `getName()` returns a username but the field expects a numeric ID.

```java
@Component
public class MyPrincipalResolver implements FlashPrincipalResolver {
    @Override
    public Object resolve(Authentication auth) {
        return ((MyUserDetails) auth.getPrincipal()).getId();
    }
}
```

No migration needed — existing behavior is preserved. This is additive.

#### Annotation consolidation

`@FlashAudit`, `@FlashMultiTenant`, `@FlashWebhook`, and `@FeatureGuard` are now deprecated. Their functionality is consolidated into `@FlashEntity`:

| Before | After |
|--------|-------|
| `@FlashAudit(trackFields = true)` | `@FlashEntity(audit = true, trackFields = true)` |
| `@FlashMultiTenant(field = "tenantId")` | `@FlashEntity(tenantField = "tenantId")` |
| `@FlashWebhook` | `@FlashEntity(webhook = true)` |
| `@FeatureGuard(max = 100)` | `@FlashEntity(maxRecords = 100)` |

The old annotations still work but emit deprecation warnings. They will be removed in the next major version.

---

## Deprecation Policy

Spring FlashAPI follows this deprecation lifecycle:

1. **Deprecated** — The old API is marked `@Deprecated(forRemoval = true)` and still works. A compiler warning is emitted. Documentation shows the replacement.
2. **Removed** — The old API is deleted in the next major version.

Deprecations are always announced in:
- The CHANGELOG (auto-generated)
- This migration guide (with before/after examples)
- The `@Deprecated` annotation in code (visible in your IDE)
