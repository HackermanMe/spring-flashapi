package io.flashapi.controller;

import io.flashapi.registry.CrudOperation;
import io.flashapi.registry.EntityMetadata;
import io.flashapi.service.FlashCrudOperations;
import io.flashapi.service.GenericCrudService;
import io.flashapi.service.ServiceResolver;
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
    private final String basePath;
    private final Set<String> existingMappings;

    public FlashRouteRegistrar(RequestMappingHandlerMapping handlerMapping,
                               GenericCrudService crudService,
                               ServiceResolver serviceResolver,
                               String basePath) {
        this.handlerMapping = handlerMapping;
        this.crudService = crudService;
        this.serviceResolver = serviceResolver;
        this.basePath = normalizePath(basePath);
        this.existingMappings = snapshotExistingMappings();
    }

    public void registerAll(List<EntityMetadata> entities) {
        for (EntityMetadata meta : entities) {
            FlashCrudOperations<Object, Object> custom = serviceResolver.resolve(meta);
            FlashController controller = new FlashController(meta, crudService, custom);
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

        var handler = new FlashEndpointHandler(controller, handlerMethodName);
        var handleMethod = FlashEndpointHandler.class.getMethod("handle",
                jakarta.servlet.http.HttpServletRequest.class,
                java.util.Map.class, java.util.Map.class);

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
