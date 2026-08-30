package io.github.hackermanme.flashapi.hooks;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Functional interface for lifecycle hooks.
 * Hooks receive the entity and current HTTP request.
 */
@FunctionalInterface
public interface EntityHook {
    void execute(Object entity, HttpServletRequest request) throws Exception;
}
