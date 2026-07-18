package io.github.hackermanme.flashapi.export;

import io.github.hackermanme.flashapi.registry.FieldMetadata;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
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
            SXSSFSheet sheet = workbook.createSheet(sheetName);

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            // Header row
            Row header = sheet.createRow(0);
            header.setHeightInPoints(22);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i).name());
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            for (int rowIdx = 0; rowIdx < entities.size(); rowIdx++) {
                Row row = sheet.createRow(rowIdx + 1);
                Object entity = entities.get(rowIdx);
                for (int col = 0; col < columns.size(); col++) {
                    Cell cell = row.createCell(col);
                    Object value = readField(columns.get(col), entity);
                    setCellValue(cell, value);
                    cell.setCellStyle(dataStyle);
                }
            }

            // Auto-size columns (tracked for SXSSF)
            sheet.trackAllColumnsForAutoSizing();
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            // Freeze header row
            sheet.createFreezePane(0, 1);

            // Auto-filter on header
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, columns.size() - 1));

            workbook.write(out);
        }
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);

        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    private static CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();

        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());

        return style;
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
