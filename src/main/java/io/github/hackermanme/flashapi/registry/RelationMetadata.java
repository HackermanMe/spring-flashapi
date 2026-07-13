package io.github.hackermanme.flashapi.registry;

import java.lang.reflect.Field;

public record RelationMetadata(
        String name,
        RelationType type,
        Class<?> targetEntity,
        String mappedBy,
        Field javaField
) {
    public enum RelationType {
        MANY_TO_ONE, ONE_TO_MANY, ONE_TO_ONE, MANY_TO_MANY
    }
}
