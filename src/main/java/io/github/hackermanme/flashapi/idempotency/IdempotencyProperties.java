package io.github.hackermanme.flashapi.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flashapi.idempotency")
public class IdempotencyProperties {

    private boolean enabled = false;
    private int ttlHours = 24;
    private int cleanupIntervalHours = 6;
    private int maxBodySize = 10240;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(int ttlHours) {
        this.ttlHours = ttlHours;
    }

    public int getCleanupIntervalHours() {
        return cleanupIntervalHours;
    }

    public void setCleanupIntervalHours(int cleanupIntervalHours) {
        this.cleanupIntervalHours = cleanupIntervalHours;
    }

    public int getMaxBodySize() {
        return maxBodySize;
    }

    public void setMaxBodySize(int maxBodySize) {
        this.maxBodySize = maxBodySize;
    }
}
