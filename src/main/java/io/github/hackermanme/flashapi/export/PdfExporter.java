package io.github.hackermanme.flashapi.export;

import io.github.hackermanme.flashapi.registry.FieldMetadata;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.*;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.ModeEnum;
import net.sf.jasperreports.engine.type.VerticalTextAlignEnum;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PdfExporter {

    private PdfExporter() {}

    public static void write(OutputStream out, String entityName, List<FieldMetadata> columns,
                             List<Object> entities, String templatePath) throws IOException {
        try {
            JasperReport report = resolveReport(entityName, columns, templatePath);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(
                    toMapList(columns, entities));

            Map<String, Object> params = new HashMap<>();
            params.put("ENTITY_NAME", entityName);
            params.put("RECORD_COUNT", entities.size());

            JasperPrint print = JasperFillManager.fillReport(report, params, dataSource);

            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            exporter.exportReport();
        } catch (JRException e) {
            throw new IOException("PDF export failed for " + entityName, e);
        }
    }

    private static JasperReport resolveReport(String entityName, List<FieldMetadata> columns,
                                              String templatePath) throws JRException, IOException {
        if (templatePath != null) {
            InputStream is = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(templatePath);
            if (is != null) {
                try (is) {
                    return JasperCompileManager.compileReport(is);
                }
            }
        }
        return buildDynamicReport(entityName, columns);
    }

    private static JasperReport buildDynamicReport(String entityName, List<FieldMetadata> columns)
            throws JRException {
        JasperDesign design = new JasperDesign();
        design.setName(entityName + "_export");
        design.setPageWidth(595);
        design.setPageHeight(842);
        design.setLeftMargin(20);
        design.setRightMargin(20);
        design.setTopMargin(30);
        design.setBottomMargin(30);

        int usableWidth = 595 - 40;
        int colWidth = Math.max(50, usableWidth / columns.size());

        // Fields
        for (FieldMetadata col : columns) {
            JRDesignField field = new JRDesignField();
            field.setName(col.name());
            field.setValueClass(Object.class);
            design.addField(field);
        }

        // Column header band
        JRDesignBand headerBand = new JRDesignBand();
        headerBand.setHeight(24);
        for (int i = 0; i < columns.size(); i++) {
            JRDesignStaticText label = new JRDesignStaticText();
            label.setX(i * colWidth);
            label.setY(0);
            label.setWidth(colWidth);
            label.setHeight(24);
            label.setText(columns.get(i).name());
            label.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
            label.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
            label.setBold(Boolean.TRUE);
            label.setMode(ModeEnum.OPAQUE);
            label.setBackcolor(new java.awt.Color(0x3C, 0x78, 0xD8));
            label.setForecolor(java.awt.Color.WHITE);
            label.setFontSize(10f);
            applyBorders(label.getLineBox(), 0.5f);
            headerBand.addElement(label);
        }
        design.setColumnHeader(headerBand);

        // Detail band
        JRDesignBand detailBand = new JRDesignBand();
        detailBand.setHeight(18);
        for (int i = 0; i < columns.size(); i++) {
            JRDesignTextField textField = new JRDesignTextField();
            textField.setX(i * colWidth);
            textField.setY(0);
            textField.setWidth(colWidth);
            textField.setHeight(18);
            textField.setBlankWhenNull(true);
            textField.setHorizontalTextAlign(HorizontalTextAlignEnum.CENTER);
            textField.setVerticalTextAlign(VerticalTextAlignEnum.MIDDLE);
            textField.setFontSize(9f);
            textField.setExpression(new JRDesignExpression(
                    "$F{" + columns.get(i).name() + "}"));
            applyBorders(textField.getLineBox(), 0.25f);
            detailBand.addElement(textField);
        }
        ((JRDesignSection) design.getDetailSection()).addBand(detailBand);

        return JasperCompileManager.compileReport(design);
    }

    private static void applyBorders(JRLineBox box, float width) {
        box.getPen().setLineWidth(width);
        box.getPen().setLineColor(java.awt.Color.DARK_GRAY);
        box.getTopPen().setLineWidth(width);
        box.getBottomPen().setLineWidth(width);
        box.getLeftPen().setLineWidth(width);
        box.getRightPen().setLineWidth(width);
    }

    private static List<Map<String, Object>> toMapList(List<FieldMetadata> columns,
                                                        List<Object> entities) {
        return entities.stream().map(entity -> {
            Map<String, Object> map = new HashMap<>();
            for (FieldMetadata col : columns) {
                try {
                    map.put(col.name(), col.javaField().get(entity));
                } catch (IllegalAccessException e) {
                    map.put(col.name(), null);
                }
            }
            return map;
        }).toList();
    }
}
