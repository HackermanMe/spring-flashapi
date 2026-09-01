package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated Use {@code @FlashEntity(tenantField = "tenantId")} instead.
 * Kept for backward compatibility. Will be removed in the next major version.
 */
@Deprecated
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashMultiTenant {

    /**
     * Java field name used to store the tenant identifier.
     * Must be a String field on the entity.
     */
    String field() default "tenantId";
}
