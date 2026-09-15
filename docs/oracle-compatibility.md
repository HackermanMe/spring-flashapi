# Compatibilité Oracle

FlashAPI est **100% compatible avec Oracle Database** (11g+). La bibliothèque lit l'`@Id` via reflection et fonctionne avec toute stratégie JPA.

---

## Configuration

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:ORCL
    username: flashapi_user
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    database-platform: org.hibernate.dialect.Oracle12cDialect
```

---

## Génération d'ID avec séquences

```java
@Entity
@FlashEntity
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "invoice_seq")
    @SequenceGenerator(
        name = "invoice_seq",
        sequenceName = "invoice_sequence",
        allocationSize = 50
    )
    private Long id;
}
```

**SQL :**
```sql
CREATE SEQUENCE invoice_sequence START WITH 1 INCREMENT BY 1;
```

---

## Stratégies supportées

| Stratégie | Support Oracle | Notes |
|-----------|----------------|-------|
| `SEQUENCE` | ✅ Recommandé | Performance optimale |
| `IDENTITY` | ❌ Non supporté | Utiliser SEQUENCE |
| `TABLE` | ✅ | Portable mais lent |
| `UUID` | ✅ | Pas de séquence |

---

## Allocation size

**❌ Lent :**
```java
@SequenceGenerator(allocationSize = 1)  // 1 call DB par INSERT
```

**✅ Rapide :**
```java
@SequenceGenerator(allocationSize = 50)  // 1 call pour 50 INSERTs
```

---

## Types de données

| Java | Oracle |
|------|--------|
| `String` | `VARCHAR2(255)` |
| `@Lob String` | `CLOB` |
| `BigDecimal` | `NUMBER(19,2)` |
| `LocalDateTime` | `TIMESTAMP` |
| `Boolean` | `NUMBER(1,0)` |

---

## Performance

### Index recommandés

```sql
CREATE INDEX idx_audit_entity ON flash_audit_log(entity_name, entity_id);
CREATE INDEX idx_customer_tenant ON customer(tenant_id);
CREATE INDEX idx_customer_deleted ON customer(deleted_at);
```

---

## Troubleshooting

**❌ sequence does not exist**  
Fix : `CREATE SEQUENCE invoice_sequence START WITH 1;`

**❌ ORA-00001 unique constraint**  
Fix : Vérifier `allocationSize` > 1

**❌ INSERT lent**  
Fix : Augmenter `allocationSize` à 50

---

## Références

- **[Best Practices](best-practices.md)** — BaseEntity pattern
- **[Multi-Tenancy](multi-tenancy.md)** — Data isolation
