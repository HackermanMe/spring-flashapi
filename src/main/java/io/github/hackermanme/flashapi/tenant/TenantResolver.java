package io.github.hackermanme.flashapi.tenant;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the current tenant from an HTTP request.
 * Implement this interface to customize tenant resolution (e.g., from JWT claims, subdomain, etc.).
 * Default: reads X-Tenant-Id header.
 */
public interface TenantResolver {

    /**
     * Extract the tenant identifier from the request.
     * Return null if no tenant context is available.
     */
    String resolve(HttpServletRequest request);
}
