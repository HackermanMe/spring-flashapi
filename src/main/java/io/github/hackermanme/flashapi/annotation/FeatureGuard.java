package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated Use {@code @FlashEntity(maxRecords = 100)} instead.
 * Kept for backward compatibility. Will be removed in the next major version.
 */
@Deprecated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureGuard {

    /**
     * Maximum number of records allowed for this entity.
     * Set to -1 (default) for no static limit — useful when only a PlanLimitResolver is used.
     */
    long max() default -1;
}
