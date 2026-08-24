package io.github.hackermanme.flashapi.openapi;

import java.util.Map;

/**
 * Extension point for customizing the generated OpenAPI specification.
 * Register as a Spring bean to modify the spec before it is served.
 *
 * <pre>
 * &#64;Bean
 * public FlashOpenApiCustomizer myCustomizer() {
 *     return spec -> {
 *         // Modify paths, schemas, info, etc.
 *         Map info = (Map) spec.get("info");
 *         info.put("title", "My Custom Title");
 *     };
 * }
 * </pre>
 */
@FunctionalInterface
public interface FlashOpenApiCustomizer {

    void customize(Map<String, Object> spec);
}
