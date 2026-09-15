package io.github.hackermanme.flashapi.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class IdempotencyTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public IdempotencyProperties idempotencyProperties() {
            IdempotencyProperties props = new IdempotencyProperties();
            props.setEnabled(true);
            props.setTtlHours(24);
            return props;
        }

        @Bean
        public IdempotencyService idempotencyService(IdempotencyRepository repository,
                                                     ObjectMapper objectMapper,
                                                     IdempotencyProperties properties) {
            return new IdempotencyService(repository, objectMapper, properties);
        }
    }

    @Autowired
    private IdempotencyRepository repository;

    @Autowired
    private IdempotencyService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void testDuplicateRequest_sameKeyReturnsExistingRecord() {
        String key = UUID.randomUUID().toString();
        String path = "/api/invoices";
        String method = "POST";
        String body = "{\"amount\": 100}";

        Optional<IdempotencyRecord> first = service.checkKey(key, path, method, body);
        assertThat(first).isEmpty();

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        service.storeResponse(key, path, method, body, 201, "{\"id\": 123}", headers);

        Optional<IdempotencyRecord> second = service.checkKey(key, path, method, body);
        assertThat(second).isPresent();
        assertThat(second.get().getIdempotencyKey()).isEqualTo(key);
        assertThat(second.get().getResponseStatus()).isEqualTo(201);
        assertThat(second.get().getResponseBody()).isEqualTo("{\"id\": 123}");
    }

    @Test
    void testConflict_differentPath_throwsException() {
        String key = UUID.randomUUID().toString();
        String body = "{\"amount\": 100}";

        service.storeResponse(key, "/api/invoices", "POST", body, 201, "{\"id\": 123}", new HashMap<>());

        assertThatThrownBy(() -> service.checkKey(key, "/api/products", "POST", body))
            .isInstanceOf(IdempotencyConflictException.class)
            .hasMessageContaining("was used for POST /api/invoices but now used for POST /api/products");
    }

    @Test
    void testConflict_differentMethod_throwsException() {
        String key = UUID.randomUUID().toString();
        String path = "/api/invoices/123";
        String body = "{\"amount\": 100}";

        service.storeResponse(key, path, "POST", body, 201, "{\"id\": 123}", new HashMap<>());

        assertThatThrownBy(() -> service.checkKey(key, path, "PUT", body))
            .isInstanceOf(IdempotencyConflictException.class)
            .hasMessageContaining("was used for POST but now used for PUT");
    }

    @Test
    void testConflict_differentBody_throwsException() {
        String key = UUID.randomUUID().toString();
        String path = "/api/invoices";
        String method = "POST";
        String body1 = "{\"amount\": 100}";
        String body2 = "{\"amount\": 200}";

        service.storeResponse(key, path, method, body1, 201, "{\"id\": 123}", new HashMap<>());

        assertThatThrownBy(() -> service.checkKey(key, path, method, body2))
            .isInstanceOf(IdempotencyConflictException.class)
            .hasMessageContaining("reused with different request body");
    }

    @Test
    void testExpiration_cleanupDeletesExpiredKeys() {
        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        Instant now = Instant.now();
        Instant expired = now.minus(1, ChronoUnit.HOURS);
        IdempotencyRecord expiredRecord = new IdempotencyRecord(
            key1, "/api/invoices", "POST", "{}", 201, "{}", "{}", expired, expired
        );
        repository.save(expiredRecord);

        Instant future = now.plus(24, ChronoUnit.HOURS);
        IdempotencyRecord validRecord = new IdempotencyRecord(
            key2, "/api/products", "POST", "{}", 201, "{}", "{}", now, future
        );
        repository.save(validRecord);

        int deleted = service.cleanupExpired();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findByIdempotencyKey(key1)).isEmpty();
        assertThat(repository.findByIdempotencyKey(key2)).isPresent();
    }

    @Test
    void testResponseHeaders_serializedAndParsed() {
        String key = UUID.randomUUID().toString();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "value");

        service.storeResponse(key, "/api/test", "POST", "{}", 200, "{}", headers);

        Optional<IdempotencyRecord> record = repository.findByIdempotencyKey(key);
        assertThat(record).isPresent();

        Map<String, String> parsedHeaders = service.parseResponseHeaders(record.get().getResponseHeaders());
        assertThat(parsedHeaders).hasSize(2);
        assertThat(parsedHeaders.get("Content-Type")).isEqualTo("application/json");
        assertThat(parsedHeaders.get("X-Custom-Header")).isEqualTo("value");
    }
}
