package io.github.hackermanme.flashapi.controller;

import io.github.hackermanme.flashapi.bulk.BulkHandler;
import io.github.hackermanme.flashapi.bulk.BulkResponse;
import io.github.hackermanme.flashapi.cache.FlashCacheManager;
import io.github.hackermanme.flashapi.export.ExportFormat;
import io.github.hackermanme.flashapi.export.ExportHandler;
import io.github.hackermanme.flashapi.registry.CrudOperation;
import io.github.hackermanme.flashapi.registry.EntityMetadata;
import io.github.hackermanme.flashapi.registry.FieldMetadata;
import io.github.hackermanme.flashapi.relation.RelationExpander;
import io.github.hackermanme.flashapi.service.FlashCrudOperations;
import io.github.hackermanme.flashapi.service.GenericCrudService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.*;

/**
 * Handles HTTP requests for a single @FlashEntity.
 * One instance per entity, created at startup. Stateless and thread-safe.
 * Delegates to a custom FlashCrudOperations if available, otherwise uses GenericCrudService.
 */
public final class FlashController {

    private static final Set<String> RESERVED_PARAMS = Set.of(
            "page", "size", "sort", "expand", "format");

    private final EntityMetadata metadata;
    private final GenericCrudService crudService;
    private final FlashCrudOperations<Object, Object> customService;
    private final ExportHandler exportHandler;
    private final BulkHandler bulkHandler;
    private final RelationExpander relationExpander;
    private final FlashCacheManager cacheManager;

    public FlashController(EntityMetadata metadata, GenericCrudService crudService,
                           FlashCrudOperations<Object, Object> customService,
                           ExportHandler exportHandler, BulkHandler bulkHandler,
                           RelationExpander relationExpander, FlashCacheManager cacheManager) {
        this.metadata = metadata;
        this.crudService = crudService;
        this.customService = customService;
        this.exportHandler = exportHandler;
        this.bulkHandler = bulkHandler;
        this.relationExpander = relationExpander;
        this.cacheManager = cacheManager;
    }

    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> list(Map<String, String> params) {
        if (!metadata.isOperationAllowed(CrudOperation.LIST)) {
            return methodNotAllowed();
        }

        Map<String, String> mutable = new HashMap<>(params);
        int page = extractInt(mutable.remove("page"), 0);
        int size = Math.clamp(extractInt(mutable.remove("size"), 20), 1, 100);
        String sortParam = mutable.remove("sort");
        Set<String> expandFields = parseExpand(mutable.remove("expand"));
        RESERVED_PARAMS.forEach(mutable::remove);

        String cacheKey = "list:" + page + ":" + size + ":" + sortParam + ":" + mutable;
        if (expandFields.isEmpty()) {
            Object cached = cacheManager.getFromCache(metadata, cacheKey);
            if (cached != null) {
                return ResponseEntity.ok((Map<String, Object>) cached);
            }
        }

        Pageable pageable = buildPageable(page, size, sortParam);
        Page<Object> result = customService != null
                ? customService.list(pageable, mutable).map(e -> (Object) e)
                : crudService.list(metadata, pageable, mutable);

        List<Map<String, Object>> data = result.getContent().stream()
                .map(e -> serialize(e, expandFields))
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", data);
        response.put("meta", Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));

        if (expandFields.isEmpty()) {
            cacheManager.putInCache(metadata, cacheKey, response);
        }
        return ResponseEntity.ok(response);
    }

    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getById(Object id, Map<String, String> params) {
        if (!metadata.isOperationAllowed(CrudOperation.READ)) {
            return methodNotAllowed();
        }
        Set<String> expandFields = parseExpand(params != null ? params.get("expand") : null);

        if (expandFields.isEmpty()) {
            String cacheKey = "id:" + id;
            Object cached = cacheManager.getFromCache(metadata, cacheKey);
            if (cached != null) {
                return ResponseEntity.ok((Map<String, Object>) cached);
            }
        }

        Optional<Object> found = customService != null
                ? customService.findById(id).map(e -> (Object) e)
                : crudService.findById(metadata, id);

        return found.map(e -> {
            Map<String, Object> response = Map.of("data", serialize(e, expandFields));
            if (expandFields.isEmpty()) {
                cacheManager.putInCache(metadata, "id:" + id, response);
            }
            return ResponseEntity.ok(response);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    public ResponseEntity<Map<String, Object>> create(Map<String, Object> body) {
        if (!metadata.isOperationAllowed(CrudOperation.CREATE)) {
            return methodNotAllowed();
        }
        Object created = customService != null
                ? customService.create(body)
                : crudService.create(metadata, body);
        cacheManager.evict(metadata);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("data", serialize(created, Set.of())));
    }

    public ResponseEntity<Map<String, Object>> update(Object id, Map<String, Object> body) {
        if (!metadata.isOperationAllowed(CrudOperation.UPDATE)) {
            return methodNotAllowed();
        }
        Optional<Object> updated = customService != null
                ? customService.update(id, body).map(e -> (Object) e)
                : crudService.update(metadata, id, body);
        cacheManager.evict(metadata);
        return updated
                .map(e -> ResponseEntity.ok(Map.<String, Object>of("data", serialize(e, Set.of()))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public ResponseEntity<Void> delete(Object id) {
        if (!metadata.isOperationAllowed(CrudOperation.DELETE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        boolean deleted = customService != null
                ? customService.delete(id)
                : crudService.delete(metadata, id);
        cacheManager.evict(metadata);
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

    @SuppressWarnings("unchecked")
    public ResponseEntity<BulkResponse> bulkCreate(Object body) {
        if (!metadata.isOperationAllowed(CrudOperation.CREATE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        List<Map<String, Object>> items = validateBulkBody(body);
        if (items == null) {
            return ResponseEntity.badRequest().build();
        }
        BulkResponse result = bulkHandler.bulkCreate(metadata, items);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @SuppressWarnings("unchecked")
    public ResponseEntity<BulkResponse> bulkUpdate(Object body) {
        if (!metadata.isOperationAllowed(CrudOperation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        List<Map<String, Object>> items = validateBulkBody(body);
        if (items == null) {
            return ResponseEntity.badRequest().build();
        }
        BulkResponse result = bulkHandler.bulkUpdate(metadata, items);
        return ResponseEntity.ok(result);
    }

    @SuppressWarnings("unchecked")
    public ResponseEntity<BulkResponse> bulkDelete(Object body) {
        if (!metadata.isOperationAllowed(CrudOperation.DELETE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        if (!(body instanceof List<?> ids)) {
            return ResponseEntity.badRequest().build();
        }
        BulkResponse result = bulkHandler.bulkDelete(metadata, (List<Object>) ids);
        return ResponseEntity.ok(result);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> validateBulkBody(Object body) {
        if (!(body instanceof List<?> list)) return null;
        if (list.isEmpty()) return null;
        if (!(list.get(0) instanceof Map)) return null;
        return (List<Map<String, Object>>) body;
    }

    public void export(Map<String, String> params, HttpServletResponse response) throws IOException {
        if (!metadata.isOperationAllowed(CrudOperation.LIST)) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        Map<String, String> mutable = new HashMap<>(params);
        String formatParam = mutable.remove("format");
        ExportFormat format = ExportFormat.fromParam(formatParam);
        if (format == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Invalid or missing 'format' parameter. Supported: csv, xlsx, pdf");
            return;
        }

        String sortParam = mutable.remove("sort");
        RESERVED_PARAMS.forEach(mutable::remove);

        exportHandler.export(metadata, format, mutable, sortParam, response);
    }

    public EntityMetadata getMetadata() {
        return metadata;
    }

    private Map<String, Object> serialize(Object entity, Set<String> expandFields) {
        if (expandFields != null && !expandFields.isEmpty() && metadata.hasRelations()) {
            return relationExpander.serialize(metadata, entity, expandFields);
        }
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

    private Set<String> parseExpand(String expandParam) {
        if (expandParam == null || expandParam.isBlank()) return Set.of();
        return Set.of(expandParam.split(","));
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
