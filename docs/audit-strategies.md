# Stratégies d'audit

FlashAPI offre **deux systèmes d'audit complémentaires** : JPA audit (colonnes) et FlashAPI audit trail (table séparée).

---

## Comparaison

| Feature | JPA Audit | FlashAPI Audit |
|---------|-----------|----------------|
| **Activation** | `@EnableJpaAuditing` | `@FlashEntity(audit = true)` ✨ défaut |
| **Stockage** | Colonnes entité | Table `flash_audit_log` |
| **Champs** | `createdBy`, `lastModifiedBy` | Historique complet + diffs |
| **Overhead** | Minimal | ~5-10ms par change |

---

## Stratégie 1 : JPA seul

**Usage :** API simple, pas de compliance

```java
@Entity
@FlashEntity(audit = false)
public class Product extends BaseEntity {
    private String name;
}
```

✅ Léger  
❌ Pas d'historique

---

## Stratégie 2 : FlashAPI seul

**Usage :** Historique complet requis

```java
@Entity
@FlashEntity  // audit = true par défaut ✨
public class Invoice {
    private BigDecimal amount;
}
```

✅ Historique complet  
❌ Pas de `lastModifiedBy` dans queries

---

## Stratégie 3 : Les deux (recommandé SaaS)

```java
@Entity
@FlashEntity  // audit = true ✨
public class Order extends BaseEntity {
    private BigDecimal total;
}

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedBy
    private String createdBy;
    
    @LastModifiedBy
    private String lastModifiedBy;
    
    @Version
    private Long version;
}
```

✅ `lastModifiedBy` rapide (pas de JOIN)  
✅ Historique complet  
✅ Optimistic locking

---

## Historique

```bash
GET /api/payments/123/history
```

**Réponse :**
```json
{
  "data": [
    {
      "timestamp": "2025-09-15T14:45:00Z",
      "user": "admin@example.com",
      "operation": "UPDATE",
      "changes": {
        "amount": { "before": 100, "after": 150 }
      }
    }
  ]
}
```

---

## Performance

**Optimisation :**
```sql
CREATE INDEX idx_audit_entity ON flash_audit_log(entity_name, entity_id);
```

---

## Retention

### Archivage annuel

```java
@Scheduled(cron = "0 0 2 * * SUN")
public void archiveOldAuditLogs() {
    auditRepository.archiveAndDelete(Instant.now().minus(365, ChronoUnit.DAYS));
}
```

---

## Désactivation

```java
@Entity
@FlashEntity(audit = false)
public class Product extends BaseEntity {
    // Seulement JPA audit
}
```

---

## Références

- **[Best Practices](best-practices.md)** — BaseEntity
- **[Configuration](configuration.md)** — Settings
