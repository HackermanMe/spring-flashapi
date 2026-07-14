# Annotations Reference

FlashAPI uses a minimal set of annotations to control how your JPA entities are exposed as REST endpoints.

## Entity-Level Annotations

### `@FlashEntity`

Marks a JPA entity for automatic REST API generation.

```java
@Entity
@FlashEntity
public class Product {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private BigDecimal price;
}
```

This generates: `GET/POST /products`, `GET/PUT/DELETE /products/{id}`

**Attributes:**

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `path` | String | `""` (auto-pluralized) | Custom URL path segment |
| `exclude` | String[] | `{}` | Operations to exclude (e.g. `{"DELETE"}`) |
| `only` | String[] | `{}` | Only allow these operations |
| `readonly` | boolean | `false` | Shortcut for `only = {"LIST", "READ"}` |
| `softDelete` | boolean | `false` | Use soft delete instead of hard delete |
| `cache` | boolean | `false` | Enable response caching ([details](cache.md)) |
| `cacheTtl` | int | `300` | Cache TTL in seconds |
| `rateLimit` | boolean | `false` | Enable per-IP rate limiting ([details](rate-limiting.md)) |
| `rateLimitRequests` | int | `100` | Max requests per window per IP |
| `rateLimitWindow` | int | `60` | Rate limit window in seconds |

> All entities are automatically documented in the [OpenAPI spec](openapi.md) served at `/api/docs`.

**Path auto-pluralization rules:**
- `Product` → `products`
- `Category` → `categories`
- `Address` → `addresses`

### `@FlashAudit`

Enables audit trail for an entity. Records who did what and when.

```java
@Entity
@FlashEntity
@FlashAudit(trackFields = true)
public class Order {
    // ...
}
```

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable audit for this entity |
| `trackFields` | boolean | `false` | Track field-level changes (old value → new value) |

### `@FlashWebhook`

Fires HTTP POST notifications to configured URLs on data changes.

```java
@Entity
@FlashEntity
@FlashWebhook(events = {"CREATE", "DELETE"})
public class Payment { ... }
```

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `events` | String[] | `{"CREATE", "UPDATE", "DELETE"}` | Events that trigger notifications |

Configure receiver URLs via `flashapi.webhook.urls`. See [Webhooks](webhooks.md) for full details.

### `@FlashMultiTenant`

Enables automatic data isolation per tenant. All queries are filtered, creates are auto-tagged.

```java
@Entity
@FlashEntity
@FlashMultiTenant(field = "tenantId")
public class Document {
    private String tenantId;  // managed by FlashAPI
    ...
}
```

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `field` | String | `"tenantId"` | Java field name storing the tenant identifier |

Tenant is resolved from HTTP header `X-Tenant-Id` by default (configurable). See [Multi-Tenancy](multi-tenancy.md) for full details.

### `@FlashSecured`

Restricts access to auto-generated endpoints by role. Requires Spring Security on the classpath.

```java
@Entity
@FlashEntity
@FlashSecured(read = "USER", write = "EDITOR", delete = "ADMIN")
public class Article { ... }
```

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `roles` | String[] | `{}` | Required for ALL operations (fallback) |
| `read` | String[] | `{}` | Required for LIST and GET by ID |
| `write` | String[] | `{}` | Required for CREATE, UPDATE, DELETE |
| `create` | String[] | `{}` | Required for POST (overrides write) |
| `update` | String[] | `{}` | Required for PUT (overrides write) |
| `delete` | String[] | `{}` | Required for DELETE (overrides write) |
| `list` | String[] | `{}` | Required for GET collection (overrides read) |

Resolution priority: specific operation > write/read group > roles > "authenticated".

Special values: `"permitAll"` (public), `"authenticated"` (any logged-in user).

See [Security](security.md) for full details and examples.

### `@EnableFlashApi`

Activates FlashAPI in your Spring Boot application. Place on your main class.

```java
@SpringBootApplication
@EnableFlashApi
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `basePackages` | String[] | `{}` (uses annotated class package) | Packages to scan for `@FlashEntity` |
| `basePath` | String | `"/api"` | Base path prefix for all endpoints |

---

## Field-Level Annotations

### `@FlashReadOnly`

Field is visible in GET responses but ignored in POST/PUT request bodies. Use for system-managed fields.

```java
@FlashReadOnly
private LocalDateTime createdAt;

@FlashReadOnly
private LocalDateTime updatedAt;
```

**Behavior:**
- GET responses: included
- POST body: ignored
- PUT body: ignored

### `@FlashWriteOnly`

Field is accepted in POST/PUT request bodies but never included in responses. Use for sensitive data.

```java
@FlashWriteOnly
private String password;
```

**Behavior:**
- GET responses: excluded
- POST body: accepted
- PUT body: accepted

### `@FlashHidden`

Field is completely invisible to the API. Never returned, never accepted. Use for internal system fields.

```java
@FlashHidden
private String internalToken;

@FlashHidden
private byte[] encryptionKey;
```

**Behavior:**
- GET responses: excluded
- POST body: ignored
- PUT body: ignored

---

## Field Visibility Matrix

| Annotation | In Response | In Create | In Update |
|---|---|---|---|
| *(none)* | Yes | Yes | Yes |
| `@FlashReadOnly` | Yes | No | No |
| `@FlashWriteOnly` | No | Yes | Yes |
| `@FlashHidden` | No | No | No |

---

## Service-Level Annotations

### `@FlashService`

Associates a custom service class with a specific entity. FlashAPI delegates CRUD operations to this service instead of the built-in `GenericCrudService`.

```java
@Service
@FlashService(Product.class)
public class InventoryService {
    // FlashAPI calls this service for all Product operations
}
```

By convention, FlashAPI already detects `ProductService` for `Product`. Use `@FlashService` only when:
- Your service name doesn't follow the `{Entity}Service` convention
- Multiple services exist and you need to be explicit

---

## Complete Example

```java
@Entity
@FlashEntity(softDelete = true)
@FlashAudit(trackFields = true)
public class User {

    @Id @GeneratedValue
    @FlashReadOnly
    private Long id;

    private String email;

    private String username;

    @FlashWriteOnly
    private String password;

    @FlashReadOnly
    private LocalDateTime createdAt;

    @FlashReadOnly
    private LocalDateTime updatedAt;

    @FlashHidden
    private String refreshToken;
}
```

**Generated endpoints:**
- `GET /api/users` — lists all users (excludes `password`, `refreshToken`)
- `GET /api/users/{id}` — single user (excludes `password`, `refreshToken`)
- `POST /api/users` — accepts `email`, `username`, `password`
- `PUT /api/users/{id}` — accepts `email`, `username`, `password`
- `DELETE /api/users/{id}` — soft-deletes (sets `deletedAt`)

**GET response:**
```json
{
  "data": {
    "id": 1,
    "email": "john@example.com",
    "username": "johndoe",
    "createdAt": "2026-01-15T10:30:00",
    "updatedAt": "2026-03-20T14:22:00"
  }
}
```

**POST request body:**
```json
{
  "email": "john@example.com",
  "username": "johndoe",
  "password": "s3cur3P@ss!"
}
```

Note: `id`, `createdAt`, `updatedAt` are silently ignored in the request body even if sent — no error, just discarded.
