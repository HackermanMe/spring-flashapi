package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables multi-tenancy for this entity. All CRUD operations are automatically
 * scoped to the current tenant — no cross-tenant data leakage.
 *
 * The specified field must exist on the entity and be of type String.
 * FlashAPI auto-sets it on CREATE and filters all queries by it.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashMultiTenant {

    /**
     * Java field name used to store the tenant identifier.
     * Must be a String field on the entity.
     */
    String field() default "tenantId";
}
