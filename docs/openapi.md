# OpenAPI Documentation

FlashAPI automatically generates a complete OpenAPI 3.0.3 specification and serves a Swagger UI for every entity endpoint -- zero configuration, zero extra dependencies.

## Endpoints

| URL | Description |
|-----|-------------|
| `GET /api/docs` | Swagger UI (interactive documentation) |
| `GET /api/docs/index.html` | Swagger UI (alternative URL) |
| `GET /api/docs/openapi.json` | Raw OpenAPI 3.0.3 JSON specification |

The base path (`/api/docs`) is configurable via `flashapi.openapi.docs-path`.

## What Gets Documented

The spec is built from the same `EntityMetadata` used for route registration **plus all detected `@RestController` classes**, so it is always in sync with your actual API:

### @FlashEntity endpoints (auto-generated)

- **CRUD operations** -- GET (list), GET by ID, POST, PUT, DELETE
- **Pagination parameters** -- `page`, `size`, `sort`, `search`, `expand`
- **Export endpoint** -- `GET /{entity}/export?format=csv|xlsx|pdf`
- **Bulk operations** -- POST/PUT/DELETE on `/{entity}/bulk`
- **Soft delete restore** -- `POST /{entity}/{id}/restore` (when `softDelete = true`)
- **Audit history** -- `GET /{entity}/{id}/history` (when audit is enabled)
- **Schemas** -- `{Entity}Response`, `{Entity}ListResponse`, `{Entity}CreateInput`, `{Entity}UpdateInput`
- **Field types** -- mapped from Java types (see table below)
- **Required fields** -- non-nullable fields marked as `required` in create input
- **Tags** -- operations grouped by entity name
- **Operation IDs** -- e.g., `listProduct`, `createProduct`, `getProductById`

### Custom @RestController endpoints (auto-detected)

FlashAPI **automatically detects** all `@RestController` classes in your application and includes their endpoints in the same Swagger UI. No extra dependency (springdoc, etc.) is needed.

What gets extracted:
- **Path and HTTP method** -- from `@RequestMapping`, `@GetMapping`, `@PostMapping`, etc.
- **Path parameters** -- from `@PathVariable`
- **Query parameters** -- from `@RequestParam`
- **Request body** -- from `@RequestBody` (DTO fields are introspected)
- **Return type** -- from `ResponseEntity<T>` generic type (DTO fields are introspected)
- **Tags** -- derived from controller class name (e.g., `AuthController` -> tag "Auth")
- **Operation IDs** -- method name (e.g., `register`, `login`)

Excluded from scanning:
- FlashAPI's own internal controllers (dashboard, docs)
- Spring's `/error` endpoint

## Configuration

### application.yml

```yaml
flashapi:
  openapi:
    enabled: true                          # default: true
    title: "My API"                        # default: "FlashAPI"
    version: "2.0.0"                       # default: "1.0.0"
    description: "My app's REST API"       # default: "Auto-generated REST API documentation"
    docs-path: /api/docs                   # default: /api/docs
```

### application.properties

```properties
flashapi.openapi.enabled=true
flashapi.openapi.title=My API
flashapi.openapi.version=2.0.0
flashapi.openapi.description=My app's REST API
flashapi.openapi.docs-path=/api/docs
```

## Disabling

### application.yml

```yaml
flashapi:
  openapi:
    enabled: false
```

### application.properties

```properties
flashapi.openapi.enabled=false
```

## How It Works

1. At startup, `OpenApiGenerator` builds the spec from all registered `EntityMetadata`.
2. The resulting JSON is generated once and cached in memory (volatile field, lazy-serialized).
3. Swagger UI HTML is served inline -- it loads the Swagger UI bundle from the unpkg CDN (`https://unpkg.com/swagger-ui-dist@5`). No JAR-bundled assets, no `springdoc` or `swagger-core` dependency required.
4. The spec URL is resolved relative to the UI path, so custom `docs-path` values work automatically.

## Type Mapping

| Java Type | OpenAPI Type | Format |
|-----------|-------------|--------|
| `String` | string | -- |
| `Integer` / `int` | integer | int32 |
| `Long` / `long` | integer | int64 |
| `Float` / `float` | number | float |
| `Double` / `double` | number | double |
| `BigDecimal` | number | -- |
| `Boolean` / `boolean` | boolean | -- |
| `UUID` | string | uuid |
| `LocalDate` | string | date |
| `LocalDateTime` | string | date-time |
| `OffsetDateTime` | string | date-time |
| Enum types | string | -- |
| Any other | object | -- |

Additional schema attributes:
- `maxLength` -- set when `@Column(length = ...)` or `@Size(max = ...)` is detected.
- `nullable: true` -- set when the field allows null.

## Customizing the Spec

FlashAPI builds the `info` block from properties. Here is how each property maps to the OpenAPI spec:

| Property | OpenAPI Field | Example |
|----------|--------------|---------|
| `flashapi.openapi.title` | `info.title` | `"Acme Commerce API"` |
| `flashapi.openapi.version` | `info.version` | `"3.1.0"` |
| `flashapi.openapi.description` | `info.description` | `"Product catalog and order management"` |
| `flashapi.base-path` | Prefix for all paths | `/api` -> paths start with `/api/products` |
| `@FlashEntity(path = "products")` | Path segment | `/api/products`, `/api/products/{id}` |

### Entity-level control

The `@FlashEntity` annotation controls what appears in the spec:

```java
@Entity
@FlashEntity(path = "products", exclude = {"DELETE"})
public class Product { ... }
```

This generates GET/POST/PUT but no DELETE operation in the spec. The `only` and `readonly` shortcuts work identically:

```java
@FlashEntity(readonly = true) // only LIST + READ in the spec
```

### Field-level control

- Fields annotated with `@JsonIgnore` or marked as non-visible are excluded from schemas.
- The ID field appears in response schemas but not in create/update input schemas.
- Non-nullable fields without defaults are listed in `required` for create input only.

## Using the Spec with Code Generators

The generated `openapi.json` is fully compatible with [openapi-generator](https://openapi-generator.tech/) for client SDK generation.

### Downloading the spec

```bash
curl -o openapi.json http://localhost:8080/api/docs/openapi.json
```

### Generating a TypeScript client

```bash
npx @openapitools/openapi-generator-cli generate \
  -i http://localhost:8080/api/docs/openapi.json \
  -g typescript-axios \
  -o ./generated-client
```

### Generating a Java client

```bash
openapi-generator generate \
  -i http://localhost:8080/api/docs/openapi.json \
  -g java \
  --library okhttp-gson \
  -o ./java-client
```

### CI integration

Add a build step that fetches the spec and regenerates the client on every release:

```bash
# In your CI pipeline
curl -sf http://localhost:8080/api/docs/openapi.json > openapi.json
openapi-generator generate -i openapi.json -g kotlin -o client/
```

### Supported generators

Any generator that accepts OpenAPI 3.0.x input works: `typescript-fetch`, `python`, `go`, `swift5`, `csharp`, `rust`, `dart`, etc. See the [full list](https://openapi-generator.tech/docs/generators).

## Custom Controllers (Auth, etc.)

FlashAPI **automatically detects and documents** all `@RestController` classes in the same Swagger UI. No extra dependency needed — no springdoc, no swagger-core, nothing to add to your pom.xml.

### How it works

At startup, FlashAPI scans all registered `@RestController` beans (excluding its own internal controllers). It extracts:
- Path mappings and HTTP methods
- `@PathVariable` and `@RequestParam` parameters
- `@RequestBody` DTO type (fields introspected via reflection)
- Return type from `ResponseEntity<T>` (fields introspected via reflection)

### Example: AuthController

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) { ... }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) { ... }
}
```

This produces in `/api/docs`:
- `POST /api/auth/register` — tag "Auth", request body schema from `RegisterRequest`, response schema from `AuthResponse`
- `POST /api/auth/login` — tag "Auth", request body schema from `LoginRequest`, response schema from `AuthResponse`

No annotations from `io.swagger.v3` needed. Just standard Spring annotations.

### Securing custom controllers

Custom controllers are secured via Spring Security's `SecurityFilterChain`, not via `@FlashSecured`:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/**").permitAll()     // Auth endpoints open
            .requestMatchers("/api/docs/**").permitAll()     // FlashAPI Swagger UI
            .requestMatchers("/api/dashboard/**").hasRole("ADMIN")
            .requestMatchers("/api/**").authenticated()      // Everything else protected
        )
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

### Summary: what documents what

| Endpoint type | Documented by | Secured by |
|---------------|---------------|------------|
| `@FlashEntity` endpoints | FlashAPI OpenAPI (`/api/docs`) | `@FlashSecured` + Spring Security |
| Custom `@RestController` | FlashAPI OpenAPI (`/api/docs`) | Spring Security's `SecurityFilterChain` |

Everything in one place. One Swagger UI. Zero extra dependencies.

## Swagger UI CDN Details

Swagger UI assets are loaded from `https://unpkg.com/swagger-ui-dist@5`:
- `swagger-ui.css`
- `swagger-ui-bundle.js`

This means:
- No additional Maven/Gradle dependency needed.
- The UI always uses the latest Swagger UI 5.x patch.
- Requires internet access from the browser (not the server). For air-gapped environments, self-host the assets and override the HTML template by providing your own `OpenApiController` bean.

## FAQ

**Q: Are my custom @RestController endpoints included?**
A: Yes. FlashAPI automatically detects all `@RestController` classes in your application and includes them in the same spec. No extra dependency needed.

**Q: Is the spec regenerated on every request?**
A: No. The spec is built once at startup. The JSON string is lazily serialized on first request and cached. There is no per-request cost.

**Q: Can I add authentication info (securitySchemes) to the spec?**
A: Not via configuration. If you need security schemes in the spec, export `openapi.json`, add them manually, and serve the modified file -- or merge via springdoc (see above).

**Q: Does the UI work behind a reverse proxy with a context path?**
A: Yes. The spec URL is resolved relative to the request URI. If your app is behind `/myapp`, the UI at `/myapp/api/docs` will correctly reference `/myapp/api/docs/openapi.json`.

**Q: What if I have 50 entities -- will the spec be huge?**
A: The spec scales linearly. 50 entities produce roughly 5000 lines of JSON. Swagger UI handles this without issues. The in-memory footprint is a single cached String.

**Q: Can I change the Swagger UI version?**
A: Not via configuration. The CDN URL is hardcoded to `swagger-ui-dist@5`. To pin a specific version, provide a custom `OpenApiController` bean that overrides `serveUi`.
