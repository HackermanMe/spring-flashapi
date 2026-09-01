# Auto-Inject Current User (`currentUserField`)

Automatically set the authenticated user's identity on entity creation. Prevents clients from spoofing ownership fields.

---

## Quick Start

```java
@Entity
@FlashEntity(currentUserField = "authorId")
public class Post {
    @Id @GeneratedValue
    private Long id;
    private String title;
    private String authorId;  // Auto-set from SecurityContext on CREATE
}
```

The client sends:

```json
POST /api/posts
{"title": "My post"}
```

FlashAPI automatically sets `authorId` to the authenticated user's `principal.getName()`. If the client includes `authorId` in the body, it is **silently stripped** — the server always wins.

---

## With @ManyToOne Relations

```java
@Entity
@FlashEntity(currentUserField = "author")
public class Post {
    @Id @GeneratedValue
    private Long id;
    private String title;

    @ManyToOne
    private Author author;  // Auto-set via getReference()
}
```

For relation fields, FlashAPI:
1. Gets the principal name from `SecurityContext`
2. Converts it to the target entity's `@Id` type
3. Uses `entityManager.getReference()` to set the relation (no extra SELECT)

The principal name must match the target entity's `@Id` value (e.g., if `Author.id` is `Long`, the principal name must be a valid `Long`).

Both `authorId` and `author` keys are stripped from the request body.

---

## Behavior

| Operation | Behavior |
|-----------|----------|
| **CREATE** | Field auto-set from `SecurityContext.getAuthentication().getName()` |
| **UPDATE** | Field stripped from body (read-only, cannot change author) |
| **LIST/READ** | No effect |

---

## Supported Field Types

| Field Type | Principal Name Conversion |
|------------|--------------------------|
| `String` | Used directly |
| `Long` / `Integer` | `Long.parseLong()` / `Integer.parseInt()` |
| `UUID` | `UUID.fromString()` |
| `@ManyToOne` relation | Converted to target entity's `@Id` type, then `getReference()` |

---

## Synergy with Owner-Based Access Control

`currentUserField` and `ownerField` (from `@FlashSecured`) work together naturally:

```java
@Entity
@FlashEntity(currentUserField = "author")
@FlashSecured(
    roles = "authenticated",
    ownerField = "author",
    ownerAdminRoles = {"ADMIN"}
)
public class Post {
    @Id @GeneratedValue
    private Long id;
    private String title;

    @ManyToOne
    private User author;
}
```

- **CREATE**: `author` is auto-set from the authenticated user
- **UPDATE/DELETE**: only the owner (or ADMIN) can modify the post
- The client never sees or touches the `author` field — fully server-controlled

This pattern gives you secure, declarative resource ownership with zero boilerplate.

---

## Without Authentication

If no `SecurityContext` is available (anonymous request), `currentUserField` is left `null`. Combine with `@FlashSecured` to ensure only authenticated users can create entities.

---

## FAQ

**Q: What if the principal name doesn't match any entity ID?**

For `@ManyToOne` relations, `getReference()` creates a lazy proxy. The FK constraint will fail at `persist()` time with a standard JPA constraint violation error if the referenced entity doesn't exist.

**Q: Can I use this with custom services?**

`currentUserField` only applies to FlashAPI's built-in `GenericCrudService`. If you implement `FlashCrudOperations`, you handle user injection in your own `create()` method.

**Q: Does it work with `@CreatedBy`?**

They serve different purposes. `@CreatedBy` fills audit fields (String only). `currentUserField` sets business relations (`@ManyToOne`) or any typed field, and also strips it from the request body for security.
