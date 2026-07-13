# Intelligent Cache

FlashAPI provides transparent response caching for GET endpoints using Spring's cache abstraction.

## Quick Start

1. Add `spring-boot-starter-cache` to your project:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

2. Enable caching in your application:

```java
@SpringBootApplication
@EnableCaching
@EnableFlashApi
public class MyApp { }
```

3. Annotate your entity:

```java
@Entity
@FlashEntity(cache = true, cacheTtl = 300)
public class Product {
    // ...
}
```

That's it. FlashAPI caches GET responses and automatically invalidates on writes.

## How It Works

- **List endpoint** (`GET /api/products`): cached by page/size/sort/filters combination
- **Get by ID** (`GET /api/products/1`): cached by entity ID
- **Create/Update/Delete**: evicts the entire cache for that entity

Cache is only applied when `?expand` is not used (expanded responses are dynamic).

## Cache Names

FlashAPI uses the pattern `flashapi:{entityPath}` for cache names:
- `flashapi:products`
- `flashapi:categories`

This lets you configure per-entity TTL in your cache provider.

## Annotation Reference

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `cache` | boolean | `false` | Enable caching for this entity |
| `cacheTtl` | int | `300` | TTL in seconds (used by cache providers that support it) |

## Cache Provider

FlashAPI works with any Spring Cache provider:
- **Simple** (in-memory ConcurrentHashMap) — good for dev
- **Caffeine** — recommended for production (supports TTL, eviction policies)
- **Redis** — for distributed caching across instances
- **EhCache** — enterprise caching

Example with Caffeine:

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=300s
```

## Without a CacheManager

If no `CacheManager` bean exists in the context, FlashAPI gracefully operates in no-op mode — caching is simply disabled, no errors.

## Behavior on writes

| Operation | Effect |
|-----------|--------|
| `POST` (create) | Full cache eviction for the entity |
| `PUT` (update) | Full cache eviction for the entity |
| `DELETE` | Full cache eviction for the entity |
| Bulk operations | Full cache eviction for the entity |

Full eviction ensures consistency. For high-write workloads where caching adds no value, simply leave `cache = false`.
