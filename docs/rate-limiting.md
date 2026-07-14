# Rate Limiting

FlashAPI includes built-in per-IP rate limiting using an in-memory token bucket algorithm. No additional dependencies required.

## Quick Start

```java
@Entity
@FlashEntity(rateLimit = true, rateLimitRequests = 100, rateLimitWindow = 60)
public class Product {
    // 100 requests per 60 seconds per client IP
}
```

All endpoints for this entity (`GET /api/products`, `GET /api/products/{id}`, `POST`, `PUT`, `DELETE`, bulk, export) are rate-limited under the same bucket.

## How It Works

- Each client IP gets an independent `TokenBucket` per entity path.
- The bucket starts full (`rateLimitRequests` tokens) and decrements by 1 on each request.
- When the bucket is empty, the request receives HTTP `429 Too Many Requests`.
- The bucket refills completely when the window elapses (sliding window reset).
- Thread-safe: uses `ConcurrentHashMap` + `AtomicLong` with CAS -- zero allocation on the hot path after warmup.

## Response Headers and Body

When the limit is exceeded:

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 60
Content-Type: application/json

{
  "error": "Rate limit exceeded",
  "retryAfter": 60
}
```

The `Retry-After` value equals `rateLimitWindow` in seconds.

## Annotation Reference

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `rateLimit` | `boolean` | `false` | Enable rate limiting for this entity |
| `rateLimitRequests` | `int` | `100` | Max requests per window per client IP |
| `rateLimitWindow` | `int` | `60` | Window duration in seconds |

## Per-Entity Configuration

Different entities can have different limits:

```java
@FlashEntity(rateLimit = true, rateLimitRequests = 1000, rateLimitWindow = 60)
public class Product { }  // High-traffic: 1000 req/min

@FlashEntity(rateLimit = true, rateLimitRequests = 10, rateLimitWindow = 60)
public class Order { }    // Sensitive: 10 req/min

@FlashEntity(rateLimit = true, rateLimitRequests = 5, rateLimitWindow = 3600)
public class PasswordReset { }  // Very strict: 5 req/hour
```

## IP Resolution

FlashAPI resolves the client IP in this order:

1. `X-Forwarded-For` header -- first IP in the comma-separated list
2. `HttpServletRequest.getRemoteAddr()` -- fallback

This works correctly behind reverse proxies (Nginx, AWS ALB, Cloudflare, Traefik).

## Concrete curl Example

```bash
# Entity configured with rateLimitRequests = 3, rateLimitWindow = 60

# Requests 1-3: succeed
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/products
# 200

curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/products
# 200

curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/products
# 200

# Request 4: rate limited
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/products
# {"error":"Rate limit exceeded","retryAfter":60}
# HTTP 429

# With verbose headers:
curl -v http://localhost:8080/api/products 2>&1 | grep -E "< HTTP|Retry-After"
# < HTTP/1.1 429
# < Retry-After: 60
```

## Custom Rate Limiter

The `FlashRateLimiter` bean is registered with `@ConditionalOnMissingBean`. Define your own bean to override:

```java
@Bean
public FlashRateLimiter flashRateLimiter() {
    return new MyCustomRateLimiter();
}
```

Your implementation must provide:

```java
public boolean isAllowed(EntityMetadata meta, String clientIp);
public int getRemainingRequests(EntityMetadata meta, String clientIp);
public void cleanup();
```

## Distributed Rate Limiting (Redis-backed)

The default implementation is in-memory (per-JVM). In a clustered environment, each instance maintains its own bucket -- meaning N instances effectively multiply the allowed rate by N.

For true distributed rate limiting, implement a Redis-backed `FlashRateLimiter`:

```java
@Bean
public FlashRateLimiter flashRateLimiter(StringRedisTemplate redis) {
    return new RedisFlashRateLimiter(redis);
}
```

```java
public class RedisFlashRateLimiter extends FlashRateLimiter {

    private final StringRedisTemplate redis;

    public RedisFlashRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean isAllowed(EntityMetadata meta, String clientIp) {
        if (!meta.rateLimitEnabled()) return true;

        String key = "flashapi:ratelimit:" + meta.path() + ":" + clientIp;
        Long count = redis.opsForValue().increment(key);

        if (count == 1) {
            redis.expire(key, Duration.ofSeconds(meta.rateLimitWindow()));
        }

        return count <= meta.rateLimitRequests();
    }

    @Override
    public int getRemainingRequests(EntityMetadata meta, String clientIp) {
        if (!meta.rateLimitEnabled()) return -1;

        String key = "flashapi:ratelimit:" + meta.path() + ":" + clientIp;
        String val = redis.opsForValue().get(key);
        int used = val != null ? Integer.parseInt(val) : 0;
        return Math.max(0, meta.rateLimitRequests() - used);
    }

    @Override
    public void cleanup() {
        // Redis TTL handles expiry automatically -- no-op
    }
}
```

Required dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### Redis configuration

#### application.yml

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

#### application.properties

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

## Testing Rate Limits

### Integration test with MockMvc

```java
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        // Entity configured with rateLimitRequests = 3
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/products"))
                   .andExpect(status().isOk());
        }

        // 4th request exceeds the limit
        mockMvc.perform(get("/api/products"))
               .andExpect(status().isTooManyRequests())
               .andExpect(jsonPath("$.error").value("Rate limit exceeded"))
               .andExpect(jsonPath("$.retryAfter").value(60))
               .andExpect(header().string("Retry-After", "60"));
    }
}
```

### Testing with different IPs

```java
@Test
void rateLimitIsPerIp() throws Exception {
    // Exhaust limit for IP-A
    for (int i = 0; i < 3; i++) {
        mockMvc.perform(get("/api/products")
                .header("X-Forwarded-For", "10.0.0.1"))
               .andExpect(status().isOk());
    }

    // IP-A is now blocked
    mockMvc.perform(get("/api/products")
            .header("X-Forwarded-For", "10.0.0.1"))
           .andExpect(status().isTooManyRequests());

    // IP-B still has full quota
    mockMvc.perform(get("/api/products")
            .header("X-Forwarded-For", "10.0.0.2"))
           .andExpect(status().isOk());
}
```

### Resetting between tests

The in-memory rate limiter retains state across tests in the same context. Inject and reset:

```java
@Autowired
private FlashRateLimiter rateLimiter;

@BeforeEach
void resetLimits() {
    rateLimiter.cleanup();
}
```

Or use `@DirtiesContext` on rate-limit tests to get a fresh context.

## Rate Limiting and Load Balancers

### X-Forwarded-For trust

FlashAPI reads the first IP from `X-Forwarded-For`. This assumes your infrastructure strips or overwrites this header at the edge. If clients can set it directly (no proxy), they can spoof their IP to bypass limits.

**Secure setup (recommended):**

```
Client -> Nginx/ALB (sets X-Forwarded-For) -> App
```

Nginx config:

```nginx
proxy_set_header X-Forwarded-For $remote_addr;
```

This ensures the header contains the real client IP, not a client-supplied value.

### Multiple proxies

With chained proxies (`Client -> CDN -> ALB -> App`), the header looks like:

```
X-Forwarded-For: client-ip, cdn-ip
```

FlashAPI takes the **first** IP (`client-ip`), which is correct when the outermost proxy is trusted.

### When NOT to trust X-Forwarded-For

If your app is directly exposed (no reverse proxy in front), disable header-based resolution to prevent spoofing. Override the rate limiter or add a filter that strips the header:

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StripForwardedFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Wrap request to return null for X-Forwarded-For
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if ("X-Forwarded-For".equalsIgnoreCase(name)) return null;
                return super.getHeader(name);
            }
        }, response);
    }
}
```

### Sticky sessions and rate limiting

With the default in-memory limiter behind a load balancer:
- **Without sticky sessions**: requests distribute across instances, effectively multiplying the limit by the number of instances.
- **With sticky sessions**: each client hits the same instance, so limits are accurate.

For consistent behavior regardless of topology, use the Redis-backed implementation (see above).

## Bucket Cleanup

Expired buckets are cleaned up lazily (only removed when checked). For long-running applications with many unique client IPs, schedule periodic cleanup:

```java
@Scheduled(fixedRate = 300_000) // every 5 minutes
public void cleanupRateLimiter() {
    flashRateLimiter.cleanup();
}
```

A bucket is considered expired when `now - windowStart > windowMillis * 2` (double the window duration as grace period).

## FAQ

**Q: Does rate limiting apply to bulk endpoints?**
A: Yes. A single bulk request consumes one token, same as any other request. If you bulk-create 100 items, that counts as 1 request against the limit.

**Q: Can I rate-limit by user ID instead of IP?**
A: Not out of the box. Provide a custom `FlashRateLimiter` that extracts the user identity from the `SecurityContext` or a request header.

**Q: What happens to in-flight requests when the bucket resets?**
A: The window resets atomically (`tokens.set(maxTokens)`). Requests arriving after the reset immediately get a full bucket. There is no partial refill.

**Q: Does the rate limiter count failed requests (404, 500)?**
A: The token is consumed before the handler executes. All requests -- successful or not -- decrement the bucket.

**Q: Can I set global rate limits instead of per-entity?**
A: FlashAPI rate limiting is per-entity. For a global limit, use a servlet filter or Spring Cloud Gateway's built-in rate limiter upstream.

**Q: Is there a way to whitelist certain IPs?**
A: Not built-in. Implement a custom `FlashRateLimiter` that returns `true` for whitelisted IPs without consuming a token.
