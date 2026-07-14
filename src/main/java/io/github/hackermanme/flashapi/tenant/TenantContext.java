package io.github.hackermanme.flashapi.tenant;

/**
 * Thread-local holder for the current tenant ID.
 * Set by TenantResolver at the start of each request, cleared after.
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
