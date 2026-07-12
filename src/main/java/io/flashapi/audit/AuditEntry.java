package io.flashapi.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "flash_audit_log", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entityType,entityId"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp")
})
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String entityType;

    @Column(nullable = false, length = 255)
    private String entityId;

    @Column(nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(length = 100)
    private String field;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(nullable = false, length = 100)
    private String performedBy;

    @Column(nullable = false)
    private Instant timestamp;

    protected AuditEntry() {}

    private AuditEntry(Builder builder) {
        this.entityType = builder.entityType;
        this.entityId = builder.entityId;
        this.action = builder.action;
        this.field = builder.field;
        this.oldValue = builder.oldValue;
        this.newValue = builder.newValue;
        this.performedBy = builder.performedBy;
        this.timestamp = builder.timestamp;
    }

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public AuditAction getAction() { return action; }
    public String getField() { return field; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public String getPerformedBy() { return performedBy; }
    public Instant getTimestamp() { return timestamp; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String entityType;
        private String entityId;
        private AuditAction action;
        private String field;
        private String oldValue;
        private String newValue;
        private String performedBy = "anonymous";
        private Instant timestamp = Instant.now();

        public Builder entityType(String v) { this.entityType = v; return this; }
        public Builder entityId(String v) { this.entityId = v; return this; }
        public Builder action(AuditAction v) { this.action = v; return this; }
        public Builder field(String v) { this.field = v; return this; }
        public Builder oldValue(String v) { this.oldValue = v; return this; }
        public Builder newValue(String v) { this.newValue = v; return this; }
        public Builder performedBy(String v) { this.performedBy = v; return this; }
        public Builder timestamp(Instant v) { this.timestamp = v; return this; }
        public AuditEntry build() { return new AuditEntry(this); }
    }
}
