package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as write-only: accepted in POST/PUT requests, never returned in responses.
 * When password=true, the value is automatically hashed with BCrypt before persistence.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashWriteOnly {

    boolean password() default false;
}
