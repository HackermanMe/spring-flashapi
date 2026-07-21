package io.github.hackermanme.flashapi.bulk;

import io.github.hackermanme.flashapi.dashboard.MetricsCollector;
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
    private volatile MetricsCollector metricsCollector;

    public BulkHandler(GenericCrudService crudService, int maxItems) {
        this.crudService = crudService;
        this.maxItems = maxItems;
    }

    public void setMetricsCollector(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
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
        recordBulk(meta.entityName());
        return BulkResponse.from(results);
    }

    public BulkResponse bulkUpdate(EntityMetadata meta, List<Map<String, Object>> items) {
        validateSize(items);
        List<BulkResult> results = new ArrayList<>(items.size());
        String identifierField = meta.hasCustomLookupField() ? meta.lookupFieldName() : meta.idFieldName();

        for (int i = 0; i < items.size(); i++) {
            try {
                Map<String, Object> item = items.get(i);
                Object id = extractIdentifier(meta, item);
                if (id == null) {
                    results.add(BulkResult.failure(i, "Missing '" + identifierField + "' field"));
                    continue;
                }

                Map<String, Object> data = new HashMap<>(item);
                data.remove(identifierField);

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
        recordBulk(meta.entityName());
        return BulkResponse.from(results);
    }

    public BulkResponse bulkDelete(EntityMetadata meta, List<Object> ids) {
        validateSize(ids);
        List<BulkResult> results = new ArrayList<>(ids.size());
        String identifierField = meta.hasCustomLookupField() ? meta.lookupFieldName() : meta.idFieldName();

        for (int i = 0; i < ids.size(); i++) {
            try {
                Object id = coerceIdentifier(meta, ids.get(i));
                boolean deleted = crudService.delete(meta, id);
                if (deleted) {
                    results.add(BulkResult.success(i, "deleted", Map.of(identifierField, id)));
                } else {
                    results.add(BulkResult.failure(i, "Not found: " + id));
                }
            } catch (Exception e) {
                results.add(BulkResult.failure(i, extractMessage(e)));
            }
        }

        log.info("FlashAPI: bulk delete {} — {}/{} succeeded",
                meta.entityName(), results.stream().filter(r -> r.error() == null).count(), ids.size());
        recordBulk(meta.entityName());
        return BulkResponse.from(results);
    }

    private void recordBulk(String entityName) {
        MetricsCollector mc = this.metricsCollector;
        if (mc != null) {
            mc.recordOperation(entityName, "BULK");
        }
    }

    private void validateSize(List<?> items) {
        if (maxItems > 0 && items.size() > maxItems) {
            throw new BulkLimitExceededException(
                    "Bulk operation limited to " + maxItems + " items, got " + items.size());
        }
    }

    private Object extractIdentifier(EntityMetadata meta, Map<String, Object> item) {
        String field = meta.hasCustomLookupField() ? meta.lookupFieldName() : meta.idFieldName();
        Object raw = item.get(field);
        if (raw == null) return null;
        return coerceIdentifier(meta, raw);
    }

    private Object coerceIdentifier(EntityMetadata meta, Object raw) {
        if (raw == null) return null;
        Class<?> type = meta.hasCustomLookupField() ? meta.lookupFieldType() : meta.idType();
        if (type.isInstance(raw)) return raw;
        String str = raw.toString();
        if (type == Long.class || type == long.class) return Long.parseLong(str);
        if (type == Integer.class || type == int.class) return Integer.parseInt(str);
        if (type == java.util.UUID.class) return java.util.UUID.fromString(str);
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
