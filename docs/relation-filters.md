# Relation Filters

Filter entities by related entity fields using dot-notation.

## Quick Start

```bash
# Products in category 5
GET /api/products?category.id=5

# Faculties at university 10
GET /api/faculties?university.id=10
```

## Syntax

`?relationName.fieldName=value`

- `relationName`: the @ManyToOne or @OneToOne field on your entity
- `fieldName`: a field on the target entity  
- `value`: the filter value

## Limitations

- Only 1 level deep (`university.id` works, `university.country.code` doesn't)
- Only @ManyToOne and @OneToOne relations (not collections)

## Operators

Combine with standard operators:

```bash
?category.id[gt]=5
?university.name[like]=Harvard
```

## Performance

FlashAPI generates a single SQL JOIN per relation, not N+1 queries. Multiple filters on the same relation reuse the same join:

```bash
# Single JOIN for both filters
GET /api/products?category.id=5&category.name[like]=Electronics
```

## Error Handling

- Unknown relations are silently ignored  
- Filtering by collection relations (`@OneToMany`, `@ManyToMany`) returns 400 Bad Request
- Nested relation filters (e.g., `category.parent.id`) return 400 Bad Request

## Implementation Details

Relation filters use JPA Criteria API `Join` objects to traverse associations:

```java
Join<Product, Category> join = root.join("category");
predicates.add(cb.equal(join.get("id"), categoryId));
```

Join instances are cached per query to avoid duplicate joins.
