# Getting Started

## Requirements

- Java 17+
- Spring Boot 3.2+
- Spring Data JPA (any JPA provider: Hibernate, EclipseLink)

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.flashapi</groupId>
    <artifactId>spring-flashapi</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

### 1. Enable FlashAPI

```java
@SpringBootApplication
@EnableFlashApi
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

### 2. Annotate your entities

```java
@Entity
@FlashEntity
public class Product {
    @Id @GeneratedValue
    private Long id;

    private String name;

    private BigDecimal price;

    private Integer stock;
}
```

### 3. Run your application

That's it. FlashAPI generates a full REST API:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/products` | List all products (paginated) |
| GET | `/api/products/{id}` | Get a single product |
| POST | `/api/products` | Create a product |
| PUT | `/api/products/{id}` | Update a product |
| DELETE | `/api/products/{id}` | Delete a product |
| POST | `/api/products/{id}/restore` | Restore a soft-deleted product (if `softDelete = true`) |
| GET | `/api/products/{id}/history` | Get audit history (if `@FlashAudit` enabled) |

## Pagination

All list endpoints return paginated responses:

```
GET /api/products?page=0&size=20&sort=name,asc
```

Response:
```json
{
  "data": [...],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 54,
    "totalPages": 3
  }
}
```

## Filtering

Filter by any field using query parameters:

```
GET /api/products?name.contains=phone&price.gte=100&price.lte=500
```

Supported operators:

| Operator | Description | Example |
|----------|-------------|---------|
| `eq` | Equals (default) | `?status=active` or `?status.eq=active` |
| `neq` | Not equals | `?status.neq=archived` |
| `gt` | Greater than | `?price.gt=100` |
| `gte` | Greater than or equal | `?price.gte=100` |
| `lt` | Less than | `?price.lt=500` |
| `lte` | Less than or equal | `?stock.lte=10` |
| `contains` | Contains (case-insensitive) | `?name.contains=phone` |
| `startswith` | Starts with (case-insensitive) | `?name.startswith=sam` |
| `endswith` | Ends with (case-insensitive) | `?email.endswith=@gmail.com` |
| `isnull` | Is null / is not null | `?deletedAt.isnull=true` |
| `in` | In list (comma-separated) | `?status.in=active,pending` |

## Configuration

Configure via `application.yml`:

```yaml
flashapi:
  base-path: /api          # Base path for all endpoints (default: /api)
  default-page-size: 20    # Default pagination size (default: 20)
  max-page-size: 100       # Maximum allowed page size (default: 100)
  audit:
    enabled: true          # Global audit toggle (default: true)
    table-name: flash_audit_entry  # Audit table name
  soft-delete:
    column-name: deletedAt # Soft delete timestamp column (default: deletedAt)
```

## Progressive Disclosure

FlashAPI follows the "progressive disclosure" pattern:

1. **Zero config** — annotate your entity, get a full CRUD API
2. **Custom service** — create a `ProductService` bean and FlashAPI delegates to it
3. **Custom controller** — define your own `@RestController` for `/products` and FlashAPI backs off

At each level, you take control of exactly what you need. FlashAPI handles the rest.

## Next Steps

- [Annotations Reference](annotations.md) — full list of annotations and their behavior
- [Soft Delete](soft-delete.md) — how soft delete works
- [Audit Trail](audit.md) — tracking who changed what
