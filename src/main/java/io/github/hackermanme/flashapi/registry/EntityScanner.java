package io.github.hackermanme.flashapi.registry;

import io.github.hackermanme.flashapi.annotation.FlashAudit;
import io.github.hackermanme.flashapi.annotation.FlashCounter;
import io.github.hackermanme.flashapi.annotation.FlashEntity;
import io.github.hackermanme.flashapi.annotation.FlashExportExclude;
import io.github.hackermanme.flashapi.annotation.FlashHidden;
import io.github.hackermanme.flashapi.annotation.FlashMultiTenant;
import io.github.hackermanme.flashapi.annotation.FlashReadOnly;
import io.github.hackermanme.flashapi.annotation.FlashSecured;
import io.github.hackermanme.flashapi.annotation.FlashWriteOnly;
import io.github.hackermanme.flashapi.counter.CounterDescriptor;
import jakarta.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Scans classpath for @FlashEntity classes and builds immutable EntityMetadata.
 * Runs once at startup. All reflection (setAccessible) happens here, never at request time.
 */
public final class EntityScanner {

    private static final Logger log = LoggerFactory.getLogger(EntityScanner.class);

    private EntityScanner() {}

    public static List<EntityMetadata> scan(String[] basePackages) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(FlashEntity.class));

        List<EntityMetadata> results = new ArrayList<>();

        for (String pkg : basePackages) {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(pkg);
            for (BeanDefinition bd : candidates) {
                try {
                    Class<?> clazz = Class.forName(bd.getBeanClassName(), true,
                            Thread.currentThread().getContextClassLoader());
                    results.add(buildMetadata(clazz));
                    log.info("FlashAPI: registered {} → /{}", clazz.getSimpleName(),
                            clazz.getAnnotation(FlashEntity.class).path().isEmpty()
                                    ? pluralize(clazz.getSimpleName())
                                    : clazz.getAnnotation(FlashEntity.class).path());
                } catch (ClassNotFoundException e) {
                    log.warn("FlashAPI: could not load class {}", bd.getBeanClassName(), e);
                }
            }
        }

        return Collections.unmodifiableList(results);
    }

    private static EntityMetadata buildMetadata(Class<?> clazz) {
        FlashEntity annotation = clazz.getAnnotation(FlashEntity.class);
        FlashAudit auditAnnotation = clazz.getAnnotation(FlashAudit.class);
        FlashMultiTenant multiTenantAnnotation = clazz.getAnnotation(FlashMultiTenant.class);
        io.github.hackermanme.flashapi.annotation.FlashWebhook webhookAnnotation =
                clazz.getAnnotation(io.github.hackermanme.flashapi.annotation.FlashWebhook.class);
        io.github.hackermanme.flashapi.annotation.FeatureGuard guardAnnotation =
                clazz.getAnnotation(io.github.hackermanme.flashapi.annotation.FeatureGuard.class);

        String path = annotation.path().isEmpty() ? pluralize(clazz.getSimpleName()) : annotation.path();
        Set<CrudOperation> ops = resolveOperations(annotation);

        // Audit: @FlashEntity(audit) takes precedence, fallback to @FlashAudit
        boolean auditEnabled = annotation.audit()
                || (auditAnnotation != null && auditAnnotation.enabled());
        boolean auditTrackFields = annotation.trackFields()
                || (auditAnnotation != null && auditAnnotation.trackFields());

        // Tenant: @FlashEntity(tenantField) takes precedence, fallback to @FlashMultiTenant
        String tenantField = !annotation.tenantField().isEmpty() ? annotation.tenantField()
                : (multiTenantAnnotation != null ? multiTenantAnnotation.field() : null);

        // Webhook: @FlashEntity(webhook) takes precedence, fallback to @FlashWebhook
        boolean webhookEnabled = annotation.webhook() || webhookAnnotation != null;
        String[] webhookEvents = annotation.webhook() ? annotation.webhookEvents()
                : (webhookAnnotation != null ? webhookAnnotation.events()
                : new String[]{"CREATE", "UPDATE", "DELETE"});

        // Feature guard: @FlashEntity(maxRecords) takes precedence, fallback to @FeatureGuard
        long maxRecords = annotation.maxRecords() > 0 ? annotation.maxRecords()
                : (guardAnnotation != null && guardAnnotation.max() > 0 ? guardAnnotation.max() : 0);

        List<FieldMetadata> fields = new ArrayList<>();
        List<RelationMetadata> relations = new ArrayList<>();
        FieldMetadata pkField = null;

        for (Field field : collectInstanceFields(clazz)) {
            field.setAccessible(true);
            RelationMetadata rel = buildRelationMetadata(field);
            if (rel != null) {
                relations.add(rel);
            } else {
                FieldMetadata fm = buildFieldMetadata(field);
                fields.add(fm);
                if (fm.primaryKey()) {
                    pkField = fm;
                }
            }
        }

        if (pkField == null) {
            throw new IllegalStateException(
                    "@FlashEntity " + clazz.getName() + " must have a field annotated with @Id");
        }

        List<FieldMetadata> immutableFields = Collections.unmodifiableList(fields);
        Map<String, FieldMetadata> fieldsByName = immutableFields.stream()
                .collect(Collectors.toUnmodifiableMap(FieldMetadata::name, Function.identity()));
        List<FieldMetadata> creatableFields = immutableFields.stream()
                .filter(FieldMetadata::isAcceptedInCreate).toList();
        List<FieldMetadata> updatableFields = immutableFields.stream()
                .filter(FieldMetadata::isAcceptedInUpdate).toList();
        List<FieldMetadata> visibleFields = immutableFields.stream()
                .filter(FieldMetadata::isVisibleInResponse).toList();
        List<FieldMetadata> exportableFields = immutableFields.stream()
                .filter(FieldMetadata::isVisibleInExport).toList();

        List<RelationMetadata> immutableRelations = Collections.unmodifiableList(relations);
        Map<String, RelationMetadata> relationsByName = immutableRelations.stream()
                .collect(Collectors.toUnmodifiableMap(RelationMetadata::name, Function.identity()));

        if (tenantField != null) {
            boolean hasTenantJavaField = immutableFields.stream()
                    .anyMatch(f -> f.name().equals(tenantField));
            if (!hasTenantJavaField) {
                throw new IllegalStateException(
                        "@FlashMultiTenant on " + clazz.getName() + " references field '" + tenantField
                                + "' which does not exist on the entity");
            }
        }

        String lookupFieldName = annotation.lookupField().isEmpty() ? null : annotation.lookupField();
        Class<?> lookupFieldType = null;
        if (lookupFieldName != null) {
            FieldMetadata lookupMeta = fieldsByName.get(lookupFieldName);
            if (lookupMeta == null) {
                throw new IllegalStateException(
                        "@FlashEntity on " + clazz.getName() + " specifies lookupField='" + lookupFieldName
                                + "' which does not exist on the entity");
            }
            lookupFieldType = lookupMeta.type();
        }

        List<ManyToOneDescriptor> manyToOneDescriptors = new ArrayList<>();
        for (RelationMetadata rel : immutableRelations) {
            if (rel.type() == RelationMetadata.RelationType.MANY_TO_ONE) {
                String fkFieldName = rel.name() + "Id";
                Class<?> targetEntity = rel.targetEntity();
                Class<?> targetIdType = extractIdType(targetEntity);
                Field relationField = rel.javaField();
                manyToOneDescriptors.add(new ManyToOneDescriptor(fkFieldName, relationField, targetEntity, targetIdType));
            }
        }
        Map<String, ManyToOneDescriptor> manyToOneByFkName = manyToOneDescriptors.stream()
                .collect(Collectors.toUnmodifiableMap(ManyToOneDescriptor::fkFieldName, Function.identity()));

        // Owner-based access control
        FlashSecured securedAnnotation = clazz.getAnnotation(FlashSecured.class);
        String ownerFieldName = null;
        Field ownerJavaField = null;
        boolean ownerFieldIsRelation = false;
        String[] ownerAdminRoles = new String[0];

        if (securedAnnotation != null && !securedAnnotation.ownerField().isEmpty()) {
            ownerFieldName = securedAnnotation.ownerField();
            ownerAdminRoles = securedAnnotation.ownerAdminRoles();

            // Check if it's a relation field
            RelationMetadata ownerRelation = relationsByName.get(ownerFieldName);
            if (ownerRelation != null) {
                ownerFieldIsRelation = true;
                ownerJavaField = ownerRelation.javaField();
                ownerJavaField.setAccessible(true);
            } else {
                // Check scalar fields
                FieldMetadata ownerScalar = fieldsByName.get(ownerFieldName);
                if (ownerScalar != null) {
                    ownerJavaField = ownerScalar.javaField();
                } else {
                    throw new IllegalStateException(
                            "@FlashSecured on " + clazz.getName() + " specifies ownerField='"
                                    + ownerFieldName + "' which does not exist on the entity");
                }
            }
        }

        // Current user field auto-injection
        String currentUserFieldName = annotation.currentUserField().isEmpty() ? null : annotation.currentUserField();
        Field currentUserJavaField = null;
        boolean currentUserFieldIsRelation = false;
        Class<?> currentUserTargetEntity = null;
        Class<?> currentUserTargetIdType = null;

        if (currentUserFieldName != null) {
            RelationMetadata curUserRelation = relationsByName.get(currentUserFieldName);
            if (curUserRelation != null) {
                currentUserFieldIsRelation = true;
                currentUserJavaField = curUserRelation.javaField();
                currentUserJavaField.setAccessible(true);
                currentUserTargetEntity = curUserRelation.targetEntity();
                currentUserTargetIdType = extractIdType(currentUserTargetEntity);
            } else {
                FieldMetadata curUserScalar = fieldsByName.get(currentUserFieldName);
                if (curUserScalar != null) {
                    currentUserJavaField = curUserScalar.javaField();
                } else {
                    throw new IllegalStateException(
                            "@FlashEntity on " + clazz.getName() + " specifies currentUserField='"
                                    + currentUserFieldName + "' which does not exist on the entity");
                }
            }
        }

        return new EntityMetadata(
                clazz, clazz.getSimpleName(), path,
                pkField.name(), pkField.type(),
                annotation.softDelete(), auditEnabled, auditTrackFields,
                annotation.cache(), annotation.cacheTtl(),
                annotation.rateLimit(), annotation.rateLimitRequests(), annotation.rateLimitWindow(),
                tenantField, lookupFieldName, lookupFieldType,
                ops, immutableFields, fieldsByName,
                creatableFields, updatableFields, visibleFields, exportableFields, pkField,
                immutableRelations, relationsByName,
                manyToOneDescriptors, manyToOneByFkName,
                ownerFieldName, ownerJavaField, ownerFieldIsRelation, ownerAdminRoles,
                currentUserFieldName, currentUserJavaField, currentUserFieldIsRelation,
                currentUserTargetEntity, currentUserTargetIdType,
                webhookEnabled, webhookEvents, maxRecords
        );
    }

    private static Class<?> extractIdType(Class<?> entityClass) {
        for (Field field : collectInstanceFields(entityClass)) {
            if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                return field.getType();
            }
        }
        return Long.class; // fallback
    }

    private static RelationMetadata buildRelationMetadata(Field field) {
        RelationMetadata.RelationType type = null;
        String mappedBy = "";
        Class<?> target = null;

        if (field.isAnnotationPresent(ManyToOne.class)) {
            type = RelationMetadata.RelationType.MANY_TO_ONE;
            target = field.getType();
        } else if (field.isAnnotationPresent(OneToOne.class)) {
            type = RelationMetadata.RelationType.ONE_TO_ONE;
            mappedBy = field.getAnnotation(OneToOne.class).mappedBy();
            target = field.getType();
        } else if (field.isAnnotationPresent(OneToMany.class)) {
            type = RelationMetadata.RelationType.ONE_TO_MANY;
            mappedBy = field.getAnnotation(OneToMany.class).mappedBy();
            target = resolveCollectionType(field);
        } else if (field.isAnnotationPresent(ManyToMany.class)) {
            type = RelationMetadata.RelationType.MANY_TO_MANY;
            mappedBy = field.getAnnotation(ManyToMany.class).mappedBy();
            target = resolveCollectionType(field);
        }

        if (type == null) return null;
        return new RelationMetadata(field.getName(), type, target, mappedBy, field);
    }

    private static Class<?> resolveCollectionType(Field field) {
        Type generic = field.getGenericType();
        if (generic instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0 && args[0] instanceof Class<?> c) {
                return c;
            }
        }
        return Object.class;
    }

    private static FieldMetadata buildFieldMetadata(Field field) {
        boolean isPk = field.isAnnotationPresent(Id.class);
        boolean isAutoGenerated = isPk && field.isAnnotationPresent(GeneratedValue.class);
        boolean isHidden = field.isAnnotationPresent(FlashHidden.class);
        boolean isReadOnly = field.isAnnotationPresent(FlashReadOnly.class)
                || field.isAnnotationPresent(FlashCounter.class);
        boolean isWriteOnly = field.isAnnotationPresent(FlashWriteOnly.class);
        boolean isPassword = isWriteOnly && field.getAnnotation(FlashWriteOnly.class).password();
        boolean isExportExcluded = field.isAnnotationPresent(FlashExportExclude.class);

        Column col = field.getAnnotation(Column.class);
        boolean nullable = col == null || col.nullable();
        boolean insertable = col == null || col.insertable();
        boolean updatable = col == null || col.updatable();
        Integer maxLength = (col != null && col.length() != 255) ? col.length() : null;

        return new FieldMetadata(
                field.getName(), field.getType(),
                isPk, isAutoGenerated, isHidden, isReadOnly, isWriteOnly, isPassword, isExportExcluded,
                nullable, insertable, updatable, maxLength, field
        );
    }

    private static Set<CrudOperation> resolveOperations(FlashEntity annotation) {
        if (annotation.readonly()) {
            return EnumSet.of(CrudOperation.LIST, CrudOperation.READ);
        }
        if (annotation.only().length > 0) {
            EnumSet<CrudOperation> ops = EnumSet.noneOf(CrudOperation.class);
            for (String op : annotation.only()) {
                ops.add(CrudOperation.valueOf(op.toUpperCase()));
            }
            return Collections.unmodifiableSet(ops);
        }
        if (annotation.exclude().length > 0) {
            EnumSet<CrudOperation> ops = EnumSet.allOf(CrudOperation.class);
            for (String op : annotation.exclude()) {
                ops.remove(CrudOperation.valueOf(op.toUpperCase()));
            }
            return Collections.unmodifiableSet(ops);
        }
        return EnumSet.allOf(CrudOperation.class);
    }

    private static List<Field> collectInstanceFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && !Modifier.isTransient(f.getModifiers())) {
                    fields.add(f);
                }
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    public static List<CounterDescriptor> collectCounterDescriptors(List<EntityMetadata> entities) {
        List<CounterDescriptor> descriptors = new ArrayList<>();
        Map<Class<?>, EntityMetadata> byClass = new HashMap<>();
        for (EntityMetadata meta : entities) {
            byClass.put(meta.entityClass(), meta);
        }

        for (EntityMetadata meta : entities) {
            for (Field field : collectInstanceFields(meta.entityClass())) {
                FlashCounter counter = field.getAnnotation(FlashCounter.class);
                if (counter == null) continue;

                Class<?> sourceEntity = counter.source();
                String relationName = counter.relation();

                EntityMetadata sourceMeta = byClass.get(sourceEntity);
                if (sourceMeta == null) {
                    throw new IllegalStateException(
                            "@FlashCounter on " + meta.entityClass().getName() + "." + field.getName()
                                    + " references source " + sourceEntity.getName()
                                    + " which is not a @FlashEntity");
                }

                RelationMetadata sourceRelation = sourceMeta.relationsByName().get(relationName);
                if (sourceRelation == null) {
                    throw new IllegalStateException(
                            "@FlashCounter on " + meta.entityClass().getName() + "." + field.getName()
                                    + " references relation '" + relationName + "' on "
                                    + sourceEntity.getSimpleName() + " which does not exist");
                }

                if (sourceRelation.type() != RelationMetadata.RelationType.MANY_TO_ONE) {
                    throw new IllegalStateException(
                            "@FlashCounter relation '" + relationName + "' on "
                                    + sourceEntity.getSimpleName() + " must be @ManyToOne");
                }

                Field relationJavaField = sourceRelation.javaField();
                relationJavaField.setAccessible(true);

                descriptors.add(new CounterDescriptor(
                        meta.entityClass(), field.getName(),
                        sourceEntity, relationName, relationJavaField));
            }
        }
        return descriptors;
    }

    private static String pluralize(String name) {
        String lower = name.substring(0, 1).toLowerCase() + name.substring(1);
        if (lower.endsWith("y") && !lower.endsWith("ey") && !lower.endsWith("ay") && !lower.endsWith("oy")) {
            return lower.substring(0, lower.length() - 1) + "ies";
        }
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")
                || lower.endsWith("sh") || lower.endsWith("ch")) {
            return lower + "es";
        }
        return lower + "s";
    }
}
