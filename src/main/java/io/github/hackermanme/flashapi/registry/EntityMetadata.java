package io.github.hackermanme.flashapi.registry;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable metadata for a registered entity.
 * Built once at startup. All collections are pre-computed — no allocation at request time.
 */
public record EntityMetadata(
        Class<?> entityClass,
        String entityName,
        String path,
        String idFieldName,
        Class<?> idType,
        boolean softDelete,
        boolean auditEnabled,
        boolean auditTrackFields,
        boolean cacheEnabled,
        int cacheTtl,
        boolean rateLimitEnabled,
        int rateLimitRequests,
        int rateLimitWindow,
        String tenantField,
        String lookupFieldName,
        Class<?> lookupFieldType,
        Set<CrudOperation> allowedOperations,
        List<FieldMetadata> fields,
        Map<String, FieldMetadata> fieldsByName,
        List<FieldMetadata> creatableFields,
        List<FieldMetadata> updatableFields,
        List<FieldMetadata> visibleFields,
        List<FieldMetadata> exportableFields,
        FieldMetadata primaryKeyField,
        List<RelationMetadata> relations,
        Map<String, RelationMetadata> relationsByName
) {
    public boolean isOperationAllowed(CrudOperation op) {
        return allowedOperations.contains(op);
    }

    public boolean hasRelations() {
        return relations != null && !relations.isEmpty();
    }

    public boolean isMultiTenant() {
        return tenantField != null;
    }

    public boolean hasCustomLookupField() {
        return lookupFieldName != null && !lookupFieldName.equals(idFieldName);
    }
}
