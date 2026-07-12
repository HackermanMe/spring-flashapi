package io.github.hackermanme.flashapi.export;

public enum ExportFormat {

    CSV("text/csv; charset=UTF-8", "csv"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    PDF("application/pdf", "pdf");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() { return contentType; }
    public String extension() { return extension; }

    public static ExportFormat fromParam(String param) {
        if (param == null || param.isBlank()) return null;
        try {
            return valueOf(param.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
