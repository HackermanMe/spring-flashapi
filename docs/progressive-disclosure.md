# Progressive Disclosure

FlashAPI follows a "progressive disclosure" philosophy: it provides everything out of the box, and recedes as you define your own classes. You never fight the framework — you simply take over what you need.

## The Three Levels

```
┌─────────────────────────────────────────────────────────────────┐
│ Level 1: Zero Config                                            │
│   You write: @FlashEntity on your class                         │
│   FlashAPI handles: Everything                                  │
├─────────────────────────────────────────────────────────────────┤
│ Level 2: Custom Service                                         │
│   You write: a ProductService bean                              │
│   FlashAPI handles: Routing, serialization, pagination, errors  │
├─────────────────────────────────────────────────────────────────┤
│ Level 3: Custom Controller                                      │
│   You write: @RestController for /api/products                  │
│   FlashAPI handles: Nothing (backs off entirely)                │
└─────────────────────────────────────────────────────────────────┘
```

---

## Level 1: Zero Config

Annotate your entity. Get a full CRUD API with no additional code.

```java
@Entity
@FlashEntity
public class Product {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private BigDecimal price;
}
```

**What you get:**
- Full CRUD endpoints (list, get, create, update, delete)
- Pagination, sorting, filtering with 11 operators
- Full-text search (`?search=`)
- Export (CSV, XLSX, PDF)
- Bulk operations
- OpenAPI documentation
- Audit trail (if `@FlashAudit` added)
- Soft delete (if `softDelete = true`)
- Cache (if `cache = true`)
- Rate limiting (if `rateLimit = true`)

**What FlashAPI handles at Level 1:**

| Responsibility | How |
|----------------|-----|
| Route registration | Dynamic, at startup |
| Request parsing | Query params → filters, pagination |
| Data access | JPA Criteria API, EntityManager |
| Serialization | Reflection-based, respects field annotations |
| Validation | Bean Validation + JPA constraints |
| Error handling | Structured JSON errors with HTTP status |
| Audit | Automatic tracking of changes |
| Cache | Transparent read/write/invalidation |

---

## Level 2: Custom Service

Create a service bean that implements `FlashCrudOperations<T, ID>`. FlashAPI detects it automatically and delegates all operations to your service.

```java
@Service
public class ProductService implements FlashCrudOperations<Product, Long> {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    @Override
    public Page<Product> list(Pageable pageable, Map<String, String> filters) {
        // your custom query logic
        return repo.findAll(pageable);
    }

    @Override
    public Optional<Product> findById(Long id) {
        return repo.findById(id);
    }

    @Override
    public Product create(Map<String, Object> data) {
        // your business rules for creation
        Product p = new Product();
        p.setName((String) data.get("name"));
        p.setPrice(new BigDecimal(data.get("price").toString()));
        return repo.save(p);
    }

    @Override
    public Optional<Product> update(Long id, Map<String, Object> data) {
        return repo.findById(id).map(p -> {
            if (data.containsKey("name")) p.setName((String) data.get("name"));
            if (data.containsKey("price")) p.setPrice(new BigDecimal(data.get("price").toString()));
            return repo.save(p);
        });
    }

    @Override
    public boolean delete(Long id) {
        if (!repo.existsById(id)) return false;
        repo.deleteById(id);
        return true;
    }
}
```

**What you control:** Business logic, data access, validation rules.

**What FlashAPI still handles:**

| Responsibility | Status |
|----------------|--------|
| Route registration | FlashAPI |
| HTTP method dispatch | FlashAPI |
| Request parsing (page/size/sort/expand) | FlashAPI |
| Response serialization | FlashAPI |
| Error handling | FlashAPI |
| Cache | FlashAPI |
| Rate limiting | FlashAPI |
| Audit | **You** (FlashAPI does not auto-audit custom services) |
| Data access | **You** |
| Validation | **You** |

### Partial override

You don't have to implement all methods. The interface provides defaults:

```java
@Service
public class ProductService implements FlashCrudOperations<Product, Long> {

    @Override
    public Product create(Map<String, Object> data) {
        // Custom creation logic with notifications, validation, etc.
        // All other operations (list, findById, update, delete) 
        // still use FlashAPI's GenericCrudService
    }
}
```

**Wait — that's not how Java interfaces work.** Correct. If you implement `FlashCrudOperations`, you must implement all methods (except `restore()` which has a default). But the trade-off is clear: you own the full CRUD lifecycle for that entity once you provide a service.

If you only want to hook into one operation, a better pattern is to use a custom controller for just that endpoint (Level 3, partial).

### Detection

FlashAPI detects custom services by:

1. **Naming convention:** Bean named `{entityName}Service` (e.g., `productService` for `Product`)
2. **Annotation:** `@FlashService(Product.class)` on any bean name

```java
// Non-standard name? Use the annotation:
@Service
@FlashService(Product.class)
public class InventoryManager implements FlashCrudOperations<Product, Long> {
    // ...
}
```

---

## Level 3: Custom Controller

Define your own `@RestController` that maps to the same path. FlashAPI detects the conflict and backs off entirely for that entity.

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping
    public ResponseEntity<List<ProductDTO>> list() {
        // Full control
    }

    @PostMapping
    public ResponseEntity<ProductDTO> create(@RequestBody CreateProductRequest req) {
        // Full control
    }
}
```

FlashAPI logs:

```
FlashAPI: skipping GET /api/products — already mapped by user
FlashAPI: skipping POST /api/products — already mapped by user
```

**What FlashAPI handles:** Nothing for that entity. Your controller is in charge.

### Partial Level 3

You can override just one endpoint. FlashAPI only backs off for specific HTTP method + path combinations it detects:

```java
@RestController
public class ProductSearchController {

    @GetMapping("/api/products")
    public ResponseEntity<?> search(@RequestParam Map<String, String> params) {
        // Custom list/search logic with Elasticsearch
    }
}
```

In this case:
- `GET /api/products` → your controller
- `POST /api/products` → FlashAPI (still handles create)
- `GET /api/products/{id}` → FlashAPI (still handles get by ID)
- etc.

---

## Decision Flowchart

```
Do you need custom business logic for this entity?
│
├─ No → Level 1 (just @FlashEntity)
│
└─ Yes
    │
    ├─ For ALL CRUD operations? → Level 2 (custom service)
    │
    └─ For specific endpoints only?
        │
        ├─ Custom logic + FlashAPI's routing/serialization → Level 2
        │
        └─ Complete control of request/response format → Level 3
```

### When to choose each level

| Situation | Level | Why |
|-----------|-------|-----|
| Simple entity, standard CRUD | 1 | Zero work needed |
| Need email notification on create | 2 | Business logic in service |
| Complex validation rules | 2 | Custom service validates |
| Custom response format | 3 | Full control of serialization |
| Integration with external API | 2 or 3 | Depends on how much control you need |
| Entity backed by Elasticsearch | 3 | Different data source |
| Standard CRUD + one custom endpoint | 1 + extra controller | Add `/api/products/analytics` without affecting FlashAPI |

---

## Comparison Table

| Feature | Level 1 | Level 2 | Level 3 |
|---------|---------|---------|---------|
| Route registration | Auto | Auto | Manual |
| Request parsing | Auto | Auto | Manual |
| Pagination | Auto | Auto | Manual |
| Filtering | Auto | Manual | Manual |
| Serialization | Auto | Auto | Manual |
| Validation | Auto | Manual | Manual |
| Error handling | Auto | Auto | Manual |
| Audit trail | Auto | Manual | Manual |
| Cache | Auto | Auto | N/A |
| Rate limiting | Auto | Auto | N/A |
| Export | Auto | Uses your list() | N/A |
| Bulk | Auto | Uses your create/update/delete | N/A |
| OpenAPI doc | Auto | Auto | Manual (use springdoc) |

---

## How Detection Works at Startup

FlashAPI runs this logic in `ContextRefreshedEvent`:

1. Scans all `@FlashEntity` classes
2. For each entity, checks if a user `@RestController` maps the same path → if yes, skip
3. For each entity, checks for a custom service bean → if found, use it instead of `GenericCrudService`
4. Registers remaining routes dynamically via `RequestMappingHandlerMapping`

This happens once at startup. Changing controller or service registration at runtime has no effect.

---

## FAQ

**Q: Can I use Level 1 for most entities and Level 2/3 for a few?**

Yes. Each entity is independent. Most teams use Level 1 for 80% of entities and Level 2 or 3 for the ones with business logic.

**Q: What if I want to add ONE extra endpoint without replacing FlashAPI's?**

Just create a controller for that specific endpoint. FlashAPI only backs off for routes it conflicts with:

```java
@RestController
public class ProductAnalyticsController {

    @GetMapping("/api/products/stats")
    public Map<String, Object> getStats() {
        // This does NOT conflict with FlashAPI's /api/products
        return Map.of("totalProducts", 42);
    }
}
```

**Q: Does FlashAPI detect my controller if it's in a different package?**

Yes. FlashAPI scans Spring's `RequestMappingHandlerMapping` which sees all registered controllers regardless of package.

**Q: What if my service bean is created after FlashAPI registers routes?**

FlashAPI resolves services at `ContextRefreshedEvent`. All `@Service` beans are available at that point. If you use lazy initialization or custom factory patterns that create beans later, they won't be detected.

**Q: Can I switch between levels without breaking anything?**

Yes. The levels are additive. If you remove a custom service, FlashAPI falls back to Level 1 on the next restart. If you add a controller, FlashAPI backs off on the next restart.
