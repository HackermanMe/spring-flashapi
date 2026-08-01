package io.github.hackermanme.flashapi.websocket;

import io.github.hackermanme.flashapi.autoconfigure.FlashProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@ConditionalOnClass(name = "org.springframework.web.socket.config.annotation.EnableWebSocket")
@ConditionalOnProperty(name = "flashapi.websocket.enabled", havingValue = "true", matchIfMissing = true)
@EnableWebSocket
public class FlashWebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(FlashWebSocketConfig.class);

    private final FlashProperties properties;
    private final FlashWebSocketHandler handler = new FlashWebSocketHandler();

    public FlashWebSocketConfig(FlashProperties properties) {
        this.properties = properties;
        log.info("FlashAPI: WebSocket enabled at {}/ws", properties.getBasePath());
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, properties.getBasePath() + "/ws")
                .setAllowedOrigins("*");
    }

    @Bean
    public FlashWebSocketHandler flashWebSocketHandler() {
        return handler;
    }
}
