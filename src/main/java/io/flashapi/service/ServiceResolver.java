package io.flashapi.service;

import io.flashapi.annotation.FlashService;
import io.flashapi.registry.EntityMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves the CRUD service for a given entity. Checks two sources in order:
 * 1. Any bean annotated with @FlashService(Entity.class)
 * 2. A bean named {entityName}Service implementing FlashCrudOperations
 *
 * Falls back to GenericCrudService if no custom service is found.
 */
public final class ServiceResolver {

    private static final Logger log = LoggerFactory.getLogger(ServiceResolver.class);

    private final ApplicationContext context;
    private final Map<Class<?>, FlashCrudOperations<?, ?>> resolved = new HashMap<>();

    public ServiceResolver(ApplicationContext context) {
        this.context = context;
    }

    @SuppressWarnings("unchecked")
    public FlashCrudOperations<Object, Object> resolve(EntityMetadata meta) {
        FlashCrudOperations<?, ?> cached = resolved.get(meta.entityClass());
        if (cached != null) {
            return (FlashCrudOperations<Object, Object>) cached;
        }

        FlashCrudOperations<?, ?> service = findByAnnotation(meta.entityClass());
        if (service == null) {
            service = findByConvention(meta.entityName());
        }

        if (service != null) {
            resolved.put(meta.entityClass(), service);
            log.info("FlashAPI: custom service found for {} → {}", meta.entityName(),
                    service.getClass().getSimpleName());
            return (FlashCrudOperations<Object, Object>) service;
        }

        return null;
    }

    private FlashCrudOperations<?, ?> findByAnnotation(Class<?> entityClass) {
        Map<String, Object> beans = context.getBeansWithAnnotation(FlashService.class);
        for (Object bean : beans.values()) {
            FlashService ann = bean.getClass().getAnnotation(FlashService.class);
            if (ann == null) {
                Class<?> superclass = bean.getClass().getSuperclass();
                if (superclass != null) {
                    ann = superclass.getAnnotation(FlashService.class);
                }
            }
            if (ann != null && ann.value() == entityClass && bean instanceof FlashCrudOperations<?, ?> ops) {
                return ops;
            }
        }
        return null;
    }

    private FlashCrudOperations<?, ?> findByConvention(String entityName) {
        String beanName = Character.toLowerCase(entityName.charAt(0)) + entityName.substring(1) + "Service";
        if (context.containsBean(beanName)) {
            Object bean = context.getBean(beanName);
            if (bean instanceof FlashCrudOperations<?, ?> ops) {
                return ops;
            }
        }
        return null;
    }
}
