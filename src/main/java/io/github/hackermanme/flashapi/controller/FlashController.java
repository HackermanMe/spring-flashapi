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
            "page", "size", "sort", "expand", "fields", "format");

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
        Set<String> selectedFields = parseFields(mutable.remove("fields"));
        RESERVED_PARAMS.forEach(mutable::remove);

        String cacheKey = "list:" + page + ":" + size + ":" + sortParam + ":" + mutable;
        if (expandFields.isEmpty() && selectedFields.isEmpty()) {
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
                .map(e -> serialize(e, expandFields, selectedFields))
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
        Set<String> selectedFields = parseFields(params != null ? params.get("fields") : null);

        if (expandFields.isEmpty() && selectedFields.isEmpty()) {
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
            Map<String, Object> response = Map.of("data", serialize(e, expandFields, selectedFields));
            if (expandFields.isEmpty() && selectedFields.isEmpty()) {
                cacheManager.putInCache(metadata, "id:" + id, response);
            }
            return ResponseEntity.ok(response);
        }).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", metadata.entityName() + " not found", "status", 404)));
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
                .body(Map.of("data", serialize(created, Set.of(), Set.of())));
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
                .map(e -> ResponseEntity.ok(Map.<String, Object>of("data", serialize(e, Set.of(), Set.of()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", metadata.entityName() + " not found", "status", 404)));
    }

    public ResponseEntity<?> delete(Object id) {
        if (!metadata.isOperationAllowed(CrudOperation.DELETE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        boolean deleted = customService != null
                ? customService.delete(id)
                : crudService.delete(metadata, id);
        cacheManager.evict(metadata);
        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", metadata.entityName() + " not found", "status", 404));
    }

    public ResponseEntity<?> restore(Object id) {
        if (!metadata.softDelete()) {
            return ResponseEntity.badRequest().build();
        }
        boolean restored = customService != null
                ? customService.restore(id)
                : crudService.restore(metadata, id);
        return restored
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", metadata.entityName() + " not found", "status", 404));
    }

    public ResponseEntity<Map<String, Object>> history(Object id) {
        if (!metadata.auditEnabled()) {
            return ResponseEntity.badRequest().build();
        }
        var entries = crudService.getHistory(metadata, id);
        return ResponseEntity.ok(Map.of("data", formatAuditHistory(entries)));
    }

    private List<Map<String, Object>> formatAuditHistory(List<?> rawEntries) {
        record GroupKey(String action, String timestamp, String performedBy) {}
        Map<GroupKey, Map<String, Map<String, Object>>> grouped = new LinkedHashMap<>();
        List<Map<String, Object>> simpleEntries = new ArrayList<>();

        for (Object raw : rawEntries) {
            if (!(raw instanceof io.github.hackermanme.flashapi.audit.AuditEntry entry)) continue;
            String action = entry.getAction().name();
            String ts = entry.getTimestamp().toString();
            String user = entry.getPerformedBy();

            if (entry.getField() == null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("action", action);
                item.put("entityType", entry.getEntityType());
                item.put("entityId", entry.getEntityId());
                item.put("timestamp", ts);
                item.put("performedBy", user);
                item.put("changes", null);
                simpleEntries.add(item);
            } else {
                GroupKey key = new GroupKey(action, ts, user);
                grouped.computeIfAbsent(key, k -> new LinkedHashMap<>())
                        .put(entry.getField(), Map.of("from", nullSafe(entry.getOldValue()), "to", nullSafe(entry.getNewValue())));
            }
        }

        List<Map<String, Object>> result = new ArrayList<>(simpleEntries);
        for (var e : grouped.entrySet()) {
            GroupKey key = e.getKey();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("action", key.action());
            item.put("entityType", metadata.entityName());
            item.put("entityId", rawEntries.isEmpty() ? "" :
                    ((io.github.hackermanme.flashapi.audit.AuditEntry) rawEntries.get(0)).getEntityId());
            item.put("timestamp", key.timestamp());
            item.put("performedBy", key.performedBy());
            item.put("changes", e.getValue());
            result.add(item);
        }

        result.sort((a, b) -> ((String) b.get("timestamp")).compareTo((String) a.get("timestamp")));
        return result;
    }

    private Object nullSafe(String value) {
        return value != null ? value : null;
    }

    @SuppressWarnings("unchecked")
    public ResponseEntity<?> bulkCreate(Object body) {
        if (!metadata.isOperationAllowed(CrudOperation.CREATE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        List<Map<String, Object>> items = validateBulkBody(body);
        if (items == null) {
            return ResponseEntity.badRequest().build();
        }
        BulkResponse result = bulkHandler.bulkCreate(metadata, items);
        return ResponseEntity.status(HttpStatus.CREATED).body(formatBulkResponse(result));
    }

    @SuppressWarnings("unchecked")
    public ResponseEntity<?> bulkUpdate(Object body) {
        if (!metadata.isOperationAllowed(CrudOperation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        List<Map<String, Object>> items = validateBulkBody(body);
        if (items == null) {
            return ResponseEntity.badRequest().build();
        }
        BulkResponse result = bulkHandler.bulkUpdate(metadata, items);
        return ResponseEntity.ok(formatBulkResponse(result));
    }

    @SuppressWarnings("unchecked")
    public ResponseEntity<?> bulkDelete(Object body) {
        if (!metadata.isOperationAllowed(CrudOperation.DELETE)) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
        }
        if (!(body instanceof List<?> ids)) {
            return ResponseEntity.badRequest().build();
        }
        BulkResponse result = bulkHandler.bulkDelete(metadata, (List<Object>) ids);
        int total = result.success() + result.failed();
        return ResponseEntity.ok(Map.of(
                "data", List.of(),
                "meta", Map.of("total", total, "succeeded", result.success(), "failed", result.failed())
        ));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> validateBulkBody(Object body) {
        if (!(body instanceof List<?> list)) return null;
        if (list.isEmpty()) return null;
        if (!(list.get(0) instanceof Map)) return null;
        return (List<Map<String, Object>>) body;
    }

    private Map<String, Object> formatBulkResponse(BulkResponse result) {
        List<Map<String, Object>> data = result.results().stream()
                .filter(r -> r.data() != null)
                .map(r -> r.data())
                .toList();
        int total = result.success() + result.failed();
        return Map.of(
                "data", data,
                "meta", Map.of("total", total, "succeeded", result.success(), "failed", result.failed())
        );
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

    public Optional<Object> findEntityForOwnerCheck(Object id) {
        return customService != null
                ? customService.findById(id).map(e -> (Object) e)
                : crudService.findById(metadata, id);
    }

    private Map<String, Object> serialize(Object entity, Set<String> expandFields, Set<String> selectedFields) {
        if (expandFields != null && !expandFields.isEmpty() && metadata.hasRelations()) {
            Map<String, Object> expanded = relationExpander.serialize(metadata, entity, expandFields);
            if (selectedFields != null && !selectedFields.isEmpty()) {
                return filterFields(expanded, selectedFields);
            }
            return expanded;
        }

        List<FieldMetadata> fieldsToSerialize = metadata.visibleFields();
        if (selectedFields != null && !selectedFields.isEmpty()) {
            // Filter to only selected fields (already validated for security)
            fieldsToSerialize = fieldsToSerialize.stream()
                    .filter(f -> selectedFields.contains(f.name()))
                    .toList();
        }

        Map<String, Object> map = new LinkedHashMap<>();
        for (FieldMetadata field : fieldsToSerialize) {
            try {
                map.put(field.name(), field.javaField().get(entity));
            } catch (IllegalAccessException e) {
                map.put(field.name(), null);
            }
        }
        return map;
    }

    private Map<String, Object> filterFields(Map<String, Object> map, Set<String> selectedFields) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (String field : selectedFields) {
            if (map.containsKey(field)) {
                filtered.put(field, map.get(field));
            }
        }
        return filtered;
    }

    private Set<String> parseFields(String fieldsParam) {
        if (fieldsParam == null || fieldsParam.isBlank()) {
            return Set.of();
        }
        Set<String> visibleFieldNames = metadata.visibleFields().stream()
                .map(FieldMetadata::name)
                .collect(java.util.stream.Collectors.toSet());

        return Arrays.stream(fieldsParam.split(","))
                .map(String::trim)
                .filter(f -> !f.isEmpty())
                .filter(visibleFieldNames::contains) // SECURITY: only allow visible fields
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
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
