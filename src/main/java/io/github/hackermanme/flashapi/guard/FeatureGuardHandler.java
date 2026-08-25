package io.github.hackermanme.flashapi.guard;

import io.github.hackermanme.flashapi.annotation.FeatureGuard;
import io.github.hackermanme.flashapi.registry.EntityMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Enforces @FeatureGuard record-count limits before CREATE operations.
 * Resolution priority: PlanLimitResolver bean > @FeatureGuard(max) > no limit.
 */
public class FeatureGuardHandler {

    private final EntityManager entityManager;
    private final PlanLimitResolver planLimitResolver;

    public FeatureGuardHandler(EntityManager entityManager, PlanLimitResolver planLimitResolver) {
        this.entityManager = entityManager;
        this.planLimitResolver = planLimitResolver;
    }

    /**
     * Check if the entity has reached its record limit. Throws RecordLimitExceededException if so.
     *
     * @param meta    the entity metadata
     * @param request the current HTTP request (used by PlanLimitResolver)
     */
    public void checkLimit(EntityMetadata meta, HttpServletRequest request) {
        long limit = resolveLimit(meta, request);
        if (limit < 0) return;

        long currentCount = countRecords(meta);
        if (currentCount >= limit) {
            throw new RecordLimitExceededException(meta.entityName(), limit);
        }
    }

    /**
     * Check if adding N records would exceed the limit.
     *
     * @param meta    the entity metadata
     * @param request the current HTTP request
     * @param adding  number of records about to be created
     */
    public void checkLimit(EntityMetadata meta, HttpServletRequest request, int adding) {
        long limit = resolveLimit(meta, request);
        if (limit < 0) return;

        long currentCount = countRecords(meta);
        if (currentCount + adding > limit) {
            throw new RecordLimitExceededException(meta.entityName(), limit);
        }
    }

    private long resolveLimit(EntityMetadata meta, HttpServletRequest request) {
        // Priority 1: PlanLimitResolver bean
        if (planLimitResolver != null) {
            long resolved = planLimitResolver.resolveLimit(meta.entityName(), request);
            if (resolved != -1) return resolved;
        }

        // Priority 2: @FeatureGuard annotation
        FeatureGuard annotation = meta.entityClass().getAnnotation(FeatureGuard.class);
        if (annotation != null && annotation.max() > 0) {
            return annotation.max();
        }

        // No limit
        return -1;
    }

    private long countRecords(EntityMetadata meta) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        query.select(cb.count(query.from(meta.entityClass())));
        return entityManager.createQuery(query).getSingleResult();
    }
}
