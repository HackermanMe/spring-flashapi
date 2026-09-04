package io.github.hackermanme.flashapi.guard;

import io.github.hackermanme.flashapi.registry.EntityMetadata;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Enforces record-count limits before CREATE operations.
 * Resolution priority: PlanLimitResolver bean > EntityMetadata.maxRecords > no limit.
 */
public class FeatureGuardHandler {

    private final EntityManager entityManager;
    private final PlanLimitResolver planLimitResolver;

    public FeatureGuardHandler(EntityManager entityManager, PlanLimitResolver planLimitResolver) {
        this.entityManager = entityManager;
        this.planLimitResolver = planLimitResolver;
    }

    public void checkLimit(EntityMetadata meta, HttpServletRequest request) {
        long limit = resolveLimit(meta, request);
        if (limit <= 0) return;
        if (countRecords(meta) >= limit) {
            throw new RecordLimitExceededException(meta.entityName(), limit);
        }
    }

    public void checkLimit(EntityMetadata meta, HttpServletRequest request, int adding) {
        long limit = resolveLimit(meta, request);
        if (limit <= 0) return;
        if (countRecords(meta) + adding > limit) {
            throw new RecordLimitExceededException(meta.entityName(), limit);
        }
    }

    private long resolveLimit(EntityMetadata meta, HttpServletRequest request) {
        if (planLimitResolver != null) {
            long resolved = planLimitResolver.resolveLimit(meta.entityName(), request);
            if (resolved != -1) return resolved;
        }
        return meta.maxRecords() > 0 ? meta.maxRecords() : -1;
    }

    private long countRecords(EntityMetadata meta) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        query.select(cb.count(query.from(meta.resolvedEntityClass())));
        return entityManager.createQuery(query).getSingleResult();
    }
}
