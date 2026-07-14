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

The spec is built from the same `EntityMetadata` used for route registration, so it is always in sync with your actual API:

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

## Integration with springdoc-openapi

FlashAPI's OpenAPI documentation is completely independent of springdoc-openapi. Both can coexist in the same application without conflict.

### Side-by-side setup

| Concern | FlashAPI | springdoc-openapi |
|---------|----------|-------------------|
| Default UI path | `/api/docs` | `/swagger-ui.html` |
| Spec path | `/api/docs/openapi.json` | `/v3/api-docs` |
| Source | `@FlashEntity`-annotated entities | `@RestController`-annotated classes |
| Dependencies | None (built-in) | `springdoc-openapi-starter-webmvc-ui` |

### Merging both specs

If you want a single unified spec (FlashAPI entities + hand-written controllers), disable FlashAPI's built-in docs and feed its spec into springdoc:

1. Disable FlashAPI's UI:

```yaml
flashapi:
  openapi:
    enabled: false
```

2. Expose FlashAPI's spec as a springdoc `GroupedOpenApi`:

```java
@Bean
public GroupedOpenApi flashApiGroup(FlashProperties props, List<EntityMetadata> entities) {
    OpenApiGenerator gen = new OpenApiGenerator(props, entities);
    Map<String, Object> spec = gen.generate();
    // Convert to springdoc's OpenAPI model or use OpenApiCustomizer
    return GroupedOpenApi.builder()
            .group("flash")
            .addOpenApiCustomizer(openApi -> {
                // Merge paths and schemas from FlashAPI spec into openApi
            })
            .build();
}
```

Alternatively, simply link to both UIs from your application landing page -- most teams find separate specs easier to maintain.

### Using springdoc only

To disable FlashAPI docs entirely and rely on springdoc:

```yaml
# application.yml
flashapi:
  openapi:
    enabled: false
```

```properties
# application.properties
flashapi.openapi.enabled=false
```

## Swagger UI CDN Details

Swagger UI assets are loaded from `https://unpkg.com/swagger-ui-dist@5`:
- `swagger-ui.css`
- `swagger-ui-bundle.js`

This means:
- No additional Maven/Gradle dependency needed.
- The UI always uses the latest Swagger UI 5.x patch.
- Requires internet access from the browser (not the server). For air-gapped environments, self-host the assets and override the HTML template by providing your own `OpenApiController` bean.

## FAQ

**Q: Can I add custom endpoints to the generated spec?**
A: Not directly. FlashAPI only documents its own generated endpoints. For custom controllers, use springdoc-openapi alongside FlashAPI (see integration section above).

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
