# Getting Started

Get a fully-featured REST API from your JPA entities in under 5 minutes.

## Requirements

| Requirement | Minimum Version |
|-------------|----------------|
| Java | 21+ |
| Spring Boot | 3.2+ (tested with 3.2, 3.3, 3.4) |
| Spring Data JPA | Any JPA provider (Hibernate, EclipseLink) |

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.hackermanme</groupId>
    <artifactId>spring-flashapi</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.hackermanme:spring-flashapi:1.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
// build.gradle
dependencies {
    implementation 'io.github.hackermanme:spring-flashapi:1.0.0'
}
```

## Quick Start

### 1. Enable FlashAPI

Add `@EnableFlashApi` to your main application class:

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
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private BigDecimal price;

    private Integer stock;

    // Getters and setters (or use Lombok @Data)
}
```

### 3. Run your application

```bash
mvn spring-boot:run
```

Or with Gradle:

```bash
./gradlew bootRun
```

That's it. FlashAPI generates a full REST API automatically:

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/products` | List all products (paginated) |
| GET | `/api/products/{id}` | Get a single product |
| POST | `/api/products` | Create a product |
| PUT | `/api/products/{id}` | Update a product |
| DELETE | `/api/products/{id}` | Delete a product |

Additional endpoints when features are enabled:

| Method | Endpoint | Condition |
|--------|----------|-----------|
| POST | `/api/products/{id}/restore` | `softDelete = true` on `@FlashEntity` |
| GET | `/api/products/{id}/history` | `@FlashAudit` on entity |

Swagger UI is auto-available at **`/api/docs`** with no extra configuration.

## Verify It Works

Once your application starts, run these commands to confirm everything is working.

**Create a product:**

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name": "Keyboard", "price": 79.99, "stock": 150}'
```

Expected response (`201 Created`):

```json
{
  "id": 1,
  "name": "Keyboard",
  "price": 79.99,
  "stock": 150
}
```

**List all products:**

```bash
curl http://localhost:8080/api/products
```

Expected response (`200 OK`):

```json
{
  "data": [
    {
      "id": 1,
      "name": "Keyboard",
      "price": 79.99,
      "stock": 150
    }
  ],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

**Get a single product:**

```bash
curl http://localhost:8080/api/products/1
```

**Update a product:**

```bash
curl -X PUT http://localhost:8080/api/products/1 \
  -H "Content-Type: application/json" \
  -d '{"name": "Mechanical Keyboard", "price": 129.99, "stock": 80}'
```

**Delete a product:**

```bash
curl -X DELETE http://localhost:8080/api/products/1
```

Expected response: `204 No Content`

**Open Swagger UI:**

Navigate to [http://localhost:8080/api/docs](http://localhost:8080/api/docs) in your browser to explore all generated endpoints interactively.

## Pagination

All list endpoints return paginated responses by default:

```bash
curl "http://localhost:8080/api/products?page=0&size=10&sort=name,asc"
```

Response:

```json
{
  "data": [...],
  "meta": {
    "page": 0,
    "size": 10,
    "totalElements": 54,
    "totalPages": 6
  }
}
```

## Filtering

Filter by any field using query parameter operators:

```bash
curl "http://localhost:8080/api/products?name.contains=key&price.gte=50&price.lte=200"
```

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

### application.yml

```yaml
flashapi:
  base-path: /api          # Base path for all endpoints (default: /api)
  default-page-size: 20    # Default pagination size (default: 20)
  max-page-size: 100       # Maximum allowed page size (default: 100)
  audit:
    enabled: true          # Global audit toggle (default: true)
    table-name: flash_audit_log  # Audit table name
  soft-delete:
    column-name: deleted_at # Soft delete timestamp field (default: deleted_at)
```

### application.properties

```properties
flashapi.base-path=/api
flashapi.default-page-size=20
flashapi.max-page-size=100
flashapi.audit.enabled=true
flashapi.audit.table-name=flash_audit_log
flashapi.soft-delete.column-name=deleted_at
```

See [Configuration Reference](configuration.md) for the complete list of properties.

## Progressive Disclosure

FlashAPI follows progressive disclosure: start with zero config, take control only where you need it.

**Level 1 -- Zero config (default):**
Annotate your entity with `@FlashEntity`. FlashAPI generates the full CRUD API, repository, service, and controller.

```java
@Entity
@FlashEntity
public class Product { ... }
// Result: full REST API at /api/products
```

**Level 2 -- Custom service:**
Create a Spring bean named `ProductService`. FlashAPI detects it and delegates all business logic to your implementation while still handling routing, pagination, and filtering.

```java
@Service
public class ProductService {
    public Product create(Product product) {
        // Your custom creation logic
        product.setSku(generateSku());
        return repository.save(product);
    }
}
```

**Level 3 -- Custom controller:**
Define your own `@RestController` mapping to the same path. FlashAPI backs off entirely for that entity.

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {
    // Full control — FlashAPI does not interfere
}
```

At each level, you take control of exactly what you need. FlashAPI handles the rest.

## Minimal Full Example

A complete working project with Maven:

**pom.xml:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.1</version>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>demo</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>io.github.hackermanme</groupId>
            <artifactId>spring-flashapi</artifactId>
            <version>1.0.0</version>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
    </dependencies>
</project>
```

**build.gradle.kts (equivalent):**

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.4.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("io.github.hackermanme:spring-flashapi:1.0.0")
    runtimeOnly("com.h2database:h2")
}
```

**src/main/java/com/example/demo/DemoApplication.java:**

```java
package com.example.demo;

import io.github.hackermanme.flashapi.EnableFlashApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableFlashApi
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

**src/main/java/com/example/demo/Product.java:**

```java
package com.example.demo;

import io.github.hackermanme.flashapi.FlashEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@FlashEntity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private BigDecimal price;

    private Integer stock;

    // Constructors
    public Product() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}
```

**src/main/resources/application.yml:**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
```

Or equivalently, **src/main/resources/application.properties:**

```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
```

Run it, then:

```bash
# Create
curl -s -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name": "Mouse", "price": 29.99, "stock": 500}' | jq

# List
curl -s http://localhost:8080/api/products | jq

# Filter
curl -s "http://localhost:8080/api/products?price.lt=50" | jq

# Swagger UI
open http://localhost:8080/api/docs
```

## Troubleshooting

### Entity not generating endpoints

**Symptom:** Application starts but no endpoints appear for your entity.

**Checklist:**
1. Verify `@EnableFlashApi` is on your main application class (or a `@Configuration` class)
2. Verify the entity has both `@Entity` (JPA) and `@FlashEntity` (FlashAPI)
3. Verify the entity is in a package scanned by Spring Boot (same package or sub-package of your `@SpringBootApplication` class)
4. Check logs for `FlashAPI: Registered entity ...` messages at startup

### 404 on endpoints

**Symptom:** Endpoints return 404.

**Fixes:**
- Confirm the base path: default is `/api`, so the endpoint is `/api/products`, not `/products`
- If you customized `flashapi.base-path`, verify your curl URL matches
- Entity class name `Product` maps to `/products` (lowercase, pluralized). Check the startup logs for the exact path registered.

### Conflict with existing controllers

**Symptom:** `BeanDefinitionOverrideException` or unexpected behavior.

**Cause:** You have a `@RestController` mapped to the same path FlashAPI is generating.

**Fix:** This is by design. When FlashAPI detects an existing controller for the same path, it backs off. If you see a conflict, ensure your controller fully covers the path or remove it to let FlashAPI handle it.

### H2 console not accessible

If you're using H2 for development and the console doesn't load:

application.yml:
```yaml
spring:
  h2:
    console:
      enabled: true
      path: /h2-console
```

application.properties:
```properties
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

### Wrong Java version

**Symptom:** `UnsupportedClassVersionError` or compilation failures.

**Fix:** FlashAPI requires Java 21+. Verify with:

```bash
java -version
```

If using Maven:
```xml
<properties>
    <java.version>21</java.version>
</properties>
```

If using Gradle:
```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

## Next Steps

- [Configuration Reference](configuration.md) -- complete list of all properties and defaults
- [OpenAPI / Swagger UI](openapi.md) -- auto-generated API documentation at `/api/docs`
- [Annotations Reference](annotations.md) -- full list of annotations and their behavior
- [Progressive Disclosure](progressive-disclosure.md) -- detailed override patterns
- [Security](security.md) -- @FlashSecured role-based authorization
- [Soft Delete](soft-delete.md) -- logical deletion with automatic filtering
- [Audit Trail](audit.md) -- tracking who changed what and when
- [Filtering & Search](search.md) -- advanced query capabilities
- [Bulk Operations](bulk.md) -- batch create, update, delete
- [Export](export.md) -- CSV, XLSX, PDF export
- [Relations](relations.md) -- handling entity relationships
- [Caching](cache.md) -- response caching configuration
- [Rate Limiting](rate-limiting.md) -- request throttling
