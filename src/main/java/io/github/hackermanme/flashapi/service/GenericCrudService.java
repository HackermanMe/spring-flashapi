package io.github.hackermanme.flashapi.service;

import io.github.hackermanme.flashapi.audit.AuditService;
import io.github.hackermanme.flashapi.registry.EntityMetadata;
import io.github.hackermanme.flashapi.registry.FieldMetadata;
import io.github.hackermanme.flashapi.softdelete.SoftDeleteHandler;
import io.github.hackermanme.flashapi.tenant.TenantHandler;
import io.github.hackermanme.flashapi.webhook.WebhookDispatcher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Generic CRUD service using JPA Criteria API.
 * Integrates audit logging and soft delete transparently.
 * No reflection at query time — field metadata is pre-computed.
 */
public class GenericCrudService {

    private final EntityManager entityManager;
    private final AuditService auditService;
    private final SoftDeleteHandler softDeleteHandler;
    private final TenantHandler tenantHandler;
    private final WebhookDispatcher webhookDispatcher;

    public GenericCrudService(EntityManager entityManager, AuditService auditService,
                              SoftDeleteHandler softDeleteHandler, TenantHandler tenantHandler,
                              WebhookDispatcher webhookDispatcher) {
        this.entityManager = entityManager;
        this.auditService = auditService;
        this.softDeleteHandler = softDeleteHandler;
        this.tenantHandler = tenantHandler;
        this.webhookDispatcher = webhookDispatcher;
    }

    @Transactional(readOnly = true)
    public Page<Object> list(EntityMetadata meta, Pageable pageable, Map<String, String> filters) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        boolean showDeleted = "true".equalsIgnoreCase(filters.remove("deleted"));
        String searchTerm = filters.remove("search");

        // Count
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<?> countRoot = countQuery.from(meta.entityClass());
        countQuery.select(cb.count(countRoot));
        List<Predicate> countPreds = buildPredicates(cb, countRoot, meta, filters, showDeleted, searchTerm);
        if (!countPreds.isEmpty()) countQuery.where(countPreds.toArray(Predicate[]::new));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        // Data
        CriteriaQuery<Object> dataQuery = cb.createQuery(Object.class);
        Root<?> root = dataQuery.from(meta.entityClass());
        dataQuery.select(root);
        List<Predicate> preds = buildPredicates(cb, root, meta, filters, showDeleted, searchTerm);
        if (!preds.isEmpty()) dataQuery.where(preds.toArray(Predicate[]::new));

        if (pageable.getSort().isSorted()) {
            List<Order> orders = new ArrayList<>();
            pageable.getSort().forEach(o -> {
                Path<?> p = root.get(o.getProperty());
                orders.add(o.isAscending() ? cb.asc(p) : cb.desc(p));
            });
            dataQuery.orderBy(orders);
        }

        TypedQuery<Object> typed = entityManager.createQuery(dataQuery);
        typed.setFirstResult((int) pageable.getOffset());
        typed.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(typed.getResultList(), pageable, total);
    }

    @Transactional(readOnly = true)
    public Optional<Object> findById(EntityMetadata meta, Object id) {
        Object entity = entityManager.find(meta.entityClass(), id);
        if (entity != null && !tenantHandler.belongsToCurrentTenant(meta, entity)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entity);
    }

    @Transactional
    public Object create(EntityMetadata meta, Map<String, Object> data) {
        Map<String, Object> mutableData = new HashMap<>(data);
        tenantHandler.injectTenant(meta, mutableData);
        Object instance = instantiate(meta);
        applyFields(instance, meta.creatableFields(), mutableData);
        entityManager.persist(instance);
        entityManager.flush();
        auditService.logCreate(meta, instance);
        webhookDispatcher.dispatch(meta, "CREATE", instance);
        return instance;
    }

    @Transactional
    public Optional<Object> update(EntityMetadata meta, Object id, Map<String, Object> data) {
        Object instance = entityManager.find(meta.entityClass(), id);
        if (instance == null) return Optional.empty();
        if (!tenantHandler.belongsToCurrentTenant(meta, instance)) return Optional.empty();

        // Snapshot before for audit diff
        Map<String, Object> beforeSnapshot = meta.auditTrackFields() ? snapshot(meta, instance) : null;

        applyFields(instance, meta.updatableFields(), data);
        Object merged = entityManager.merge(instance);
        entityManager.flush();

        if (meta.auditTrackFields()) {
            auditService.logUpdate(meta, wrapSnapshot(meta, beforeSnapshot), merged);
        } else {
            auditService.logUpdate(meta, null, merged);
        }

        webhookDispatcher.dispatch(meta, "UPDATE", merged);
        return Optional.of(merged);
    }

    @Transactional
    public boolean delete(EntityMetadata meta, Object id) {
        Object instance = entityManager.find(meta.entityClass(), id);
        if (instance == null) return false;
        if (!tenantHandler.belongsToCurrentTenant(meta, instance)) return false;

        if (meta.softDelete()) {
            boolean deleted = softDeleteHandler.softDelete(meta, id);
            if (deleted) webhookDispatcher.dispatch(meta, "DELETE", instance);
            return deleted;
        }

        auditService.logDelete(meta, instance);
        webhookDispatcher.dispatch(meta, "DELETE", instance);
        entityManager.remove(instance);
        entityManager.flush();
        return true;
    }

    @Transactional
    public boolean restore(EntityMetadata meta, Object id) {
        if (!meta.softDelete()) return false;
        return softDeleteHandler.restore(meta, id);
    }

    @Transactional(readOnly = true)
    public List<?> getHistory(EntityMetadata meta, Object id) {
        return auditService.getHistory(meta.entityName(), id.toString());
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<?> root,
                                            EntityMetadata meta, Map<String, String> filters,
                                            boolean showDeleted, String searchTerm) {
        List<Predicate> predicates = new ArrayList<>();

        // Multi-tenant filter
        Predicate tenantPred = tenantHandler.tenantPredicate(cb, root, meta);
        if (tenantPred != null) {
            predicates.add(tenantPred);
        }

        // Soft delete filter
        if (meta.softDelete()) {
            predicates.add(showDeleted
                    ? softDeleteHandler.onlyDeleted(cb, root)
                    : softDeleteHandler.notDeleted(cb, root));
        }

        // Full-text search across all String fields
        if (searchTerm != null && !searchTerm.isBlank()) {
            String pattern = "%" + searchTerm.toLowerCase() + "%";
            List<Predicate> searchPredicates = new ArrayList<>();
            for (FieldMetadata field : meta.fields()) {
                if (field.type() == String.class) {
                    searchPredicates.add(cb.like(cb.lower(root.get(field.name())), pattern));
                }
            }
            if (!searchPredicates.isEmpty()) {
                predicates.add(cb.or(searchPredicates.toArray(Predicate[]::new)));
            }
        }

        Map<String, FieldMetadata> fieldMap = meta.fieldsByName();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            ParsedFilter parsed = parseFilterKey(key);

            FieldMetadata field = fieldMap.get(parsed.fieldName);
            if (field == null) continue;

            Predicate p = buildPredicate(cb, root.get(parsed.fieldName), parsed.operator, value, field.type());
            if (p != null) predicates.add(p);
        }

        return predicates;
    }

    @SuppressWarnings("unchecked")
    private Predicate buildPredicate(CriteriaBuilder cb, Path<?> path,
                                     String op, String value, Class<?> type) {
        return switch (op) {
            case "eq" -> cb.equal(path, convert(value, type));
            case "neq" -> cb.notEqual(path, convert(value, type));
            case "gt" -> cb.greaterThan((Path<Comparable>) path, (Comparable) convert(value, type));
            case "gte" -> cb.greaterThanOrEqualTo((Path<Comparable>) path, (Comparable) convert(value, type));
            case "lt" -> cb.lessThan((Path<Comparable>) path, (Comparable) convert(value, type));
            case "lte" -> cb.lessThanOrEqualTo((Path<Comparable>) path, (Comparable) convert(value, type));
            case "contains" -> cb.like(cb.lower((Path<String>) path), "%" + value.toLowerCase() + "%");
            case "startswith" -> cb.like(cb.lower((Path<String>) path), value.toLowerCase() + "%");
            case "endswith" -> cb.like(cb.lower((Path<String>) path), "%" + value.toLowerCase());
            case "isnull" -> "true".equalsIgnoreCase(value) ? cb.isNull(path) : cb.isNotNull(path);
            case "in" -> {
                String[] parts = value.split(",");
                List<Object> values = Arrays.stream(parts).map(v -> convert(v.trim(), type)).toList();
                yield path.in(values);
            }
            default -> null;
        };
    }

    private ParsedFilter parseFilterKey(String key) {
        int dot = key.lastIndexOf('.');
        if (dot > 0 && dot < key.length() - 1) {
            String possibleOp = key.substring(dot + 1);
            if (OPERATORS.contains(possibleOp)) {
                return new ParsedFilter(key.substring(0, dot), possibleOp);
            }
        }
        return new ParsedFilter(key, "eq");
    }

    private static final Set<String> OPERATORS = Set.of(
            "eq", "neq", "gt", "gte", "lt", "lte",
            "contains", "startswith", "endswith", "isnull", "in");

    private Object convert(String value, Class<?> type) {
        if (type == String.class) return value;
        if (type == Long.class || type == long.class) return Long.parseLong(value);
        if (type == Integer.class || type == int.class) return Integer.parseInt(value);
        if (type == Double.class || type == double.class) return Double.parseDouble(value);
        if (type == Float.class || type == float.class) return Float.parseFloat(value);
        if (type == Boolean.class || type == boolean.class) return Boolean.parseBoolean(value);
        if (type == java.util.UUID.class) return java.util.UUID.fromString(value);
        if (type == java.time.LocalDate.class) return java.time.LocalDate.parse(value);
        if (type == java.time.LocalDateTime.class) return java.time.LocalDateTime.parse(value);
        if (type == java.time.Instant.class) return java.time.Instant.parse(value);
        if (type == java.math.BigDecimal.class) return new java.math.BigDecimal(value);
        if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, value);
        return value;
    }

    private Object instantiate(EntityMetadata meta) {
        try {
            return meta.entityClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException(
                    meta.entityName() + " must have a public no-arg constructor", e);
        }
    }

    private void applyFields(Object instance, List<FieldMetadata> fields, Map<String, Object> data) {
        for (FieldMetadata field : fields) {
            if (!data.containsKey(field.name())) continue;
            Object value = data.get(field.name());
            try {
                field.javaField().set(instance, coerce(value, field.type()));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot set " + field.name() + " on " + instance.getClass().getSimpleName(), e);
            }
        }
    }

    private Object coerce(Object value, Class<?> target) {
        if (value == null) return null;
        if (target.isInstance(value)) return value;
        String str = value.toString();
        return convert(str, target);
    }

    private Map<String, Object> snapshot(EntityMetadata meta, Object entity) {
        Map<String, Object> snap = new HashMap<>();
        for (FieldMetadata f : meta.visibleFields()) {
            try {
                snap.put(f.name(), f.javaField().get(entity));
            } catch (IllegalAccessException ignored) {}
        }
        return snap;
    }

    /**
     * Wraps a snapshot map into a proxy-like object for audit diffing.
     * AuditService.diffFields reads fields via reflection, so we need the real entity or a wrapper.
     */
    private Object wrapSnapshot(EntityMetadata meta, Map<String, Object> snapshot) {
        Object proxy = instantiate(meta);
        for (FieldMetadata f : meta.visibleFields()) {
            if (snapshot.containsKey(f.name())) {
                try {
                    f.javaField().set(proxy, snapshot.get(f.name()));
                } catch (IllegalAccessException ignored) {}
            }
        }
        return proxy;
    }

    private record ParsedFilter(String fieldName, String operator) {}
}
