# Declarative Counters (`@FlashCounter`)

Maintain denormalized counters that auto-increment on CREATE and auto-decrement on DELETE of related entities. No lifecycle hooks needed.

---

## Quick Start

```java
@Entity
@FlashEntity
public class Post {
    @Id @GeneratedValue
    private Long id;
    private String content;

    @FlashCounter(source = PostLike.class, relation = "post")
    private int likeCount;  // Auto-maintained, read-only
}

@Entity
@FlashEntity
public class PostLike {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne
    private Post post;   // Must be @ManyToOne — referenced by @FlashCounter(relation)

    private String userId;
}
```

When a `PostLike` is created → `Post.likeCount` increments by 1.
When a `PostLike` is deleted → `Post.likeCount` decrements by 1.

---

## How It Works

1. **At startup**: FlashAPI scans for `@FlashCounter` fields, validates that the `source` entity and `relation` field exist, and builds a global counter registry.
2. **On CREATE**: after persisting the source entity, FlashAPI executes an atomic JPQL `UPDATE ... SET counter = counter + 1 WHERE id = ?`.
3. **On DELETE**: same but with `counter - 1`.
4. **Atomicity**: the counter update is a single SQL statement — no read-modify-write race condition. Safe under concurrent writes.

---

## Annotation Reference

```java
@FlashCounter(
    source = PostLike.class,  // Entity class that drives the counter
    relation = "post"         // @ManyToOne field on source pointing to this entity
)
private int likeCount;
```

| Attribute | Type | Description |
|-----------|------|-------------|
| `source` | Class<?> | The entity whose creation/deletion triggers counter updates |
| `relation` | String | The `@ManyToOne` field name on the source entity that points back to the entity containing the counter |

---

## Behavior

| Event | Counter Effect |
|-------|---------------|
| Source entity created | +1 |
| Source entity deleted (hard or soft) | -1 |
| Source entity updated | No change |
| Counter field in request body | Silently stripped (read-only) |

---

## Multiple Counters

An entity can have multiple counters from different sources:

```java
@Entity
@FlashEntity
public class Post {
    @Id @GeneratedValue
    private Long id;

    @FlashCounter(source = PostLike.class, relation = "post")
    private int likeCount;

    @FlashCounter(source = Comment.class, relation = "post")
    private int commentCount;
}
```

---

## Supported Field Types

The counter field should be `int` or `Integer`. The initial value is `0` (database default).

---

## Validation at Startup

FlashAPI validates counter configuration at startup and fails fast with clear error messages:

| Error | Cause |
|-------|-------|
| `references source X which is not a @FlashEntity` | The `source` class is not annotated with `@FlashEntity` |
| `references relation 'x' on Y which does not exist` | The `relation` field doesn't exist on the source entity |
| `must be @ManyToOne` | The relation field is not a `@ManyToOne` relation |

---

## Limitations

- Only works with FlashAPI's built-in `GenericCrudService`. Custom services (`FlashCrudOperations`) handle their own counter logic.
- The `relation` field must be `@ManyToOne` (not `@OneToOne` or collection types).
- Counter can go negative if deletions happen without corresponding creates (e.g., if records existed before `@FlashCounter` was added). Initialize counters via a migration script if needed.

---

## FAQ

**Q: Is the counter update atomic?**

Yes. It uses a single JPQL `UPDATE` statement (`SET counter = counter + 1`), which translates to an atomic SQL update. No read-modify-write race condition.

**Q: Does it work with soft delete?**

Yes. Soft-deleting a source entity decrements the counter.

**Q: Can I set the counter field manually?**

No. `@FlashCounter` fields are automatically read-only — the value is stripped from CREATE and UPDATE request bodies.

**Q: What about bulk operations?**

Bulk create and delete trigger counter updates for each item individually within the same transaction.
