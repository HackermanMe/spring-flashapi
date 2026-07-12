package io.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a field from the API entirely: never returned in responses, never accepted in requests.
 * Useful for internal system fields (encryption keys, internal tokens, technical state).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashHidden {
}
