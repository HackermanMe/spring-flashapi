# Field Selection (Sparse Fieldsets)

Reduce response payload size by selecting only the fields you need.

## Quick Start

```bash
# Return only id and name
GET /api/products?fields=id,name

Response:
{
  "data": [
    { "id": 1, "name": "Phone" }  # price excluded
  ]
}
```

## Syntax

`?fields=field1,field2,field3`

Comma-separated list of field names.

## Works With

- **List endpoint**: `GET /api/products?fields=id,name`
- **Get by ID**: `GET /api/products/1?fields=name,price`
- **Combined with other params**: `?page=2&fields=id,name&sort=name,asc`

## Security

Only **visible** fields can be selected. Fields marked as `hidden` or `writeonly` are automatically filtered out even if requested.

## Invalid Fields

Invalid or non-existent fields are **silently ignored**:

```bash
GET /api/products?fields=name,nonexistent,alsoInvalid

Response includes only "name" (valid fields).
```

## Empty or Omitted

- **Omit `?fields=`** → all visible fields returned (default behavior)
- **Empty `?fields=`** → all visible fields returned

## Combined with Expand

Use with `?expand=` to include relations but limit their fields:

```bash
GET /api/products?expand=category&fields=id,name,category

Response:
{
  "data": [{
    "id": 1,
    "name": "Phone",
    "category": {
      "id": 5,
      "name": "Electronics",
      "description": "Tech products"
    }
  }]
}
```

Note: `?fields=` applies to the **root entity**. Expanded relation fields are not (yet) selectable.

## Performance

Field selection filters at the **serialization layer**, not the database query layer. All columns are fetched from the database, but only requested fields are sent in the response.

For most use cases this is sufficient. If you need true column projection (SELECT only specific columns), use a custom `FlashCrudOperations` implementation.

## Cache Behavior

- When `?fields=` is used, response is **not cached** (cache disabled)
- Without `?fields=`, normal cache rules apply (see [Cache](cache.md))

## JSON:API Compatibility

This feature follows the [JSON:API sparse fieldsets](https://jsonapi.org/format/#fetching-sparse-fieldsets) pattern, though FlashAPI does not claim full JSON:API compliance.
