package io.github.hackermanme.flashapi.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final int ttlHours;

    public IdempotencyService(IdempotencyRepository repository,
                             ObjectMapper objectMapper,
                             IdempotencyProperties properties) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.ttlHours = properties.getTtlHours();
    }

    public Optional<IdempotencyRecord> checkKey(String idempotencyKey,
                                                String requestPath,
                                                String requestMethod,
                                                String requestBody) {
        Optional<IdempotencyRecord> existing = repository.findByIdempotencyKey(idempotencyKey);

        if (existing.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyRecord record = existing.get();

        if (!record.getRequestPath().equals(requestPath)) {
            throw new IdempotencyConflictException(
                "Idempotency key '" + idempotencyKey + "' was used for " +
                record.getRequestMethod() + " " + record.getRequestPath() +
                " but now used for " + requestMethod + " " + requestPath
            );
        }

        if (!record.getRequestMethod().equals(requestMethod)) {
            throw new IdempotencyConflictException(
                "Idempotency key '" + idempotencyKey + "' was used for " +
                record.getRequestMethod() + " but now used for " + requestMethod
            );
        }

        if (requestBody != null && !normalizeJson(requestBody).equals(normalizeJson(record.getRequestBody()))) {
            throw new IdempotencyConflictException(
                "Idempotency key '" + idempotencyKey + "' reused with different request body"
            );
        }

        log.debug("Idempotency key '{}' found, replaying response", idempotencyKey);
        return Optional.of(record);
    }

    @Transactional
    public void storeResponse(String idempotencyKey,
                             String requestPath,
                             String requestMethod,
                             String requestBody,
                             int responseStatus,
                             String responseBody,
                             Map<String, String> responseHeaders) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttlHours, ChronoUnit.HOURS);

        String headersJson;
        try {
            headersJson = objectMapper.writeValueAsString(responseHeaders);
        } catch (Exception e) {
            log.warn("Failed to serialize response headers, storing empty map", e);
            headersJson = "{}";
        }

        IdempotencyRecord record = new IdempotencyRecord(
            idempotencyKey,
            requestPath,
            requestMethod,
            requestBody,
            responseStatus,
            responseBody,
            headersJson,
            now,
            expiresAt
        );

        repository.save(record);
        log.debug("Stored idempotency key '{}' with TTL {}h", idempotencyKey, ttlHours);
    }

    public Map<String, String> parseResponseHeaders(String headersJson) {
        if (headersJson == null || headersJson.isEmpty() || headersJson.equals("{}")) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(headersJson,
                objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, String.class));
        } catch (Exception e) {
            log.warn("Failed to parse response headers JSON, returning empty map", e);
            return new HashMap<>();
        }
    }

    @Transactional
    public int cleanupExpired() {
        int deleted = repository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Idempotency cleanup: removed {} expired keys", deleted);
        }
        return deleted;
    }

    private String normalizeJson(String json) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception e) {
            return json.trim();
        }
    }
}
