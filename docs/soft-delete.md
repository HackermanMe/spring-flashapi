# Soft Delete

Soft delete marks entities as deleted without removing them from the database. Instead of `DELETE FROM ...`, FlashAPI sets a timestamp on a configurable column.

## Setup

### 1. Add the timestamp field to your entity

```java
@Entity
@FlashEntity(softDelete = true)
public class Order {
    @Id @GeneratedValue
    private Long id;

    private String reference;

    private Instant deletedAt;  // FlashAPI uses this field
}
```

### 2. Configure the column name (optional)

By default, FlashAPI looks for a field named `deletedAt`. You can change this globally:

```yaml
flashapi:
  soft-delete:
    column-name: deletedAt   # default
```

## Behavior

When `softDelete = true` on a `@FlashEntity`:

| Operation | What happens |
|-----------|-------------|
| `DELETE /api/orders/{id}` | Sets `deletedAt = now()` instead of removing the row |
| `GET /api/orders` | Excludes entities where `deletedAt IS NOT NULL` |
| `GET /api/orders?deleted=true` | Shows only soft-deleted entities |
| `POST /api/orders/{id}/restore` | Sets `deletedAt = null`, bringing the entity back |

## How it works internally

- On **delete**: `SoftDeleteHandler` sets the `deletedAt` field to `Instant.now()` via reflection.
- On **list/read**: a `WHERE deletedAt IS NULL` predicate is automatically added to all queries.
- On **restore**: `SoftDeleteHandler` sets `deletedAt` back to `null`.

No extra repository, no custom query — it's built into the CRUD pipeline.

## Viewing deleted entities

Pass `?deleted=true` to any list endpoint:

```
GET /api/orders?deleted=true
```

This returns **only** soft-deleted entities (where `deletedAt IS NOT NULL`).

## Restoring a deleted entity

```
POST /api/orders/42/restore
```

Response: `204 No Content` on success, `404` if not found, `400` if soft delete is not enabled for this entity.

## Important notes

- The `deletedAt` field must exist on the entity class. If `softDelete = true` but the field is missing, FlashAPI throws an error at startup with a clear message.
- Soft-deleted entities are excluded from all queries by default — including filter queries and count queries.
- Audit trail records soft deletes as `DELETE` actions and restores as `UPDATE` actions.
- Hard delete is still possible programmatically via `EntityManager.remove()` in a custom service — FlashAPI only intercepts its own endpoints.
