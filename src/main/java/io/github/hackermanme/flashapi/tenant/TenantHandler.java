package io.github.hackermanme.flashapi.tenant;

import io.github.hackermanme.flashapi.registry.EntityMetadata;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

/**
 * Handles multi-tenant operations: filtering queries, injecting tenant on create,
 * and verifying tenant ownership on read/update/delete.
 */
public final class TenantHandler {

    public Predicate tenantPredicate(CriteriaBuilder cb, Root<?> root, EntityMetadata meta) {
        String tenantField = meta.tenantField();
        if (tenantField == null) return null;

        String currentTenant = TenantContext.get();
        if (currentTenant == null) return null;

        return cb.equal(root.get(tenantField), currentTenant);
    }

    public void injectTenant(EntityMetadata meta, Map<String, Object> data) {
        String tenantField = meta.tenantField();
        if (tenantField == null) return;

        String currentTenant = TenantContext.get();
        if (currentTenant != null) {
            data.put(tenantField, currentTenant);
        }
    }

    public boolean belongsToCurrentTenant(EntityMetadata meta, Object entity) {
        String tenantField = meta.tenantField();
        if (tenantField == null) return true;

        String currentTenant = TenantContext.get();
        if (currentTenant == null) return true;

        try {
            Field field = meta.entityClass().getDeclaredField(tenantField);
            field.setAccessible(true);
            Object entityTenant = field.get(entity);
            return currentTenant.equals(entityTenant);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return false;
        }
    }
}
