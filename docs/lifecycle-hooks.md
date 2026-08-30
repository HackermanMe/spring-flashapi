# Lifecycle Hooks

FlashAPI lifecycle hooks enable you to inject custom business logic at specific points in entity CRUD operations. Hooks execute within the same transaction as the operation, giving you full access to the request context and entity state.

## Available Hooks

| Annotation | Trigger Point | Use Cases |
|-----------|--------------|-----------|
| `@FlashBeforeCreate` | Before `persist()` | Validation, auto-fill fields, permission checks |
| `@FlashAfterCreate` | After `persist()`, before commit | Send notifications, trigger workflows |
| `@FlashBeforeUpdate` | Before `merge()` | Validation, audit changes, permission checks |
| `@FlashAfterUpdate` | After `merge()`, before commit | Send notifications, update related entities |
| `@FlashBeforeDelete` | Before `remove()` | Permission checks, cascade deletion logic |
| `@FlashAfterDelete` | After `remove()`, before commit | Send notifications, cleanup related data |

## Quick Start

### 1. Create a Hook Component

```java
import io.github.hackermanme.flashapi.hooks.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ProductHooks {

    @FlashBeforeCreate
    public void beforeProductCreate(Object entity, HttpServletRequest request) {
        Product product = (Product) entity;
        
        // Auto-generate SKU
        if (product.getSku() == null) {
            product.setSku(generateSku(product.getName()));
        }
        
        // Permission check via request header
        String apiKey = request.getHeader("X-API-Key");
        if (!isAuthorized(apiKey, "product.create")) {
            throw new SecurityException("Unauthorized");
        }
    }

    @FlashAfterCreate
    public void afterProductCreate(Object entity, HttpServletRequest request) {
        Product product = (Product) entity;
        
        // Send notification
        notificationService.send("New product created: " + product.getName());
        
        // Trigger indexing
        searchIndex.index(product);
    }
}
```

### 2. Method Signature

All hook methods **must** follow this signature:

```java
void methodName(Object entity, HttpServletRequest request) throws Exception
```

- **`entity`**: The entity instance being operated on. Cast to your specific entity type.
- **`request`**: The current HTTP request. Access headers, user info, parameters. Can be `null` for non-HTTP triggers.
- **Return**: Hooks must return `void`.
- **Exceptions**: Throw any exception to abort the transaction.

## Transaction Behavior

Hooks execute within the **same transaction** as the CRUD operation:

- **Before hooks** → run → **persist/merge/remove** → **flush** → **After hooks** → **commit**
- If a hook throws an exception, the entire transaction rolls back.
- After hooks can still modify entities — changes are committed with the transaction.

## Use Cases

### Permission Checks

```java
@FlashBeforeUpdate
public void checkUpdatePermission(Object entity, HttpServletRequest request) {
    String userId = request.getHeader("X-User-ID");
    Document doc = (Document) entity;
    
    if (!doc.getOwnerId().equals(userId)) {
        throw new SecurityException("You can only edit your own documents");
    }
}
```

### Validation

```java
@FlashBeforeCreate
public void validateOrder(Object entity, HttpServletRequest request) {
    Order order = (Order) entity;
    
    if (order.getItems().isEmpty()) {
        throw new IllegalArgumentException("Order must have at least one item");
    }
    
    if (order.getTotalAmount() <= 0) {
        throw new IllegalArgumentException("Order total must be positive");
    }
}
```

### Audit Trail

```java
@FlashBeforeUpdate
public void captureOldState(Object entity, HttpServletRequest request) {
    Employee employee = (Employee) entity;
    String userId = request.getHeader("X-User-ID");
    
    auditLog.record("employee.update", employee.getId(), userId, 
                    Map.of("old_salary", employee.getSalary()));
}
```

### Send Notifications

```java
@FlashAfterCreate
public void notifyTeam(Object entity, HttpServletRequest request) {
    Issue issue = (Issue) entity;
    
    slack.postMessage("#support", 
        "New issue created: " + issue.getTitle() + " (Priority: " + issue.getPriority() + ")");
}
```

### Cascade Operations

```java
@FlashBeforeDelete
public void archiveRelatedData(Object entity, HttpServletRequest request) {
    Project project = (Project) entity;
    
    // Archive tasks before project deletion
    taskRepository.findByProjectId(project.getId())
        .forEach(task -> archiveService.archive(task));
}
```

## Performance

- **Hooks are loaded once at startup**: Reflection overhead happens only during application initialization, not on every request.
- **Indexed lookup**: Hook registry uses a `Map<AnnotationType, List<Hook>>` for O(1) lookups.
- **No overhead when no hooks exist**: If no hooks are registered for a given operation, execution skips immediately.

## Security

- **Request access**: Hooks receive the `HttpServletRequest` for authentication/authorization checks.
- **Same transaction**: Hooks cannot commit partial changes — all-or-nothing.
- **Exception = rollback**: Any thrown exception aborts the entire operation.

## Error Handling

When a hook throws an exception:

1. Transaction rolls back
2. Exception propagates to the controller
3. FlashExceptionHandler converts it to an HTTP error response

```java
@FlashBeforeCreate
public void validate(Object entity, HttpServletRequest request) {
    if (!isValid(entity)) {
        throw new IllegalArgumentException("Invalid entity"); // → 400 Bad Request
    }
}
```

## Testing

```java
@SpringBootTest
@AutoConfigureMockMvc
class MyHooksTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MyHookListener hookListener; // Your @Component with hooks

    @Test
    void testHookInvocation() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Test\"}"))
            .andExpect(status().isCreated());

        // Verify hook was called
        assertThat(hookListener.wasCalled()).isTrue();
    }
}
```

## Advanced: Entity-Specific Hooks

To run hooks only for specific entity types, check the entity class:

```java
@FlashBeforeCreate
public void beforeCreate(Object entity, HttpServletRequest request) {
    if (entity instanceof Product) {
        Product product = (Product) entity;
        // Product-specific logic
    } else if (entity instanceof Order) {
        Order order = (Order) entity;
        // Order-specific logic
    }
}
```

Or create separate hook components per entity:

```java
@Component
public class ProductHooks {
    @FlashBeforeCreate
    public void beforeProductCreate(Object entity, HttpServletRequest request) {
        if (!(entity instanceof Product)) return;
        Product product = (Product) entity;
        // ...
    }
}

@Component
public class OrderHooks {
    @FlashBeforeCreate
    public void beforeOrderCreate(Object entity, HttpServletRequest request) {
        if (!(entity instanceof Order)) return;
        Order order = (Order) entity;
        // ...
    }
}
```

## Example: Complete Workflow

```java
@Component
public class OrderLifecycleHooks {

    @Autowired
    private InventoryService inventory;

    @Autowired
    private PaymentService payment;

    @Autowired
    private NotificationService notifications;

    @FlashBeforeCreate
    public void validateAndReserve(Object entity, HttpServletRequest request) {
        Order order = (Order) entity;
        
        // Check stock availability
        for (OrderItem item : order.getItems()) {
            if (!inventory.isAvailable(item.getProductId(), item.getQuantity())) {
                throw new IllegalStateException("Product " + item.getProductId() + " out of stock");
            }
        }
        
        // Reserve inventory
        order.getItems().forEach(item -> 
            inventory.reserve(item.getProductId(), item.getQuantity(), order.getId())
        );
    }

    @FlashAfterCreate
    public void processPaymentAndNotify(Object entity, HttpServletRequest request) {
        Order order = (Order) entity;
        
        // Process payment
        PaymentResult result = payment.charge(order.getTotalAmount(), order.getPaymentMethod());
        if (!result.isSuccess()) {
            throw new PaymentException("Payment failed: " + result.getError());
        }
        
        // Send confirmation email
        notifications.sendEmail(order.getCustomerEmail(), 
            "Order Confirmation", 
            "Your order #" + order.getId() + " has been confirmed.");
        
        // Notify fulfillment team
        notifications.sendSlack("#fulfillment", 
            "New order #" + order.getId() + " ready for processing.");
    }

    @FlashBeforeDelete
    public void refundAndRelease(Object entity, HttpServletRequest request) {
        Order order = (Order) entity;
        
        // Refund payment
        if (order.isPaid()) {
            payment.refund(order.getPaymentId());
        }
        
        // Release reserved inventory
        order.getItems().forEach(item -> 
            inventory.release(item.getProductId(), item.getQuantity(), order.getId())
        );
    }
}
```

## Comparison with JPA Lifecycle Callbacks

| Feature | FlashAPI Hooks | JPA `@PrePersist` etc. |
|---------|---------------|----------------------|
| Request access | ✅ Yes | ❌ No |
| Spring beans injection | ✅ Yes | ⚠️ Requires `@Configurable` |
| Separate from entity | ✅ Yes | ❌ Must be in entity class |
| Test isolation | ✅ Easy | ⚠️ Harder |
| Multiple listeners | ✅ Yes | ⚠️ Requires `@EntityListeners` |

Use FlashAPI hooks when you need **request context**, **service injection**, or **separation of concerns**. Use JPA callbacks for simple entity-internal logic (e.g., `createdAt` auto-fill).

## FAQ

**Q: Can I have multiple hooks of the same type?**  
A: Yes. All registered hooks execute in registration order.

**Q: What if `getCurrentRequest()` returns null?**  
A: Hooks invoked outside HTTP requests (e.g., batch jobs) receive `null`. Check before accessing:
```java
if (request != null) {
    String userId = request.getHeader("X-User-ID");
}
```

**Q: Can I modify the entity in "after" hooks?**  
A: Yes. Changes made in after hooks are committed with the transaction.

**Q: Do hooks work with soft delete?**  
A: Yes. `@FlashBeforeDelete` and `@FlashAfterDelete` fire for both soft and hard deletes.

**Q: Can I call other FlashAPI operations inside hooks?**  
A: Yes, but be careful with infinite recursion. Example: creating a `Log` entity in `afterCreate` hook → that triggers `beforeCreate` hook for `Log` → loop. Add guards:
```java
@FlashAfterCreate
public void log(Object entity, HttpServletRequest request) {
    if (entity instanceof Log) return; // Guard against recursion
    logRepository.save(new Log(entity.toString()));
}
```

## See Also

- [Webhooks](../README.md#webhooks) — HTTP callbacks for external systems
- [Audit Logging](../README.md#audit-logging) — Built-in change tracking
- [Multi-Tenancy](../README.md#multi-tenancy) — Request-scoped tenant isolation
