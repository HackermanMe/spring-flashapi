package io.github.hackermanme.flashapi.security;

import io.github.hackermanme.flashapi.annotation.FlashSecured;
import io.github.hackermanme.flashapi.registry.CrudOperation;
import io.github.hackermanme.flashapi.registry.EntityMetadata;

import java.util.Collection;
import java.util.Set;

/**
 * Evaluates @FlashSecured authorization rules against the current request.
 * Stateless — one instance shared across all entities.
 */
public final class SecurityEvaluator {

    private static final String PERMIT_ALL = "permitAll";
    private static final String AUTHENTICATED = "authenticated";

    public SecurityResult evaluate(EntityMetadata metadata, CrudOperation operation) {
        FlashSecured secured = metadata.entityClass().getAnnotation(FlashSecured.class);
        if (secured == null) {
            return SecurityResult.ALLOWED;
        }

        String[] requiredRoles = resolveRoles(secured, operation);

        if (requiredRoles.length == 1 && PERMIT_ALL.equals(requiredRoles[0])) {
            return SecurityResult.ALLOWED;
        }

        Object principal = getCurrentPrincipal();
        if (principal == null) {
            return SecurityResult.UNAUTHENTICATED;
        }

        if (requiredRoles.length == 0 || (requiredRoles.length == 1 && AUTHENTICATED.equals(requiredRoles[0]))) {
            return SecurityResult.ALLOWED;
        }

        Collection<String> userAuthorities = getCurrentAuthorities();
        for (String role : requiredRoles) {
            if (userAuthorities.contains(role) || userAuthorities.contains("ROLE_" + role)) {
                return SecurityResult.ALLOWED;
            }
        }

        return SecurityResult.FORBIDDEN;
    }

    private String[] resolveRoles(FlashSecured secured, CrudOperation operation) {
        String[] specific = switch (operation) {
            case CREATE -> secured.create();
            case UPDATE -> secured.update();
            case DELETE -> secured.delete();
            case READ -> secured.read();
            case LIST -> secured.list();
        };

        if (specific.length > 0) {
            return specific;
        }

        String[] group = switch (operation) {
            case CREATE, UPDATE, DELETE -> secured.write();
            case READ, LIST -> secured.read();
        };

        if (group.length > 0) {
            return group;
        }

        if (secured.roles().length > 0) {
            return secured.roles();
        }

        return new String[]{AUTHENTICATED};
    }

    private Object getCurrentPrincipal() {
        try {
            var ctx = org.springframework.security.core.context.SecurityContextHolder.getContext();
            var auth = ctx.getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return null;
            }
            if (auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
                return null;
            }
            if (auth.getPrincipal() instanceof String s && "anonymousUser".equals(s)) {
                return null;
            }
            return auth.getPrincipal();
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Collection<String> getCurrentAuthorities() {
        try {
            var ctx = org.springframework.security.core.context.SecurityContextHolder.getContext();
            var auth = ctx.getAuthentication();
            if (auth == null) {
                return Set.of();
            }
            return auth.getAuthorities().stream()
                    .map(ga -> ga.getAuthority())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (NoClassDefFoundError e) {
            return Set.of();
        }
    }
}
