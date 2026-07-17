package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Excludes a field from export (CSV, Excel, PDF) while keeping it visible in API responses.
 * Use for fields that are relevant in JSON but inappropriate in exported files
 * (e.g., password hashes, internal tokens, large text blobs).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashExportExclude {
}
