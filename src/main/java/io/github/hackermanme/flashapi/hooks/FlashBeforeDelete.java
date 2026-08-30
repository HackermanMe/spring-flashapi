package io.github.hackermanme.flashapi.hooks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be invoked before an entity is deleted (before remove).
 * Method signature must be: void methodName(Object entity, HttpServletRequest request) throws Exception
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashBeforeDelete {
}
