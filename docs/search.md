# Full-Text Search

FlashAPI provides built-in full-text search across all String fields of an entity.

## Usage

```http
GET /api/products?search=laptop
```

This searches all String fields (name, description, code, etc.) for "laptop" using case-insensitive partial matching.

## How It Works

- Generates a `LOWER(field) LIKE '%term%'` predicate for each String field
- Combines all field predicates with `OR`
- Combined with other filters using `AND`

## Examples

### Basic search

```http
GET /api/products?search=pro
```

Returns products where ANY String field contains "pro" (case-insensitive).

### Combined with filters

```http
GET /api/products?search=apple&price.gte=1000
```

Searches for "apple" AND filters by price >= 1000.

### Combined with sort

```http
GET /api/products?search=pro&sort=price,asc
```

### Combined with pagination

```http
GET /api/products?search=pro&page=0&size=10
```

The `totalElements` in the response meta reflects the filtered count.

## Search Fields

FlashAPI searches across ALL visible String fields in the entity. For a Product with:
- `name` (String)
- `description` (String)
- `sku` (String)
- `price` (Double)
- `stock` (Integer)

The search will match against `name`, `description`, and `sku`. Non-String fields are ignored.

## Empty Search

An empty or blank `search` parameter is ignored (returns all results):

```http
GET /api/products?search=     → same as GET /api/products
```

## Performance Notes

- The search uses database-level `LIKE` with wildcards — this performs a full table scan
- For small to medium datasets (< 100k rows), this is fast enough
- For larger datasets, consider adding database indexes on frequently searched columns
- For true full-text search on large datasets, consider integrating Elasticsearch or PostgreSQL's `tsvector`

## Interaction with Cache

Search results are cached when caching is enabled. The cache key includes the search term, so different searches produce different cache entries. Cache is invalidated on any write operation.
