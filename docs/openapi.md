# OpenAPI Documentation

FlashAPI automatically generates a complete OpenAPI 3.0 specification and serves a Swagger UI — zero configuration, zero dependencies.

## Endpoints

| URL | Description |
|-----|-------------|
| `GET /api/docs` | Swagger UI (interactive documentation) |
| `GET /api/docs/index.html` | Swagger UI (alternative URL) |
| `GET /api/docs/openapi.json` | Raw OpenAPI 3.0 JSON specification |

## What's Included

The generated spec documents every endpoint FlashAPI creates for your entities:

- **CRUD operations** — GET list, GET by ID, POST, PUT, DELETE
- **Pagination parameters** — page, size, sort, search, expand
- **Export endpoint** — with format enum (csv, xlsx, pdf)
- **Bulk operations** — POST/PUT/DELETE on /bulk
- **Soft delete restore** — POST /{id}/restore (when enabled)
- **Audit history** — GET /{id}/history (when enabled)
- **Schemas** — response, list response, create input, update input per entity
- **Field types** — mapped from Java types (String → string, Long → integer/int64, etc.)
- **Required fields** — non-nullable fields marked as required in create input
- **Tags** — operations grouped by entity name

## Configuration

```yaml
flashapi:
  openapi:
    enabled: true                          # default: true
    title: "My API"                        # default: "FlashAPI"
    version: "2.0.0"                       # default: "1.0.0"
    description: "My app's REST API"       # default: "Auto-generated REST API documentation"
    docs-path: /api/docs                   # default: /api/docs
```

## Disabling

```yaml
flashapi:
  openapi:
    enabled: false
```

## How It Works

- At startup, FlashAPI builds the OpenAPI spec from the same `EntityMetadata` used for route registration
- The spec JSON is generated once and cached in memory
- Swagger UI is served from unpkg CDN (no bundled assets, no extra dependency)
- The spec includes all entities, their fields, types, and operations

## Type Mapping

| Java Type | OpenAPI Type | Format |
|-----------|-------------|--------|
| String | string | — |
| Integer/int | integer | int32 |
| Long/long | integer | int64 |
| Float/float | number | float |
| Double/double | number | double |
| BigDecimal | number | — |
| Boolean/boolean | boolean | — |
| UUID | string | uuid |
| LocalDate | string | date |
| LocalDateTime | string | date-time |
| OffsetDateTime | string | date-time |
| Enum | string | — |

## Integration with springdoc-openapi

FlashAPI's built-in OpenAPI documentation is independent of springdoc-openapi. If you already use springdoc for your hand-written controllers, both can coexist. FlashAPI registers its own docs path (`/api/docs`) which doesn't conflict with springdoc's default (`/swagger-ui.html`).

To disable FlashAPI's docs when using springdoc exclusively:

```yaml
flashapi:
  openapi:
    enabled: false
```
