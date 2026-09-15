# FlashAPI vs Spring Data REST

## TL;DR

- **Spring Data REST** → Prototype tool. Expose your data model as REST in 5 minutes.
- **FlashAPI** → Production framework. All enterprise features built-in.

**Migration path:** Start prototyping with Spring Data REST → Ship with FlashAPI.

---

## When to Use What

### Use Spring Data REST when:

✅ Building an internal admin panel  
✅ Rapid prototyping / POC  
✅ Simple CRUD, no business logic  
✅ You want HATEOAS/HAL  

### Use FlashAPI when:

✅ Production SaaS APIs  
✅ Multi-tenant applications  
✅ Compliance/audit requirements  
✅ Complex security (owner-based)  
✅ Need exports, real-time, webhooks  

---

## Feature Comparison

| Feature | Spring Data REST | FlashAPI |
|---------|------------------|----------|
| **CRUD generation** | ✅ | ✅ |
| **Pagination & sorting** | ✅ | ✅ |
| **Filtering** | ⚠️ Manual (QueryDSL) | ✅ 11 operators built-in |
| **Multi-tenancy** | ❌ | ✅ |
| **Audit trail** | ❌ | ✅ |
| **Owner security** | ❌ | ✅ |
| **Export PDF/Excel** | ❌ | ✅ |
| **Soft delete** | ❌ | ✅ |
| **Rate limiting** | ❌ | ✅ |
| **WebSocket events** | ❌ | ✅ |
| **Webhooks** | ❌ | ✅ |
| **Bulk operations** | ❌ | ✅ |

---

## Code Example

### Task: Multi-tenant blog with owner security

**Spring Data REST approach:**
```java
@Entity
public class Post {
    @Id @GeneratedValue
    private Long id;
    private String title;
    private Long authorId;
    private String tenantId;
}

@RepositoryRestResource
public interface PostRepository extends JpaRepository<Post, Long> {}

// THEN manually implement:
// - TenantFilter (50 lines)
// - OwnerSecurityHandler (80 lines)
// - AuditAspect (100 lines)
// - ExportController (150 lines)
// Total: ~400 lines of custom code
```

**FlashAPI approach:**
```java
@Entity
@FlashEntity(audit = true)
@FlashSecured(ownerField = "author", ownerAdminRoles = "ADMIN")
@FlashMultiTenant(field = "tenantId")
public class Post {
    @Id @GeneratedValue
    private Long id;
    
    private String title;
    
    @ManyToOne
    private User author;
    
    private String tenantId;
}

// That's it. 13 lines.
```

---

## Migration Strategy

**Step 1:** Prototype with Spring Data REST
```java
@RepositoryRestResource
public interface ProductRepository extends JpaRepository<Product, Long> {}
```

**Step 2:** Add FlashAPI when you need production features
```xml
<dependency>
    <groupId>io.github.hackermanme</groupId>
    <artifactId>spring-flashapi</artifactId>
    <version>3.1.2</version>
</dependency>
```

**Step 3:** Replace annotation
```java
@Entity
@FlashEntity(audit = true)  // ← Changed from @RepositoryRestResource
@FlashMultiTenant(field = "tenantId")
public class Product { ... }
```

**Step 4:** Delete old repository interface (no longer needed)

---

## Real-World Timeline

### Implementing from scratch:

| Feature | Time |
|---------|------|
| Multi-tenancy | 2-3 days |
| Audit trail | 2-3 days |
| Exports | 1-2 days |
| Owner security | 1-2 days |
| Soft delete | 1 day |
| Rate limiting | 1 day |
| WebSocket | 2 days |
| **Total** | **~2 weeks** |

### With FlashAPI:

**3 annotations** = done.

---

## What You Give Up

**Spring Data REST advantages FlashAPI doesn't have:**

1. **HATEOAS/HAL** — FlashAPI uses simpler JSON format
2. **Query methods** — `findByNameContaining` vs `?name.contains=`
3. **Projections** — FlashAPI has `?fields=` instead

**Bottom line:** If you need strict HATEOAS, use Spring Data REST. Otherwise, FlashAPI is simpler.

---

## FAQ

**"Can I use both in the same project?"**

Not recommended — two different response formats confuse API consumers.

Better: Spring Data REST for prototype → FlashAPI for production.

**"Can I migrate incrementally?"**

Yes. FlashAPI detects custom `@RestController` and backs off.

Migrate one entity at a time.

**"What if I need custom business logic?"**

Implement `FlashCrudOperations<T, ID>`:
```java
@Service
public class ProductService implements FlashCrudOperations<Product, Long> {
    @Override
    public Product create(Map<String, Object> data) {
        // Your business logic
    }
}
```

FlashAPI detects and delegates automatically.

---

## Next Steps

- **[Quick Start](getting-started.md)** — install in 5 minutes
- **[Best Practices](best-practices.md)** — recommended patterns
- **[Security Guide](security.md)** — owner & role-based access
- **[Multi-Tenancy Guide](multi-tenancy.md)** — data isolation
