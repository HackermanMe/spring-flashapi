# Bulk Operations

Spring FlashAPI generates bulk endpoints for every `@FlashEntity`, allowing batch create, update, and delete in a single HTTP call. Each item is processed independently with best-effort semantics.

## Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/{entity}/bulk` | Create multiple entities |
| `PUT` | `/api/{entity}/bulk` | Update multiple entities |
| `DELETE` | `/api/{entity}/bulk` | Delete multiple entities by ID |

All bulk endpoints return HTTP 200 with a detailed per-item report, regardless of individual item success or failure.

---

## Bulk Create

Send a JSON array of objects. Each item follows the same rules as a single `POST` (hidden/readOnly fields are ignored, validation applies).

```bash
curl -X POST http://localhost:8080/api/products/bulk \
  -H "Content-Type: application/json" \
  -d '[
    {"name": "Laptop", "price": 999.99, "stock": 50},
    {"name": "Phone", "price": 599.99, "stock": 100},
    {"name": "Tablet", "price": 399.99, "stock": 75}
  ]'
```

**Response (201-style semantics, HTTP 200):**

```json
{
    "success": 3,
    "failed": 0,
    "results": [
        {"index": 0, "status": "created", "data": {"id": 1, "name": "Laptop", "price": 999.99, "stock": 50}},
        {"index": 1, "status": "created", "data": {"id": 2, "name": "Phone", "price": 599.99, "stock": 100}},
        {"index": 2, "status": "created", "data": {"id": 3, "name": "Tablet", "price": 399.99, "stock": 75}}
    ]
}
```

---

## Bulk Update

Send an array of objects, each **must include the ID field**. Only provided fields are updated (partial patch semantics).

```bash
curl -X PUT http://localhost:8080/api/products/bulk \
  -H "Content-Type: application/json" \
  -d '[
    {"id": 1, "name": "Gaming Laptop", "price": 1499.99},
    {"id": 2, "stock": 200}
  ]'
```

**Response:**

```json
{
    "success": 2,
    "failed": 0,
    "results": [
        {"index": 0, "status": "updated", "data": {"id": 1, "name": "Gaming Laptop", "price": 1499.99, "stock": 50}},
        {"index": 1, "status": "updated", "data": {"id": 2, "name": "Phone", "price": 599.99, "stock": 200}}
    ]
}
```

---

## Bulk Delete

Send a JSON array of IDs.

```bash
curl -X DELETE http://localhost:8080/api/products/bulk \
  -H "Content-Type: application/json" \
  -d '[1, 2, 3]'
```

**Response:**

```json
{
    "success": 3,
    "failed": 0,
    "results": [
        {"index": 0, "status": "deleted", "data": {"id": 1}},
        {"index": 1, "status": "deleted", "data": {"id": 2}},
        {"index": 2, "status": "deleted", "data": {"id": 3}}
    ]
}
```

If the entity has `softDelete = true`, bulk delete performs soft delete (same semantics as individual `DELETE`).

---

## Response Format

All bulk operations return the same structure:

```json
{
    "success": <int>,
    "failed": <int>,
    "results": [
        {
            "index": <int>,
            "status": "created" | "updated" | "deleted" | "error",
            "data": { ... } | null,
            "error": "message" | null
        }
    ]
}
```

| Field | Description |
|-------|-------------|
| `success` | Count of items that succeeded |
| `failed` | Count of items that failed |
| `results` | Ordered array matching input indices |
| `results[].index` | Zero-based position in the input array |
| `results[].status` | One of: `created`, `updated`, `deleted`, `error` |
| `results[].data` | Serialized entity on success, `null` on error |
| `results[].error` | Error message on failure, `null` on success |

---

## Partial Success Response Example

When some items succeed and others fail, the response reflects both:

```bash
curl -X POST http://localhost:8080/api/products/bulk \
  -H "Content-Type: application/json" \
  -d '[
    {"name": "Valid Product", "price": 29.99, "stock": 10},
    {"name": "", "price": 19.99, "stock": 5},
    {"name": "Another Valid", "price": 49.99, "stock": 20},
    {"price": 9.99, "stock": 1}
  ]'
```

**Response:**

```json
{
    "success": 2,
    "failed": 2,
    "results": [
        {
            "index": 0,
            "status": "created",
            "data": {"id": 4, "name": "Valid Product", "price": 29.99, "stock": 10}
        },
        {
            "index": 1,
            "status": "error",
            "error": "name: must not be blank"
        },
        {
            "index": 2,
            "status": "created",
            "data": {"id": 5, "name": "Another Valid", "price": 49.99, "stock": 20}
        },
        {
            "index": 3,
            "status": "error",
            "error": "name: must not be null"
        }
    ]
}
```

Items at index 0 and 2 are committed to the database. Items at index 1 and 3 are not. There is no rollback of successful items when others fail.

---

## Using with Transactions (Best-Effort Semantics)

Bulk operations are **not transactional across items**. Each item is processed independently in sequence:

- If item 3 fails validation, items 1 and 2 are already committed
- Each item gets its own persistence flush
- There is no all-or-nothing guarantee

**Why best-effort?**

This matches the behavior of production APIs (Stripe, Shopify, etc.) and avoids the frustration of re-submitting 99 valid items because 1 failed. The response tells you precisely which items succeeded and which failed so your client can retry only the failures.

**If you need all-or-nothing semantics:**

FlashAPI does not provide transactional bulk out of the box. If you need atomic batch behavior, implement a custom service (`FlashCrudOperations<T, ID>`) and wrap your logic in a `@Transactional` method that throws on any failure:

```java
@Service
@Transactional
public class ProductService implements FlashCrudOperations<Product, Long> {
    // Your create/update/delete methods here.
    // If any item fails, the entire transaction rolls back.
}
```

Note that with a custom service, bulk operations still call your `create()`/`update()`/`delete()` method once per item. To get true transactional behavior, you would need to pre-validate all items and throw before any writes.

---

## Performance Tips for Large Batches

**Tune `max-items` to your workload.** The default limit of 100 items is conservative. For lightweight entities (few fields, no cascades), you can safely raise it:

```yaml
# application.yml
flashapi:
  bulk:
    max-items: 500
```

```properties
# application.properties
flashapi.bulk.max-items=500
```

**Batch sizing recommendations:**

| Entity complexity | Suggested max-items | Rationale |
|-------------------|---------------------|-----------|
| Simple (5-10 scalar fields) | 200-500 | Low per-item cost |
| Medium (validation + audit) | 50-100 | Each item triggers audit writes |
| Complex (cascades, custom service logic) | 20-50 | Heavy per-item processing |

**Database-level considerations:**

- Each item in a bulk create/update results in a separate `INSERT`/`UPDATE` statement. FlashAPI does not use JDBC batch inserts directly.
- If audit is enabled, each item generates an additional audit `INSERT`.
- For very large imports (1000+ records), consider splitting into multiple bulk requests or using a dedicated import mechanism outside FlashAPI.

**Connection pool saturation:** A single bulk request holds one database connection for its entire duration. With 500 items, each requiring a separate SQL statement, this can be seconds. Size your connection pool (HikariCP `maximumPoolSize`) accordingly if you expect concurrent bulk requests.

**Client-side chunking pattern:**

```javascript
// Split large arrays into chunks of max-items
const CHUNK_SIZE = 100;
for (let i = 0; i < items.length; i += CHUNK_SIZE) {
    const chunk = items.slice(i, i + CHUNK_SIZE);
    const response = await fetch('/api/products/bulk', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(chunk)
    });
    // Process response, collect failures for retry
}
```

---

## Audit Integration

Each item in a bulk operation generates its own audit entry (if audit is enabled on the entity via `@FlashEntity`):

- Bulk create: one `CREATE` audit entry per successfully created item
- Bulk update: one `UPDATE` audit entry per successfully updated item (with field diffs)
- Bulk delete: one `DELETE` audit entry per successfully deleted item

Failed items do not generate audit entries.

---

## Configuration

### application.yml

```yaml
flashapi:
  bulk:
    max-items: 100    # maximum items per bulk request (default: 100)
```

### application.properties

```properties
flashapi.bulk.max-items=100
```

### Properties Reference

| Property | Default | Description |
|----------|---------|-------------|
| `flashapi.bulk.max-items` | `100` | Maximum number of items in a single bulk request. Returns HTTP 413 if exceeded. Set to `0` to disable the limit (not recommended in production). |

---

## Error Responses

| Case | HTTP Status | Response |
|------|-------------|----------|
| Empty array body `[]` | 400 | Bad Request |
| Body is not an array | 400 | Bad Request |
| Array exceeds `max-items` limit | 413 | Payload Too Large with message |
| Entity has excluded CREATE/UPDATE/DELETE | 405 | Method Not Allowed |

Example 413 response:

```bash
curl -X POST http://localhost:8080/api/products/bulk \
  -H "Content-Type: application/json" \
  -d '[... 150 items ...]'
```

```json
{
    "status": 413,
    "error": "Payload Too Large",
    "message": "Bulk operation limited to 100 items, got 150"
}
```

---

## Usage with Custom Services

If you register a custom service implementing `FlashCrudOperations<T, ID>`, bulk operations delegate to it. Each item goes through your custom `create()`, `update()`, or `delete()` method individually:

```java
@Service
public class ProductService implements FlashCrudOperations<Product, Long> {
    
    @Override
    public Product create(Map<String, Object> body) {
        // Your business logic runs for EVERY item in a bulk create
        // Validation, enrichment, side effects — all apply per item
    }
    
    @Override
    public Optional<Product> update(Long id, Map<String, Object> body) {
        // Runs per item in bulk update
    }
    
    @Override
    public boolean delete(Long id) {
        // Runs per item in bulk delete
    }
}
```

---

## FAQ

**Q: Can I mix creates and updates in one bulk call?**  
No. Use `POST /bulk` for creates and `PUT /bulk` for updates. They are separate endpoints with different semantics.

**Q: Does bulk update support partial patches?**  
Yes. Only the fields present in each item object are updated. Missing fields remain unchanged. The ID field is required to identify the target entity.

**Q: What happens if the same ID appears twice in a bulk update?**  
Both updates are processed sequentially. The second update overwrites the first. Both appear in the response as `"status": "updated"`.

**Q: Are bulk operations atomic?**  
No. Each item is independent. See "Using with Transactions" above for details on how to achieve atomicity if needed.

**Q: Does the `max-items` limit apply per request or globally?**  
Per request. You can send multiple bulk requests concurrently, each up to the limit.

**Q: How do I know which input item caused an error?**  
Each result in the `results` array has an `index` field matching the zero-based position in your input array.

**Q: Does bulk delete support soft delete?**  
Yes. If the entity has `softDelete = true` in its `@FlashEntity` annotation, bulk delete marks items as deleted rather than removing them from the database. Each soft-deleted item can be restored individually via `POST /api/{entity}/{id}/restore`.

**Q: Is there a bulk restore endpoint?**  
No. Restore is only available as a single-item operation.

**Q: What ID types are supported in bulk delete?**  
`Long`, `Integer`, `UUID`, and `String`. The IDs in the JSON array are automatically coerced to the entity's ID type.
