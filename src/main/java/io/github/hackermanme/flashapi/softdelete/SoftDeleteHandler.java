package io.github.hackermanme.flashapi.softdelete;

import io.github.hackermanme.flashapi.registry.EntityMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Handles soft delete operations.
 * Instead of removing the entity, sets a deletedAt timestamp.
 * Assumes the entity has a field named per flashapi.soft-delete.column-name config.
 */
public class SoftDeleteHandler {

    private final EntityManager entityManager;
    private final String deletedAtColumn;

    public SoftDeleteHandler(EntityManager entityManager, String deletedAtColumn) {
        this.entityManager = entityManager;
        this.deletedAtColumn = deletedAtColumn;
    }

    @Transactional
    public boolean softDelete(EntityMetadata meta, Object id) {
        Object entity = entityManager.find(meta.entityClass(), id);
        if (entity == null) return false;

        try {
            var field = findField(meta.entityClass(), deletedAtColumn);
            field.setAccessible(true);
            Object timestamp = field.getType() == LocalDateTime.class
                    ? LocalDateTime.now() : Instant.now();
            field.set(entity, timestamp);
            entityManager.merge(entity);
            entityManager.flush();
            return true;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(
                    "Entity " + meta.entityName() + " has softDelete=true but no field named '"
                            + deletedAtColumn + "'. Add: private LocalDateTime " + deletedAtColumn + ";");
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot set " + deletedAtColumn + " on " + meta.entityName(), e);
        }
    }

    @Transactional
    public boolean restore(EntityMetadata meta, Object id) {
        Object entity = entityManager.find(meta.entityClass(), id);
        if (entity == null) return false;

        try {
            var field = findField(meta.entityClass(), deletedAtColumn);
            field.setAccessible(true);
            field.set(entity, null);
            entityManager.merge(entity);
            entityManager.flush();
            return true;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Cannot restore " + meta.entityName(), e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    /**
     * Adds a WHERE deletedAt IS NULL predicate to exclude soft-deleted entities.
     */
    public Predicate notDeleted(CriteriaBuilder cb, Root<?> root) {
        return cb.isNull(root.get(deletedAtColumn));
    }

    /**
     * Adds a WHERE deletedAt IS NOT NULL predicate to show only soft-deleted entities.
     */
    public Predicate onlyDeleted(CriteriaBuilder cb, Root<?> root) {
        return cb.isNotNull(root.get(deletedAtColumn));
    }
}
