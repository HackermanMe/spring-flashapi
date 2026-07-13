package io.github.hackermanme.flashapi.autoconfigure;

import io.github.hackermanme.flashapi.annotation.EnableFlashApi;
import io.github.hackermanme.flashapi.audit.AuditService;
import io.github.hackermanme.flashapi.bulk.BulkHandler;
import io.github.hackermanme.flashapi.cache.FlashCacheManager;
import io.github.hackermanme.flashapi.controller.FlashRouteRegistrar;
import io.github.hackermanme.flashapi.exception.FlashExceptionHandler;
import io.github.hackermanme.flashapi.export.ExportHandler;
import io.github.hackermanme.flashapi.ratelimit.FlashRateLimiter;
import io.github.hackermanme.flashapi.registry.EntityMetadata;
import io.github.hackermanme.flashapi.registry.EntityScanner;
import io.github.hackermanme.flashapi.relation.RelationExpander;
import io.github.hackermanme.flashapi.service.GenericCrudService;
import io.github.hackermanme.flashapi.service.ServiceResolver;
import io.github.hackermanme.flashapi.softdelete.SoftDeleteHandler;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
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

    @Bean
    @ConditionalOnMissingBean
    public ExportHandler flashExportHandler(GenericCrudService crudService) {
        return new ExportHandler(crudService,
                properties.getExport().getMaxRows(),
                properties.getExport().getReportsPath());
    }

    @Bean
    @ConditionalOnMissingBean
    public BulkHandler flashBulkHandler(GenericCrudService crudService) {
        return new BulkHandler(crudService, properties.getBulk().getMaxItems());
    }

    @Bean
    @ConditionalOnMissingBean
    public RelationExpander flashRelationExpander() {
        return new RelationExpander(properties.getRelations().getMaxDepth());
    }

    @Bean
    @ConditionalOnMissingBean
    public FlashCacheManager flashCacheManager() {
        CacheManager cacheManager = null;
        try {
            cacheManager = context.getBean(CacheManager.class);
        } catch (Exception ignored) {
        }
        return new FlashCacheManager(cacheManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public FlashRateLimiter flashRateLimiter() {
        return new FlashRateLimiter();
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
        ExportHandler exportHandler = context.getBean(ExportHandler.class);
        BulkHandler bulkHandler = context.getBean(BulkHandler.class);
        RelationExpander relationExpander = context.getBean(RelationExpander.class);
        FlashCacheManager cacheManager = context.getBean(FlashCacheManager.class);
        FlashRateLimiter rateLimiter = context.getBean(FlashRateLimiter.class);
        FlashRouteRegistrar registrar = new FlashRouteRegistrar(
                handlerMapping, crudService, serviceResolver, exportHandler, bulkHandler,
                relationExpander, cacheManager, rateLimiter, properties.getBasePath());
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
