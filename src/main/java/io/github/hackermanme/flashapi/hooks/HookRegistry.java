package io.github.hackermanme.flashapi.hooks;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that scans Spring beans for lifecycle hook methods.
 * Hooks are loaded once at startup and indexed by type for fast lookup.
 */
public class HookRegistry {

    private static final Logger logger = LoggerFactory.getLogger(HookRegistry.class);

    private final Map<Class<? extends Annotation>, List<EntityHook>> hooks = new HashMap<>();
    private final ApplicationContext applicationContext;

    public HookRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    private void scanHooks() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            // Skip HookRegistry itself to avoid circular reference
            if (beanName.equals("hookRegistry")) continue;

            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                // Skip beans that can't be instantiated
                continue;
            }

            Class<?> beanClass = bean.getClass();
            for (Method method : beanClass.getDeclaredMethods()) {
                registerIfAnnotated(bean, method, FlashBeforeCreate.class);
                registerIfAnnotated(bean, method, FlashAfterCreate.class);
                registerIfAnnotated(bean, method, FlashBeforeUpdate.class);
                registerIfAnnotated(bean, method, FlashAfterUpdate.class);
                registerIfAnnotated(bean, method, FlashBeforeDelete.class);
                registerIfAnnotated(bean, method, FlashAfterDelete.class);
            }
        }

        logger.info("Lifecycle hooks registered: BeforeCreate={}, AfterCreate={}, BeforeUpdate={}, AfterUpdate={}, BeforeDelete={}, AfterDelete={}",
                hooks.getOrDefault(FlashBeforeCreate.class, List.of()).size(),
                hooks.getOrDefault(FlashAfterCreate.class, List.of()).size(),
                hooks.getOrDefault(FlashBeforeUpdate.class, List.of()).size(),
                hooks.getOrDefault(FlashAfterUpdate.class, List.of()).size(),
                hooks.getOrDefault(FlashBeforeDelete.class, List.of()).size(),
                hooks.getOrDefault(FlashAfterDelete.class, List.of()).size()
        );
    }

    private void registerIfAnnotated(Object bean, Method method, Class<? extends Annotation> annotationClass) {
        if (!method.isAnnotationPresent(annotationClass)) {
            return;
        }

        // Validate signature: (Object entity, HttpServletRequest request)
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length != 2 || paramTypes[0] != Object.class || paramTypes[1] != HttpServletRequest.class) {
            logger.warn("Hook method {} in {} has invalid signature. Expected: void method(Object, HttpServletRequest)",
                    method.getName(), bean.getClass().getName());
            return;
        }

        method.setAccessible(true);
        EntityHook hook = (entity, request) -> {
            try {
                method.invoke(bean, entity, request);
            } catch (Exception e) {
                throw new HookExecutionException("Hook execution failed: " + method.getName(), e);
            }
        };

        hooks.computeIfAbsent(annotationClass, k -> new ArrayList<>()).add(hook);
        logger.info("Registered {} hook: {}.{}", annotationClass.getSimpleName(), bean.getClass().getSimpleName(), method.getName());
    }

    /**
     * Invoke all hooks of the given type.
     */
    public void invokeHooks(Class<? extends Annotation> hookType, Object entity, HttpServletRequest request) {
        List<EntityHook> hookList = hooks.get(hookType);
        if (hookList == null || hookList.isEmpty()) {
            return;
        }

        for (EntityHook hook : hookList) {
            try {
                hook.execute(entity, request);
            } catch (Exception e) {
                logger.error("Hook execution failed for {}: {}", hookType.getSimpleName(), e.getMessage(), e);
                throw new HookExecutionException("Hook execution failed for " + hookType.getSimpleName(), e);
            }
        }
    }
}
