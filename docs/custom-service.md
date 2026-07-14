# Custom Services

When FlashAPI's built-in CRUD logic isn't enough, provide a custom service. FlashAPI delegates all operations to your service while still handling routing, serialization, pagination, and error responses.

## The Interface

```java
public interface FlashCrudOperations<T, ID> {
    Page<T> list(Pageable pageable, Map<String, String> filters);
    Optional<T> findById(ID id);
    T create(Map<String, Object> data);
    Optional<T> update(ID id, Map<String, Object> data);
    boolean delete(ID id);
    default boolean restore(ID id) { return false; }
}
```

All methods are required except `restore()` which has a default no-op implementation.

---

## Detection

FlashAPI detects custom services at startup in this order:

### 1. By annotation (highest priority)

```java
@Service
@FlashService(Product.class)
public class InventoryManager implements FlashCrudOperations<Product, Long> {
    // ...
}
```

### 2. By naming convention

Name your bean `{entityName}Service` (lowercase first letter):

```java
@Service
public class ProductService implements FlashCrudOperations<Product, Long> {
    // Bean name: "productService" → matches entity "Product"
}
```

### Resolution order

1. Check for beans annotated with `@FlashService(Product.class)`
2. Check for a bean named `productService` implementing `FlashCrudOperations`
3. Fall back to `GenericCrudService` (FlashAPI's built-in)

Only step 3 uses FlashAPI's built-in logic (Criteria API, audit, soft delete).

---

## Complete Example: OrderService

A real-world service with business rules, notifications, and constraints:

```java
@Service
public class OrderService implements FlashCrudOperations<Order, Long> {

    private final OrderRepository repo;
    private final ProductRepository productRepo;
    private final NotificationService notifications;

    public OrderService(OrderRepository repo, ProductRepository productRepo,
                        NotificationService notifications) {
        this.repo = repo;
        this.productRepo = productRepo;
        this.notifications = notifications;
    }

    @Override
    public Page<Order> list(Pageable pageable, Map<String, String> filters) {
        if (filters.containsKey("status")) {
            return repo.findByStatus(OrderStatus.valueOf(filters.get("status")), pageable);
        }
        return repo.findAll(pageable);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return repo.findById(id);
    }

    @Override
    @Transactional
    public Order create(Map<String, Object> data) {
        Order order = new Order();
        order.setReference(generateReference());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());

        Long productId = Long.valueOf(data.get("productId").toString());
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        if (product.getStock() <= 0) {
            throw new IllegalStateException("Product out of stock: " + product.getName());
        }

        order.setProduct(product);
        order.setQuantity(Integer.parseInt(data.get("quantity").toString()));
        order.setTotalPrice(product.getPrice().multiply(BigDecimal.valueOf(order.getQuantity())));

        product.setStock(product.getStock() - order.getQuantity());
        productRepo.save(product);

        Order saved = repo.save(order);
        notifications.sendOrderConfirmation(saved);
        return saved;
    }

    @Override
    @Transactional
    public Optional<Order> update(Long id, Map<String, Object> data) {
        return repo.findById(id).map(order -> {
            if (order.getStatus() == OrderStatus.SHIPPED) {
                throw new IllegalStateException("Cannot modify a shipped order");
            }
            if (data.containsKey("status")) {
                order.setStatus(OrderStatus.valueOf(data.get("status").toString()));
            }
            return repo.save(order);
        });
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        return repo.findById(id).map(order -> {
            if (order.getStatus() == OrderStatus.SHIPPED) {
                throw new IllegalStateException("Cannot delete a shipped order");
            }
            repo.delete(order);
            return true;
        }).orElse(false);
    }

    private String generateReference() {
        return "ORD-" + Instant.now().toEpochMilli();
    }
}
```

---

## What You Control vs. What FlashAPI Handles

| Responsibility | Custom Service (you) | FlashAPI |
|----------------|---------------------|----------|
| Business logic | Yes | — |
| Data access | Yes | — |
| Validation | Yes | — |
| Transaction management | Yes | — |
| Route registration | — | Yes |
| HTTP method dispatch | — | Yes |
| Request parameter parsing | — | Yes (page, size, sort, expand) |
| Path variable conversion | — | Yes (String → Long, UUID, etc.) |
| Response serialization | — | Yes (only visible fields) |
| Error handling | — | Yes (exception → HTTP status) |
| Cache | — | Yes (read/write/invalidate) |
| Rate limiting | — | Yes (checked before delegation) |
| OpenAPI spec generation | — | Yes |

---

## Error Handling in Custom Services

When your service throws an exception, `FlashExceptionHandler` catches it and returns a JSON error response:

| Exception Type | HTTP Status | Response |
|----------------|-------------|----------|
| `IllegalArgumentException` | 400 Bad Request | `{"error": "message"}` |
| `IllegalStateException` | 409 Conflict | `{"error": "message"}` |
| `jakarta.persistence.EntityNotFoundException` | 404 Not Found | `{"error": "message"}` |
| `org.springframework.security.access.AccessDeniedException` | 403 Forbidden | `{"error": "message"}` |
| Any other `RuntimeException` | 500 Internal Server Error | `{"error": "Internal server error"}` |

### Custom exceptions

Throw standard exceptions with descriptive messages:

```java
@Override
public Order create(Map<String, Object> data) {
    if (!data.containsKey("productId")) {
        throw new IllegalArgumentException("productId is required");
    }
    // ...
}
```

Response:

```json
HTTP 400 Bad Request
{"error": "productId is required"}
```

---

## Interaction with Other Features

### Cache

Cache works transparently with custom services:
- Before `list()` / `findById()` → FlashAPI checks the cache
- After `create()` / `update()` / `delete()` → FlashAPI evicts the cache
- Your service code doesn't need to know about caching

### Rate Limiting

Rate limiting is checked **before** your service is called. If the limit is exceeded, your service never runs.

### Bulk Operations

Bulk endpoints delegate to your service:
- `POST /bulk` → calls `create()` for each item
- `PUT /bulk` → calls `update()` for each item
- `DELETE /bulk` → calls `delete()` for each item

Each item is independent — if one fails, others still succeed.

### Audit

**Important:** FlashAPI does NOT automatically audit custom services. If you need audit, inject `AuditService` and call it yourself:

```java
@Service
public class ProductService implements FlashCrudOperations<Product, Long> {

    private final AuditService auditService;

    @Override
    public Product create(Map<String, Object> data) {
        Product saved = repo.save(product);
        auditService.logCreate("Product", saved.getId().toString());
        return saved;
    }
}
```

### Export

Export endpoints call your `list()` method (with no pagination limit). Your custom filters and queries apply to exports as well.

---

## Accessing EntityManager

If you need low-level JPA access:

```java
@Service
public class ProductService implements FlashCrudOperations<Product, Long> {

    private final EntityManager em;

    public ProductService(EntityManager em) {
        this.em = em;
    }

    @Override
    public Page<Product> list(Pageable pageable, Map<String, String> filters) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Product> query = cb.createQuery(Product.class);
        Root<Product> root = query.from(Product.class);
        // build custom query with criteria API
        // ...
    }
}
```

---

## Testing Custom Services

Unit test your service independently of FlashAPI:

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository repo;

    @Mock
    private ProductRepository productRepo;

    @Mock
    private NotificationService notifications;

    @InjectMocks
    private OrderService service;

    @Test
    void createOrder_withValidData_succeeds() {
        Product product = new Product();
        product.setId(1L);
        product.setPrice(BigDecimal.TEN);
        product.setStock(50);

        when(productRepo.findById(1L)).thenReturn(Optional.of(product));
        when(repo.save(any())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(42L);
            return o;
        });

        Map<String, Object> data = Map.of("productId", 1L, "quantity", 2);
        Order result = service.create(data);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getTotalPrice()).isEqualByComparingTo("20");
        assertThat(product.getStock()).isEqualTo(48);
        verify(notifications).sendOrderConfirmation(result);
    }

    @Test
    void createOrder_outOfStock_throws() {
        Product product = new Product();
        product.setStock(0);
        when(productRepo.findById(1L)).thenReturn(Optional.of(product));

        Map<String, Object> data = Map.of("productId", 1L, "quantity", 1);

        assertThatThrownBy(() -> service.create(data))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("out of stock");
    }

    @Test
    void deleteShippedOrder_throws() {
        Order order = new Order();
        order.setStatus(OrderStatus.SHIPPED);
        when(repo.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shipped");
    }
}
```

Integration test via MockMvc (FlashAPI still handles routing):

```java
@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void createOrder_outOfStock_returns409() throws Exception {
        // Assume product with id=1 has stock=0
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"productId": 1, "quantity": 1}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("out of stock")));
    }
}
```

---

## FAQ

**Q: What if my service name doesn't follow the convention?**

Use `@FlashService(Product.class)`:

```java
@Service
@FlashService(Product.class)
public class InventoryManager implements FlashCrudOperations<Product, Long> { }
```

**Q: Can I have one service for multiple entities?**

No. Each entity gets at most one service. If you need shared logic, extract it into a helper class that both services use.

**Q: Does my service need to be `@Transactional`?**

FlashAPI doesn't manage transactions for custom services. If you need transactional behavior, annotate your methods with `@Transactional` yourself.

**Q: What happens if my service throws a checked exception?**

`FlashCrudOperations` methods don't declare checked exceptions. Wrap them in a `RuntimeException`:

```java
try {
    externalApi.call();
} catch (IOException e) {
    throw new RuntimeException("External API call failed", e);
}
```

FlashAPI returns a 500 response for unhandled runtime exceptions.

**Q: Can I inject `FlashCacheManager` into my service to control caching?**

Yes. But normally you don't need to — FlashAPI handles cache read/write/eviction around your service calls. Inject it only if you need manual eviction (e.g., after a background job updates data).

**Q: Does the `data` map in `create()` and `update()` include hidden/readOnly fields?**

No. FlashAPI filters the request body before passing it to your service. You only receive fields that are allowed for that operation (`creatableFields` for create, `updatableFields` for update).

**Q: What if I return `null` from `create()`?**

Don't. It will cause a NullPointerException during serialization. Always return the created entity.

**Q: Can I use Spring Data repositories in my custom service?**

Absolutely. This is the recommended approach:

```java
@Service
public class ProductService implements FlashCrudOperations<Product, Long> {

    private final ProductRepository repo; // Spring Data JPA repository

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    @Override
    public Page<Product> list(Pageable pageable, Map<String, String> filters) {
        return repo.findAll(pageable);
    }

    // ...
}
```
