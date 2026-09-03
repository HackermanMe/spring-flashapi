package io.github.hackermanme.flashapi.service;

import io.github.hackermanme.flashapi.audit.AuditService;
import io.github.hackermanme.flashapi.counter.CounterRegistry;
import io.github.hackermanme.flashapi.dashboard.MetricsCollector;
import io.github.hackermanme.flashapi.hooks.*;
import io.github.hackermanme.flashapi.security.FlashPrincipalResolver;
import io.github.hackermanme.flashapi.registry.EntityMetadata;
import io.github.hackermanme.flashapi.registry.FieldMetadata;
import io.github.hackermanme.flashapi.softdelete.SoftDeleteHandler;
import io.github.hackermanme.flashapi.tenant.TenantHandler;
import io.github.hackermanme.flashapi.webhook.WebhookDispatcher;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Generic CRUD service using JPA Criteria API.
 * Integrates audit logging and soft delete transparently.
 * No reflection at query time — field metadata is pre-computed.
 */
public class GenericCrudService {

    private static final Logger log = LoggerFactory.getLogger(GenericCrudService.class);

    private final EntityManager entityManager;
    private final AuditService auditService;
    private final SoftDeleteHandler softDeleteHandler;
    private final TenantHandler tenantHandler;
    private final WebhookDispatcher webhookDispatcher;
    private final HookRegistry hookRegistry;
    private volatile MetricsCollector metricsCollector;
    private volatile FlashEventBroadcaster eventBroadcaster;
    private volatile CounterRegistry counterRegistry;
    private volatile FlashPrincipalResolver principalResolver;

    public GenericCrudService(EntityManager entityManager, AuditService auditService,
                              SoftDeleteHandler softDeleteHandler, TenantHandler tenantHandler,
                              WebhookDispatcher webhookDispatcher, HookRegistry hookRegistry) {
        this.entityManager = entityManager;
        this.auditService = auditService;
        this.softDeleteHandler = softDeleteHandler;
        this.tenantHandler = tenantHandler;
        this.webhookDispatcher = webhookDispatcher;
        this.hookRegistry = hookRegistry;
    }

    public void setMetricsCollector(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    public void setEventBroadcaster(FlashEventBroadcaster broadcaster) {
        this.eventBroadcaster = broadcaster;
    }

    public void setCounterRegistry(CounterRegistry counterRegistry) {
        this.counterRegistry = counterRegistry;
    }

    public void setPrincipalResolver(FlashPrincipalResolver resolver) {
        this.principalResolver = resolver;
    }

    private void broadcastEvent(EntityMetadata meta, String action, Object entity) {
        var broadcaster = this.eventBroadcaster;
        if (broadcaster == null) return;
        String eventType = switch (action) {
            case "CREATE" -> "ENTITY_CREATED";
            case "UPDATE" -> "ENTITY_UPDATED";
            case "DELETE" -> "ENTITY_DELETED";
            case "RESTORE" -> "ENTITY_RESTORED";
            default -> null;
        };
        if (eventType == null) return;
        Map<String, Object> data = serialize(meta, entity);
        broadcaster.broadcast(meta.entityName(), eventType, data);
    }

    private void recordMetric(String entityName, String operation) {
        if (metricsCollector != null) metricsCollector.recordOperation(entityName, operation);
    }

    private void recordMetric(String entityName, String operation, String entityId) {
        if (metricsCollector != null) metricsCollector.recordOperation(entityName, operation, entityId);
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
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

        recordMetric(meta.entityName(), searchTerm != null ? "SEARCH" : "READ");
        return new PageImpl<>(typed.getResultList(), pageable, total);
    }

    @Transactional(readOnly = true)
    public Optional<Object> findById(EntityMetadata meta, Object id) {
        if (meta.hasCustomLookupField()) {
            return findByLookupField(meta, id);
        }
        Object entity = entityManager.find(meta.entityClass(), id);
        if (entity != null && !tenantHandler.belongsToCurrentTenant(meta, entity)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entity);
    }

    @Transactional(readOnly = true)
    public Optional<Object> findByLookupField(EntityMetadata meta, Object lookupValue) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> query = cb.createQuery(Object.class);
        Root<?> root = query.from(meta.entityClass());
        query.select(root);
        query.where(cb.equal(root.get(meta.lookupFieldName()), lookupValue));
        List<Object> results = entityManager.createQuery(query).setMaxResults(1).getResultList();
        if (results.isEmpty()) return Optional.empty();
        Object entity = results.get(0);
        if (!tenantHandler.belongsToCurrentTenant(meta, entity)) {
            return Optional.empty();
        }
        return Optional.of(entity);
    }

    @Transactional
    public Object create(EntityMetadata meta, Map<String, Object> data) {
        Map<String, Object> mutableData = new HashMap<>(data);
        tenantHandler.injectTenant(meta, mutableData);
        stripCurrentUserField(meta, mutableData);
        Object instance = instantiate(meta);
        applyFields(instance, meta.creatableFields(), mutableData, meta);
        injectCurrentUser(meta, instance);
        fillAuditFields(instance, true);

        HttpServletRequest request = getCurrentRequest();
        hookRegistry.invokeHooks(FlashBeforeCreate.class, instance, request);

        entityManager.persist(instance);
        entityManager.flush();

        hookRegistry.invokeHooks(FlashAfterCreate.class, instance, request);

        updateCounters(meta, instance, true);
        auditService.logCreate(meta, instance);
        webhookDispatcher.dispatch(meta, "CREATE", instance);
        broadcastEvent(meta, "CREATE", instance);
        recordMetric(meta.entityName(), "CREATE");
        return instance;
    }

    @Transactional
    public Optional<Object> update(EntityMetadata meta, Object id, Map<String, Object> data) {
        Object instance = meta.hasCustomLookupField()
                ? findByLookupField(meta, id).orElse(null)
                : entityManager.find(meta.entityClass(), id);
        if (instance == null) return Optional.empty();
        if (!tenantHandler.belongsToCurrentTenant(meta, instance)) return Optional.empty();

        // Snapshot before for audit diff
        Map<String, Object> beforeSnapshot = meta.auditTrackFields() ? snapshot(meta, instance) : null;

        Map<String, Object> mutableUpdateData = new HashMap<>(data);
        stripCurrentUserField(meta, mutableUpdateData);
        applyFields(instance, meta.updatableFields(), mutableUpdateData, meta);
        fillAuditFields(instance, false);

        HttpServletRequest request = getCurrentRequest();
        hookRegistry.invokeHooks(FlashBeforeUpdate.class, instance, request);

        Object merged = entityManager.merge(instance);
        entityManager.flush();

        hookRegistry.invokeHooks(FlashAfterUpdate.class, merged, request);

        if (meta.auditTrackFields()) {
            auditService.logUpdate(meta, wrapSnapshot(meta, beforeSnapshot), merged);
        } else {
            auditService.logUpdate(meta, null, merged);
        }

        webhookDispatcher.dispatch(meta, "UPDATE", merged);
        broadcastEvent(meta, "UPDATE", merged);
        recordMetric(meta.entityName(), "UPDATE");
        return Optional.of(merged);
    }

    @Transactional
    public boolean delete(EntityMetadata meta, Object id) {
        Object instance = meta.hasCustomLookupField()
                ? findByLookupField(meta, id).orElse(null)
                : entityManager.find(meta.entityClass(), id);
        if (instance == null) return false;
        if (!tenantHandler.belongsToCurrentTenant(meta, instance)) return false;

        HttpServletRequest request = getCurrentRequest();
        hookRegistry.invokeHooks(FlashBeforeDelete.class, instance, request);

        if (meta.softDelete()) {
            Object realId = extractPrimaryKey(meta, instance);
            boolean deleted = softDeleteHandler.softDelete(meta, realId);
            if (deleted) {
                hookRegistry.invokeHooks(FlashAfterDelete.class, instance, request);
                updateCounters(meta, instance, false);
                webhookDispatcher.dispatch(meta, "DELETE", instance);
                broadcastEvent(meta, "DELETE", instance);
                recordMetric(meta.entityName(), "DELETE");
            }
            return deleted;
        }

        auditService.logDelete(meta, instance);
        entityManager.remove(instance);
        entityManager.flush();

        hookRegistry.invokeHooks(FlashAfterDelete.class, instance, request);

        updateCounters(meta, instance, false);
        webhookDispatcher.dispatch(meta, "DELETE", instance);
        broadcastEvent(meta, "DELETE", instance);
        recordMetric(meta.entityName(), "DELETE");
        return true;
    }

    @Transactional
    public boolean restore(EntityMetadata meta, Object id) {
        if (!meta.softDelete()) return false;
        Object instance;
        Object realId;
        if (meta.hasCustomLookupField()) {
            instance = findByLookupField(meta, id).orElse(null);
            if (instance == null) return false;
            realId = extractPrimaryKey(meta, instance);
        } else {
            instance = entityManager.find(meta.entityClass(), id);
            if (instance == null) return false;
            realId = id;
        }
        boolean restored = softDeleteHandler.restore(meta, realId);
        if (restored) {
            broadcastEvent(meta, "RESTORE", instance);
        }
        return restored;
    }

    private Object extractPrimaryKey(EntityMetadata meta, Object instance) {
        try {
            return meta.primaryKeyField().javaField().get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read primary key from " + meta.entityName(), e);
        }
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
        Map<String, jakarta.persistence.criteria.Join<Object, Object>> joins = new java.util.HashMap<>();

        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            ParsedFilter parsed = parseFilterKey(key);

            // Check for relation filter (e.g., category.id)
            if (parsed.fieldName.contains(".")) {
                String[] parts = parsed.fieldName.split("\\.", 2);
                if (parts.length == 2) {
                    String relationName = parts[0];
                    String targetField = parts[1];

                    // Reject deep nesting (max 1 level)
                    if (targetField.contains(".")) {
                        throw new IllegalArgumentException("Nested relation filters not supported: " + parsed.fieldName);
                    }

                    // Validate relation exists
                    io.github.hackermanme.flashapi.registry.RelationMetadata relation =
                            meta.relationsByName().get(relationName);
                    if (relation == null) continue; // silently ignore unknown relations

                    // Only allow @ManyToOne and @OneToOne
                    if (relation.type() == io.github.hackermanme.flashapi.registry.RelationMetadata.RelationType.ONE_TO_MANY ||
                        relation.type() == io.github.hackermanme.flashapi.registry.RelationMetadata.RelationType.MANY_TO_MANY) {
                        throw new IllegalArgumentException("Cannot filter by collection relation: " + relationName);
                    }

                    // Reuse or create join
                    jakarta.persistence.criteria.Join<Object, Object> join = joins.get(relationName);
                    if (join == null) {
                        join = root.join(relationName);
                        joins.put(relationName, join);
                    }

                    // Build predicate on joined entity
                    Class<?> targetType = inferFieldType(relation.targetEntity(), targetField);
                    if (targetType != null) {
                        Predicate p = buildPredicate(cb, join.get(targetField), parsed.operator, value, targetType);
                        if (p != null) predicates.add(p);
                    }
                    continue;
                }
            }

            // Standard field filter
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

    private void applyFields(Object instance, List<FieldMetadata> fields, Map<String, Object> data, EntityMetadata metadata) {
        for (FieldMetadata field : fields) {
            if (!data.containsKey(field.name())) continue;
            Object value = data.get(field.name());
            try {
                Object finalValue = coerce(value, field.type());
                if (field.password() && finalValue instanceof String raw) {
                    finalValue = hashPassword(raw);
                }
                field.javaField().set(instance, finalValue);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot set " + field.name() + " on " + instance.getClass().getSimpleName(), e);
            }
        }

        // Resolve @ManyToOne relations via FK IDs (e.g., "categoryId": 1 -> Category entity)
        for (io.github.hackermanme.flashapi.registry.ManyToOneDescriptor descriptor : metadata.manyToOneDescriptors()) {
            String fkFieldName = descriptor.fkFieldName();
            if (!data.containsKey(fkFieldName)) continue;
            Object idValue = data.get(fkFieldName);
            if (idValue == null) {
                // Set null on the relation field
                try {
                    descriptor.relationField().set(instance, null);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot set null on " + descriptor.relationField().getName(), e);
                }
            } else {
                // Resolve FK ID to managed entity reference
                Object convertedId = coerce(idValue, descriptor.targetIdType());
                Object reference = entityManager.getReference(descriptor.targetEntity(), convertedId);
                try {
                    descriptor.relationField().set(instance, reference);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot set relation " + descriptor.relationField().getName(), e);
                }
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

    private Map<String, Object> serialize(EntityMetadata meta, Object entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (FieldMetadata field : meta.visibleFields()) {
            try {
                if (field.type().isAnnotationPresent(jakarta.persistence.Entity.class)) continue;
                Object value = field.javaField().get(entity);
                map.put(field.name(), toJsonSafe(value));
            } catch (IllegalAccessException e) {
                map.put(field.name(), null);
            }
        }
        return map;
    }

    private Object toJsonSafe(Object value) {
        if (value == null) return null;
        if (value instanceof Number || value instanceof Boolean || value instanceof String) return value;
        if (value instanceof Enum<?> e) return e.name();
        if (value instanceof java.time.temporal.Temporal) return value.toString();
        if (value instanceof java.util.UUID) return value.toString();
        return value.toString();
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

    /**
     * Infers the field type of a target entity for relation filters.
     * Uses reflection to find the field on the target entity class.
     */
    private Class<?> inferFieldType(Class<?> targetEntity, String fieldName) {
        try {
            java.lang.reflect.Field field = targetEntity.getDeclaredField(fieldName);
            return field.getType();
        } catch (NoSuchFieldException e) {
            // Try parent class
            Class<?> parent = targetEntity.getSuperclass();
            if (parent != null && parent != Object.class) {
                return inferFieldType(parent, fieldName);
            }
            return null;
        }
    }

    private String hashPassword(String raw) {
        try {
            Class<?> encoderClass = Class.forName("org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder");
            Object encoder = encoderClass.getDeclaredConstructor().newInstance();
            return (String) encoderClass.getMethod("encode", CharSequence.class).invoke(encoder, raw);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(
                    "FlashAPI: @FlashWriteOnly(password=true) requires spring-boot-starter-security on the classpath");
        } catch (Exception e) {
            throw new IllegalStateException("FlashAPI: failed to hash password", e);
        }
    }

    private void updateCounters(EntityMetadata meta, Object instance, boolean created) {
        var registry = this.counterRegistry;
        if (registry == null || !registry.hasCountersFor(meta.entityClass())) return;
        if (created) {
            registry.onSourceCreated(meta.entityClass(), instance);
        } else {
            registry.onSourceDeleted(meta.entityClass(), instance);
        }
    }

    private void stripCurrentUserField(EntityMetadata meta, Map<String, Object> data) {
        if (!meta.hasCurrentUserField()) return;
        data.remove(meta.currentUserFieldName());
        if (meta.currentUserFieldIsRelation()) {
            data.remove(meta.currentUserFieldName() + "Id");
        }
    }

    private void injectCurrentUser(EntityMetadata meta, Object instance) {
        if (!meta.hasCurrentUserField()) return;

        Object resolvedId = resolveCurrentUserIdentifier();
        if (resolvedId == null) {
            log.warn("FlashAPI: could not resolve current user for field '{}' on entity '{}'. " +
                    "The field will be null. If using a FlashPrincipalResolver, verify it returns a non-null value.",
                    meta.currentUserFieldName(), meta.entityName());
            return;
        }

        try {
            if (meta.currentUserFieldIsRelation()) {
                Object convertedId = convertToTargetType(resolvedId, meta.currentUserTargetIdType());
                Object reference = entityManager.getReference(meta.currentUserTargetEntity(), convertedId);
                meta.currentUserJavaField().set(instance, reference);
            } else {
                Class<?> fieldType = meta.currentUserJavaField().getType();
                Object convertedValue = convertToTargetType(resolvedId, fieldType);
                meta.currentUserJavaField().set(instance, convertedValue);
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "FlashAPI: failed to inject current user (resolved='" + resolvedId + "', type=" +
                    resolvedId.getClass().getSimpleName() + ") into field '" + meta.currentUserFieldName() +
                    "' on entity '" + meta.entityName() + "'. " +
                    "Check that the resolved value matches the target field type (" +
                    (meta.currentUserFieldIsRelation() ? meta.currentUserTargetIdType().getSimpleName() : meta.currentUserJavaField().getType().getSimpleName()) +
                    ").", e);
        }
    }

    private Object resolveCurrentUserIdentifier() {
        FlashPrincipalResolver resolver = this.principalResolver;
        if (resolver != null) {
            try {
                Class<?> holderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
                Object ctx = holderClass.getMethod("getContext").invoke(null);
                Object auth = ctx.getClass().getMethod("getAuthentication").invoke(ctx);
                if (auth == null) return null;
                boolean authenticated = (boolean) auth.getClass().getMethod("isAuthenticated").invoke(auth);
                if (!authenticated) return null;
                return resolver.resolve((org.springframework.security.core.Authentication) auth);
            } catch (Exception e) {
                log.warn("FlashAPI: FlashPrincipalResolver failed to resolve current user identity: {}", e.getMessage(), e);
                return null;
            }
        }
        return resolveCurrentUser();
    }

    private Object convertToTargetType(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isInstance(value)) return value;
        String str = value.toString();
        if (targetType == String.class) return str;
        if (targetType == Long.class || targetType == long.class) return Long.parseLong(str);
        if (targetType == Integer.class || targetType == int.class) return Integer.parseInt(str);
        if (targetType == java.util.UUID.class) return java.util.UUID.fromString(str);
        return value;
    }

    private void fillAuditFields(Object instance, boolean isCreate) {
        String currentUser = resolveCurrentUser();
        if (currentUser == null) return;

        Class<?> clazz = instance.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                boolean isCreatedBy = isCreate && field.isAnnotationPresent(CreatedBy.class);
                boolean isModifiedBy = field.isAnnotationPresent(LastModifiedBy.class);
                if (isCreatedBy || isModifiedBy) {
                    field.setAccessible(true);
                    try {
                        if (field.getType() == String.class) {
                            field.set(instance, currentUser);
                        }
                    } catch (IllegalAccessException ignored) {}
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private String resolveCurrentUser() {
        try {
            Class<?> holderClass = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = holderClass.getMethod("getContext").invoke(null);
            Object auth = context.getClass().getMethod("getAuthentication").invoke(context);
            if (auth == null) return null;
            boolean authenticated = (boolean) auth.getClass().getMethod("isAuthenticated").invoke(auth);
            if (!authenticated) return null;
            Object name = auth.getClass().getMethod("getName").invoke(auth);
            String username = name != null ? name.toString() : null;
            if ("anonymousUser".equals(username)) return null;
            return username;
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
