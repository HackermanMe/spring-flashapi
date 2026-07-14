package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts access to auto-generated endpoints for this entity.
 * Requires Spring Security on the classpath — fails fast at startup if absent.
 *
 * Resolution priority (first non-empty wins):
 * 1. Specific operation: create(), update(), delete(), read(), list()
 * 2. Group: write() → applies to create/update/delete, read() → applies to list/read
 * 3. roles() → applies to all operations
 * 4. If annotation present but all empty → "authenticated" (any logged-in user)
 *
 * Special values:
 * - "permitAll" — no restriction (use to open specific operations on a locked entity)
 * - "authenticated" — any authenticated user, no role check
 * - Any other value — treated as a role name (checked via GrantedAuthority)
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashSecured {

    /**
     * Roles required for ALL operations. Overridden by more specific attributes.
     */
    String[] roles() default {};

    /**
     * Roles required for LIST and single READ (GET) operations.
     * Overrides roles() for read operations.
     */
    String[] read() default {};

    /**
     * Roles required for CREATE, UPDATE, and DELETE operations.
     * Overrides roles() for write operations.
     */
    String[] write() default {};

    /**
     * Roles required for CREATE (POST). Overrides write() and roles().
     */
    String[] create() default {};

    /**
     * Roles required for UPDATE (PUT). Overrides write() and roles().
     */
    String[] update() default {};

    /**
     * Roles required for DELETE. Overrides write() and roles().
     */
    String[] delete() default {};

    /**
     * Roles required for LIST (GET collection). Overrides read() and roles().
     */
    String[] list() default {};
}
