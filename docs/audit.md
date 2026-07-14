# Audit Trail

FlashAPI automatically records every create, update, and delete operation in a dedicated audit table. Entries capture who changed what, when, and (optionally) field-level diffs with old/new values.

---

## Quick Start

### 1. Annotate your entity

```java
@Entity
@FlashEntity
@FlashAudit(trackFields = true)
public class Invoice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal amount;
    private String status;
    private String clientName;
}
```

### 2. Run your application

FlashAPI creates the `flash_audit_log` table through JPA/Hibernate DDL auto-generation. No manual migration required for development.

---

## Configuration

### application.yml

```yaml
flashapi:
  audit:
    enabled: true                # Global kill-switch (default: true)
    table-name: flash_audit_log  # Table name (default: flash_audit_log)
```

### application.properties

```properties
flashapi.audit.enabled=true
flashapi.audit.table-name=flash_audit_log
```

---

## @FlashAudit Annotation

| Attribute     | Type    | Default | Description                                              |
|---------------|---------|---------|----------------------------------------------------------|
| `enabled`     | boolean | `true`  | Enable/disable audit for this entity                     |
| `trackFields` | boolean | `false` | Record field-level diffs (old value / new value per field)|

```java
// Audit enabled, but no field diffs
@FlashAudit
public class Customer { ... }

// Audit with field-level change tracking
@FlashAudit(trackFields = true)
public class Invoice { ... }

// Audit explicitly disabled
@FlashAudit(enabled = false)
public class TemporaryLog { ... }
```

---

## What Gets Recorded

### Without `trackFields` (default)

One row per operation:

```json
{
  "entityType": "Invoice",
  "entityId": "42",
  "action": "UPDATE",
  "field": null,
  "oldValue": null,
  "newValue": null,
  "performedBy": "john.doe",
  "timestamp": "2026-03-20T14:22:00Z"
}
```

### With `trackFields = true`

One row per changed field:

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

---

## Querying Audit History via the API

### Endpoint

```
GET /api/{entity-path}/{id}/history
```

### Example

```bash
curl -s http://localhost:8080/api/invoices/42/history | jq .
```

**Response (200 OK):**

```json
{
  "data": [
    {
      "id": 7,
      "entityType": "Invoice",
      "entityId": "42",
      "action": "UPDATE",
      "field": "status",
      "oldValue": "DRAFT",
      "newValue": "PAID",
      "performedBy": "admin",
      "timestamp": "2026-03-20T15:30:00Z"
    },
    {
      "id": 5,
      "entityType": "Invoice",
      "entityId": "42",
      "action": "UPDATE",
      "field": "amount",
      "oldValue": "100.00",
      "newValue": "150.00",
      "performedBy": "john.doe",
      "timestamp": "2026-03-20T14:22:00Z"
    },
    {
      "id": 3,
      "entityType": "Invoice",
      "entityId": "42",
      "action": "CREATE",
      "field": null,
      "oldValue": null,
      "newValue": null,
      "performedBy": "john.doe",
      "timestamp": "2026-03-20T10:00:00Z"
    }
  ]
}
```

Entries are always ordered **most recent first**.

Returns `400 Bad Request` if audit is not enabled for the entity.

---

## Querying Audit Data Directly

The `flash_audit_log` table is a standard JPA entity. You can query it with JPQL, Criteria API, or native SQL.

### Native SQL examples

```sql
-- All changes to Invoice #42
SELECT * FROM flash_audit_log
WHERE entity_type = 'Invoice' AND entity_id = '42'
ORDER BY timestamp DESC;

-- All deletes performed by a user in the last 24 hours
SELECT * FROM flash_audit_log
WHERE performed_by = 'john.doe'
  AND action = 'DELETE'
  AND timestamp > NOW() - INTERVAL '24 hours';

-- Count of operations per entity type (monitoring dashboard)
SELECT entity_type, action, COUNT(*)
FROM flash_audit_log
GROUP BY entity_type, action;
```

### Spring Data JPA repository (custom)

```java
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    List<AuditEntry> findByEntityTypeAndEntityIdOrderByTimestampDesc(
            String entityType, String entityId);

    List<AuditEntry> findByPerformedByAndTimestampAfter(
            String user, Instant since);

    @Query("SELECT a FROM AuditEntry a WHERE a.action = :action AND a.timestamp BETWEEN :from AND :to")
    List<AuditEntry> findByActionInRange(
            @Param("action") AuditAction action,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
```

The `AuditEntry` entity is in package `io.github.hackermanme.flashapi.audit`. Import it directly in your repositories/services.

---

## Integrating with Spring Security

FlashAPI resolves the current user automatically via `SecurityContextHolder`:

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
return auth.getName(); // stored as performedBy
```

### Behavior matrix

| Condition                                    | `performedBy` value |
|----------------------------------------------|---------------------|
| Spring Security on classpath + authenticated | `auth.getName()`    |
| Spring Security on classpath + anonymous     | `"anonymous"`       |
| Spring Security not on classpath             | `"anonymous"`       |

### Typical Spring Security setup

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").authenticated()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

With this configuration, every audit entry records the authenticated principal name automatically.

### Custom user resolution

If you need a different strategy (e.g., extract from a JWT claim or a custom header), override the resolution by providing a bean that sets the SecurityContext before FlashAPI's handler runs. A servlet filter is the cleanest approach:

```java
@Component
public class TenantUserFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String user = req.getHeader("X-User-Id"); // your custom header
        if (user != null) {
            var auth = new UsernamePasswordAuthenticationToken(user, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(req, res);
    }
}
```

---

## Storage Considerations

The audit table grows proportionally to write traffic. With `trackFields = true`, a single UPDATE touching N fields produces N rows.

### Growth estimation

| Scenario                      | Rows/day (approx)       |
|-------------------------------|-------------------------|
| 100 creates/day, no tracking | 100                     |
| 100 updates/day, 3 fields avg, trackFields=true | 300   |
| 1000 mixed ops/day, trackFields=true | 2,000-5,000     |

### Mitigation strategies

1. **Disable field tracking** for high-churn entities where diffs are not needed:

   ```java
   @FlashAudit(trackFields = false)
   public class EventLog { ... }
   ```

2. **Partition by timestamp** (PostgreSQL example):

   ```sql
   CREATE TABLE flash_audit_log (
       id BIGSERIAL,
       entity_type VARCHAR(100) NOT NULL,
       entity_id VARCHAR(255) NOT NULL,
       action VARCHAR(10) NOT NULL,
       field VARCHAR(100),
       old_value TEXT,
       new_value TEXT,
       performed_by VARCHAR(100) NOT NULL,
       timestamp TIMESTAMPTZ NOT NULL
   ) PARTITION BY RANGE (timestamp);

   CREATE TABLE flash_audit_log_2026_q1 PARTITION OF flash_audit_log
       FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
   ```

3. **Archive old entries** with a scheduled job:

   ```sql
   -- Move entries older than 90 days to archive
   INSERT INTO flash_audit_log_archive SELECT * FROM flash_audit_log WHERE timestamp < NOW() - INTERVAL '90 days';
   DELETE FROM flash_audit_log WHERE timestamp < NOW() - INTERVAL '90 days';
   ```

4. **Disable audit globally** for load-testing or bulk imports:

   **application.yml:**
   ```yaml
   flashapi:
     audit:
       enabled: false
   ```

   **application.properties:**
   ```properties
   flashapi.audit.enabled=false
   ```

---

## Audit Table Schema

Table: `flash_audit_log`

| Column         | Type         | Nullable | Description                                         |
|----------------|--------------|----------|-----------------------------------------------------|
| `id`           | BIGINT (PK)  | No       | Auto-generated primary key                          |
| `entity_type`  | VARCHAR(100) | No       | Entity class simple name                            |
| `entity_id`    | VARCHAR(255) | No       | Primary key value (always stored as String)         |
| `action`       | VARCHAR(10)  | No       | `CREATE`, `UPDATE`, or `DELETE`                     |
| `field`        | VARCHAR(100) | Yes      | Changed field name (only with `trackFields`)        |
| `old_value`    | TEXT         | Yes      | Previous value as string                            |
| `new_value`    | TEXT         | Yes      | New value as string                                 |
| `performed_by` | VARCHAR(100) | No       | Username who performed the operation                |
| `timestamp`    | TIMESTAMP    | No       | When the operation occurred                         |

**Indexes:**
- `idx_audit_entity` on `(entity_type, entity_id)` -- fast history lookups
- `idx_audit_timestamp` on `(timestamp)` -- range queries, archival

---

## Important Behaviors

- Audit runs in the **same transaction** as the CRUD operation. A rollback reverts both the data change and the audit entry.
- Entity IDs are stored as strings regardless of their Java type (`Long`, `UUID`, `String`). This keeps the schema type-agnostic.
- Audit entries are **immutable**. FlashAPI never updates or deletes them.
- The `trackFields` diff compares all visible fields. Fields annotated with `@FlashHidden` or `@FlashWriteOnly` are excluded.
- Soft deletes are recorded as `DELETE` actions; restores are recorded as `UPDATE` actions.

---

## FAQ

**Q: Can I disable audit for a single entity without disabling it globally?**

Yes. Use `@FlashAudit(enabled = false)` on that entity. The global `flashapi.audit.enabled` property is a kill-switch; per-entity annotation takes precedence when the global flag is `true`.

**Q: Does audit impact performance?**

Minimally. Each audit entry is a simple `INSERT` within the existing transaction. No extra round-trips or external calls. With `trackFields = true`, performance depends on the number of fields changed per update.

**Q: Can I change the table name?**

Yes, via configuration:

```yaml
# application.yml
flashapi:
  audit:
    table-name: my_custom_audit_table
```

```properties
# application.properties
flashapi.audit.table-name=my_custom_audit_table
```

**Q: What happens if Spring Security is not on the classpath?**

FlashAPI catches `NoClassDefFoundError` and falls back to `"anonymous"`. No configuration needed.

**Q: Are audit entries queryable from the auto-generated REST endpoints?**

No. Audit entries are only exposed through the `/history` sub-resource endpoint. They are not a `@FlashEntity` and do not get their own CRUD routes. Use direct JPA queries for advanced access.

**Q: How do I audit only specific operations (e.g., only DELETEs)?**

FlashAPI audits all operations when enabled. To selectively audit, disable FlashAPI audit and implement your own listener using the `AuditService` class directly in a custom `@FlashService`.

**Q: Does audit work with soft delete?**

Yes. A soft delete records a `DELETE` action. A restore records an `UPDATE` action. The audit trail fully captures the soft-delete lifecycle.
