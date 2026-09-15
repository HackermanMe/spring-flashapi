package io.github.hackermanme.flashapi.idempotency;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Stores idempotency keys and their associated responses.
 * Prevents duplicate request processing (double-click, network retry, webhook replay).
 */
@Entity
@Table(name = "flash_idempotency_keys", indexes = {
        @Index(name = "idx_key_path_method", columnList = "idempotency_key,request_path,request_method"),
        @Index(name = "idx_expires_at", columnList = "expires_at")
})
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "request_path", nullable = false, length = 500)
    private String requestPath;

    @Column(name = "request_method", nullable = false, length = 10)
    private String requestMethod;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "response_headers", columnDefinition = "TEXT")
    private String responseHeaders;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
        // JPA constructor
    }

    public IdempotencyRecord(String idempotencyKey, String requestPath, String requestMethod,
                             String requestBody, int responseStatus, String responseBody,
                             String responseHeaders, Instant createdAt, Instant expiresAt) {
        this.idempotencyKey = idempotencyKey;
        this.requestPath = requestPath;
        this.requestMethod = requestMethod;
        this.requestBody = requestBody;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.responseHeaders = responseHeaders;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    // Getters

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getResponseHeaders() {
        return responseHeaders;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
