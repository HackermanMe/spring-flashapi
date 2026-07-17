package io.github.hackermanme.flashapi.export;

import io.github.hackermanme.flashapi.registry.EntityMetadata;
import io.github.hackermanme.flashapi.registry.FieldMetadata;
import io.github.hackermanme.flashapi.service.GenericCrudService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ExportHandler {

    private static final Logger log = LoggerFactory.getLogger(ExportHandler.class);
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int FETCH_BATCH_SIZE = 500;

    private final GenericCrudService crudService;
    private final int maxRows;
    private final String reportsPath;

    public ExportHandler(GenericCrudService crudService, int maxRows, String reportsPath) {
        this.crudService = crudService;
        this.maxRows = maxRows;
        this.reportsPath = reportsPath;
    }

    public void export(EntityMetadata meta, ExportFormat format, Map<String, String> filters,
                       String sortParam, HttpServletResponse response) throws IOException {

        validateFormatAvailability(format);

        List<FieldMetadata> columns = meta.exportableFields();
        String filename = meta.path() + "_" + LocalDateTime.now().format(TIMESTAMP_FMT)
                + "." + format.extension();

        response.setContentType(format.contentType());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        List<Object> entities = fetchAll(meta, filters, sortParam);

        switch (format) {
            case CSV -> CsvExporter.write(response.getOutputStream(), columns, entities);
            case XLSX -> ExcelExporter.write(response.getOutputStream(), meta.entityName(), columns, entities);
            case PDF -> {
                String templatePath = resolveTemplatePath(meta);
                PdfExporter.write(response.getOutputStream(), meta.entityName(), columns, entities, templatePath);
            }
        }

        response.flushBuffer();
        log.info("FlashAPI: exported {} {} records as {}", entities.size(), meta.entityName(), format);
    }

    private List<Object> fetchAll(EntityMetadata meta, Map<String, String> filters, String sortParam) {
        List<Object> all = new ArrayList<>();
        Sort sort = parseSort(sortParam);
        int page = 0;

        while (true) {
            Pageable pageable = PageRequest.of(page, FETCH_BATCH_SIZE, sort);
            Page<Object> batch = crudService.list(meta, pageable, new java.util.HashMap<>(filters));
            all.addAll(batch.getContent());

            if (maxRows > 0 && all.size() >= maxRows) {
                log.warn("FlashAPI: export for {} truncated at {} rows (max-rows={})",
                        meta.entityName(), maxRows, maxRows);
                return all.subList(0, maxRows);
            }
            if (!batch.hasNext()) break;
            page++;
        }
        return all;
    }

    private Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) return Sort.unsorted();
        String[] parts = sortParam.split(",", 2);
        String field = parts[0].trim();
        Sort.Direction dir = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field);
    }

    private String resolveTemplatePath(EntityMetadata meta) {
        if (reportsPath == null || reportsPath.isBlank()) return null;
        String path = reportsPath.endsWith("/") ? reportsPath : reportsPath + "/";
        String templateFile = path + meta.path() + ".jrxml";
        if (Thread.currentThread().getContextClassLoader().getResource(templateFile) != null) {
            return templateFile;
        }
        return null;
    }

    private void validateFormatAvailability(ExportFormat format) {
        if (format == ExportFormat.XLSX && !isPoiAvailable()) {
            throw new ExportUnavailableException(
                    "Excel export requires Apache POI. Add 'org.apache.poi:poi-ooxml' to your dependencies.");
        }
        if (format == ExportFormat.PDF && !isJasperAvailable()) {
            throw new ExportUnavailableException(
                    "PDF export requires JasperReports. Add 'net.sf.jasperreports:jasperreports' to your dependencies.");
        }
    }

    private static boolean isPoiAvailable() {
        try {
            Class.forName("org.apache.poi.xssf.streaming.SXSSFWorkbook");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean isJasperAvailable() {
        try {
            Class.forName("net.sf.jasperreports.engine.JasperReport");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
