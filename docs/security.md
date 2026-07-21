# Security (@FlashSecured)

FlashAPI provides endpoint-level authorization through the `@FlashSecured` annotation. It controls **who can access which operations** on your entities — without implementing authentication itself.

**Philosophy:** FlashAPI handles authorization (role checks). Authentication (login, tokens, sessions) is your responsibility via Spring Security. FlashAPI never stores credentials, issues tokens, or manages sessions.

---

## Requirements

`@FlashSecured` requires Spring Security on the classpath. FlashAPI fails fast at startup if the annotation is used without it:

```
IllegalStateException: FlashAPI: @FlashSecured is used but Spring Security is not on the classpath.
Add spring-boot-starter-security to your dependencies.
```

### Maven

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
```

### Gradle (Groovy DSL)

```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
```

---

## Quick Start

```java
@Entity
@FlashEntity
@FlashSecured(roles = "ADMIN")
public class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private BigDecimal amount;
    private String status;
}
```

Result: all endpoints for `Invoice` require the `ADMIN` role. Unauthenticated users get `401`, authenticated users without `ADMIN` get `403`.

---

## Annotation Reference

```java
@FlashSecured(
    roles = {},     // Required for ALL operations (fallback)
    read = {},      // Required for LIST and GET by ID
    write = {},     // Required for CREATE, UPDATE, DELETE
    create = {},    // Required for POST (overrides write)
    update = {},    // Required for PUT (overrides write)
    delete = {},    // Required for DELETE (overrides write)
    list = {}       // Required for GET collection (overrides read)
)
```

### Resolution Priority

For each incoming request, FlashAPI resolves the required roles in this order (first non-empty wins):

1. **Specific operation** — `create()`, `update()`, `delete()`, `list()`
2. **Group** — `write()` (applies to create/update/delete), `read()` (applies to list/read-by-id)
3. **Global** — `roles()` (applies to all operations)
4. **Implicit** — if annotation is present but all attributes are empty → requires any authenticated user

### Special Values

| Value | Meaning |
|-------|---------|
| `"permitAll"` | No restriction — endpoint is public |
| `"authenticated"` | Any authenticated user, no role check |
| Any other string | Treated as a role (checked against `GrantedAuthority`) |

### Role Matching

FlashAPI checks both the raw authority name and the `ROLE_` prefixed version:

```java
// User has authority "ROLE_ADMIN"
@FlashSecured(roles = "ADMIN")      // ✓ matches (checks "ADMIN" and "ROLE_ADMIN")
@FlashSecured(roles = "ROLE_ADMIN") // ✓ matches directly
```

Any role in the list matching grants access (OR logic, not AND).

---

## Examples

### All operations require same role

```java
@Entity
@FlashEntity
@FlashSecured(roles = "ADMIN")
public class SystemConfig { ... }
```

### Read public, write restricted

```java
@Entity
@FlashEntity
@FlashSecured(read = "permitAll", write = "EDITOR")
public class Article { ... }
```

- `GET /api/articles` → public
- `GET /api/articles/1` → public
- `POST /api/articles` → requires EDITOR
- `PUT /api/articles/1` → requires EDITOR
- `DELETE /api/articles/1` → requires EDITOR

### Fine-grained per operation

```java
@Entity
@FlashEntity
@FlashSecured(
    list = "permitAll",
    read = "USER",
    create = "EDITOR",
    update = "EDITOR",
    delete = "ADMIN"
)
public class Product { ... }
```

- `GET /api/products` → public
- `GET /api/products/1` → requires USER
- `POST /api/products` → requires EDITOR
- `PUT /api/products/1` → requires EDITOR
- `DELETE /api/products/1` → requires ADMIN

### Multiple roles (OR logic)

```java
@Entity
@FlashEntity
@FlashSecured(write = {"EDITOR", "MODERATOR"})
public class Comment { ... }
```

Either `EDITOR` or `MODERATOR` can write. Both can. No need to have both.

### Authenticated only (no role check)

```java
@Entity
@FlashEntity
@FlashSecured
public class UserProfile { ... }
```

All attributes are empty → implicit "authenticated" check. Any logged-in user can access all operations.

### Mixed: restrict write, open specific operation

```java
@Entity
@FlashEntity
@FlashSecured(roles = "ADMIN", list = "permitAll")
public class Category { ... }
```

- `GET /api/categories` → public (list explicitly opened)
- Everything else → requires ADMIN

---

## HTTP Response Codes

| Code | Meaning | When |
|------|---------|------|
| `401 Unauthorized` | No authentication provided | No `Authentication` in `SecurityContext`, or anonymous |
| `403 Forbidden` | Authenticated but wrong role | User exists but lacks required authority |

### Response Body

```json
// 401
{"error": "Authentication required"}

// 403
{"error": "Access denied"}
```

---

## Pipeline Order

For entities with multiple features enabled, the full pipeline is:

```
Request → Tenant Resolution → Security Check → Rate Limit → Business Logic → Response
```

Security checks happen **before** rate limiting. This means:
- Unauthenticated requests never consume rate limit tokens
- A 401/403 is returned immediately without touching the database
- For `@FlashMultiTenant` entities, tenant resolution happens first (400 if missing header)

---

## Integration with Spring Security

FlashAPI reads the `SecurityContextHolder` to determine the current user. It does NOT configure Spring Security — that's your job.

### Minimal Spring Security configuration

**application.yml:**

```yaml
spring:
  security:
    user:
      name: admin
      password: secret
      roles: ADMIN
```

**application.properties:**

```properties
spring.security.user.name=admin
spring.security.user.password=secret
spring.security.user.roles=ADMIN
```

### Typical JWT configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}
```

**application.yml:**

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com/realms/myapp
```

**application.properties:**

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.com/realms/myapp
```

### Custom UserDetailsService

```java
@Service
public class MyUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public MyUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(user.getRoles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .toList())
                .build();
    }
}
```

---

## Custom Controllers vs @FlashSecured

`@FlashSecured` only applies to FlashAPI-generated endpoints. Your hand-written controllers (`AuthController`, custom REST endpoints) are secured exclusively via Spring Security's `SecurityFilterChain`:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()  // Your auth controller
            .requestMatchers("/api/**").authenticated()   // FlashAPI + other endpoints
        )
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

For documentation, custom controllers are detected by springdoc-openapi (not by FlashAPI's built-in OpenAPI). See [openapi.md — Custom Controllers](openapi.md#custom-controllers-auth-etc) for setup details.

---

## Entities Without @FlashSecured

Entities that do NOT have `@FlashSecured` are **completely open** — no authentication or authorization check is performed by FlashAPI. This is the default behavior and the zero-config experience.

If you want global security for all endpoints, configure it via Spring Security's `HttpSecurity`:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/**").authenticated()
        )
        .httpBasic(Customizer.withDefaults());
    return http.build();
}
```

This works orthogonally with `@FlashSecured`: Spring Security's filter chain runs first (authentication), then FlashAPI's `@FlashSecured` adds role-based authorization on top.

---

## Combining with Other FlashAPI Features

### With Soft Delete

```java
@Entity
@FlashEntity(softDelete = true)
@FlashSecured(roles = "ADMIN", read = "USER")
public class Document { ... }
```

- `DELETE /api/documents/1` → soft-deletes, requires ADMIN
- `POST /api/documents/1/restore` → requires ADMIN (mapped to UPDATE operation)

### With Audit

```java
@Entity
@FlashEntity
@FlashAudit(trackFields = true)
@FlashSecured(roles = "EDITOR")
public class Report { ... }
```

- `GET /api/reports/1/history` → requires EDITOR (mapped to READ operation)
- Audit entries record the authenticated user's name automatically

### With Export and Bulk

```java
@Entity
@FlashEntity
@FlashSecured(read = "VIEWER", write = "EDITOR")
public class Order { ... }
```

- `GET /api/orders/export?format=csv` → requires VIEWER (mapped to LIST)
- `POST /api/orders/bulk` → requires EDITOR (mapped to CREATE)
- `PUT /api/orders/bulk` → requires EDITOR (mapped to UPDATE)
- `DELETE /api/orders/bulk` → requires EDITOR (mapped to DELETE)

### With Rate Limiting

Security runs before rate limiting:

```java
@Entity
@FlashEntity(rateLimit = true, rateLimitRequests = 10)
@FlashSecured(roles = "USER")
public class SearchResult { ... }
```

Unauthenticated users get `401` without consuming any rate limit budget.

---

## FAQ

**Q: Does @FlashSecured replace Spring Security?**

No. It complements it. Spring Security handles authentication (who are you?). `@FlashSecured` handles authorization for FlashAPI endpoints (what can you do?). You need Spring Security on the classpath for `@FlashSecured` to work.

**Q: What if I have Spring Security but no @FlashSecured?**

The entity's endpoints have no FlashAPI-level role check. But Spring Security's filter chain still applies — if you configured `authorizeHttpRequests` to require auth on `/api/**`, that still works.

**Q: Can I use @FlashSecured without any Spring Security configuration?**

Yes, but Spring Security's defaults will auto-generate a user/password and secure all endpoints via Basic Auth. At minimum, configure a `SecurityFilterChain` bean to control what's protected.

**Q: Are roles case-sensitive?**

Yes. `"ADMIN"` and `"admin"` are different. Convention: use UPPERCASE for role names.

**Q: Can I require multiple roles (AND logic)?**

No. `@FlashSecured` uses OR logic — any matching role grants access. For AND logic, implement a custom `SecurityFilterChain` or use Spring Security's `@PreAuthorize` on a custom controller (Level 3 progressive disclosure).

**Q: What about row-level security (e.g., user can only see their own orders)?**

`@FlashSecured` is endpoint-level only. For row-level security, override with a custom service (Level 2):

```java
@Service
public class OrderService implements FlashCrudOperations<Order, Long> {
    @Override
    public Page<Order> list(Map<String, String> params, Pageable pageable) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return orderRepository.findByUserId(userId, pageable);
    }
}
```

**Q: What about field-level security?**

Not supported by `@FlashSecured`. Use `@FlashHidden` for fields that should never appear, or implement a custom service for dynamic field filtering based on role.

**Q: Does it work with OAuth2/JWT?**

Yes. As long as Spring Security populates the `SecurityContext` with an `Authentication` that has `GrantedAuthority` entries, FlashAPI can check them. This works with JWT, OAuth2, LDAP, SAML, or any Spring Security authentication mechanism.

**Q: What happens if the SecurityContext has no Authentication at all?**

The user is treated as unauthenticated → `401`.

**Q: Can I override security for testing?**

Use Spring Security Test utilities:

```java
@Test
@WithMockUser(roles = "ADMIN")
void adminCanDelete() throws Exception {
    mvc.perform(delete("/api/products/1"))
        .andExpect(status().isNoContent());
}
```
