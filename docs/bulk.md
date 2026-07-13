# Bulk Operations

Spring FlashAPI generates bulk endpoints for every `@FlashEntity`, allowing batch create, update, and delete in a single HTTP call.

## Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/{entity}/bulk` | Create multiple entities |
| `PUT` | `/api/{entity}/bulk` | Update multiple entities |
| `DELETE` | `/api/{entity}/bulk` | Delete multiple entities by ID |

## Bulk Create

Send an array of objects. Each item follows the same rules as a single POST (hidden/readOnly fields are ignored, validation applies).

```bash
POST /api/products/bulk
Content-Type: application/json

[
    {"name": "Laptop", "price": 999.99, "stock": 50},
    {"name": "Phone", "price": 599.99, "stock": 100},
    {"name": "Tablet", "price": 399.99, "stock": 75}
]
```

## Bulk Update

Send an array of objects, each **must include the ID field**. Only provided fields are updated.

```bash
PUT /api/products/bulk
Content-Type: application/json

[
    {"id": 1, "name": "Gaming Laptop", "price": 1499.99},
    {"id": 2, "stock": 200}
]
```

## Bulk Delete

Send an array of IDs.

```bash
DELETE /api/products/bulk
Content-Type: application/json

[1, 2, 3]
```

If the entity has `softDelete = true`, bulk delete performs soft delete (same as individual DELETE).

## Response format

All bulk operations return a detailed report per item:

```json
{
    "success": 2,
    "failed": 1,
    "results": [
        {
            "index": 0,
            "status": "created",
            "data": {"id": 1, "name": "Laptop", "price": 999.99, "stock": 50}
        },
        {
            "index": 1,
            "status": "created",
            "data": {"id": 2, "name": "Phone", "price": 599.99, "stock": 100}
        },
        {
            "index": 2,
            "status": "error",
            "error": "name: must not be blank"
        }
    ]
}
```

### Result statuses

| Status | Meaning |
|--------|---------|
| `created` | Entity successfully created (bulk create) |
| `updated` | Entity successfully updated (bulk update) |
| `deleted` | Entity successfully deleted (bulk delete) |
| `error` | Operation failed — see `error` field for details |

## Best-effort semantics

Bulk operations are **not transactional across items**. Each item is processed independently:

- If item 3 fails, items 1 and 2 are still committed
- The response tells you exactly which items succeeded and which failed
- Failed items include a descriptive error message

This matches the behavior of modern APIs (Stripe, Shopify, etc.) and avoids the frustration of having to re-submit 99 items because 1 failed.

## Audit integration

Each item in a bulk operation generates its own audit entry (if audit is enabled on the entity). Bulk create generates CREATE entries, bulk update generates UPDATE entries with field diffs, and bulk delete generates DELETE entries.

## Configuration

```yaml
flashapi:
  bulk:
    max-items: 100    # maximum items per bulk request
```

| Property | Default | Description |
|----------|---------|-------------|
| `flashapi.bulk.max-items` | `100` | Maximum number of items in a single bulk request. Returns HTTP 413 if exceeded. |

## Error responses

| Case | HTTP Status | Description |
|------|-------------|-------------|
| Empty array body | 400 | Bad Request |
| Invalid body format | 400 | Bad Request |
| Exceeds `max-items` limit | 413 | Payload Too Large |
| Entity has no CREATE/UPDATE/DELETE access | 405 | Method Not Allowed |

## Usage with custom services

If you have a custom service (`FlashCrudOperations<T, ID>`), bulk operations delegate to it — each item goes through your custom `create()`, `update()`, or `delete()` method. Your business logic applies to every item in the batch.
