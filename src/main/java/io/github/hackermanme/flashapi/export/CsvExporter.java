package io.github.hackermanme.flashapi.export;

import io.github.hackermanme.flashapi.registry.FieldMetadata;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvExporter {

    private CsvExporter() {}

    public static void write(OutputStream out, List<FieldMetadata> columns, List<Object> entities)
            throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        // BOM for Excel UTF-8 recognition
        writer.write('﻿');

        // Header row
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) writer.write(',');
            writer.write(escapeCsv(columns.get(i).name()));
        }
        writer.write("\r\n");
        writer.flush();

        // Data rows
        for (Object entity : entities) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) writer.write(',');
                Object value = readField(columns.get(i), entity);
                writer.write(escapeCsv(value == null ? "" : value.toString()));
            }
            writer.write("\r\n");
            writer.flush();
        }
    }

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static Object readField(FieldMetadata field, Object entity) {
        try {
            return field.javaField().get(entity);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
