# Export (CSV, Excel, PDF)

Spring FlashAPI automatically registers an export endpoint for every `@FlashEntity` that has LIST access. No controller code needed.

## Endpoint

```
GET /api/{entity}/export?format={csv|xlsx|pdf}
```

---

## Quick decision guide

| Format | Best for | Size limit | Dependencies |
|--------|----------|------------|--------------|
| **CSV** | Data pipelines, large datasets, inter-system exchange | Unlimited (streamed) | None |
| **XLSX** | Business users, typed data (numbers/dates), Excel power users | ~1M rows (Excel limit) | Apache POI |
| **PDF** | Reports, printable documents, branded exports | Use `max-rows`; all data held in memory | JasperReports |

**Rules of thumb:**

- Need it fast with zero setup? **CSV.**
- End user opens it in Excel/Sheets? **XLSX** (typed cells, no import wizard).
- Goes to management or gets printed? **PDF.**
- Dataset > 100k rows? **CSV** (streaming, O(1) memory). Avoid PDF.

---

## curl examples

```bash
# Basic CSV export
curl -o products.csv "http://localhost:8080/api/products/export?format=csv"

# Excel export with filters and sorting
curl -o products.xlsx "http://localhost:8080/api/products/export?format=xlsx&price.gte=100&sort=name,asc"

# PDF export
curl -o products.pdf "http://localhost:8080/api/products/export?format=pdf"

# Export with multiple filters
curl -o filtered.csv "http://localhost:8080/api/orders/export?format=csv&status.eq=SHIPPED&createdAt.gte=2024-01-01&sort=createdAt,desc"

# Export with IN filter (multiple values)
curl -o categories.xlsx "http://localhost:8080/api/products/export?format=xlsx&category.in=ELECTRONICS,BOOKS,TOYS"

# Export including soft-deleted records
curl -o all.csv "http://localhost:8080/api/products/export?format=csv&deleted=true"

# Export with contains filter (partial text match)
curl -o search.csv "http://localhost:8080/api/products/export?format=csv&name.contains=phone&brand.startswith=Sam"

# Export with authentication header
curl -H "Authorization: Bearer <token>" -o report.pdf "http://localhost:8080/api/invoices/export?format=pdf"

# Export and check HTTP status
curl -w "\n%{http_code}" -o export.csv "http://localhost:8080/api/products/export?format=csv"
```

---

## Formats

### CSV

- **Dependency:** None (always available)
- **Content-Type:** `text/csv; charset=UTF-8`
- **Filename header:** `Content-Disposition: attachment; filename="{entity}_{timestamp}.csv"`
- UTF-8 with BOM for proper Excel recognition
- Standard RFC 4180 escaping (quotes around values containing commas or newlines)
- Streamed row by row — constant memory regardless of dataset size

### Excel (XLSX)

- **Dependency:** `org.apache.poi:poi-ooxml` (optional, must be on classpath)
- **Content-Type:** `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- **Filename header:** `Content-Disposition: attachment; filename="{entity}_{timestamp}.xlsx"`
- Uses streaming API (`SXSSFWorkbook`) — constant memory with a 100-row window
- Numeric and date values are typed correctly (not stored as strings)

#### Maven

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

#### Gradle (Kotlin DSL)

```kotlin
implementation("org.apache.poi:poi-ooxml:5.2.5")
```

#### Gradle (Groovy DSL)

```groovy
implementation 'org.apache.poi:poi-ooxml:5.2.5'
```

### PDF

- **Dependency:** `net.sf.jasperreports:jasperreports` (optional, must be on classpath)
- **Content-Type:** `application/pdf`
- **Filename header:** `Content-Disposition: attachment; filename="{entity}_{timestamp}.pdf"`
- Two modes:
  - **Auto-generated layout** — clean table generated dynamically from entity metadata
  - **Custom template** — your own `.jrxml` report with full JasperReports power

#### Maven

```xml
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports</artifactId>
    <version>6.21.0</version>
</dependency>
```

#### Gradle (Kotlin DSL)

```kotlin
implementation("net.sf.jasperreports:jasperreports:6.21.0")
```

#### Gradle (Groovy DSL)

```groovy
implementation 'net.sf.jasperreports:jasperreports:6.21.0'
```

---

## Custom PDF templates

Place a `.jrxml` file in the configured reports path (default: `classpath:flashapi/reports/`):

```
src/main/resources/flashapi/reports/products.jrxml
```

The file name must match the entity path (the same value used in URLs). If your entity is available at `/api/products`, the template file is `products.jrxml`.

### How data is passed to the template

FlashAPI feeds your template with a **`JRBeanCollectionDataSource`** containing `Map<String, Object>` entries. Each map has the visible fields of the entity as keys.

Available **parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| `ENTITY_NAME` | `String` | Entity name (e.g., `"Product"`) |
| `RECORD_COUNT` | `Integer` | Total number of records in the export |

### Template discovery

1. FlashAPI checks if a `.jrxml` file exists at `{reports-path}/{entity-path}.jrxml`
2. If found: compiles and uses it
3. If not found: generates a clean table layout dynamically

This follows the FlashAPI philosophy: **provide a working default, recede when you take over.**

---

## Filtering and sorting

All existing filters apply to exports:

```
GET /api/products/export?format=csv&name.contains=phone&price.gte=100&sort=price,desc
```

The export respects:

- All filter operators: `eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `contains`, `startswith`, `endswith`, `isnull`, `in`
- Sorting: `sort=field,direction` (supports multiple: `sort=price,desc&sort=name,asc`)
- Soft delete visibility: `deleted=true` to include soft-deleted records

Pagination parameters (`page`, `size`) are **ignored** — export always returns all matching records.

---

## Column rules

Exported columns follow the same visibility rules as the GET response:

| Annotation | Exported? |
|------------|-----------|
| `@FlashHidden` | No |
| `@FlashWriteOnly` | No |
| `@FlashReadOnly` | Yes |
| No annotation | Yes |

- Column order follows field declaration order in the entity class
- Column headers are the Java field names (e.g., `firstName`, `createdAt`)

---

## Configuration

### application.yml

```yaml
flashapi:
  export:
    max-rows: 0                       # 0 = unlimited; positive value caps exports
    reports-path: flashapi/reports     # classpath path for .jrxml templates
```

### application.properties

```properties
flashapi.export.max-rows=0
flashapi.export.reports-path=flashapi/reports
```

### Property reference

| Property | Default | Description |
|----------|---------|-------------|
| `flashapi.export.max-rows` | `0` | Maximum rows per export. `0` = no limit. When exceeded, the export is truncated and a warning is logged. |
| `flashapi.export.reports-path` | `flashapi/reports` | Classpath location for custom `.jrxml` templates. |

### Configuration examples

**Limit exports to 10,000 rows:**

```yaml
# application.yml
flashapi:
  export:
    max-rows: 10000
```

```properties
# application.properties
flashapi.export.max-rows=10000
```

**Custom reports directory:**

```yaml
# application.yml
flashapi:
  export:
    reports-path: reports/jasper
```

```properties
# application.properties
flashapi.export.reports-path=reports/jasper
```

---

## Error responses

| Case | HTTP Status | Message |
|------|-------------|---------|
| Missing `format` param | 400 | Invalid or missing 'format' parameter |
| Unknown format (e.g., `xml`) | 400 | Invalid or missing 'format' parameter |
| XLSX without POI on classpath | 400 | Excel export requires Apache POI |
| PDF without JasperReports on classpath | 400 | PDF export requires JasperReports |
| Entity has no LIST access | 405 | Method Not Allowed |

---

## Performance notes

| Format | Memory model | Notes |
|--------|--------------|-------|
| CSV | O(1) — streamed | No practical size limit |
| XLSX | O(1) — 100-row window via `SXSSFWorkbook` | POI streaming API handles large files |
| PDF | O(n) — full dataset in memory | Use `max-rows` or filters for large datasets |

- **Batched fetching:** Data is fetched in batches of 500 rows internally to avoid loading the entire result set in a single query.
- **Connection handling:** The database connection is released between batches; exports do not hold a connection for the entire duration.
- **Timeout:** Exports follow your standard Spring MVC timeout settings. For very large exports, consider increasing `spring.mvc.async.request-timeout`.

---

## FAQ

**Q: Can I export a subset of columns?**
A: Not directly via query parameters. Use `@FlashHidden` on fields you never want exported, or create a dedicated `@FlashEntity` view that only exposes the columns you need.

**Q: Does export respect security/authorization?**
A: Yes. Export goes through the same filter chain as any other endpoint. If your entity requires authentication or specific roles, those rules apply identically to the export endpoint.

**Q: What happens with `@ManyToOne` or nested objects?**
A: Nested entities are exported as their `toString()` representation. For relational IDs, the foreign key value is used. Complex nested graphs are flattened to a single cell value.

**Q: Can I change the column header names?**
A: Column headers use the Java field name by default. There is no annotation override for export headers in the current version — if you need custom headers, use a custom `.jrxml` template (PDF) or post-process the CSV.

**Q: What encoding is used for CSV?**
A: UTF-8 with a BOM (byte order mark). This ensures Excel on Windows opens the file correctly without a manual import step.

**Q: Can I export to JSON instead?**
A: The standard LIST endpoint already returns JSON. Use `GET /api/{entity}?size=999999` (or a sufficiently large size) if you need all records as JSON. The export endpoint is designed for non-JSON formats.

**Q: How do I schedule recurring exports?**
A: FlashAPI does not include a scheduler. Call the export endpoint from a cron job, Spring `@Scheduled` method, or any external scheduler via HTTP.

**Q: Does the export endpoint support `Accept` header content negotiation?**
A: No. The format is determined exclusively by the `format` query parameter. The `Accept` header is ignored.

**Q: What if my JRXML template fails to compile?**
A: FlashAPI logs the compilation error and returns a 500 with a generic error message. Check your application logs for the full JasperReports stack trace. The auto-generated layout is NOT used as a fallback when a template exists but fails.

**Q: Is there a way to export all entities at once (bulk multi-entity export)?**
A: No. Each export call targets a single entity. Orchestrate multiple calls client-side if you need data from several entities.
