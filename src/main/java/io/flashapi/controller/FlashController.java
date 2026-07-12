package io.flashapi.controller;

import io.flashapi.registry.CrudOperation;
import io.flashapi.registry.EntityMetadata;
import io.flashapi.registry.FieldMetadata;
import io.flashapi.service.FlashCrudOperations;
import io.flashapi.service.GenericCrudService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

/**
 * Handles HTTP requests for a single @FlashEntity.
 * One instance per entity, created at startup. Stateless and thread-safe.
 * Delegates to a custom FlashCrudOperations if available, otherwise uses GenericCrudService.
 */
public final class FlashController {

    private static final Set<String> RESERVED_PARAMS = Set.of(
            "page", "size", "sort", "search", "expand");

    private final EntityMetadata metadata;
    private final GenericCrudService crudService;
    private final FlashCrudOperations<Object, Object> customService;

    public FlashController(EntityMetadata metadata, GenericCrudService crudService,
                           FlashCrudOperations<Object, Object> customService) {
        this.metadata = metadata;
        this.crudService = crudService;
        this.customService = customService;
    }

    public ResponseEntity<Map<String, Object>> list(Map<String, String> params) {
        if (!metadata.isOperationAllowed(CrudOperation.LIST)) {
            return methodNotAllowed();
        }

        Map<String, String> mutable = new HashMap<>(params);
        int page = extractInt(mutable.remove("page"), 0);
        int size = Math.clamp(extractInt(mutable.remove("size"), 20), 1, 100);
        String sortParam = mutable.remove("sort");
        RESERVED_PARAMS.forEach(mutable::remove);

        Pageable pageable = buildPageable(page, size, sortParam);
        Page<Object> result = customService != null
                ? customService.list(pageable, mutable).map(e -> (Object) e)
                : crudService.list(metadata, pageable, mutable);

        List<Map<String, Object>> data = result.getContent().stream()
                .map(this::serialize)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
        return ResponseEntity.ok(response);
    }

    public ResponseEntity<Map<String, Object>> getById(Object id) {
        if (!metadata.isOperationAllowed(CrudOperation.READ)) {
            return methodNotAllowed();
        }
        Optional<Object> found = customService != null
                ? customService.findById(id).map(e -> (Object) e)
                : crudService.findById(metadata, id);
        return found
                .map(e -> ResponseEntity.ok(Map.<String, Object>of("data", serialize(e))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public ResponseEntity<Map<String, Object>> create(Map<String, Object> body) {
        if (!metadata.isOperationAllowed(CrudOperation.CREATE)) {
            return methodNotAllowed();
        }
        Object created = customService != null
                ? customService.create(body)
                : crudService.create(metadata, body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("data", serialize(created)));
    }

    public ResponseEntity<Map<String, Object>> update(Object id, Map<String, Object> body) {
        if (!metadata.isOperationAllowed(CrudOperation.UPDATE)) {
            return methodNotAllowed();
        }
        Optional<Object> updated = customService != null
                ? customService.update(id, body).map(e -> (Object) e)
                : crudService.update(metadata, id, body);
        return updated
                .map(e -> ResponseEntity.ok(Map.<String, Object>of("data", serialize(e))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public ResponseEntity<Void> delete(Object id) {
        if (!metadata.isOperationAllowed(CrudOperation.DELETE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        boolean deleted = customService != null
                ? customService.delete(id)
                : crudService.delete(metadata, id);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    public ResponseEntity<Void> restore(Object id) {
        if (!metadata.softDelete()) {
            return ResponseEntity.badRequest().build();
        }
        boolean restored = customService != null
                ? customService.restore(id)
                : crudService.restore(metadata, id);
        return restored
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    public ResponseEntity<Map<String, Object>> history(Object id) {
        if (!metadata.auditEnabled()) {
            return ResponseEntity.badRequest().build();
        }
        var entries = crudService.getHistory(metadata, id);
        return ResponseEntity.ok(Map.of("data", entries));
    }

    public EntityMetadata getMetadata() {
        return metadata;
    }

    private Map<String, Object> serialize(Object entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (FieldMetadata field : metadata.visibleFields()) {
            try {
                map.put(field.name(), field.javaField().get(entity));
            } catch (IllegalAccessException e) {
                map.put(field.name(), null);
            }
        }
        return map;
    }

    private Pageable buildPageable(int page, int size, String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return PageRequest.of(page, size);
        }
        String[] parts = sortParam.split(",", 2);
        String field = parts[0].trim();
        Sort.Direction dir = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(dir, field));
    }

    private int extractInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> methodNotAllowed() {
        return (ResponseEntity<T>) ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }
}
