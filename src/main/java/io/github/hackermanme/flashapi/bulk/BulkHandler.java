package io.github.hackermanme.flashapi.bulk;

import io.github.hackermanme.flashapi.registry.EntityMetadata;
import io.github.hackermanme.flashapi.registry.FieldMetadata;
import io.github.hackermanme.flashapi.service.GenericCrudService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public final class BulkHandler {

    private static final Logger log = LoggerFactory.getLogger(BulkHandler.class);

    private final GenericCrudService crudService;
    private final int maxItems;

    public BulkHandler(GenericCrudService crudService, int maxItems) {
        this.crudService = crudService;
        this.maxItems = maxItems;
    }

    public BulkResponse bulkCreate(EntityMetadata meta, List<Map<String, Object>> items) {
        validateSize(items);
        List<BulkResult> results = new ArrayList<>(items.size());

        for (int i = 0; i < items.size(); i++) {
            try {
                Object created = crudService.create(meta, items.get(i));
                results.add(BulkResult.success(i, "created", serialize(meta, created)));
            } catch (Exception e) {
                results.add(BulkResult.failure(i, extractMessage(e)));
            }
        }

        log.info("FlashAPI: bulk create {} — {}/{} succeeded",
                meta.entityName(), results.stream().filter(r -> r.error() == null).count(), items.size());
        return BulkResponse.from(results);
    }

    public BulkResponse bulkUpdate(EntityMetadata meta, List<Map<String, Object>> items) {
        validateSize(items);
        List<BulkResult> results = new ArrayList<>(items.size());

        for (int i = 0; i < items.size(); i++) {
            try {
                Map<String, Object> item = items.get(i);
                Object id = extractId(meta, item);
                if (id == null) {
                    results.add(BulkResult.failure(i, "Missing '" + meta.idFieldName() + "' field"));
                    continue;
                }

                Map<String, Object> data = new HashMap<>(item);
                data.remove(meta.idFieldName());

                Optional<Object> updated = crudService.update(meta, id, data);
                if (updated.isPresent()) {
                    results.add(BulkResult.success(i, "updated", serialize(meta, updated.get())));
                } else {
                    results.add(BulkResult.failure(i, "Not found: " + id));
                }
            } catch (Exception e) {
                results.add(BulkResult.failure(i, extractMessage(e)));
            }
        }

        log.info("FlashAPI: bulk update {} — {}/{} succeeded",
                meta.entityName(), results.stream().filter(r -> r.error() == null).count(), items.size());
        return BulkResponse.from(results);
    }

    public BulkResponse bulkDelete(EntityMetadata meta, List<Object> ids) {
        validateSize(ids);
        List<BulkResult> results = new ArrayList<>(ids.size());

        for (int i = 0; i < ids.size(); i++) {
            try {
                Object id = coerceId(meta, ids.get(i));
                boolean deleted = crudService.delete(meta, id);
                if (deleted) {
                    results.add(BulkResult.success(i, "deleted", Map.of(meta.idFieldName(), id)));
                } else {
                    results.add(BulkResult.failure(i, "Not found: " + id));
                }
            } catch (Exception e) {
                results.add(BulkResult.failure(i, extractMessage(e)));
            }
        }

        log.info("FlashAPI: bulk delete {} — {}/{} succeeded",
                meta.entityName(), results.stream().filter(r -> r.error() == null).count(), ids.size());
        return BulkResponse.from(results);
    }

    private void validateSize(List<?> items) {
        if (maxItems > 0 && items.size() > maxItems) {
            throw new BulkLimitExceededException(
                    "Bulk operation limited to " + maxItems + " items, got " + items.size());
        }
    }

    private Object extractId(EntityMetadata meta, Map<String, Object> item) {
        Object raw = item.get(meta.idFieldName());
        if (raw == null) return null;
        return coerceId(meta, raw);
    }

    private Object coerceId(EntityMetadata meta, Object raw) {
        if (raw == null) return null;
        Class<?> idType = meta.idType();
        if (idType.isInstance(raw)) return raw;
        String str = raw.toString();
        if (idType == Long.class || idType == long.class) return Long.parseLong(str);
        if (idType == Integer.class || idType == int.class) return Integer.parseInt(str);
        if (idType == java.util.UUID.class) return java.util.UUID.fromString(str);
        return str;
    }

    private Map<String, Object> serialize(EntityMetadata meta, Object entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (FieldMetadata field : meta.visibleFields()) {
            try {
                map.put(field.name(), field.javaField().get(entity));
            } catch (IllegalAccessException e) {
                map.put(field.name(), null);
            }
        }
        return map;
    }

    private String extractMessage(Exception e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
