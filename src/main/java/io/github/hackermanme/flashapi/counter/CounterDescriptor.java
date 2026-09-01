package io.github.hackermanme.flashapi.counter;

import java.lang.reflect.Field;

/**
 * Describes a single counter relationship: when sourceEntity is created/deleted,
 * increment/decrement counterFieldName on targetEntity via relationFieldName.
 */
public record CounterDescriptor(
        Class<?> targetEntity,
        String counterFieldName,
        Class<?> sourceEntity,
        String relationFieldName,
        Field relationJavaField
) {}
