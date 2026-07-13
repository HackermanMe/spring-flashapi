# Rate Limiting

FlashAPI includes built-in per-IP rate limiting using an in-memory token bucket algorithm.

## Quick Start

```java
@Entity
@FlashEntity(rateLimit = true, rateLimitRequests = 100, rateLimitWindow = 60)
public class Product {
    // 100 requests per minute per IP
}
```

No additional dependencies required.

## How It Works

- Each client IP gets an independent token bucket per entity
- When the bucket is empty, requests receive HTTP `429 Too Many Requests`
- The bucket refills automatically when the window expires
- `X-Forwarded-For` header is respected (first IP in the chain)

## Response on Rate Limit Exceeded

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 60

{
  "error": "Rate limit exceeded",
  "retryAfter": 60
}
```

## Annotation Reference

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `rateLimit` | boolean | `false` | Enable rate limiting for this entity |
| `rateLimitRequests` | int | `100` | Max requests per window per client IP |
| `rateLimitWindow` | int | `60` | Window duration in seconds |

## Per-Entity Configuration

Different entities can have different limits:

```java
@FlashEntity(rateLimit = true, rateLimitRequests = 1000, rateLimitWindow = 60)
public class Product { }  // High-traffic: 1000/min

@FlashEntity(rateLimit = true, rateLimitRequests = 10, rateLimitWindow = 60)
public class Order { }    // Sensitive: 10/min
```

## IP Resolution

FlashAPI resolves the client IP in this order:
1. `X-Forwarded-For` header (first IP in comma-separated list)
2. `HttpServletRequest.getRemoteAddr()` (fallback)

This works correctly behind reverse proxies (Nginx, AWS ALB, Cloudflare).

## Custom Rate Limiter

The `FlashRateLimiter` bean is created with `@ConditionalOnMissingBean`. Replace it for custom behavior:

```java
@Bean
public FlashRateLimiter flashRateLimiter() {
    // Return your custom implementation (e.g., Redis-backed for distributed)
    return new MyRedisRateLimiter();
}
```

## Limitations

- In-memory only: rate limits are per-JVM instance (not shared across a cluster)
- For distributed rate limiting, provide a custom `FlashRateLimiter` backed by Redis or similar
- Expired buckets are cleaned up lazily; for long-running applications with many unique IPs, consider periodic cleanup via `flashRateLimiter.cleanup()`
