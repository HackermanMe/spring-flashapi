# Relations & Expand

Spring FlashAPI automatically detects JPA relationship annotations and allows expanding related entities in API responses via the `?expand` query parameter. Relations are excluded from responses by default to avoid lazy-loading surprises and uncontrolled payload sizes.

## How It Works

At startup, FlashAPI scans each `@FlashEntity` for fields annotated with:

- `@ManyToOne`
- `@OneToMany`
- `@OneToOne`
- `@ManyToMany`

These fields are **excluded from the default response**. They only appear when explicitly requested via `?expand=fieldName`.

---

## Usage

### Expand a single relation

```bash
curl http://localhost:8080/api/products/1?expand=category
```

**Response:**

```json
{
    "data": {
        "id": 1,
        "name": "Laptop",
        "price": 999.99,
        "stock": 50,
        "category": {
            "id": 1,
            "name": "Electronics",
            "description": "Electronic devices and accessories"
        }
    }
}
```

### Expand multiple relations

Comma-separated field names:

```bash
curl http://localhost:8080/api/products/1?expand=category,tags
```

**Response:**

```json
{
    "data": {
        "id": 1,
        "name": "Laptop",
        "price": 999.99,
        "stock": 50,
        "category": {
            "id": 1,
            "name": "Electronics"
        },
        "tags": [
            {"id": 1, "label": "portable"},
            {"id": 2, "label": "premium"}
        ]
    }
}
```

### Works on both list and detail endpoints

```bash
curl http://localhost:8080/api/products?expand=category           # list
curl http://localhost:8080/api/products/1?expand=category         # detail
curl "http://localhost:8080/api/products?expand=category&page=0&size=10"  # paginated list
```

---

## Behavior Reference

| Scenario | Result |
|----------|--------|
| Relation exists and is loaded | Object or array of objects included |
| Relation is `null` (no FK set) | `"category": null` |
| Expand field doesn't match any relation name | Silently ignored |
| No `?expand` param | Relations excluded from response |
| Multiple expand fields | All matching relations included |

---

## Assigning Relations (Write Operations)

Relations are **read-only** in FlashAPI's generated endpoints. Sending a nested object or an ID in a relation field during `POST` or `PUT` is ignored — FlashAPI only writes scalar fields.

### Recommended Pattern: FK field + relation field

To make a relation assignable via the API, add both a scalar FK field (writable) and a relation annotation (read-only for expand):

```java
@Entity
@FlashEntity
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private BigDecimal price;

    @Column(name = "category_id")
    private Long categoryId;  // writable via POST/PUT

    @ManyToOne
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private Category category;  // read-only, used for ?expand=category
}
```

Now you can assign the category on create/update via the scalar field:

```bash
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{"name": "Laptop", "price": 999.99, "categoryId": 1}'
```

And read the full relation via expand:

```bash
curl http://localhost:8080/api/products/1?expand=category
```

### Why this design?

- Relations are complex objects — FlashAPI would need to resolve references, handle cascading, and validate existence. This is logic better handled by a custom service (Level 2).
- The FK field pattern is explicit, simple, and works with any JPA provider.
- If you need full control over relation management, override with a custom service.

---

## Collection Relations

For `@OneToMany` and `@ManyToMany`, the expanded value is an array:

```bash
curl http://localhost:8080/api/categories/1?expand=products
```

**Response:**

```json
{
    "data": {
        "id": 1,
        "name": "Electronics",
        "products": [
            {"id": 1, "name": "Laptop", "price": 999.99, "stock": 50},
            {"id": 2, "name": "Phone", "price": 599.99, "stock": 100}
        ]
    }
}
```

Empty collections serialize as `[]`:

```json
{
    "data": {
        "id": 5,
        "name": "Empty Category",
        "products": []
    }
}
```

---

## Depth Control

To prevent infinite recursion (Product -> Category -> Products -> Category...), FlashAPI limits expand depth.

### Configuration

#### application.yml

```yaml
flashapi:
  relations:
    max-depth: 1    # default: 1
```

#### application.properties

```properties
flashapi.relations.max-depth=1
```

### How depth works

- `max-depth: 1` (default): Expanded entities include their scalar fields only. Their own relations are never recursively expanded.
- `max-depth: 2`: Expanded entities could in theory have their own relations expanded, but currently FlashAPI does not support nested expand syntax (`?expand=category.products`). The depth parameter guards against future nested expansion.

With the default `max-depth: 1`, expanding a product's category shows the category's scalar fields but does NOT include the category's `products` list:

```bash
curl http://localhost:8080/api/products/1?expand=category
```

```json
{
    "data": {
        "id": 1,
        "name": "Laptop",
        "price": 999.99,
        "category": {
            "id": 1,
            "name": "Electronics",
            "description": "Electronic devices"
        }
    }
}
```

The `category` object above contains only scalar fields, no nested relations.

---

## What Gets Serialized in Expanded Entities

When an entity is expanded, FlashAPI serializes:

- All non-static, non-transient scalar fields (via reflection)
- Relation fields (`@ManyToOne`, `@OneToMany`, etc.) are **excluded** from expanded entities

This means expanded objects are always "flat" -- they show their own data but never recursively include sub-relations.

---

## Circular References

### What happens

Consider this model:

```java
@Entity
@FlashEntity(path = "orders")
public class Order {
    @Id @GeneratedValue
    private Long id;
    private String reference;
    
    @ManyToOne
    private Customer customer;  // Order -> Customer
}

@Entity
@FlashEntity(path = "customers")
public class Customer {
    @Id @GeneratedValue
    private Long id;
    private String name;
    
    @OneToMany(mappedBy = "customer")
    private List<Order> orders;  // Customer -> Order (circular)
}
```

**Expanding `customer` on an Order:**

```bash
curl http://localhost:8080/api/orders/1?expand=customer
```

```json
{
    "data": {
        "id": 1,
        "reference": "ORD-001",
        "customer": {
            "id": 42,
            "name": "Alice"
        }
    }
}
```

The `customer` object does NOT include its `orders` list. The depth limit stops recursion at the first level. There is no infinite loop, no `StackOverflowError`, and no circular JSON.

**Expanding `orders` on a Customer:**

```bash
curl http://localhost:8080/api/customers/42?expand=orders
```

```json
{
    "data": {
        "id": 42,
        "name": "Alice",
        "orders": [
            {"id": 1, "reference": "ORD-001"},
            {"id": 2, "reference": "ORD-002"}
        ]
    }
}
```

Each order in the array excludes its `customer` field. No circular reference is possible.

---

## N+1 Query Mitigation Strategies

### The problem

FlashAPI does NOT override your JPA fetch strategy. If a relation is `LAZY` (the JPA default for `@OneToMany`/`@ManyToMany`), expanding it on a list endpoint triggers one additional query per entity in the result set:

```
SELECT * FROM products WHERE ... LIMIT 20          -- 1 query
SELECT * FROM categories WHERE id = ?              -- repeated 20 times (N+1)
```

### Strategy 1: Eager fetch for small, frequently-expanded `@ManyToOne`

If the related table is small (categories, statuses, types), eager fetch eliminates the N+1:

```java
@ManyToOne(fetch = FetchType.EAGER)
private Category category;
```

The category is loaded in the initial query via JOIN. No extra queries.

**When to use:** The related entity has few rows (< 1000) and is almost always expanded.  
**When to avoid:** Large related tables or relations rarely expanded (wastes memory).

### Strategy 2: Entity graphs (JPA standard)

Define a named entity graph to control fetch behavior without hardcoding `EAGER`:

```java
@Entity
@FlashEntity(path = "products")
@NamedEntityGraph(
    name = "Product.withCategory",
    attributeNodes = @NamedAttributeNode("category")
)
public class Product { ... }
```

Then in a custom repository:

```java
@EntityGraph("Product.withCategory")
List<Product> findAll(Specification<Product> spec, Pageable pageable);
```

### Strategy 3: Batch fetching (Hibernate-specific)

Configure Hibernate to fetch lazy associations in batches rather than one at a time:

#### application.yml

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 16
```

#### application.properties

```properties
spring.jpa.properties.hibernate.default_batch_fetch_size=16
```

This converts N+1 into N/16+1 queries. For 20 products, instead of 20 category queries, Hibernate issues 2 queries fetching 16 + 4 categories using `WHERE id IN (?, ?, ...)`.

### Strategy 4: Keep LAZY and accept N+1 for small page sizes

With `defaultPageSize = 20`, the N+1 cost is 21 queries. On a local database with sub-millisecond latency, this is often acceptable (< 10ms total). Measure before optimizing.

### Strategy 5: Custom service with JOIN FETCH

For full control, implement a custom service that uses a JPQL join fetch:

```java
@Service
public class ProductService implements FlashCrudOperations<Product, Long> {
    
    @PersistenceContext
    private EntityManager em;

    @Override
    public Page<Product> list(Pageable pageable, Map<String, String> filters) {
        // JOIN FETCH eliminates N+1 entirely
        var query = em.createQuery(
            "SELECT p FROM Product p JOIN FETCH p.category WHERE ...", Product.class);
        // ...
    }
}
```

### Recommendation

For most applications, **Strategy 3 (batch fetching)** provides the best tradeoff: zero code changes, works globally, and reduces N+1 to a manageable constant.

---

## Relations and Caching

### Cache bypass on expand

When `?expand` is present, FlashAPI **bypasses the response cache entirely**. This is by design:

- Cached responses contain only scalar fields (no relations)
- Expanded responses include related entity data that may have changed independently
- Different expand combinations would require separate cache entries, creating cache explosion

**Behavior summary:**

| Request | Cache used? |
|---------|-------------|
| `GET /api/products` | Yes (if `cache = true` on entity) |
| `GET /api/products/1` | Yes |
| `GET /api/products?expand=category` | No -- cache bypassed |
| `GET /api/products/1?expand=category` | No -- cache bypassed |

### Cache invalidation on writes

Write operations (`POST`, `PUT`, `DELETE`) evict the entire entity cache regardless of whether the request used expand:

```java
// From FlashController source:
cacheManager.evict(metadata);  // called after create, update, delete
```

This means:
- Creating/updating a product evicts the products cache
- It does NOT automatically evict the categories cache, even if the product's category changed

### Implications for related entity changes

If entity A has a cached list, and you update entity B (which appears in A's expanded responses), A's cached non-expanded response remains valid. Since expand always bypasses cache, stale relation data in cache is not a concern.

**However:** If you have a custom caching layer outside FlashAPI (e.g., Redis with manual keys), be aware that FlashAPI only evicts its own cache namespace (`flashapi:{path}`).

### Configuring cache on entities with relations

```java
@Entity
@FlashEntity(path = "products", cache = true, cacheTtl = 300)
public class Product {
    @Id @GeneratedValue
    private Long id;
    private String name;
    private BigDecimal price;
    
    @ManyToOne
    private Category category;  // not included in cached responses
}
```

The cache stores only the scalar-field response. When a client adds `?expand=category`, the cache is skipped and a fresh database query runs.

---

## Configuration Reference

### application.yml

```yaml
flashapi:
  relations:
    max-depth: 1    # how deep expand can recurse (default: 1)
```

### application.properties

```properties
flashapi.relations.max-depth=1
```

### Properties

| Property | Default | Description |
|----------|---------|-------------|
| `flashapi.relations.max-depth` | `1` | Maximum depth for nested expansion. `1` = expand direct relations only, their sub-relations are never included. |

---

## FAQ

**Q: Can I expand nested relations (e.g., `?expand=category.parent`)?**  
No. FlashAPI supports only first-level expansion. The `?expand` parameter accepts field names on the queried entity, not dot-paths. Nested expansion is not supported.

**Q: What happens if I expand a field that isn't a relation?**  
It is silently ignored. Only fields annotated with `@ManyToOne`, `@OneToMany`, `@OneToOne`, or `@ManyToMany` can be expanded.

**Q: Does expand work with filtering and sorting?**  
Yes. `?expand` is independent of other query parameters. You can combine them freely:  
```
GET /api/products?expand=category&sort=price,desc&page=0&size=10&name=Laptop
```

**Q: Will expanding a `@OneToMany` with 10,000 items return all of them?**  
Yes. There is no pagination on expanded collections. If a relation can contain many items, consider not expanding it and instead querying the related entity's own endpoint with a filter. For example, instead of expanding `orders` on a customer with 10,000 orders, call `GET /api/orders?customerId=42&page=0&size=20`.

**Q: Does FlashAPI support `@Embeddable` / `@Embedded` as expandable relations?**  
No. Embedded objects are scalar fields and are always included in the response. Only JPA relationship annotations (`@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`) are recognized as expandable relations.

**Q: Can I restrict which relations are expandable?**  
Not via configuration. All detected JPA relations are expandable. To hide a relation entirely, mark the field with `@JsonIgnore` or use a DTO projection via a custom service.

**Q: Is there a performance difference between expanding on list vs. detail endpoints?**  
Yes. On a detail endpoint (`GET /api/products/1?expand=category`), there is at most one additional query. On a list endpoint (`GET /api/products?expand=category`), the N+1 problem applies -- see "N+1 Query Mitigation Strategies" above.

**Q: Does expanding affect the `meta` pagination object?**  
No. The `meta` object always reflects the paginated result of the root entity. Expanded relations are nested within each item in `data` and do not affect counts or page sizes.

**Q: Can I use expand with bulk endpoints?**  
No. Bulk endpoints (`/bulk`) return their own response format and do not support the `?expand` parameter.
