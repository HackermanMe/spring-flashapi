# Custom Services

When FlashAPI's built-in CRUD logic isn't enough, you can provide a custom service. FlashAPI delegates all operations to your service while still handling routing, serialization, pagination, and error responses.

## The Interface

Implement `FlashCrudOperations<T, ID>`:

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

## Detection

FlashAPI detects custom services in two ways:

### 1. By naming convention

Name your bean `{entityName}Service` (lowercase first letter):

```java
@Service
public class ProductService implements FlashCrudOperations<Product, Long> {
    // For entity named "Product", FlashAPI looks for bean named "productService"
    
    @Override
    public Page<Product> list(Pageable pageable, Map<String, String> filters) {
        // your logic
    }
    
    @Override
    public Optional<Product> findById(Long id) {
        // your logic
    }
    
    @Override
    public Product create(Map<String, Object> data) {
        // your logic
    }
    
    @Override
    public Optional<Product> update(Long id, Map<String, Object> data) {
        // your logic
    }
    
    @Override
    public boolean delete(Long id) {
        // your logic
    }
}
```

### 2. By annotation

When the naming convention doesn't work (different service name, shared service, etc.):

```java
@Service
@FlashService(Product.class)
public class InventoryManager implements FlashCrudOperations<Product, Long> {
    // ...
}
```

The annotation takes priority over naming convention.

## What you control

When a custom service is found, FlashAPI delegates:

| Operation | Delegated to |
|-----------|-------------|
| `GET /products` | `service.list(pageable, filters)` |
| `GET /products/{id}` | `service.findById(id)` |
| `POST /products` | `service.create(body)` |
| `PUT /products/{id}` | `service.update(id, body)` |
| `DELETE /products/{id}` | `service.delete(id)` |
| `POST /products/{id}/restore` | `service.restore(id)` |
| `GET /products/{id}/history` | Built-in (AuditService — not delegated to custom service) |

## What FlashAPI still handles

Even with a custom service, FlashAPI takes care of:

- Route registration and HTTP method dispatch
- Request parameter parsing (pagination, sort, filters)
- Response serialization (only visible fields, per your annotations)
- Error handling and HTTP status codes
- Path variable type conversion (String → Long, UUID, etc.)

## Example: Adding business rules

```java
@Service
public class OrderService implements FlashCrudOperations<Order, Long> {

    private final OrderRepository repo;
    private final NotificationService notifications;

    public OrderService(OrderRepository repo, NotificationService notifications) {
        this.repo = repo;
        this.notifications = notifications;
    }

    @Override
    public Order create(Map<String, Object> data) {
        Order order = new Order();
        order.setReference((String) data.get("reference"));
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        
        Order saved = repo.save(order);
        notifications.sendOrderConfirmation(saved);
        return saved;
    }

    @Override
    public boolean delete(Long id) {
        Order order = repo.findById(id).orElse(null);
        if (order == null) return false;
        if (order.getStatus() == OrderStatus.SHIPPED) {
            throw new IllegalStateException("Cannot delete a shipped order");
        }
        repo.delete(order);
        return true;
    }

    // ... other methods
}
```

## Resolution order

1. Check for beans annotated with `@FlashService(Entity.class)`
2. Check for a bean named `{entityName}Service` implementing `FlashCrudOperations`
3. Fall back to `GenericCrudService`

Only step 3 uses FlashAPI's built-in logic (Criteria API, audit, soft delete). If you provide a custom service, you're responsible for your own audit/soft-delete logic if you need it.

## Important notes

- Your service **must** implement `FlashCrudOperations`. A bean with the right name but without the interface is ignored.
- The `restore()` method has a default implementation returning `false`. Override it if your entity supports soft delete.
- FlashAPI resolves services once at startup. Changing service registration at runtime has no effect.
- If your service throws an exception, FlashAPI's global exception handler (`FlashExceptionHandler`) catches it and returns an appropriate HTTP error response.
