package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Associates a custom service bean with a specific @FlashEntity class.
 * Use this when the service name does not follow the convention ({EntityName}Service).
 * FlashAPI will delegate CRUD operations to this service instead of the built-in GenericCrudService.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashService {
    Class<?> value();
}
