<p align="center">
  <img src="docs/assets/logo1.svg" alt="Spring FlashAPI" width="400"/>
</p>

<p align="center">
  <strong>Zero-config REST APIs from your JPA entities.</strong><br>
  Annotate. Run. Done.
</p>

<p align="center">
  <a href="https://github.com/HackermanMe/spring-flashapi/actions"><img src="https://img.shields.io/github/actions/workflow/status/HackermanMe/spring-flashapi/ci.yml?branch=main&style=flat-square&logo=github" alt="CI"></a>
  <a href="https://github.com/HackermanMe/spring-flashapi/blob/main/LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue?style=flat-square" alt="License"></a>
  <img src="https://img.shields.io/badge/Java-21%2B-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21+">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2%2B-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.2+">
</p>

---

## What is Spring FlashAPI?

Spring FlashAPI is a Spring Boot starter that **auto-generates a full REST API** from your JPA entities. No repositories, no services, no controllers — unless you want them.

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

That's it. You now have:

```
GET    /api/products                     — paginated list with filtering & sorting
GET    /api/products/{id}                — single resource
POST   /api/products                     — create
PUT    /api/products/{id}                — update
DELETE /api/products/{id}                — delete (or soft-delete)
GET    /api/products/export?format=csv   — export (csv, xlsx, pdf)
POST   /api/products/bulk                — batch create
PUT    /api/products/bulk                — batch update
DELETE /api/products/bulk                — batch delete
```

## Features

- **Zero boilerplate** — annotate your entity, get a full CRUD API
- **Progressive disclosure** — FlashAPI recedes as you define your own services or controllers
- **Dynamic filtering** — `?price.gte=100&name.contains=phone` with 11 operators
- **Full-text search** — `?search=laptop` across all String fields
- **Pagination & sorting** — `?page=0&size=20&sort=name,asc`
- **Export** — CSV, Excel, and PDF with one query param (`?format=csv`)
- **Bulk operations** — batch create, update, delete with per-item error reporting
- **Relations & expand** — `?expand=category,tags` to include related entities
- **Intelligent caching** — transparent response caching with auto-invalidation on writes
- **Rate limiting** — per-IP token bucket, configurable per entity
- **OpenAPI documentation** — auto-generated spec + Swagger UI at `/api/docs`
- **Soft delete** — timestamp-based with restore endpoint
- **Audit trail** — who changed what, when, with field-level diffs
- **Field visibility** — `@FlashReadOnly`, `@FlashWriteOnly`, `@FlashHidden`, `@FlashExportExclude`
- **Lookup field** — use UUID or any unique field in URLs instead of exposing auto-increment IDs
- **Custom services** — implement `FlashCrudOperations<T, ID>` to add business logic
- **Authorization** — `@FlashSecured` for role-based endpoint access control
- **Multi-tenancy** — `@FlashMultiTenant` for automatic data isolation per tenant
- **Webhooks** — `@FlashWebhook` for real-time event notifications to external systems
- **Spring Security aware** — audit resolves current user automatically
- **Type-safe IDs** — supports Long, Integer, UUID, String

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>io.github.hackermanme</groupId>
    <artifactId>spring-flashapi</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Enable FlashAPI

```java
@SpringBootApplication
@EnableFlashApi
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

### 3. Annotate your entities

```java
@Entity
@FlashEntity(softDelete = true)
@FlashAudit(trackFields = true)
public class User {

    @Id @GeneratedValue
    @FlashReadOnly
    private Long id;

    private String email;

    @FlashWriteOnly
    private String password;

    @FlashReadOnly
    private LocalDateTime createdAt;

    @FlashHidden
    private String internalToken;
}
```

### 4. Run your app

```bash
./mvnw spring-boot:run
```

## Progressive Disclosure

FlashAPI follows a simple philosophy: **provide everything, recede when you take over.**

| Level | You write | FlashAPI handles |
|-------|-----------|-----------------|
| **1. Zero config** | `@FlashEntity` on your class | Everything |
| **2. Custom service** | A `ProductService` bean | Routing, serialization, errors |
| **3. Custom controller** | Your own `@RestController` | Nothing (backs off) |

```java
// Level 2: FlashAPI detects this and delegates to it
@Service
public class ProductService implements FlashCrudOperations<Product, Long> {
    @Override
    public Product create(Map<String, Object> data) {
        // your business logic here
    }
    // ...
}
```

## Search

All list endpoints support full-text search across String fields:

```
GET /api/products?search=laptop
```

Combine with filters, sort, and pagination:

```
GET /api/products?search=pro&price.gte=500&sort=name,asc&page=0&size=10
```

## Filtering

All list endpoints support dynamic filtering:

| Operator | Example | Description |
|----------|---------|-------------|
| `eq` | `?status=active` | Equals (default) |
| `neq` | `?status.neq=archived` | Not equals |
| `gt` / `gte` | `?price.gte=100` | Greater than (or equal) |
| `lt` / `lte` | `?stock.lte=10` | Less than (or equal) |
| `contains` | `?name.contains=phone` | Case-insensitive contains |
| `startswith` | `?name.startswith=sam` | Starts with |
| `endswith` | `?email.endswith=@gmail.com` | Ends with |
| `isnull` | `?deletedAt.isnull=true` | Is null |
| `in` | `?status.in=active,pending` | In list |

## Export

Every entity with LIST access automatically gets an export endpoint:

```
GET /api/products/export?format=csv
GET /api/products/export?format=xlsx
GET /api/products/export?format=pdf
```

All filters and sorting apply to exports too:

```
GET /api/products/export?format=csv&price.gte=100&sort=name,asc
```

| Format | Dependency required | Description |
|--------|-------------------|-------------|
| CSV | None | Always available, UTF-8 with BOM |
| XLSX | `org.apache.poi:poi-ooxml` | Streaming Excel (constant memory) |
| PDF | `net.sf.jasperreports:jasperreports` | Auto-generated table or custom template |

### PDF with custom JasperReports templates

Place a `.jrxml` file matching your entity path in `classpath:flashapi/reports/`:

```
src/main/resources/flashapi/reports/products.jrxml
```

FlashAPI will use your template and feed it the filtered data. If no template exists, a clean table layout is generated dynamically.

Available parameters in your template:
- `ENTITY_NAME` — the entity name (e.g., "Product")
- `RECORD_COUNT` — total number of records in this export

### Configuration

```yaml
flashapi:
  export:
    max-rows: 0         # 0 = unlimited, set a cap to prevent abuse
    reports-path: flashapi/reports  # classpath location for .jrxml templates
```

## Configuration

Use `application.yml` or `application.properties` — both work identically.

**application.yml:**

```yaml
flashapi:
  base-path: /api
  default-page-size: 20
  max-page-size: 100
  audit:
    enabled: true
  soft-delete:
    column-name: deletedAt
  export:
    max-rows: 0
    reports-path: flashapi/reports
  bulk:
    max-items: 100
```

**application.properties:**

```properties
flashapi.base-path=/api
flashapi.default-page-size=20
flashapi.max-page-size=100
flashapi.audit.enabled=true
flashapi.soft-delete.column-name=deletedAt
flashapi.export.max-rows=0
flashapi.export.reports-path=flashapi/reports
flashapi.bulk.max-items=100
```

## Documentation

| Guide | Description |
|-------|-------------|
| [Getting Started](docs/getting-started.md) | Installation and first steps |
| [Annotations](docs/annotations.md) | Full annotation reference |
| [Custom Services](docs/custom-service.md) | Taking control of business logic |
| [Export](docs/export.md) | CSV, Excel, and PDF export |
| [Bulk Operations](docs/bulk.md) | Batch create, update, delete |
| [Relations & Expand](docs/relations.md) | Include related entities in responses |
| [Cache](docs/cache.md) | Intelligent response caching |
| [Rate Limiting](docs/rate-limiting.md) | Per-IP rate limiting |
| [Search](docs/search.md) | Full-text search across fields |
| [OpenAPI](docs/openapi.md) | Auto-generated Swagger UI & spec |
| [Security](docs/security.md) | @FlashSecured role-based authorization |
| [Multi-Tenancy](docs/multi-tenancy.md) | Automatic data isolation per tenant |
| [Webhooks](docs/webhooks.md) | Event notifications on data changes |
| [Cookbook](docs/cookbook.md) | CORS, security, Docker, monitoring, recipes |
| [Soft Delete](docs/soft-delete.md) | Timestamp-based soft delete |
| [Audit Trail](docs/audit.md) | Change tracking and history |
| [Configuration](docs/configuration.md) | All available properties |
| [Progressive Disclosure](docs/progressive-disclosure.md) | The framework philosophy |

## Requirements

- Java 21+
- Spring Boot 3.2+
- Spring Data JPA (Hibernate or any JPA provider)

## Building from source

```bash
git clone https://github.com/HackermanMe/spring-flashapi.git
cd spring-flashapi
./mvnw verify
```

## Contributing

Contributions are welcome! Please open an issue first to discuss what you'd like to change.

## License

[Apache License 2.0](LICENSE)
