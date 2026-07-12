package io.github.hackermanme.flashapi.export;

import io.github.hackermanme.flashapi.registry.FieldMetadata;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.time.temporal.Temporal;
import java.util.List;

public final class ExcelExporter {

    private static final int STREAMING_WINDOW = 100;

    private ExcelExporter() {}

    public static void write(OutputStream out, String sheetName, List<FieldMetadata> columns,
                             List<Object> entities) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(STREAMING_WINDOW)) {
            Sheet sheet = workbook.createSheet(sheetName);

            // Header row
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                header.createCell(i).setCellValue(columns.get(i).name());
            }

            // Data rows
            for (int rowIdx = 0; rowIdx < entities.size(); rowIdx++) {
                Row row = sheet.createRow(rowIdx + 1);
                Object entity = entities.get(rowIdx);
                for (int col = 0; col < columns.size(); col++) {
                    Cell cell = row.createCell(col);
                    Object value = readField(columns.get(col), entity);
                    setCellValue(cell, value);
                }
            }

            workbook.write(out);
        }
    }

    private static void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else if (value instanceof Temporal) {
            cell.setCellValue(value.toString());
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private static Object readField(FieldMetadata field, Object entity) {
        try {
            return field.javaField().get(entity);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
