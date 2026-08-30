package io.github.hackermanme.flashapi.exception;

import io.github.hackermanme.flashapi.bulk.BulkLimitExceededException;
import io.github.hackermanme.flashapi.controller.FlashEndpointHandler;
import io.github.hackermanme.flashapi.controller.FlashBulkEndpointHandler;
import io.github.hackermanme.flashapi.export.ExportUnavailableException;
import io.github.hackermanme.flashapi.guard.RecordLimitExceededException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exception handler scoped to FlashAPI handlers only.
 * Does not interfere with user-defined controllers or Spring's default error handling.
 */
@RestControllerAdvice(assignableTypes = {FlashEndpointHandler.class, FlashBulkEndpointHandler.class})
@Order(Ordered.LOWEST_PRECEDENCE)
public class FlashExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FlashExceptionHandler.class);


    @ExceptionHandler(ExportUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleExportUnavailable(ExportUnavailableException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        body.put("error", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(RecordLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRecordLimitExceeded(RecordLimitExceededException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 403);
        body.put("error", ex.getMessage());
        body.put("entity", ex.getEntityName());
        body.put("limit", ex.getLimit());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(BulkLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleBulkLimit(BulkLimitExceededException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 413);
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        body.put("error", ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<Map<String, Object>> handleNumberFormat(NumberFormatException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        body.put("error", "Invalid numeric value: " + ex.getMessage());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 404);
        body.put("error", ex.getMessage() != null ? ex.getMessage() : "Entity not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 409);
        String message = extractConstraintMessage(ex);
        body.put("error", message);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        String message = "Malformed request body";
        if (ex.getCause() != null) {
            String cause = ex.getCause().getMessage();
            if (cause != null) {
                int lineBreak = cause.indexOf('\n');
                message = lineBreak > 0 ? cause.substring(0, lineBreak) : cause;
            }
        }
        body.put("error", message);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"))
                .toList();
        body.put("error", "Validation failed");
        body.put("details", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        // Check for jakarta.validation.ConstraintViolationException by name
        // to avoid hard dependency on hibernate-validator
        if (isConstraintViolation(ex)) {
            return handleConstraintViolation(ex);
        }
        log.error("FlashAPI unhandled exception", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 500);
        body.put("error", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private boolean isConstraintViolation(Exception ex) {
        return ex.getClass().getName().equals("jakarta.validation.ConstraintViolationException");
    }

    private ResponseEntity<Map<String, Object>> handleConstraintViolation(Exception ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        try {
            var violations = (java.util.Set<?>) ex.getClass().getMethod("getConstraintViolations").invoke(ex);
            List<String> messages = violations.stream()
                    .map(v -> {
                        try {
                            Object path = v.getClass().getMethod("getPropertyPath").invoke(v);
                            Object msg = v.getClass().getMethod("getMessage").invoke(v);
                            return path + ": " + msg;
                        } catch (Exception e) {
                            return v.toString();
                        }
                    })
                    .toList();
            body.put("error", "Validation failed");
            body.put("details", messages);
        } catch (Exception e) {
            body.put("error", ex.getMessage());
        }
        return ResponseEntity.badRequest().body(body);
    }

    private String extractConstraintMessage(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        String msg = root.getMessage();
        if (msg == null) return "Data integrity violation";
        String lower = msg.toLowerCase();
        if (lower.contains("unique") || lower.contains("duplicate")) {
            return "Duplicate value violates unique constraint";
        }
        if (lower.contains("foreign key") || lower.contains("referential integrity")) {
            return "Referenced entity does not exist or is in use";
        }
        if (lower.contains("not null") || lower.contains("not-null")) {
            return "Required field cannot be null";
        }
        return "Data integrity violation: " + truncate(msg, 120);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

}
