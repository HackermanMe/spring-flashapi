# Cookbook — Integration Recipes

Real-world solutions for integrating FlashAPI into production applications. Each recipe is self-contained and copy-pasteable.

---

## CORS Configuration

FlashAPI endpoints are registered via Spring MVC's `RequestMappingHandlerMapping` — standard Spring CORS configuration applies.

### Allow all origins (development only)

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
```

### Restrict origins (production)

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("https://myapp.com", "https://admin.myapp.com")
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
```

### Via properties (Spring Boot 3.2+)

**application.yml:**

```yaml
spring:
  web:
    cors:
      mapping: /api/**
      allowed-origins: https://myapp.com
      allowed-methods: GET,POST,PUT,DELETE
```

**application.properties:**

```properties
spring.web.cors.mapping=/api/**
spring.web.cors.allowed-origins=https://myapp.com
spring.web.cors.allowed-methods=GET,POST,PUT,DELETE
```

---

## JSON Serialization

FlashAPI uses the application's `ObjectMapper`. Customize it via Spring Boot properties.

### Date format

**application.yml:**

```yaml
spring:
  jackson:
    date-format: yyyy-MM-dd'T'HH:mm:ss'Z'
    time-zone: UTC
    serialization:
      write-dates-as-timestamps: false
```

**application.properties:**

```properties
spring.jackson.date-format=yyyy-MM-dd'T'HH:mm:ss'Z'
spring.jackson.time-zone=UTC
spring.jackson.serialization.write-dates-as-timestamps=false
```

### Null handling

**application.yml:**

```yaml
spring:
  jackson:
    default-property-inclusion: non_null
```

**application.properties:**

```properties
spring.jackson.default-property-inclusion=non_null
```

This removes `null` fields from all FlashAPI responses.

### Naming strategy (camelCase vs snake_case)

**application.yml:**

```yaml
spring:
  jackson:
    property-naming-strategy: SNAKE_CASE
```

**application.properties:**

```properties
spring.jackson.property-naming-strategy=SNAKE_CASE
```

Result: `{"id": 1, "created_at": "...", "client_name": "..."}` instead of `{"id": 1, "createdAt": "...", "clientName": "..."}`.

### Programmatic customization

```java
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer customizer() {
        return builder -> builder
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .serializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
```

---

## Reverse Proxy (Nginx, Traefik, AWS ALB)

### Context path

If your app is behind a proxy at `https://api.myapp.com/v1/`, configure the context path:

**application.yml:**

```yaml
server:
  servlet:
    context-path: /v1

flashapi:
  base-path: /api
```

**application.properties:**

```properties
server.servlet.context-path=/v1
flashapi.base-path=/api
```

Endpoints become: `https://api.myapp.com/v1/api/products`

### Forwarded headers

If your proxy passes `X-Forwarded-*` headers (for correct URL generation, HTTPS detection, rate limiting):

**application.yml:**

```yaml
server:
  forward-headers-strategy: framework
```

**application.properties:**

```properties
server.forward-headers-strategy=framework
```

### Nginx example

```nginx
server {
    listen 443 ssl;
    server_name api.myapp.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## Database-Specific Configuration

### PostgreSQL

**application.yml:**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: mypass
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate
```

**application.properties:**

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
spring.datasource.username=myuser
spring.datasource.password=mypass
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
```

**Recommended indexes for FlashAPI:**

```sql
-- For soft delete queries
CREATE INDEX idx_products_deleted_at ON products(deleted_at) WHERE deleted_at IS NULL;

-- For audit queries
CREATE INDEX idx_audit_entity ON flash_audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_timestamp ON flash_audit_log(timestamp DESC);

-- For search (trigram extension)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_products_name_trgm ON products USING gin(name gin_trgm_ops);
```

### MySQL

**application.yml:**

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true
    username: myuser
    password: mypass
  jpa:
    database-platform: org.hibernate.dialect.MySQLDialect
    hibernate:
      ddl-auto: validate
```

**application.properties:**

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb?useSSL=false&allowPublicKeyRetrieval=true
spring.datasource.username=myuser
spring.datasource.password=mypass
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=validate
```

**Recommended indexes:**

```sql
-- For search (full-text)
ALTER TABLE products ADD FULLTEXT INDEX idx_products_ft(name, description);

-- For soft delete
ALTER TABLE products ADD INDEX idx_products_deleted_at(deleted_at);
```

### H2 (testing/development)

**application.yml:**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
  h2:
    console:
      enabled: true
      path: /h2-console
```

**application.properties:**

```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

---

## Securing Endpoints (Before Auth v2.0)

FlashAPI doesn't have built-in auth yet. Here are ways to protect your endpoints now.

### Spring Security (recommended)

**Maven:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public: read-only
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        // Swagger UI
                        .requestMatchers("/api/docs/**").permitAll()
                        // Write operations: require authentication
                        .requestMatchers(HttpMethod.POST, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public UserDetailsService users() {
        var admin = User.withDefaultPasswordEncoder()
                .username("admin").password("secret").roles("ADMIN").build();
        return new InMemoryUserDetailsManager(admin);
    }
}
```

### JWT with Spring Security (stateless API)

For REST APIs, JWT is the standard. Add `spring-boot-starter-oauth2-resource-server`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

```java
@Configuration
@EnableWebSecurity
public class JwtSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .requestMatchers("/api/docs/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
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
          issuer-uri: https://auth.myapp.com/realms/myrealm
```

**application.properties:**

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.myapp.com/realms/myrealm
```

Works with Keycloak, Auth0, Firebase Auth, AWS Cognito, or any OIDC provider.

---

## Monitoring with Spring Actuator

**Maven:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**application.yml:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,caches
  endpoint:
    health:
      show-details: always
```

**application.properties:**

```properties
management.endpoints.web.exposure.include=health,info,metrics,caches
management.endpoint.health.show-details=always
```

### Useful metrics for FlashAPI

```bash
# HTTP request metrics (all FlashAPI endpoints)
curl http://localhost:8080/actuator/metrics/http.server.requests?tag=uri:/api/products

# Cache hit/miss (if using Caffeine)
curl http://localhost:8080/actuator/metrics/cache.gets?tag=cache:flashapi:products

# JPA query count
curl http://localhost:8080/actuator/metrics/hibernate.query.executions

# Connection pool
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

### Custom health indicator

```java
@Component
public class FlashApiHealthIndicator extends AbstractHealthIndicator {

    private final EntityManager em;

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            em.createNativeQuery("SELECT 1").getSingleResult();
            builder.up().withDetail("database", "reachable");
        } catch (Exception e) {
            builder.down().withException(e);
        }
    }
}
```

---

## Logging

FlashAPI uses SLF4J. Configure log levels:

**application.yml:**

```yaml
logging:
  level:
    io.github.hackermanme.flashapi: DEBUG
    io.github.hackermanme.flashapi.controller: TRACE
```

**application.properties:**

```properties
logging.level.io.github.hackermanme.flashapi=DEBUG
logging.level.io.github.hackermanme.flashapi.controller=TRACE
```

| Level | What you see |
|-------|-------------|
| `INFO` (default) | Entity registration, route registration |
| `DEBUG` | Skipped routes (user controller conflicts), cache hits/misses |
| `TRACE` | Full request/response details, query parameters |

---

## Multi-Instance Deployment

### What's per-JVM (not shared)

| Component | Shared? | Multi-instance solution |
|-----------|---------|------------------------|
| Rate limiter | No (in-memory) | Provide custom Redis-backed `FlashRateLimiter` |
| Cache (Simple/Caffeine) | No (in-memory) | Use Redis cache |
| Audit | Yes (database) | Works automatically |
| Soft delete | Yes (database) | Works automatically |

### Rate limiter with Redis

See the [Rate Limiting docs](rate-limiting.md#distributed-rate-limiting) for a complete Redis implementation.

### Cache with Redis

See the [Cache docs](cache.md#redis-for-distributedmulti-instance-production) for Redis cache configuration.

---

## Adding FlashAPI to an Existing Project

### Step 1: Add the dependency

```xml
<dependency>
    <groupId>io.github.hackermanme</groupId>
    <artifactId>spring-flashapi</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2: Add `@EnableFlashApi`

```java
@SpringBootApplication
@EnableFlashApi
public class MyExistingApp { }
```

### Step 3: Annotate entities you want to expose

```java
@Entity
@FlashEntity  // ← add this
public class Product { ... }
```

### What about existing controllers?

FlashAPI checks for route conflicts at startup. If you already have a `@RestController` that maps `/api/products`, FlashAPI backs off for that entity. No conflict, no error.

### What about existing configuration?

FlashAPI reads from the same `application.properties` / `application.yml`. It doesn't require any property to exist — all have sensible defaults.

### Gradual adoption

You can annotate one entity at a time. Start with a simple entity to verify everything works, then expand:

```java
// Week 1: expose one entity
@FlashEntity
public class Category { }

// Week 2: add more
@FlashEntity(softDelete = true)
@FlashAudit
public class Product { }

// Week 3: add the rest
@FlashEntity(cache = true, rateLimit = true)
public class Order { }
```

---

## Custom Response Headers

Add headers to all FlashAPI responses via a filter:

```java
@Component
public class CustomHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Powered-By", "FlashAPI");
        response.setHeader("X-Request-Id", UUID.randomUUID().toString());
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }
}
```

---

## Custom Error Response Format

Replace `FlashExceptionHandler` to change the error format:

```java
@RestControllerAdvice
public class MyExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", 400,
                "error", "Bad Request",
                "message", e.getMessage(),
                "timestamp", Instant.now()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        return ResponseEntity.internalServerError().body(Map.of(
                "status", 500,
                "error", "Internal Server Error",
                "message", "An unexpected error occurred",
                "timestamp", Instant.now()
        ));
    }
}
```

FlashAPI's `FlashExceptionHandler` is created with `@ConditionalOnMissingBean`. If you define your own `@RestControllerAdvice`, Spring picks it up instead.

---

## Testing FlashAPI in Your Project

### Integration test setup

```java
@SpringBootTest
@AutoConfigureMockMvc
class ProductApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void listProducts() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.page").value(0));
    }

    @Test
    void createProduct() throws Exception {
        mvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Test", "price": 9.99}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists());
    }
}
```

### Test properties

**src/test/resources/application.properties:**

```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.jpa.hibernate.ddl-auto=create-drop
spring.cache.type=simple
flashapi.base-path=/api
```

Or **src/test/resources/application.yml:**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
  cache:
    type: simple

flashapi:
  base-path: /api
```

---

## Environment Variables

All FlashAPI properties can be set via environment variables (standard Spring Boot binding):

```bash
export FLASHAPI_BASE_PATH=/api/v2
export FLASHAPI_DEFAULT_PAGE_SIZE=50
export FLASHAPI_MAX_PAGE_SIZE=200
export FLASHAPI_OPENAPI_TITLE="My Production API"
export FLASHAPI_OPENAPI_ENABLED=false
export FLASHAPI_BULK_MAX_ITEMS=500
```

Naming rule: uppercase, dots → underscores, hyphens → removed.

Useful for Docker/Kubernetes deployments where you don't want to modify config files:

```dockerfile
ENV FLASHAPI_BASE_PATH=/api
ENV FLASHAPI_OPENAPI_TITLE="Container API"
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/myapp
```

---

## Docker Deployment

### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/myapp.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### docker-compose.yml

```yaml
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/myapp
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: secret
      FLASHAPI_OPENAPI_TITLE: "My API"
    depends_on:
      - db

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: myapp
      POSTGRES_PASSWORD: secret
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```
