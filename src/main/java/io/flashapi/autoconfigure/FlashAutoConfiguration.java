package io.flashapi.autoconfigure;

import io.flashapi.annotation.EnableFlashApi;
import io.flashapi.audit.AuditService;
import io.flashapi.controller.FlashRouteRegistrar;
import io.flashapi.exception.FlashExceptionHandler;
import io.flashapi.registry.EntityMetadata;
import io.flashapi.registry.EntityScanner;
import io.flashapi.service.GenericCrudService;
import io.flashapi.service.ServiceResolver;
import io.flashapi.softdelete.SoftDeleteHandler;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(FlashProperties.class)
public class FlashAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FlashAutoConfiguration.class);

    private final ApplicationContext context;
    private final FlashProperties properties;
    private final EntityManager entityManager;
    private final RequestMappingHandlerMapping handlerMapping;

    public FlashAutoConfiguration(ApplicationContext context, FlashProperties properties,
                                  EntityManager entityManager,
                                  RequestMappingHandlerMapping handlerMapping) {
        this.context = context;
        this.properties = properties;
        this.entityManager = entityManager;
        this.handlerMapping = handlerMapping;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditService flashAuditService() {
        return new AuditService(entityManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public SoftDeleteHandler flashSoftDeleteHandler() {
        return new SoftDeleteHandler(entityManager, properties.getSoftDelete().getColumnName());
    }

    @Bean
    @ConditionalOnMissingBean
    public GenericCrudService flashCrudService(AuditService auditService, SoftDeleteHandler softDeleteHandler) {
        return new GenericCrudService(entityManager, auditService, softDeleteHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlashExceptionHandler flashExceptionHandler() {
        return new FlashExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public ServiceResolver flashServiceResolver() {
        return new ServiceResolver(context);
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady() {
        String[] basePackages = resolveBasePackages();
        if (basePackages.length == 0) {
            log.warn("FlashAPI: no base packages found. Add @EnableFlashApi(basePackages = \"...\") or place @EnableFlashApi on your main class.");
            return;
        }

        List<EntityMetadata> entities = EntityScanner.scan(basePackages);
        if (entities.isEmpty()) {
            log.info("FlashAPI: no @FlashEntity classes found in packages: {}", (Object) basePackages);
            return;
        }

        GenericCrudService crudService = context.getBean(GenericCrudService.class);
        ServiceResolver serviceResolver = context.getBean(ServiceResolver.class);
        FlashRouteRegistrar registrar = new FlashRouteRegistrar(
                handlerMapping, crudService, serviceResolver, properties.getBasePath());
        registrar.registerAll(entities);

        log.info("FlashAPI: {} entities registered, endpoints available at {}/",
                entities.size(), properties.getBasePath());
    }

    private String[] resolveBasePackages() {
        Map<String, Object> beans = context.getBeansWithAnnotation(EnableFlashApi.class);
        for (Object bean : beans.values()) {
            Class<?> beanClass = bean.getClass();
            // Handle CGLIB proxies
            if (beanClass.getName().contains("$$")) {
                beanClass = beanClass.getSuperclass();
            }
            EnableFlashApi annotation = beanClass.getAnnotation(EnableFlashApi.class);
            if (annotation != null) {
                if (annotation.basePackages().length > 0) {
                    return annotation.basePackages();
                }
                return new String[]{beanClass.getPackageName()};
            }
        }
        return new String[]{};
    }
}
