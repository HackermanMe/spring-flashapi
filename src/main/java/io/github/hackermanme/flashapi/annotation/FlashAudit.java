package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated Use {@code @FlashEntity(audit = true, trackFields = true)} instead.
 * Kept for backward compatibility. Will be removed in the next major version.
 */
@Deprecated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashAudit {

    boolean enabled() default true;

    /**
     * When true, logs individual field changes (old_value → new_value).
     * When false, only logs the action (CREATE/UPDATE/DELETE).
     */
    boolean trackFields() default false;
}
