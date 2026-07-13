package io.github.hackermanme.flashapi.relation;

import io.github.hackermanme.flashapi.registry.EntityMetadata;
import io.github.hackermanme.flashapi.registry.FieldMetadata;
import io.github.hackermanme.flashapi.registry.RelationMetadata;
import jakarta.persistence.Id;

import java.lang.reflect.Field;
import java.util.*;

public final class RelationExpander {

    private final int maxDepth;

    public RelationExpander(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public Map<String, Object> serialize(EntityMetadata meta, Object entity, Set<String> expandFields) {
        return serializeWithDepth(meta, entity, expandFields, 0);
    }

    private Map<String, Object> serializeWithDepth(EntityMetadata meta, Object entity,
                                                    Set<String> expandFields, int depth) {
        Map<String, Object> map = new LinkedHashMap<>();

        for (FieldMetadata field : meta.visibleFields()) {
            try {
                map.put(field.name(), field.javaField().get(entity));
            } catch (IllegalAccessException e) {
                map.put(field.name(), null);
            }
        }

        if (expandFields == null || expandFields.isEmpty() || depth >= maxDepth) {
            return map;
        }

        for (String expand : expandFields) {
            RelationMetadata rel = meta.relationsByName().get(expand);
            if (rel == null) continue;

            try {
                Object related = rel.javaField().get(entity);
                if (related == null) {
                    map.put(expand, null);
                } else if (related instanceof Collection<?> collection) {
                    map.put(expand, serializeCollection(rel.targetEntity(), collection));
                } else {
                    map.put(expand, serializeRelatedEntity(related));
                }
            } catch (IllegalAccessException e) {
                map.put(expand, null);
            }
        }

        return map;
    }

    private List<Map<String, Object>> serializeCollection(Class<?> targetClass, Collection<?> collection) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object item : collection) {
            list.add(serializeRelatedEntity(item));
        }
        return list;
    }

    private Map<String, Object> serializeRelatedEntity(Object entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field field : collectVisibleFields(entity.getClass())) {
            try {
                field.setAccessible(true);
                if (isRelationField(field)) continue;
                map.put(field.getName(), field.get(entity));
            } catch (IllegalAccessException e) {
                map.put(field.getName(), null);
            }
        }
        return map;
    }

    private List<Field> collectVisibleFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                if (java.lang.reflect.Modifier.isTransient(f.getModifiers())) continue;
                fields.add(f);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean isRelationField(Field field) {
        return field.isAnnotationPresent(jakarta.persistence.ManyToOne.class)
                || field.isAnnotationPresent(jakarta.persistence.OneToMany.class)
                || field.isAnnotationPresent(jakarta.persistence.OneToOne.class)
                || field.isAnnotationPresent(jakarta.persistence.ManyToMany.class);
    }
}
