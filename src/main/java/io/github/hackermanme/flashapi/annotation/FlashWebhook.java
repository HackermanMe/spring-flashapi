package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated Use {@code @FlashEntity(webhook = true)} instead.
 * Kept for backward compatibility. Will be removed in the next major version.
 */
@Deprecated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashWebhook {

    /**
     * Events to fire webhooks for. Default: all write operations.
     * Values: "CREATE", "UPDATE", "DELETE"
     */
    String[] events() default {"CREATE", "UPDATE", "DELETE"};
}
