package io.github.hackermanme.flashapi.registry;

import java.lang.reflect.Field;

/**
 * Describes a @ManyToOne relation for create/update resolution.
 * Enables accepting FK IDs in request bodies (e.g., "categoryId": 1)
 * and resolving them to managed entity references via EntityManager.getReference().
 *
 * @param fkFieldName  The FK field name in request body (e.g., "categoryId")
 * @param relationField The Java field on the entity (e.g., Product.category)
 * @param targetEntity  The target entity class (e.g., Category.class)
 * @param targetIdType  The ID type of the target entity (e.g., Long.class)
 */
public record ManyToOneDescriptor(
        String fkFieldName,
        Field relationField,
        Class<?> targetEntity,
        Class<?> targetIdType
) {}
