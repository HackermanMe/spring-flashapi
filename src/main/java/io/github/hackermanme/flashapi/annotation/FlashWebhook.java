package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables webhook notifications for this entity.
 * FlashAPI sends an HTTP POST to configured URLs on create/update/delete events.
 * Webhooks are fired asynchronously — they never block the API response.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashWebhook {

    /**
     * Events to fire webhooks for. Default: all write operations.
     * Values: "CREATE", "UPDATE", "DELETE"
     */
    String[] events() default {"CREATE", "UPDATE", "DELETE"};
}
