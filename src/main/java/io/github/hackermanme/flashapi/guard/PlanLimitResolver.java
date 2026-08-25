package io.github.hackermanme.flashapi.guard;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Pluggable interface for resolving record-count limits dynamically (e.g., per tenant/plan).
 * Register as a Spring bean to override the static @FeatureGuard(max) value.
 *
 * <pre>
 * &#64;Bean
 * public PlanLimitResolver planLimits() {
 *     return (entityName, request) -> {
 *         String plan = getCurrentTenantPlan(request);
 *         return switch (plan) {
 *             case "free" -> 100;
 *             case "pro" -> 10_000;
 *             default -> -1; // unlimited
 *         };
 *     };
 * }
 * </pre>
 *
 * @return the maximum allowed records, or -1 for no limit
 */
@FunctionalInterface
public interface PlanLimitResolver {

    long resolveLimit(String entityName, HttpServletRequest request);
}
