package io.github.hackermanme.flashapi.audit;

import io.github.hackermanme.flashapi.registry.EntityMetadata;
import io.github.hackermanme.flashapi.registry.FieldMetadata;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Records audit trail for entity operations.
 * Runs in the same transaction as the CRUD operation (REQUIRED propagation).
 * User resolution: Spring Security if available, "anonymous" otherwise.
 */
public class AuditService {

    private final EntityManager entityManager;

    public AuditService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void logCreate(EntityMetadata meta, Object entity) {
        if (!meta.auditEnabled()) return;

        String entityId = extractId(meta, entity);
        AuditEntry entry = AuditEntry.builder()
                .entityType(meta.entityName())
                .entityId(entityId)
                .action(AuditAction.CREATE)
                .performedBy(currentUser())
                .timestamp(Instant.now())
                .build();
        entityManager.persist(entry);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void logUpdate(EntityMetadata meta, Object before, Object after) {
        if (!meta.auditEnabled()) return;

        String entityId = extractId(meta, after);
        Instant now = Instant.now();
        String user = currentUser();

        if (meta.auditTrackFields()) {
            List<AuditEntry> entries = diffFields(meta, entityId, before, after, user, now);
            entries.forEach(entityManager::persist);
        } else {
            AuditEntry entry = AuditEntry.builder()
                    .entityType(meta.entityName())
                    .entityId(entityId)
                    .action(AuditAction.UPDATE)
                    .performedBy(user)
                    .timestamp(now)
                    .build();
            entityManager.persist(entry);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void logDelete(EntityMetadata meta, Object entity) {
        if (!meta.auditEnabled()) return;

        String entityId = extractId(meta, entity);
        AuditEntry entry = AuditEntry.builder()
                .entityType(meta.entityName())
                .entityId(entityId)
                .action(AuditAction.DELETE)
                .performedBy(currentUser())
                .timestamp(Instant.now())
                .build();
        entityManager.persist(entry);
    }

    public List<AuditEntry> getHistory(String entityType, String entityId) {
        return entityManager.createQuery(
                        "SELECT a FROM AuditEntry a WHERE a.entityType = :type AND a.entityId = :id ORDER BY a.timestamp DESC",
                        AuditEntry.class)
                .setParameter("type", entityType)
                .setParameter("id", entityId)
                .getResultList();
    }

    private List<AuditEntry> diffFields(EntityMetadata meta, String entityId,
                                         Object before, Object after, String user, Instant now) {
        List<AuditEntry> entries = new ArrayList<>();
        for (FieldMetadata field : meta.visibleFields()) {
            try {
                Object oldVal = field.javaField().get(before);
                Object newVal = field.javaField().get(after);
                if (!Objects.equals(oldVal, newVal)) {
                    entries.add(AuditEntry.builder()
                            .entityType(meta.entityName())
                            .entityId(entityId)
                            .action(AuditAction.UPDATE)
                            .field(field.name())
                            .oldValue(oldVal != null ? oldVal.toString() : null)
                            .newValue(newVal != null ? newVal.toString() : null)
                            .performedBy(user)
                            .timestamp(now)
                            .build());
                }
            } catch (IllegalAccessException ignored) {}
        }
        return entries;
    }

    private String extractId(EntityMetadata meta, Object entity) {
        try {
            Object id = meta.primaryKeyField().javaField().get(entity);
            return id != null ? id.toString() : "null";
        } catch (IllegalAccessException e) {
            return "unknown";
        }
    }

    private String currentUser() {
        try {
            Class<?> holderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object ctx = holderClass.getMethod("getContext").invoke(null);
            Object auth = ctx.getClass().getMethod("getAuthentication").invoke(ctx);
            if (auth != null) {
                boolean authenticated = (boolean) auth.getClass().getMethod("isAuthenticated").invoke(auth);
                Object principal = auth.getClass().getMethod("getPrincipal").invoke(auth);
                if (authenticated && !"anonymousUser".equals(principal)) {
                    return (String) auth.getClass().getMethod("getName").invoke(auth);
                }
            }
        } catch (Exception ignored) {
        }
        return "anonymous";
    }
}
