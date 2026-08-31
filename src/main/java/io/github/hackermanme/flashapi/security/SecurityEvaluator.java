package io.github.hackermanme.flashapi.security;

import io.github.hackermanme.flashapi.annotation.FlashSecured;
import io.github.hackermanme.flashapi.registry.CrudOperation;
import io.github.hackermanme.flashapi.registry.EntityMetadata;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;

/**
 * Evaluates @FlashSecured authorization rules against the current request.
 * Stateless — one instance shared across all entities.
 */
public class SecurityEvaluator {

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

    /**
     * Checks if the current user owns the given entity.
     * Called after the entity is loaded, only for UPDATE/DELETE when ownerField is set.
     */
    public SecurityResult evaluateOwnership(EntityMetadata metadata, Object entity) {
        if (!metadata.hasOwnerField()) {
            return SecurityResult.ALLOWED;
        }

        Object principal = getCurrentPrincipal();
        if (principal == null) {
            return SecurityResult.UNAUTHENTICATED;
        }

        // Admin bypass
        String[] adminRoles = metadata.ownerAdminRoles();
        if (adminRoles.length > 0) {
            Collection<String> authorities = getCurrentAuthorities();
            for (String role : adminRoles) {
                if (authorities.contains(role) || authorities.contains("ROLE_" + role)) {
                    return SecurityResult.ALLOWED;
                }
            }
        }

        String principalName = getCurrentPrincipalName();
        if (principalName == null) {
            return SecurityResult.FORBIDDEN;
        }

        try {
            Field ownerField = metadata.ownerJavaField();
            Object ownerValue = ownerField.get(entity);

            if (ownerValue == null) {
                return SecurityResult.FORBIDDEN;
            }

            String ownerIdentifier;
            if (metadata.ownerFieldIsRelation()) {
                // Extract the ID from the related entity (e.g., author.id)
                Field idField = findIdField(ownerValue.getClass());
                if (idField == null) {
                    return SecurityResult.FORBIDDEN;
                }
                idField.setAccessible(true);
                Object ownerId = idField.get(ownerValue);
                ownerIdentifier = ownerId != null ? ownerId.toString() : null;
            } else {
                ownerIdentifier = ownerValue.toString();
            }

            if (principalName.equals(ownerIdentifier)) {
                return SecurityResult.ALLOWED;
            }
        } catch (IllegalAccessException e) {
            // Fail closed
        }

        return SecurityResult.FORBIDDEN;
    }

    protected String getCurrentPrincipalName() {
        Object principal = getCurrentPrincipal();
        return principal != null ? principal.toString() : null;
    }

    protected Object getCurrentPrincipal() {
        return null;
    }

    protected Collection<String> getCurrentAuthorities() {
        return Set.of();
    }

    private static Field findIdField(Class<?> clazz) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (f.isAnnotationPresent(jakarta.persistence.Id.class)) {
                    return f;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
