# Progressive Disclosure

FlashAPI follows a "progressive disclosure" philosophy: it provides everything out of the box, and recedes as you define your own classes.

## The Three Levels

### Level 1: Zero Config

Annotate your entity. Get a full CRUD API.

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

FlashAPI generates: repository logic, service logic, controller, routes, serialization, pagination, filtering. You write nothing else.

### Level 2: Custom Service

Create a service bean for your entity. FlashAPI delegates all operations to it.

```java
@Service
public class ProductService {
    // FlashAPI detects this by naming convention (Product → ProductService)
    // and delegates CRUD operations to it instead of GenericCrudService
}
```

Or use `@FlashService` for non-standard names:

```java
@Service
@FlashService(Product.class)
public class InventoryManager {
    // ...
}
```

At this level, you control the business logic. FlashAPI still handles routing, serialization, pagination, and error handling.

### Level 3: Custom Controller

Define your own `@RestController` that maps to the same path. FlashAPI detects the conflict and backs off entirely for that entity.

```java
@RestController
@RequestMapping("/api/products")
public class ProductController {
    // Full control. FlashAPI does nothing for Product.
}
```

## How detection works

FlashAPI registers routes at application startup (via `ContextRefreshedEvent`). Before registering a route, it checks if a user-defined mapping already exists for that path pattern. If it does, FlashAPI skips that entity entirely and logs a message:

```
FlashAPI: skipping Product — user-defined controller already maps /api/products
```

For services, FlashAPI looks up the Spring ApplicationContext for:
1. A bean named `{entityName}Service` (e.g., `productService` for `Product`)
2. Any bean annotated with `@FlashService(Product.class)`

If found, FlashAPI uses that bean for CRUD operations instead of `GenericCrudService`.

## Why this matters

Most CRUD generators force an all-or-nothing choice: either you use the generator, or you write everything by hand. FlashAPI lets you:

- Start fast with zero boilerplate
- Override one entity's logic without touching others
- Gradually take control as requirements grow
- Never fight the framework

The idea: FlashAPI should feel like a helpful colleague who steps back when you say "I got this."
