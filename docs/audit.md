# Audit Trail

FlashAPI can automatically record who modified what, when, and how. The audit trail is stored in a JPA-managed table alongside your data.

## Setup

### 1. Enable audit on your entity

```java
@Entity
@FlashEntity
@FlashAudit(trackFields = true)
public class Invoice {
    @Id @GeneratedValue
    private Long id;

    private BigDecimal amount;
    private String status;
    private String clientName;
}
```

### 2. Run your app

FlashAPI auto-creates the audit table via JPA (Hibernate `ddl-auto`). No manual migration needed for development.

## Configuration

```yaml
flashapi:
  audit:
    enabled: true                      # Global toggle (default: true)
    table-name: flash_audit_entry      # Table name (default: flash_audit_entry)
```

## Annotation options

| Attribute | Default | Description |
|-----------|---------|-------------|
| `enabled` | `true` | Enable/disable audit for this entity |
| `trackFields` | `false` | Record field-level diffs (old → new) |

## What gets recorded

### Without `trackFields`

Each operation produces one audit entry:

```json
{
  "entityType": "Invoice",
  "entityId": "42",
  "action": "UPDATE",
  "performedBy": "john.doe",
  "timestamp": "2026-03-20T14:22:00Z"
}
```

### With `trackFields = true`

Each changed field produces its own entry with old/new values:

```json
[
  {
    "entityType": "Invoice",
    "entityId": "42",
    "action": "UPDATE",
    "field": "status",
    "oldValue": "DRAFT",
    "newValue": "SENT",
    "performedBy": "john.doe",
    "timestamp": "2026-03-20T14:22:00Z"
  },
  {
    "entityType": "Invoice",
    "entityId": "42",
    "action": "UPDATE",
    "field": "amount",
    "oldValue": "150.00",
    "newValue": "175.50",
    "performedBy": "john.doe",
    "timestamp": "2026-03-20T14:22:00Z"
  }
]
```

## Querying audit history

```
GET /api/invoices/42/history
```

Returns all audit entries for entity ID 42, ordered by most recent first.

## User resolution

FlashAPI resolves the current user automatically:

1. **Spring Security present**: reads `SecurityContextHolder.getContext().getAuthentication().getName()`
2. **No Spring Security**: falls back to `"anonymous"`

No configuration needed. If you add Spring Security later, audit automatically picks it up.

## Audit entity schema

The `flash_audit_entry` table has the following columns:

| Column | Type | Description |
|--------|------|-------------|
| `id` | Long (auto) | Primary key |
| `entity_type` | String | Entity class name |
| `entity_id` | String | Primary key value (stored as String for type-agnostic support) |
| `action` | Enum | `CREATE`, `UPDATE`, or `DELETE` |
| `field` | String (nullable) | Field name (only with `trackFields`) |
| `old_value` | String (nullable) | Previous value as string |
| `new_value` | String (nullable) | New value as string |
| `performed_by` | String | Username |
| `timestamp` | Instant | When it happened |

Indexed on `(entity_type, entity_id)` and `timestamp` for fast lookups.

## Important notes

- Audit runs in the **same transaction** as the CRUD operation. If the operation rolls back, the audit entry is also rolled back.
- Entity IDs are stored as strings regardless of their actual type (Long, UUID, String). This keeps the audit table simple and type-agnostic.
- Audit entries are immutable — FlashAPI never updates or deletes them.
- The `trackFields` diff compares all visible fields. Hidden and write-only fields are not tracked.
