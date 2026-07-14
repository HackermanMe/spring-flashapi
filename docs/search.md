# Full-Text Search

FlashAPI provides built-in search across all String fields of an entity using database-level `LIKE` queries. No configuration, no external dependencies.

## Usage

```http
GET /api/products?search=laptop
```

Searches all visible String fields for "laptop" using case-insensitive partial matching (`LOWER(field) LIKE '%laptop%'`).

## How It Works

1. FlashAPI iterates over all `FieldMetadata` where `field.type() == String.class`.
2. For each String field, generates: `cb.like(cb.lower(root.get(fieldName)), "%term%")`.
3. Combines all field predicates with `OR`.
4. Combines the search predicate with other filters using `AND`.

The generated SQL (conceptually):

```sql
SELECT * FROM product
WHERE (LOWER(name) LIKE '%laptop%'
    OR LOWER(description) LIKE '%laptop%'
    OR LOWER(sku) LIKE '%laptop%')
AND price >= 500        -- additional filter
ORDER BY name ASC
LIMIT 20 OFFSET 0;
```

## Concrete curl Examples

### Basic search

```bash
curl -s http://localhost:8080/api/products?search=pro | jq
```

Response:

```json
{
  "data": [
    {
      "id": 1,
      "name": "MacBook Pro",
      "description": "Apple laptop with M3 chip",
      "sku": "MBP-2024",
      "price": 2499.00
    },
    {
      "id": 5,
      "name": "AirPods Pro",
      "description": "Active noise cancellation earbuds",
      "sku": "APP-2024",
      "price": 249.00
    }
  ],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

The term "pro" matched `name` on both results (case-insensitive).

### Combined with filters

```bash
curl -s "http://localhost:8080/api/products?search=apple&price.gte=1000" | jq
```

Response:

```json
{
  "data": [
    {
      "id": 1,
      "name": "MacBook Pro",
      "description": "Apple laptop with M3 chip",
      "sku": "MBP-2024",
      "price": 2499.00
    }
  ],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

Search matched "apple" in the `description` field, AND `price >= 1000` filtered out the AirPods.

### Combined with sort and pagination

```bash
curl -s "http://localhost:8080/api/products?search=pro&sort=price,desc&page=0&size=5" | jq
```

### Empty search

An empty or blank `search` parameter is ignored (returns all results):

```bash
# These are equivalent:
curl http://localhost:8080/api/products?search=
curl http://localhost:8080/api/products
```

### No results

```bash
curl -s http://localhost:8080/api/products?search=xyznonexistent | jq
```

```json
{
  "data": [],
  "meta": {
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  }
}
```

## Search Fields

FlashAPI searches ALL visible fields where `type == String.class`. For a Product entity with:

| Field | Type | Searched? |
|-------|------|-----------|
| `id` | Long | No |
| `name` | String | Yes |
| `description` | String | Yes |
| `sku` | String | Yes |
| `price` | Double | No |
| `stock` | Integer | No |
| `createdAt` | LocalDateTime | No |

Only `name`, `description`, and `sku` participate in search.

## Interaction with Other Features

### Filters

Search and filters combine with AND:

```
GET /api/products?search=pro&category=electronics&price.lte=3000
```

Translates to: `(name LIKE '%pro%' OR description LIKE '%pro%' OR ...) AND category = 'electronics' AND price <= 3000`.

### Soft delete

If the entity has `softDelete = true`, search automatically excludes soft-deleted records (unless `?deleted=true` is passed).

### Caching

Search results are cached when `cache = true`. The cache key includes the search term, so `?search=foo` and `?search=bar` are cached independently. Any write operation invalidates the entire entity cache.

### Export

Search can be combined with export:

```bash
curl -o results.csv "http://localhost:8080/api/products/export?format=csv&search=pro"
```

## Indexing for Better Performance

The default `LIKE '%term%'` query with leading wildcard cannot use standard B-tree indexes. For datasets beyond a few thousand rows, add trigram or GIN indexes.

### PostgreSQL trigram index

```sql
-- Enable extension (once per database)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Create index on frequently searched columns
CREATE INDEX idx_product_name_trgm ON product USING gin (name gin_trgm_ops);
CREATE INDEX idx_product_description_trgm ON product USING gin (description gin_trgm_ops);
```

This makes `LIKE '%term%'` queries use the GIN index instead of a sequential scan.

### MySQL full-text index (partial improvement)

MySQL's B-tree indexes do not help with leading-wildcard LIKE. However, you can add a `FULLTEXT` index for MySQL's own search -- though FlashAPI's generated queries still use `LIKE`, so the benefit is limited to cases where the optimizer can leverage it:

```sql
ALTER TABLE product ADD FULLTEXT INDEX ft_product_name (name);
ALTER TABLE product ADD FULLTEXT INDEX ft_product_desc (description);
```

### Composite approach

For best results with FlashAPI's LIKE-based search, focus indexes on the columns most likely to be searched and keep those columns short (names, SKUs, codes) rather than long text (descriptions, comments).

### Monitoring slow queries

```sql
-- PostgreSQL: find sequential scans on your entity tables
SELECT relname, seq_scan, seq_tup_read
FROM pg_stat_user_tables
WHERE relname = 'product'
ORDER BY seq_tup_read DESC;
```

## Search Limitations

FlashAPI's built-in search is intentionally simple. It does **not** provide:

| Feature | Status | Alternative |
|---------|--------|-------------|
| Stemming (search "running" finds "run") | Not supported | Elasticsearch, PostgreSQL tsvector |
| Fuzzy matching (typo tolerance) | Not supported | Elasticsearch, Meilisearch |
| Relevance scoring / ranking | Not supported | Elasticsearch, Solr |
| Per-field weighting (title > description) | Not supported | Elasticsearch boost |
| Phrase search ("exact phrase") | Not supported | PostgreSQL `phraseto_tsquery` |
| Highlighting (matched snippet) | Not supported | Elasticsearch highlight |
| Stop word removal | Not supported | Any full-text engine |
| Multi-language analysis | Not supported | Elasticsearch with language analyzers |
| Synonym expansion | Not supported | Elasticsearch synonym filter |
| Search-as-you-type / autocomplete | Not supported | Elasticsearch completion suggester |

### What it does provide

- Case-insensitive substring match across all String fields
- Works with any JPA-compatible database (H2, PostgreSQL, MySQL, Oracle, SQL Server)
- Zero configuration
- Composable with all other FlashAPI features (filters, sort, pagination, export)
- Adequate for admin panels, back-office tools, and datasets under ~100k rows

## Migrating to Full-Text Search Engines

When FlashAPI's built-in search is no longer sufficient, here are migration paths.

### Option 1: PostgreSQL tsvector (no extra infrastructure)

Stay within your existing database. Best for moderate datasets (up to ~10M rows) that need stemming and ranking.

**Step 1: Add a tsvector column and index**

```sql
ALTER TABLE product ADD COLUMN search_vector tsvector;

UPDATE product SET search_vector =
    setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(description, '')), 'B');

CREATE INDEX idx_product_search ON product USING gin(search_vector);
```

**Step 2: Keep it updated with a trigger**

```sql
CREATE OR REPLACE FUNCTION product_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', coalesce(NEW.name, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(NEW.description, '')), 'B');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_product_search
    BEFORE INSERT OR UPDATE ON product
    FOR EACH ROW EXECUTE FUNCTION product_search_trigger();
```

**Step 3: Query via a custom repository**

```java
@Query(value = "SELECT * FROM product WHERE search_vector @@ plainto_tsquery('english', :query) " +
               "ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC",
       nativeQuery = true)
List<Product> fullTextSearch(@Param("query") String query, Pageable pageable);
```

**Step 4: Disable FlashAPI search for this entity**

FlashAPI search cannot be selectively disabled per entity. Instead, override the list endpoint with a custom controller that uses the native query above, and exclude LIST from FlashAPI:

```java
@FlashEntity(exclude = {"LIST"})
public class Product { ... }
```

### Option 2: Elasticsearch (dedicated search infrastructure)

Best for large datasets, complex queries, multi-language support, and near-real-time indexing.

**Dependencies:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

**Configuration:**

#### application.yml

```yaml
spring:
  elasticsearch:
    uris: http://localhost:9200
```

#### application.properties

```properties
spring.elasticsearch.uris=http://localhost:9200
```

**Index document:**

```java
@Document(indexName = "products")
public class ProductDocument {
    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String sku;
}
```

**Sync strategy:**

Listen to FlashAPI's entity lifecycle (or use a database CDC tool like Debezium) to keep Elasticsearch in sync:

```java
@Component
public class ProductIndexer {

    private final ElasticsearchOperations esOps;

    @TransactionalEventListener
    public void onProductChange(EntityChangeEvent event) {
        if ("Product".equals(event.getEntityName())) {
            ProductDocument doc = mapToDocument(event.getEntity());
            esOps.save(doc);
        }
    }
}
```

### Option 3: Meilisearch / Typesense (simple dedicated search)

For teams that want full-text search without the operational complexity of Elasticsearch. Both provide typo tolerance, ranking, and instant search with minimal configuration.

```bash
# Index via HTTP
curl -X POST 'http://localhost:7700/indexes/products/documents' \
  -H 'Content-Type: application/json' \
  --data-binary @products.json
```

Use FlashAPI for CRUD and a thin search proxy for search queries. The trade-off: one more service to deploy, but dramatically better search quality.

### Decision matrix

| Criteria | FlashAPI built-in | PostgreSQL tsvector | Elasticsearch |
|----------|-------------------|---------------------|---------------|
| Setup effort | Zero | Low (SQL migrations) | High (cluster) |
| Extra infrastructure | None | None | Yes |
| Stemming | No | Yes | Yes |
| Typo tolerance | No | No | Yes |
| Relevance ranking | No | Yes (ts_rank) | Yes |
| Multi-language | No | Limited | Yes |
| Dataset size sweet spot | < 100k rows | < 10M rows | Unlimited |
| Operational complexity | None | Low | High |

## Performance Notes

| Dataset Size | Expected Behavior |
|-------------|-------------------|
| < 10k rows | Instant (< 10ms) |
| 10k - 100k rows | Fast (10-100ms) without indexes |
| 100k - 1M rows | Slow without trigram indexes; fast with them |
| > 1M rows | Consider dedicated search engine |

These are ballpark figures for PostgreSQL on modern hardware. Actual performance depends on row width, number of String fields, and available memory.

## FAQ

**Q: Can I search only specific fields instead of all String fields?**
A: Not via configuration. FlashAPI searches all visible String fields. To restrict search to specific fields, exclude unwanted String fields from visibility using `@JsonIgnore`, or override the list endpoint with a custom controller.

**Q: Does search work with enum fields?**
A: No. Only fields with Java type `String.class` are included. Enum fields (mapped to `string` in OpenAPI) are not searched.

**Q: Is the search term escaped for SQL injection?**
A: Yes. The search term is passed as a JPA parameter via the Criteria API, not concatenated into SQL. It is safe from injection.

**Q: Can I search across related entities (joins)?**
A: No. Search operates on the root entity's fields only. Expanded relations are not searched.

**Q: What about special characters in the search term?**
A: Characters like `%` and `_` (SQL wildcards) are passed as literal values through JPA parameter binding. Searching for "100%" will match the literal string "100%", not "100" followed by anything.

**Q: Can I use multiple search terms (AND/OR)?**
A: No. The entire `search` parameter is treated as a single substring. `?search=red shoes` searches for the literal string "red shoes", not "red" AND "shoes" separately.

**Q: Does search work with H2 in tests?**
A: Yes. The LIKE operator and LOWER function are standard SQL and work identically on H2, PostgreSQL, MySQL, and other JPA-supported databases.
