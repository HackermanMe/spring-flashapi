package io.github.hackermanme.flashapi.registry;

import io.github.hackermanme.flashapi.annotation.FlashAudit;
import io.github.hackermanme.flashapi.annotation.FlashEntity;
import io.github.hackermanme.flashapi.annotation.FlashHidden;
import io.github.hackermanme.flashapi.annotation.FlashMultiTenant;
import io.github.hackermanme.flashapi.annotation.FlashReadOnly;
import io.github.hackermanme.flashapi.annotation.FlashWriteOnly;
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
                    Class<?> clazz = Class.forName(bd.getBeanClassName());
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

        String path = annotation.path().isEmpty() ? pluralize(clazz.getSimpleName()) : annotation.path();
        Set<CrudOperation> ops = resolveOperations(annotation);
        boolean auditEnabled = auditAnnotation == null || auditAnnotation.enabled();
        boolean auditTrackFields = auditAnnotation != null && auditAnnotation.trackFields();
        String tenantField = multiTenantAnnotation != null ? multiTenantAnnotation.field() : null;

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

        return new EntityMetadata(
                clazz, clazz.getSimpleName(), path,
                pkField.name(), pkField.type(),
                annotation.softDelete(), auditEnabled, auditTrackFields,
                annotation.cache(), annotation.cacheTtl(),
                annotation.rateLimit(), annotation.rateLimitRequests(), annotation.rateLimitWindow(),
                tenantField,
                ops, immutableFields, fieldsByName,
                creatableFields, updatableFields, visibleFields, pkField,
                immutableRelations, relationsByName
        );
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
        boolean isReadOnly = field.isAnnotationPresent(FlashReadOnly.class);
        boolean isWriteOnly = field.isAnnotationPresent(FlashWriteOnly.class);

        Column col = field.getAnnotation(Column.class);
        boolean nullable = col == null || col.nullable();
        boolean insertable = col == null || col.insertable();
        boolean updatable = col == null || col.updatable();
        Integer maxLength = (col != null && col.length() != 255) ? col.length() : null;

        return new FieldMetadata(
                field.getName(), field.getType(),
                isPk, isAutoGenerated, isHidden, isReadOnly, isWriteOnly,
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
