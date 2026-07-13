package io.github.hackermanme.flashapi.autoconfigure;

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
    private ExportProperties export = new ExportProperties();
    private BulkProperties bulk = new BulkProperties();

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

    public ExportProperties getExport() { return export; }
    public void setExport(ExportProperties export) { this.export = export; }

    public BulkProperties getBulk() { return bulk; }
    public void setBulk(BulkProperties bulk) { this.bulk = bulk; }

    public static class BulkProperties {
        private int maxItems = 100;

        public int getMaxItems() { return maxItems; }
        public void setMaxItems(int maxItems) { this.maxItems = maxItems; }
    }

    public static class ExportProperties {
        private int maxRows = 0;
        private String reportsPath = "flashapi/reports";

        public int getMaxRows() { return maxRows; }
        public void setMaxRows(int maxRows) { this.maxRows = maxRows; }

        public String getReportsPath() { return reportsPath; }
        public void setReportsPath(String reportsPath) { this.reportsPath = reportsPath; }
    }

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
