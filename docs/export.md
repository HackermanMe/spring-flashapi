# Export (CSV, Excel, PDF)

Spring FlashAPI automatically registers an export endpoint for every `@FlashEntity` that has LIST access.

## Endpoint

```
GET /api/{entity}/export?format={csv|xlsx|pdf}
```

## Quick examples

```bash
# Export all products as CSV
curl -O http://localhost:8080/api/products/export?format=csv

# Export filtered data as Excel
curl -O "http://localhost:8080/api/products/export?format=xlsx&price.gte=100&sort=name,asc"

# Export as PDF
curl -O http://localhost:8080/api/products/export?format=pdf
```

## Formats

### CSV

- **Dependency:** None (always available)
- **Content-Type:** `text/csv; charset=UTF-8`
- UTF-8 with BOM for proper Excel recognition
- Standard RFC 4180 escaping (quotes around values containing commas or newlines)
- Streamed row by row — constant memory regardless of dataset size

### Excel (XLSX)

- **Dependency:** `org.apache.poi:poi-ooxml` (optional, must be in your classpath)
- **Content-Type:** `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- Uses streaming API (SXSSFWorkbook) — constant memory with a 100-row window
- Numeric values are typed correctly (not stored as strings)

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

### PDF

- **Dependency:** `net.sf.jasperreports:jasperreports` (optional, must be in your classpath)
- **Content-Type:** `application/pdf`
- Two modes:
  - **Auto-generated layout** — clean table generated dynamically from entity metadata
  - **Custom template** — your own `.jrxml` report with full JasperReports power

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>net.sf.jasperreports</groupId>
    <artifactId>jasperreports</artifactId>
    <version>6.21.0</version>
</dependency>
```

## Custom PDF templates

Place a `.jrxml` file in the configured reports path (default: `classpath:flashapi/reports/`):

```
src/main/resources/flashapi/reports/products.jrxml
```

The file name must match the entity path (the same value used in URLs). For example, if your entity is available at `/api/products`, the template file is `products.jrxml`.

### How data is passed to the template

FlashAPI feeds your template with a **`JRBeanCollectionDataSource`** containing `Map<String, Object>` entries. Each map has the visible fields of the entity as keys.

Available **parameters**:

| Parameter | Type | Description |
|-----------|------|-------------|
| `ENTITY_NAME` | `String` | Entity name (e.g., "Product") |
| `RECORD_COUNT` | `Integer` | Total number of records |

### Template discovery

1. FlashAPI checks if a `.jrxml` file exists at `{reports-path}/{entity-path}.jrxml`
2. If found → compiles and uses it
3. If not found → generates a clean table layout dynamically

This follows the FlashAPI philosophy: **provide a working default, recede when you take over.**

## Filtering and sorting

All existing filters apply to exports:

```
GET /api/products/export?format=csv&name.contains=phone&price.gte=100&sort=price,desc
```

The export respects:
- All filter operators (`eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `contains`, `startswith`, `endswith`, `isnull`, `in`)
- Sorting (`sort=field,direction`)
- Soft delete visibility (`deleted=true` to include soft-deleted records)

Pagination parameters (`page`, `size`) are ignored — export always returns all matching records.

## Column rules

Exported columns follow the same visibility rules as the GET response:
- `@FlashHidden` fields are **excluded**
- `@FlashWriteOnly` fields are **excluded**
- `@FlashReadOnly` fields are **included**
- Column order follows field declaration order in the entity class
- Column headers are the Java field names (e.g., `firstName`, `createdAt`)

## Configuration

```yaml
flashapi:
  export:
    max-rows: 0                       # 0 = unlimited; set a positive value to cap exports
    reports-path: flashapi/reports     # classpath path to look for .jrxml templates
```

| Property | Default | Description |
|----------|---------|-------------|
| `flashapi.export.max-rows` | `0` | Maximum rows per export. `0` means no limit. When exceeded, the export is truncated and a warning is logged. |
| `flashapi.export.reports-path` | `flashapi/reports` | Classpath location where FlashAPI looks for custom `.jrxml` templates. |

## Error responses

| Case | HTTP Status | Message |
|------|-------------|---------|
| Missing `format` param | 400 | Invalid or missing 'format' parameter |
| Unknown format (e.g., `xml`) | 400 | Invalid or missing 'format' parameter |
| XLSX without POI in classpath | 400 | Excel export requires Apache POI |
| PDF without JasperReports in classpath | 400 | PDF export requires JasperReports |
| Entity has no LIST access | 405 | Method Not Allowed |

## Performance notes

- **CSV:** Streamed directly to the response — O(1) memory
- **XLSX:** Uses POI's streaming API with a 100-row memory window
- **PDF:** JasperReports requires all data in memory for layout. For very large datasets, consider using `max-rows` or filtering.
- **Batched fetching:** Data is fetched in batches of 500 rows internally to avoid loading the entire dataset in a single query
