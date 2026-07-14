package io.github.hackermanme.flashapi.tenant;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Default TenantResolver that reads from the X-Tenant-Id HTTP header.
 */
public final class HeaderTenantResolver implements TenantResolver {

    private final String headerName;

    public HeaderTenantResolver(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String value = request.getHeader(headerName);
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }
}
