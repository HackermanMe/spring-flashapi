# Error Handling

FlashAPI translates common exceptions into proper HTTP status codes with readable error messages.

## Error Response Format

All errors follow this structure:

```json
{
  "status": 400,
  "error": "Human-readable error message"
}
```

Some errors include additional context:

```json
{
  "status": 400,
  "error": "Validation failed",
  "details": [
    { "field": "price", "message": "must be greater than 0" }
  ]
}
```

## HTTP Status Codes

| Status | Exception | Meaning |
|--------|-----------|---------|
| **400** | `IllegalArgumentException` | Invalid request parameter |
| **400** | `HttpMessageNotReadableException` | Malformed JSON body |
| **400** | `MethodArgumentNotValidException` | Bean validation failed (`@Valid`) |
| **400** | `ConstraintViolationException` | JSR-303 validation failed |
| **403** | `RecordLimitExceededException` | Entity limit reached (see Feature Guard) |
| **404** | `EntityNotFoundException` | Entity not found by ID |
| **409** | `DataIntegrityViolationException` | Constraint violation (unique, FK, not-null) |
| **413** | `BulkLimitExceededException` | Bulk operation too large |
| **500** | Generic `Exception` | Unhandled server error |

## Constraint Violations (409 Conflict)

FlashAPI detects common database constraint violations and returns readable messages:

| Constraint Type | Message |
|----------------|---------|
| Unique constraint | `"Duplicate value violates unique constraint"` |
| Foreign key violation | `"Referenced entity does not exist or is in use"` |
| Not-null constraint | `"Required field cannot be null"` |

Example:

```bash
POST /api/products
{ "name": "Duplicate" }  # name has unique constraint

HTTP 409 Conflict
{
  "status": 409,
  "error": "Duplicate value violates unique constraint"
}
```

## Bean Validation

Use JSR-303 annotations on your entity:

```java
@Entity
@FlashEntity
public class Product {
    @NotNull
    @Min(0)
    private BigDecimal price;
}
```

Invalid input returns 400 with field details:

```bash
POST /api/products
{ "name": "Phone", "price": -10 }

HTTP 400 Bad Request
{
  "status": 400,
  "error": "Validation failed",
  "details": [
    { "field": "price", "message": "must be greater than or equal to 0" }
  ]
}
```

## Entity Not Found (404)

GET, PUT, or DELETE on a non-existent ID returns 404:

```bash
GET /api/products/999999

HTTP 404 Not Found
{
  "status": 404,
  "error": "Entity not found"
}
```

## Malformed JSON (400)

Invalid JSON syntax returns 400 with parse details:

```bash
POST /api/products
{ invalid json }

HTTP 400 Bad Request
{
  "status": 400,
  "error": "Unexpected character 'i' at position 2"
}
```

## Transaction Rollback

All errors during CRUD operations automatically rollback the transaction. No partial writes occur.

## Custom Error Handling

To add custom error handling, register a `@RestControllerAdvice`:

```java
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)  // Run before FlashAPI's handler
public class CustomErrorHandler {
    @ExceptionHandler(MyCustomException.class)
    public ResponseEntity<?> handleCustom(MyCustomException ex) {
        return ResponseEntity.status(422)
            .body(Map.of("status", 422, "error", ex.getMessage()));
    }
}
```
