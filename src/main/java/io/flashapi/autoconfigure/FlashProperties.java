package io.flashapi.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for FlashAPI.
 * Bound to "flashapi.*" in application.yml/properties.
 */
@ConfigurationProperties(prefix = "flashapi")
public class FlashProperties {

    private String basePath = "/api";
    private int defaultPageSize = 20;
    private int maxPageSize = 100;
    private AuditProperties audit = new AuditProperties();
    private SoftDeleteProperties softDelete = new SoftDeleteProperties();

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }

    public int getDefaultPageSize() { return defaultPageSize; }
    public void setDefaultPageSize(int defaultPageSize) { this.defaultPageSize = defaultPageSize; }

    public int getMaxPageSize() { return maxPageSize; }
    public void setMaxPageSize(int maxPageSize) { this.maxPageSize = maxPageSize; }

    public AuditProperties getAudit() { return audit; }
    public void setAudit(AuditProperties audit) { this.audit = audit; }

    public SoftDeleteProperties getSoftDelete() { return softDelete; }
    public void setSoftDelete(SoftDeleteProperties softDelete) { this.softDelete = softDelete; }

    public static class AuditProperties {
        private boolean enabled = true;
        private String tableName = "flash_audit_log";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
    }

    public static class SoftDeleteProperties {
        private String columnName = "deleted_at";

        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
    }
}
