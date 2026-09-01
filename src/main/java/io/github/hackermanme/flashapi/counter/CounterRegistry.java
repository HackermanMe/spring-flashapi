package io.github.hackermanme.flashapi.counter;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Global registry of counter relationships. Built once after entity scanning.
 * Maps source entity classes to the counters they drive.
 *
 * On CREATE of a source entity: increments all associated counters.
 * On DELETE of a source entity: decrements all associated counters.
 * Uses atomic JPQL UPDATE (no read-modify-write race condition).
 */
public final class CounterRegistry {

    private static final Logger log = LoggerFactory.getLogger(CounterRegistry.class);

    private final Map<Class<?>, List<CounterDescriptor>> bySource;
    private final EntityManager entityManager;

    public CounterRegistry(List<CounterDescriptor> descriptors, EntityManager entityManager) {
        this.entityManager = entityManager;
        Map<Class<?>, List<CounterDescriptor>> map = new HashMap<>();
        for (CounterDescriptor d : descriptors) {
            map.computeIfAbsent(d.sourceEntity(), k -> new ArrayList<>()).add(d);
        }
        this.bySource = Collections.unmodifiableMap(map);
        if (!descriptors.isEmpty()) {
            log.info("FlashAPI: {} counter(s) registered", descriptors.size());
        }
    }

    public void onSourceCreated(Class<?> sourceEntity, Object sourceInstance) {
        updateCounters(sourceEntity, sourceInstance, 1);
    }

    public void onSourceDeleted(Class<?> sourceEntity, Object sourceInstance) {
        updateCounters(sourceEntity, sourceInstance, -1);
    }

    public boolean hasCountersFor(Class<?> sourceEntity) {
        return bySource.containsKey(sourceEntity);
    }

    private void updateCounters(Class<?> sourceEntity, Object sourceInstance, int delta) {
        List<CounterDescriptor> counters = bySource.get(sourceEntity);
        if (counters == null) return;

        for (CounterDescriptor counter : counters) {
            try {
                Object relatedEntity = counter.relationJavaField().get(sourceInstance);
                if (relatedEntity == null) continue;

                Object targetId = extractId(relatedEntity);
                if (targetId == null) continue;

                String jpql = "UPDATE " + counter.targetEntity().getSimpleName()
                        + " e SET e." + counter.counterFieldName()
                        + " = e." + counter.counterFieldName() + " + :delta"
                        + " WHERE e.id = :id";

                int updated = entityManager.createQuery(jpql)
                        .setParameter("delta", delta)
                        .setParameter("id", targetId)
                        .executeUpdate();

                if (updated > 0) {
                    // Evict the target entity from persistence context so the stale
                    // cached value doesn't overwrite our atomic JPQL update on flush
                    Object managed = entityManager.find(counter.targetEntity(), targetId);
                    if (managed != null) {
                        entityManager.refresh(managed);
                    }
                    log.debug("FlashAPI: counter {}.{} {} by {} for id={}",
                            counter.targetEntity().getSimpleName(), counter.counterFieldName(),
                            delta > 0 ? "incremented" : "decremented", Math.abs(delta), targetId);
                }
            } catch (IllegalAccessException e) {
                log.warn("FlashAPI: failed to update counter {}.{}: {}",
                        counter.targetEntity().getSimpleName(), counter.counterFieldName(), e.getMessage());
            }
        }
    }

    private Object extractId(Object entity) {
        return entityManager.getEntityManagerFactory()
                .getPersistenceUnitUtil().getIdentifier(entity);
    }
}
