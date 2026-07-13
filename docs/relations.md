# Relations & Expand

Spring FlashAPI automatically detects JPA relationship annotations and allows expanding related entities in API responses via the `?expand` query parameter.

## How it works

FlashAPI scans your entity's fields for JPA relationship annotations at startup:

- `@ManyToOne`
- `@OneToMany`
- `@OneToOne`
- `@ManyToMany`

These fields are **excluded from the default response** (no lazy-loading surprises). They only appear when explicitly requested via `?expand`.

## Usage

### Expand a single relation

```bash
GET /api/products/1?expand=category
```

```json
{
    "data": {
        "id": 1,
        "name": "Laptop",
        "price": 999.99,
        "category": {
            "id": 1,
            "name": "Electronics"
        }
    }
}
```

### Expand multiple relations

```bash
GET /api/products?expand=category,tags
```

### Works on list and detail endpoints

```bash
GET /api/products?expand=category              # list with expand
GET /api/products/1?expand=category            # detail with expand
```

## Behavior

| Scenario | Result |
|----------|--------|
| Relation exists and is loaded | Object or array of objects included |
| Relation is `null` (no FK set) | `"category": null` |
| Expand field doesn't match any relation | Silently ignored |
| No `?expand` param | Relations not included (default) |

## Collection relations

For `@OneToMany` and `@ManyToMany`, the expanded value is an array:

```bash
GET /api/categories/1?expand=products
```

```json
{
    "data": {
        "id": 1,
        "name": "Electronics",
        "products": [
            {"id": 1, "name": "Laptop", "price": 999.99, "stock": 50},
            {"id": 2, "name": "Phone", "price": 599.99, "stock": 100}
        ]
    }
}
```

## Depth control

To prevent infinite recursion (e.g., Product → Category → Products → Category...), FlashAPI limits expand depth.

```yaml
flashapi:
  relations:
    max-depth: 1    # default: 1 (expand target entity fields only, no nested expand)
```

With `max-depth: 1`, expanding a product's category shows the category's scalar fields but does NOT recursively expand the category's own relations.

## What gets serialized in expanded entities

When an entity is expanded, FlashAPI serializes:
- All non-static, non-transient scalar fields
- Relationship fields are **excluded** (no recursive expansion beyond max-depth)

This means expanded objects are always "flat" — they contain their own data but not their sub-relations.

## Configuration

```yaml
flashapi:
  relations:
    max-depth: 1    # how deep expand can recurse (default: 1)
```

| Property | Default | Description |
|----------|---------|-------------|
| `flashapi.relations.max-depth` | `1` | Maximum depth for nested expansion. `1` = expand direct relations only. |

## Fetch strategy note

FlashAPI does NOT override your JPA fetch strategy. If a relation is `LAZY` (the default), expanding it will trigger a query. For list endpoints, this can cause N+1 queries.

**Recommendations:**
- For frequently expanded relations, consider `@ManyToOne(fetch = FetchType.EAGER)` if the relation is small
- For large collections (`@OneToMany`), keep `LAZY` and only expand when needed
- Future versions may support `JOIN FETCH` optimization automatically
