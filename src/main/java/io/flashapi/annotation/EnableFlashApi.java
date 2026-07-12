package io.flashapi.annotation;

import io.flashapi.autoconfigure.FlashAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Activates FlashAPI auto-configuration.
 * Place on your @SpringBootApplication class.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(FlashAutoConfiguration.class)
public @interface EnableFlashApi {

    /**
     * Base packages to scan for @FlashEntity classes.
     * Defaults to the package of the annotated class.
     */
    String[] basePackages() default {};

    /**
     * URL prefix for all generated endpoints.
     */
    String basePath() default "/api";
}
