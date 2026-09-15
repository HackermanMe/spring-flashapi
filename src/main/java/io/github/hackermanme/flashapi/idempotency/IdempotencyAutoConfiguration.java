package io.github.hackermanme.flashapi.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
@ConditionalOnProperty(prefix = "flashapi.idempotency", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(IdempotencyProperties.class)
@EnableScheduling
public class IdempotencyAutoConfiguration implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAutoConfiguration.class);

    private final IdempotencyService idempotencyService;

    public IdempotencyAutoConfiguration(IdempotencyService idempotencyService, IdempotencyProperties properties) {
        this.idempotencyService = idempotencyService;
        log.info("FlashAPI Idempotency enabled (TTL: {}h, cleanup: every {}h)",
            properties.getTtlHours(), properties.getCleanupIntervalHours());
    }

    @Bean
    public IdempotencyService idempotencyService(IdempotencyRepository repository,
                                                 ObjectMapper objectMapper,
                                                 IdempotencyProperties properties) {
        return new IdempotencyService(repository, objectMapper, properties);
    }

    @Bean
    public IdempotencyInterceptor idempotencyInterceptor(IdempotencyService idempotencyService) {
        return new IdempotencyInterceptor(idempotencyService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(idempotencyInterceptor(idempotencyService));
    }

    @Scheduled(fixedRateString = "${flashapi.idempotency.cleanup-interval-hours:6}", timeUnit = TimeUnit.HOURS)
    public void cleanupExpiredKeys() {
        idempotencyService.cleanupExpired();
    }
}
