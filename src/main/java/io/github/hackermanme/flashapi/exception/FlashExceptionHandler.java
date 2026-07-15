package io.github.hackermanme.flashapi.exception;

import io.github.hackermanme.flashapi.bulk.BulkLimitExceededException;
import io.github.hackermanme.flashapi.export.ExportUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for FlashAPI-generated endpoints.
 * Low priority so user-defined handlers take precedence.
 */
@RestControllerAdvice
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("FlashAPI unhandled exception", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 500);
        body.put("error", "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

}
