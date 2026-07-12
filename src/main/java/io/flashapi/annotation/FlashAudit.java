package io.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Controls audit behavior for a @FlashEntity.
 * By default, audit is enabled for all entities. Use this to disable or configure.
 */
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
