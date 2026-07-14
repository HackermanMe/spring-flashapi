# Soft Delete

Soft delete marks entities as deleted without removing them from the database. Instead of issuing `DELETE FROM ...`, FlashAPI sets an `Instant` timestamp on a configurable column. Soft-deleted entities are excluded from all queries by default, but can be listed and restored through dedicated endpoints.

---

## Quick Start

### 1. Add the timestamp field to your entity

```java
@Entity
@FlashEntity(softDelete = true)
public class Order {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reference;
    private BigDecimal total;

    private Instant deletedAt;  // FlashAPI uses this field
}
```

### 2. Run your application

FlashAPI detects `softDelete = true`, verifies the field exists, and wires the soft-delete logic into the CRUD pipeline. No additional configuration required.

---

## Configuration

### application.yml

```yaml
flashapi:
  soft-delete:
    column-name: deleted_at   # Java field name to use (default: deleted_at)
```

### application.properties

```properties
flashapi.soft-delete.column-name=deleted_at
```

The `column-name` value must match the **Java field name** declared in your entity class.

---

## Endpoint Behavior

| Operation                         | HTTP                          | What happens                                      |
|-----------------------------------|-------------------------------|---------------------------------------------------|
| Delete                            | `DELETE /api/orders/{id}`     | Sets `deletedAt = Instant.now()` (row preserved)  |
| List (default)                    | `GET /api/orders`             | Excludes rows where `deletedAt IS NOT NULL`       |
| List deleted only                 | `GET /api/orders?deleted=true`| Shows only soft-deleted rows                      |
| Restore                           | `POST /api/orders/{id}/restore` | Sets `deletedAt = null`                        |

---

## Full Lifecycle Example

### Create an order

```bash
curl -s -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"reference": "ORD-001", "total": 250.00}' | jq .
```

```json
{
  "data": {
    "id": 1,
    "reference": "ORD-001",
    "total": 250.00,
    "deletedAt": null
  }
}
```

### Soft-delete the order

```bash
curl -s -X DELETE http://localhost:8080/api/orders/1 -w "\nHTTP %{http_code}\n"
```

```
HTTP 204
```

### List orders (soft-deleted excluded)

```bash
curl -s http://localhost:8080/api/orders | jq .
```

```json
{
  "data": [],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

### List only soft-deleted orders

```bash
curl -s "http://localhost:8080/api/orders?deleted=true" | jq .
```

```json
{
  "data": [
    {
      "id": 1,
      "reference": "ORD-001",
      "total": 250.00,
      "deletedAt": "2026-03-20T14:30:00Z"
    }
  ],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### Restore the order

```bash
curl -s -X POST http://localhost:8080/api/orders/1/restore -w "\nHTTP %{http_code}\n"
```

```
HTTP 204
```

### Verify restoration

```bash
curl -s http://localhost:8080/api/orders | jq .
```

```json
{
  "data": [
    {
      "id": 1,
      "reference": "ORD-001",
      "total": 250.00,
      "deletedAt": null
    }
  ],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## How It Works Internally

1. **On delete:** `SoftDeleteHandler` sets the `deletedAt` field to `Instant.now()` via reflection, then merges and flushes the entity.
2. **On list/read:** A `WHERE deleted_at IS NULL` predicate is automatically appended to all Criteria API queries via `SoftDeleteHandler.notDeleted()`.
3. **On `?deleted=true`:** The predicate flips to `WHERE deleted_at IS NOT NULL` via `SoftDeleteHandler.onlyDeleted()`.
4. **On restore:** `SoftDeleteHandler` sets `deletedAt` back to `null`, merges, and flushes.

No extra repository, no custom query. It is built into the CRUD pipeline.

---

## Database Migrations

For production environments where Hibernate DDL auto-generation is disabled, add the column manually.

### PostgreSQL

```sql
ALTER TABLE orders ADD COLUMN deleted_at TIMESTAMPTZ;
CREATE INDEX idx_orders_deleted_at ON orders (deleted_at);
```

### MySQL

```sql
ALTER TABLE orders ADD COLUMN deleted_at TIMESTAMP(6) NULL;
CREATE INDEX idx_orders_deleted_at ON orders (deleted_at);
```

### Flyway migration example

```sql
-- V3__add_soft_delete_to_orders.sql
ALTER TABLE orders ADD COLUMN deleted_at TIMESTAMP(6) NULL;
CREATE INDEX idx_orders_deleted_at ON orders (deleted_at);
```

### Liquibase changeset example

```xml
<changeSet id="3" author="dev">
    <addColumn tableName="orders">
        <column name="deleted_at" type="TIMESTAMP(6)"/>
    </addColumn>
    <createIndex tableName="orders" indexName="idx_orders_deleted_at">
        <column name="deleted_at"/>
    </createIndex>
</changeSet>
```

The index on `deleted_at` is recommended for performance -- it accelerates the `IS NULL` / `IS NOT NULL` filter applied to every query.

---

## Soft Delete and Unique Constraints

This is a common gotcha. Consider a `username` column with a unique constraint:

```sql
ALTER TABLE users ADD CONSTRAINT uq_username UNIQUE (username);
```

If user `alice` is soft-deleted (`deleted_at = '2026-01-15T...'`), trying to create a new user with `username = 'alice'` will violate the constraint -- the row still exists in the database.

### Solutions

**Option 1: Partial unique index (PostgreSQL)**

```sql
CREATE UNIQUE INDEX uq_username_active ON users (username) WHERE deleted_at IS NULL;
```

Only enforces uniqueness among non-deleted rows. This is the cleanest approach.

**Option 2: Composite unique constraint**

```sql
ALTER TABLE users ADD CONSTRAINT uq_username_deleted UNIQUE (username, deleted_at);
```

This works because `NULL` values are considered distinct in most databases. Two soft-deleted rows with the same username but different `deleted_at` timestamps do not conflict. However, only one active (non-deleted) row can exist per username.

**Option 3: Sentinel value approach (MySQL, where partial indexes are unavailable)**

Instead of `NULL`, use a sentinel value for active records and the deletion timestamp for deleted ones. This requires custom handling outside FlashAPI's default behavior.

**Recommendation:** Use partial unique indexes if your database supports them (PostgreSQL, SQL Server). For MySQL, use composite unique constraints.

---

## Permanently Purging Soft-Deleted Records

FlashAPI does not provide a built-in purge endpoint. To permanently remove soft-deleted records, use direct database operations or a scheduled job.

### Manual purge via SQL

```sql
-- Delete all soft-deleted orders older than 30 days
DELETE FROM orders
WHERE deleted_at IS NOT NULL
  AND deleted_at < NOW() - INTERVAL '30 days';
```

### Scheduled purge with Spring

```java
@Component
public class SoftDeletePurgeJob {

    private final EntityManager entityManager;

    public SoftDeletePurgeJob(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Scheduled(cron = "0 0 3 * * *") // daily at 3 AM
    @Transactional
    public void purgeOldRecords() {
        Instant threshold = Instant.now().minus(Duration.ofDays(30));

        int deleted = entityManager.createQuery(
                "DELETE FROM Order o WHERE o.deletedAt IS NOT NULL AND o.deletedAt < :threshold")
            .setParameter("threshold", threshold)
            .executeUpdate();

        log.info("Purged {} soft-deleted orders older than 30 days", deleted);
    }
}
```

### Purge with audit trail cleanup

If the entity has audit enabled, you may want to purge audit entries alongside:

```java
@Transactional
public void purgeWithAudit(Instant threshold) {
    // Collect IDs to purge
    List<String> ids = entityManager.createQuery(
            "SELECT CAST(o.id AS string) FROM Order o WHERE o.deletedAt < :t", String.class)
        .setParameter("t", threshold)
        .getResultList();

    if (ids.isEmpty()) return;

    // Remove audit entries
    entityManager.createQuery(
            "DELETE FROM AuditEntry a WHERE a.entityType = 'Order' AND a.entityId IN :ids")
        .setParameter("ids", ids)
        .executeUpdate();

    // Remove the entities
    entityManager.createQuery(
            "DELETE FROM Order o WHERE o.deletedAt < :t")
        .setParameter("t", threshold)
        .executeUpdate();
}
```

---

## Restore Endpoint Details

```
POST /api/{entity-path}/{id}/restore
```

| Response code     | Condition                                   |
|-------------------|---------------------------------------------|
| `204 No Content`  | Entity restored successfully                |
| `404 Not Found`   | Entity with given ID does not exist         |
| `400 Bad Request`  | Soft delete is not enabled for this entity |

---

## Important Behaviors

- The `deletedAt` field **must** exist on the entity class. If `softDelete = true` but the field is missing, FlashAPI throws a clear error at startup:
  > `Entity Order has softDelete=true but no field named 'deleted_at'. Add: private Instant deleted_at;`
- Soft-deleted entities are excluded from **all** queries by default, including filter queries, search queries, and count operations.
- Audit trail (if enabled) records soft deletes as `DELETE` actions and restores as `UPDATE` actions.
- Hard delete is still possible programmatically via `EntityManager.remove()` in a custom service. FlashAPI only intercepts its own endpoints.
- The `?deleted=true` parameter shows **only** deleted entities. There is no parameter to show both active and deleted in a single query through the FlashAPI endpoints.

---

## FAQ

**Q: Can I use a `Boolean` field instead of `Instant`?**

No. FlashAPI requires an `Instant` field. The timestamp records when the deletion occurred, which is valuable for retention policies and purge jobs.

**Q: Can I customize the field name per entity?**

The field name is configured globally via `flashapi.soft-delete.column-name`. All entities with `softDelete = true` must use the same field name.

**Q: Does soft delete work with `@FlashAudit`?**

Yes. A soft delete produces a `DELETE` audit entry. A restore produces an `UPDATE` audit entry. The full lifecycle is captured.

**Q: Can I still hard-delete an entity?**

Not through FlashAPI's REST endpoints. If `softDelete = true`, the `DELETE` endpoint always soft-deletes. For permanent removal, use direct JPA (`EntityManager.remove()`) or native SQL in a custom service or scheduled job.

**Q: What happens if I call restore on an entity that is not soft-deleted?**

The entity is found (it exists in the database), `deletedAt` is already `null`, so setting it to `null` is a no-op. The endpoint returns `204 No Content`.

**Q: Does `GET /api/orders/{id}` return a soft-deleted entity?**

Yes. The single-entity `findById` lookup uses `EntityManager.find()` which does not apply the soft-delete predicate. This is by design -- it allows the restore endpoint to locate the entity. If you need to hide soft-deleted entities from direct ID lookups, implement a custom `@FlashService`.

**Q: How does soft delete interact with relationships (cascading)?**

FlashAPI soft-deletes only the targeted entity. It does not cascade soft-delete to related entities. If you need cascade behavior, implement it in a custom `@FlashService`.

**Q: What index should I add for performance?**

Add an index on the `deleted_at` column. Every list query filters on this column, so an index avoids full table scans:

```sql
CREATE INDEX idx_orders_deleted_at ON orders (deleted_at);
```

For PostgreSQL, a partial index is even more efficient if most records are active:

```sql
CREATE INDEX idx_orders_soft_deleted ON orders (id) WHERE deleted_at IS NOT NULL;
```
