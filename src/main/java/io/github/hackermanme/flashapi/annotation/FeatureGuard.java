package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces a record-count limit on CREATE operations for this entity.
 * When the count of existing records reaches the limit, further creates are rejected with HTTP 403.
 *
 * Resolution priority:
 * 1. PlanLimitResolver bean (if present) — dynamic per-tenant/plan limit
 * 2. max() annotation value — static limit
 * 3. No limit if neither is set (max = -1 and no resolver)
 *
 * Applies to both single create and bulk create operations.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureGuard {

    /**
     * Maximum number of records allowed for this entity.
     * Set to -1 (default) for no static limit — useful when only a PlanLimitResolver is used.
     */
    long max() default -1;
}
