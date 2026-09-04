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
        Map<String, RelationMetadata> relationsByName,
        List<ManyToOneDescriptor> manyToOneDescriptors,
        Map<String, ManyToOneDescriptor> manyToOneByFkName,
        String ownerFieldName,
        java.lang.reflect.Field ownerJavaField,
        boolean ownerFieldIsRelation,
        String[] ownerAdminRoles,
        String currentUserFieldName,
        java.lang.reflect.Field currentUserJavaField,
        boolean currentUserFieldIsRelation,
        Class<?> currentUserTargetEntity,
        Class<?> currentUserTargetIdType,
        boolean webhookEnabled,
        String[] webhookEvents,
        long maxRecords
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

    public boolean hasOwnerField() {
        return ownerFieldName != null && !ownerFieldName.isEmpty();
    }

    public boolean hasCurrentUserField() {
        return currentUserFieldName != null && !currentUserFieldName.isEmpty();
    }

    /**
     * Returns the entity class resolved through the current thread's context ClassLoader.
     * Necessary for Spring Boot DevTools compatibility: after a restart, the Restart ClassLoader
     * creates new Class instances, making stored references stale for JPA/Hibernate operations.
     */
    public Class<?> resolvedEntityClass() {
        return resolveClass(entityClass);
    }

    public static Class<?> resolveClass(Class<?> clazz) {
        ClassLoader contextCL = Thread.currentThread().getContextClassLoader();
        if (contextCL == null || clazz.getClassLoader() == contextCL) return clazz;
        try {
            return Class.forName(clazz.getName(), true, contextCL);
        } catch (ClassNotFoundException e) {
            return clazz;
        }
    }
}
