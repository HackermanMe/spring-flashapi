package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a JPA entity for automatic CRUD endpoint generation.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashEntity {

    /**
     * URL path for this entity. Defaults to pluralized lowercase class name.
     * Example: "products" → generates /api/products
     */
    String path() default "";

    /**
     * HTTP methods to exclude from generation.
     * Values: "CREATE", "READ", "UPDATE", "DELETE", "LIST"
     */
    String[] exclude() default {};

    /**
     * If set, ONLY these methods are generated. Mutually exclusive with exclude().
     */
    String[] only() default {};

    /**
     * Shortcut for only={"LIST","READ"}. Mutually exclusive with only() and exclude().
     */
    boolean readonly() default false;

    /**
     * Enable soft delete. DELETE marks the entity instead of removing it.
     */
    boolean softDelete() default false;

    /**
     * Enable response caching for GET endpoints.
     */
    boolean cache() default false;

    /**
     * Cache TTL in seconds. Only used if cache=true.
     */
    int cacheTtl() default 300;

    /**
     * Field used as the URL path variable for single-resource endpoints.
     * Defaults to the @Id field. Use this to expose a UUID or slug instead of the primary key.
     * The field must exist on the entity and have a unique constraint.
     * Example: lookupField = "trackingId" → GET /api/eleves/{trackingId}
     */
    String lookupField() default "";

    /**
     * Enable rate limiting for this entity's endpoints.
     */
    boolean rateLimit() default false;

    /**
     * Maximum requests per window per client IP. Only used if rateLimit=true.
     */
    int rateLimitRequests() default 100;

    /**
     * Rate limit window in seconds. Only used if rateLimit=true.
     */
    int rateLimitWindow() default 60;
}
