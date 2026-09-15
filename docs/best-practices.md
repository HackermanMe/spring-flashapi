# FlashAPI Best Practices

## BaseEntity Pattern with Audit & Optimistic Locking

### The Recommended Pattern

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedBy
    private String lastModifiedBy;

    @LastModifiedDate
    private LocalDateTime lastModifiedAt;

    @Version
    private Long version;

    // Getters/setters
}
```

Then extend it:

```java
@Entity
@FlashEntity(audit = true)
public class Invoice extends BaseEntity {
    @Id @GeneratedValue
    private Long id;
    
    private BigDecimal amount;
}
```

**You get:**
- ✅ JPA audit (`createdBy`, `lastModifiedBy`) — auto-filled by JPA
- ✅ FlashAPI audit trail — separate `flash_audit_log` table
- ✅ Optimistic locking via `@Version`

---

## Why Two Audit Systems?

### JPA Audit (in entity table)

**Fields:** `createdBy`, `createdAt`, `lastModifiedBy`, `lastModifiedAt`

**Purpose:** Record WHO created/modified and WHEN.

**Use case:** Display "Last edited by John at 14:30" in UI.

**Storage:** Columns in your entity table.

---

### FlashAPI Audit Trail (separate table)

**Enabled by:** `@FlashEntity(audit = true)`

**Purpose:** Complete change history with field-level diffs.

**Use case:** Compliance, debugging, "show me all changes".

**Storage:** Dedicated `flash_audit_log` table.

**Access:**
```bash
GET /api/invoices/{id}/history
```

**Response:**
```json
{
  "data": [
    {
      "timestamp": "2025-09-13T14:30:00Z",
      "user": "john@example.com",
      "operation": "UPDATE",
      "changes": {
        "status": { "before": "draft", "after": "sent" }
      }
    }
  ]
}
```

---

## Optimistic Locking with @Version

**Problem:** Two users modify the same entity → second save overwrites first → data loss.

**Solution:** Add `@Version`:

```java
@Version
private Long version;
```

**How it works:**

1. User A loads Invoice #123 (version = 5)
2. User B loads Invoice #123 (version = 5)
3. User A saves → version becomes 6
4. User B saves with version = 5 → JPA throws `OptimisticLockException`

**Result:** User B must reload and retry. No data loss.

---

## Configuration

Enable JPA auditing:

```java
@SpringBootApplication
@EnableFlashApi
@EnableJpaAuditing
public class MyApp {}
```

**Spring Security Integration:**

```java
@Configuration
public class AuditConfig {
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName);
    }
}
```

Now `@CreatedBy` and `@LastModifiedBy` auto-fill with authenticated username.

---

## When NOT to Use BaseEntity

### Simple lookup tables

```java
@Entity
public class Country {
    @Id
    private String code;
    private String name;
}
```

No need for audit — data rarely changes.

### Immutable entities

```java
@Entity
@FlashEntity(audit = true)
public class PaymentEvent {
    @Id @GeneratedValue
    private Long id;
    
    @CreatedDate
    private LocalDateTime timestamp;
    
    private BigDecimal amount;
}
```

Append-only log — no updates, so no `@Version` needed.

---

## Common Mistakes

### ❌ Missing @EnableJpaAuditing

**Result:** `createdBy` stays null.

**Fix:** Add `@EnableJpaAuditing` to your `@SpringBootApplication`.

### ❌ Forgetting FlashAPI audit is separate

```java
@FlashEntity  // ⚠️ Missing audit = true
```

**Result:**
- ✅ JPA audit works (`createdBy`)
- ❌ No audit trail (`/api/invoices/{id}/history` empty)

**Fix:** Add `audit = true`.

---

## Oracle Compatibility

FlashAPI fully supports Oracle sequences:

```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "invoice_seq")
@SequenceGenerator(name = "invoice_seq", sequenceName = "invoice_sequence", allocationSize = 1)
private Long id;
```

FlashAPI reads `@Id` via reflection — works with **any** ID strategy:
- `IDENTITY` (MySQL, PostgreSQL)
- `SEQUENCE` (Oracle, PostgreSQL)
- `TABLE` (any database)
- `UUID` (any database)

No configuration needed.

---

## Summary

| Feature | What it does | When to use |
|---------|--------------|-------------|
| **JPA audit** | Records who & when | Always (except immutable/lookup) |
| **FlashAPI audit** (`audit = true`) | Complete change history | Compliance, debugging |
| **@Version** | Prevents concurrent overwrites | Entities with frequent updates |

---

## Next Steps

- **[Audit Trail Guide](audit.md)** — complete audit documentation
- **[Security Guide](security.md)** — owner & role-based access
- **[vs Spring Data REST](vs-spring-data-rest.md)** — comparison guide
