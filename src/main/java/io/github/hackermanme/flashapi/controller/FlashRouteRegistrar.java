package io.github.hackermanme.flashapi.controller;

import io.github.hackermanme.flashapi.bulk.BulkHandler;
import io.github.hackermanme.flashapi.cache.FlashCacheManager;
import io.github.hackermanme.flashapi.export.ExportHandler;
import io.github.hackermanme.flashapi.ratelimit.FlashRateLimiter;
import io.github.hackermanme.flashapi.registry.CrudOperation;
import io.github.hackermanme.flashapi.registry.EntityMetadata;
import io.github.hackermanme.flashapi.relation.RelationExpander;
import io.github.hackermanme.flashapi.security.SecurityEvaluator;
import io.github.hackermanme.flashapi.tenant.TenantResolver;
import io.github.hackermanme.flashapi.service.FlashCrudOperations;
import io.github.hackermanme.flashapi.service.GenericCrudService;
import io.github.hackermanme.flashapi.service.ServiceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registers dynamic REST endpoints for each @FlashEntity.
 * Detects existing user mappings and skips conflicts.
 * Uses Spring MVC's programmatic route registration.
 */
public final class FlashRouteRegistrar {

    private static final Logger log = LoggerFactory.getLogger(FlashRouteRegistrar.class);

    private final RequestMappingHandlerMapping handlerMapping;
    private final GenericCrudService crudService;
    private final ServiceResolver serviceResolver;
    private final ExportHandler exportHandler;
    private final BulkHandler bulkHandler;
    private final RelationExpander relationExpander;
    private final FlashCacheManager cacheManager;
    private final FlashRateLimiter rateLimiter;
    private final SecurityEvaluator securityEvaluator;
    private final TenantResolver tenantResolver;
    private final String basePath;
    private final Set<String> existingMappings;

    public FlashRouteRegistrar(RequestMappingHandlerMapping handlerMapping,
                               GenericCrudService crudService,
                               ServiceResolver serviceResolver,
                               ExportHandler exportHandler,
                               BulkHandler bulkHandler,
                               RelationExpander relationExpander,
                               FlashCacheManager cacheManager,
                               FlashRateLimiter rateLimiter,
                               SecurityEvaluator securityEvaluator,
                               TenantResolver tenantResolver,
                               String basePath) {
        this.handlerMapping = handlerMapping;
        this.crudService = crudService;
        this.serviceResolver = serviceResolver;
        this.exportHandler = exportHandler;
        this.bulkHandler = bulkHandler;
        this.relationExpander = relationExpander;
        this.cacheManager = cacheManager;
        this.rateLimiter = rateLimiter;
        this.securityEvaluator = securityEvaluator;
        this.tenantResolver = tenantResolver;
        this.basePath = normalizePath(basePath);
        this.existingMappings = snapshotExistingMappings();
    }

    public void registerAll(List<EntityMetadata> entities) {
        for (EntityMetadata meta : entities) {
            FlashCrudOperations<Object, Object> custom = serviceResolver.resolve(meta);
            FlashController controller = new FlashController(
                    meta, crudService, custom, exportHandler, bulkHandler, relationExpander, cacheManager);
            registerEntity(meta, controller);
        }
    }

    private void registerEntity(EntityMetadata meta, FlashController controller) {
        String collectionPath = basePath + "/" + meta.path();
        String itemPath = collectionPath + "/{id}";

        try {
            if (meta.isOperationAllowed(CrudOperation.LIST)) {
                register(collectionPath, RequestMethod.GET, controller, "list");
            }
            if (meta.isOperationAllowed(CrudOperation.CREATE)) {
                register(collectionPath, RequestMethod.POST, controller, "create");
            }
            if (meta.isOperationAllowed(CrudOperation.READ)) {
                register(itemPath, RequestMethod.GET, controller, "getById");
            }
            if (meta.isOperationAllowed(CrudOperation.UPDATE)) {
                register(itemPath, RequestMethod.PUT, controller, "update");
            }
            if (meta.isOperationAllowed(CrudOperation.DELETE)) {
                register(itemPath, RequestMethod.DELETE, controller, "delete");
            }
            if (meta.softDelete()) {
                register(itemPath + "/restore", RequestMethod.POST, controller, "restore");
            }
            if (meta.auditEnabled()) {
                register(itemPath + "/history", RequestMethod.GET, controller, "history");
            }
            if (meta.isOperationAllowed(CrudOperation.LIST)) {
                register(collectionPath + "/export", RequestMethod.GET, controller, "export");
            }
            if (meta.isOperationAllowed(CrudOperation.CREATE)) {
                registerBulk(collectionPath + "/bulk", RequestMethod.POST, controller, "bulkCreate");
            }
            if (meta.isOperationAllowed(CrudOperation.UPDATE)) {
                registerBulk(collectionPath + "/bulk", RequestMethod.PUT, controller, "bulkUpdate");
            }
            if (meta.isOperationAllowed(CrudOperation.DELETE)) {
                registerBulk(collectionPath + "/bulk", RequestMethod.DELETE, controller, "bulkDelete");
            }
            log.info("FlashAPI: routes registered for {} at {}", meta.entityName(), collectionPath);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("FlashAPI internal error: missing handler method", e);
        }
    }

    private void register(String path, RequestMethod method,
                          FlashController controller, String handlerMethodName)
            throws NoSuchMethodException {

        String mappingKey = method.name() + " " + path;
        if (isAlreadyMapped(path, method)) {
            log.debug("FlashAPI: skipping {} — already mapped by user", mappingKey);
            return;
        }

        RequestMappingInfo info = RequestMappingInfo
                .paths(path)
                .methods(method)
                .build();

        var handler = new FlashEndpointHandler(controller, handlerMethodName, rateLimiter, securityEvaluator, tenantResolver);
        var handleMethod = FlashEndpointHandler.class.getMethod("handle",
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class,
                java.util.Map.class, java.util.Map.class);

        handlerMapping.registerMapping(info, handler, handleMethod);
    }

    private void registerBulk(String path, RequestMethod method,
                              FlashController controller, String handlerMethodName)
            throws NoSuchMethodException {

        String mappingKey = method.name() + " " + path;
        if (isAlreadyMapped(path, method)) {
            log.debug("FlashAPI: skipping {} — already mapped by user", mappingKey);
            return;
        }

        RequestMappingInfo info = RequestMappingInfo
                .paths(path)
                .methods(method)
                .build();

        var handler = new FlashBulkEndpointHandler(controller, handlerMethodName, rateLimiter, securityEvaluator, tenantResolver);
        var handleMethod = FlashBulkEndpointHandler.class.getMethod("handle",
                jakarta.servlet.http.HttpServletRequest.class, Object.class);

        handlerMapping.registerMapping(info, handler, handleMethod);
    }

    private boolean isAlreadyMapped(String path, RequestMethod method) {
        // Normalize: /api/products/{id} matches /api/products/{anything}
        String normalized = method.name() + " " + path.replaceAll("\\{[^}]+}", "{*}");
        return existingMappings.contains(normalized);
    }

    private Set<String> snapshotExistingMappings() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> {
                    Set<String> patterns = info.getPatternValues();
                    Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                    if (methods.isEmpty()) {
                        return patterns.stream().map(p -> "ALL " + p.replaceAll("\\{[^}]+}", "{*}"));
                    }
                    return methods.stream()
                            .flatMap(m -> patterns.stream().map(p -> m.name() + " " + p.replaceAll("\\{[^}]+}", "{*}")));
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalizePath(String path) {
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return path;
    }
}
