# Intelligent Cache

FlashAPI provides transparent response caching for GET endpoints using Spring's cache abstraction. Zero custom code — enable it per entity and FlashAPI handles cache reads, writes, and invalidation automatically.

## Quick Start

### 1. Add the cache starter

**Maven:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

**Gradle (Kotlin DSL):**

```kotlin
implementation("org.springframework.boot:spring-boot-starter-cache")
```

**Gradle (Groovy):**

```groovy
implementation 'org.springframework.boot:spring-boot-starter-cache'
```

### 2. Enable caching in your application

```java
@SpringBootApplication
@EnableCaching
@EnableFlashApi
public class MyApp {
    public static void main(String[] args) {
        SpringApplication.run(MyApp.class, args);
    }
}
```

### 3. Annotate your entity

```java
@Entity
@FlashEntity(cache = true, cacheTtl = 300)
public class Product {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private BigDecimal price;
}
```

That's it. GET responses are now cached. Writes invalidate the cache automatically.

---

## How It Works

### What gets cached

| Endpoint | Cache key pattern | Cached? |
|----------|------------------|---------|
| `GET /api/products` | `list:{page}:{size}:{sort}:{filters}` | Yes |
| `GET /api/products?search=phone` | `list:0:20:null:{search=phone}` | Yes |
| `GET /api/products/42` | `id:42` | Yes |
| `GET /api/products?expand=category` | — | **No** (expanded responses are dynamic) |

### What invalidates the cache

| Operation | Effect |
|-----------|--------|
| `POST /api/products` (create) | Full cache eviction for the entity |
| `PUT /api/products/42` (update) | Full cache eviction for the entity |
| `DELETE /api/products/42` | Full cache eviction for the entity |
| `POST /api/products/bulk` | Full cache eviction for the entity |
| `PUT /api/products/bulk` | Full cache eviction for the entity |
| `DELETE /api/products/bulk` | Full cache eviction for the entity |

Full eviction guarantees consistency — no stale data. For high-write workloads where caching adds no value, leave `cache = false`.

### Cache names

FlashAPI uses the pattern `flashapi:{entityPath}`:

- `flashapi:products`
- `flashapi:categories`
- `flashapi:orders`

This lets you configure per-entity TTL, eviction policies, and size limits in your cache provider.

---

## Annotation Reference

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `cache` | boolean | `false` | Enable caching for this entity |
| `cacheTtl` | int | `300` | TTL hint in seconds (used by providers that support it) |

```java
// Cache enabled, 5-minute TTL
@FlashEntity(cache = true, cacheTtl = 300)

// Cache enabled, 1-hour TTL (rarely updated data)
@FlashEntity(cache = true, cacheTtl = 3600)

// No cache (default — high-write entity)
@FlashEntity
```

---

## Cache Providers

FlashAPI works with any Spring Cache implementation. You choose the backend.

### Simple (in-memory — for development)

**application.yml:**

```yaml
spring:
  cache:
    type: simple
```

**application.properties:**

```properties
spring.cache.type=simple
```

Uses `ConcurrentHashMap`. No TTL, no eviction, no size limit. Fine for local dev, **not for production**.

### Caffeine (recommended for single-instance production)

**Maven:**

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

**Gradle:**

```kotlin
implementation("com.github.ben-manes.caffeine:caffeine")
```

**application.yml:**

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=300s
```

**application.properties:**

```properties
spring.cache.type=caffeine
spring.cache.caffeine.spec=maximumSize=10000,expireAfterWrite=300s
```

Caffeine provides TTL enforcement, LRU eviction, and near-optimal hit rates. This is the **recommended** choice for single-JVM deployments.

### Redis (for distributed/multi-instance production)

**Maven:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

**Gradle:**

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

**application.yml:**

```yaml
spring:
  cache:
    type: redis
  data:
    redis:
      host: localhost
      port: 6379
      password: secret
```

**application.properties:**

```properties
spring.cache.type=redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=secret
```

Redis shares cache across all instances of your application. Cache invalidation on one node propagates to all others.

---

## Without a CacheManager

If no `CacheManager` bean exists in the context (no cache starter, no `@EnableCaching`), FlashAPI operates in **no-op mode** — caching is silently disabled, no errors. This means:

- You can set `cache = true` on entities even before adding a cache provider
- Adding a cache provider later activates caching with zero code change
- Tests without cache configuration still pass

---

## When NOT to Use Cache

| Scenario | Recommendation |
|----------|---------------|
| Entity is written more often than read | Leave `cache = false` — eviction on every write defeats the purpose |
| Data must always reflect real-time state (stock levels, live prices) | Leave `cache = false` |
| Entity has very few records (< 100) | Optional — the DB query is already fast |
| Entity has many records, queried frequently with same params | **Use cache** — biggest win |
| Entity data changes rarely (config, categories, reference data) | **Use cache** with high TTL |

---

## Monitoring Cache

### With Caffeine + Actuator

**application.yml:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: caches,metrics
```

**application.properties:**

```properties
management.endpoints.web.exposure.include=caches,metrics
```

Then:

```bash
# List all caches
curl http://localhost:8080/actuator/caches

# Cache hit/miss metrics (Caffeine exposes these via Micrometer)
curl http://localhost:8080/actuator/metrics/cache.gets?tag=cache:flashapi:products&tag=result:hit
curl http://localhost:8080/actuator/metrics/cache.gets?tag=cache:flashapi:products&tag=result:miss
```

### With Redis

Use `redis-cli MONITOR` or Redis Insight to watch cache operations in real time.

---

## Custom CacheManager

The `FlashCacheManager` bean is created with `@ConditionalOnMissingBean`. To provide a custom implementation (e.g., with per-entity TTL based on `cacheTtl`):

```java
@Configuration
public class CacheConfig {

    @Bean
    public FlashCacheManager flashCacheManager(CacheManager cacheManager) {
        return new MyCustomFlashCacheManager(cacheManager);
    }
}
```

FlashAPI detects your bean and uses it instead of its default.

---

## FAQ

**Q: Does the cache work if I only have `application.properties` (no `.yml`)?**

Yes. FlashAPI reads standard Spring Boot properties. Both file formats are fully equivalent.

**Q: Can I enable cache on some entities and not others?**

Yes. The `cache` attribute is per-entity:

```java
@FlashEntity(cache = true)   // cached
public class Product { }

@FlashEntity                  // not cached (default)
public class Order { }
```

**Q: What happens if I create/update/delete while the cache is active?**

The entire cache for that entity is evicted immediately. The next GET rebuilds the cache from the database. No stale data is ever served.

**Q: Does `?expand` work with cache?**

No. Requests with `?expand` bypass the cache entirely (both read and write). Expanded responses depend on related entity state which may change independently.

**Q: Is the cache shared across JVM instances?**

Depends on your provider. Simple and Caffeine are per-JVM. Redis is shared across all instances.

**Q: What if I want different TTLs per entity?**

The `cacheTtl` attribute is a hint. Whether it's enforced depends on your provider. With Caffeine's global spec, all caches share the same TTL. For per-cache TTL, use a custom `CacheManager`:

```java
@Bean
public CacheManager cacheManager() {
    SimpleCacheManager manager = new SimpleCacheManager();
    manager.setCaches(List.of(
        buildCache("flashapi:products", Duration.ofMinutes(5)),
        buildCache("flashapi:categories", Duration.ofHours(1))
    ));
    return manager;
}

private CaffeineCache buildCache(String name, Duration ttl) {
    return new CaffeineCache(name, Caffeine.newBuilder()
            .expireAfterWrite(ttl)
            .maximumSize(10000)
            .build());
}
```

**Q: Can I manually evict the cache?**

Yes. Inject `FlashCacheManager` and call `evict()`:

```java
@Autowired
private FlashCacheManager flashCacheManager;

public void refreshProducts(EntityMetadata meta) {
    flashCacheManager.evict(meta);
}
```

Or evict via Spring's `CacheManager` directly:

```java
@Autowired
private CacheManager cacheManager;

public void clearProductCache() {
    Cache cache = cacheManager.getCache("flashapi:products");
    if (cache != null) cache.clear();
}
```

**Q: Does search (`?search=`) work with cache?**

Yes. The search term is part of the cache key. `?search=phone` and `?search=laptop` produce separate cache entries. Both are invalidated on writes.
