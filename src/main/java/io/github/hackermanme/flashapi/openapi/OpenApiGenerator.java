package io.github.hackermanme.flashapi.openapi;

import io.github.hackermanme.flashapi.autoconfigure.FlashProperties;
import io.github.hackermanme.flashapi.registry.CrudOperation;
import io.github.hackermanme.flashapi.registry.EntityMetadata;
import io.github.hackermanme.flashapi.registry.FieldMetadata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

public final class OpenApiGenerator {

    private final FlashProperties properties;
    private final List<EntityMetadata> entities;

    public OpenApiGenerator(FlashProperties properties, List<EntityMetadata> entities) {
        this.properties = properties;
        this.entities = entities;
    }

    public Map<String, Object> generate() {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("openapi", "3.0.3");
        spec.put("info", buildInfo());
        spec.put("paths", buildPaths());
        spec.put("components", Map.of("schemas", buildSchemas()));
        return spec;
    }

    private Map<String, Object> buildInfo() {
        var openapi = properties.getOpenapi();
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", openapi.getTitle());
        info.put("version", openapi.getVersion());
        info.put("description", openapi.getDescription());
        return info;
    }

    private Map<String, Object> buildPaths() {
        Map<String, Object> paths = new LinkedHashMap<>();
        String basePath = normalizePath(properties.getBasePath());

        for (EntityMetadata meta : entities) {
            String collectionPath = basePath + "/" + meta.path();
            String paramName = meta.hasCustomLookupField() ? meta.lookupFieldName() : "id";
            String itemPath = collectionPath + "/{" + paramName + "}";

            if (meta.isOperationAllowed(CrudOperation.LIST) || meta.isOperationAllowed(CrudOperation.CREATE)) {
                paths.put(collectionPath, buildCollectionOperations(meta));
            }
            if (meta.isOperationAllowed(CrudOperation.READ) || meta.isOperationAllowed(CrudOperation.UPDATE)
                    || meta.isOperationAllowed(CrudOperation.DELETE)) {
                paths.put(itemPath, buildItemOperations(meta));
            }
            if (meta.isOperationAllowed(CrudOperation.LIST)) {
                paths.put(collectionPath + "/export", buildExportOperation(meta));
            }
            if (meta.isOperationAllowed(CrudOperation.CREATE) || meta.isOperationAllowed(CrudOperation.UPDATE)
                    || meta.isOperationAllowed(CrudOperation.DELETE)) {
                paths.put(collectionPath + "/bulk", buildBulkOperations(meta));
            }
            if (meta.softDelete()) {
                paths.put(itemPath + "/restore", buildRestoreOperation(meta));
            }
            if (meta.auditEnabled()) {
                paths.put(itemPath + "/history", buildHistoryOperation(meta));
            }
        }
        return paths;
    }

    private Map<String, Object> buildCollectionOperations(EntityMetadata meta) {
        Map<String, Object> ops = new LinkedHashMap<>();
        if (meta.isOperationAllowed(CrudOperation.LIST)) {
            ops.put("get", buildListOperation(meta));
        }
        if (meta.isOperationAllowed(CrudOperation.CREATE)) {
            ops.put("post", buildCreateOperation(meta));
        }
        return ops;
    }

    private Map<String, Object> buildItemOperations(EntityMetadata meta) {
        Map<String, Object> ops = new LinkedHashMap<>();
        if (meta.isOperationAllowed(CrudOperation.READ)) {
            ops.put("get", buildReadOperation(meta));
        }
        if (meta.isOperationAllowed(CrudOperation.UPDATE)) {
            ops.put("put", buildUpdateOperation(meta));
        }
        if (meta.isOperationAllowed(CrudOperation.DELETE)) {
            ops.put("delete", buildDeleteOperation(meta));
        }
        return ops;
    }

    private Map<String, Object> buildListOperation(EntityMetadata meta) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", "List " + meta.entityName());
        op.put("operationId", "list" + meta.entityName());
        op.put("tags", List.of(meta.entityName()));
        op.put("parameters", buildListParameters(meta));
        op.put("responses", Map.of(
                "200", Map.of(
                        "description", "Paginated list of " + meta.entityName(),
                        "content", jsonContent(ref(meta.entityName() + "ListResponse"))
                )
        ));
        return op;
    }

    private Map<String, Object> buildCreateOperation(EntityMetadata meta) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", "Create " + meta.entityName());
        op.put("operationId", "create" + meta.entityName());
        op.put("tags", List.of(meta.entityName()));
        op.put("requestBody", Map.of(
                "required", true,
                "content", jsonContent(ref(meta.entityName() + "CreateInput"))
        ));
        op.put("responses", Map.of(
                "201", Map.of(
                        "description", meta.entityName() + " created",
                        "content", jsonContent(ref(meta.entityName() + "Response"))
                )
        ));
        return op;
    }

    private Map<String, Object> buildReadOperation(EntityMetadata meta) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", "Get " + meta.entityName() + " by ID");
        op.put("operationId", "get" + meta.entityName() + "ById");
        op.put("tags", List.of(meta.entityName()));
        op.put("parameters", List.of(idPathParam(meta), expandParam()));
        op.put("responses", Map.of(
                "200", Map.of(
                        "description", meta.entityName() + " found",
                        "content", jsonContent(ref(meta.entityName() + "Response"))
                ),
                "404", Map.of("description", "Not found")
        ));
        return op;
    }

    private Map<String, Object> buildUpdateOperation(EntityMetadata meta) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", "Update " + meta.entityName());
        op.put("operationId", "update" + meta.entityName());
        op.put("tags", List.of(meta.entityName()));
        op.put("parameters", List.of(idPathParam(meta)));
        op.put("requestBody", Map.of(
                "required", true,
                "content", jsonContent(ref(meta.entityName() + "UpdateInput"))
        ));
        op.put("responses", Map.of(
                "200", Map.of(
                        "description", meta.entityName() + " updated",
                        "content", jsonContent(ref(meta.entityName() + "Response"))
                ),
                "404", Map.of("description", "Not found")
        ));
        return op;
    }

    private Map<String, Object> buildDeleteOperation(EntityMetadata meta) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("summary", "Delete " + meta.entityName());
        op.put("operationId", "delete" + meta.entityName());
        op.put("tags", List.of(meta.entityName()));
        op.put("parameters", List.of(idPathParam(meta)));
        op.put("responses", Map.of(
                "204", Map.of("description", "Deleted"),
                "404", Map.of("description", "Not found")
        ));
        return op;
    }

    private Map<String, Object> buildExportOperation(EntityMetadata meta) {
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("get", Map.of(
                "summary", "Export " + meta.entityName(),
                "operationId", "export" + meta.entityName(),
                "tags", List.of(meta.entityName()),
                "parameters", List.of(
                        Map.of("name", "format", "in", "query", "required", true,
                                "schema", Map.of("type", "string", "enum", List.of("csv", "xlsx", "pdf")))
                ),
                "responses", Map.of(
                        "200", Map.of("description", "File download")
                )
        ));
        return op;
    }

    private Map<String, Object> buildBulkOperations(EntityMetadata meta) {
        Map<String, Object> ops = new LinkedHashMap<>();
        if (meta.isOperationAllowed(CrudOperation.CREATE)) {
            ops.put("post", Map.of(
                    "summary", "Bulk create " + meta.entityName(),
                    "operationId", "bulkCreate" + meta.entityName(),
                    "tags", List.of(meta.entityName()),
                    "requestBody", Map.of("required", true,
                            "content", jsonContent(Map.of("type", "array", "items", ref(meta.entityName() + "CreateInput")))),
                    "responses", Map.of("200", Map.of("description", "Bulk result"))
            ));
        }
        if (meta.isOperationAllowed(CrudOperation.UPDATE)) {
            ops.put("put", Map.of(
                    "summary", "Bulk update " + meta.entityName(),
                    "operationId", "bulkUpdate" + meta.entityName(),
                    "tags", List.of(meta.entityName()),
                    "requestBody", Map.of("required", true,
                            "content", jsonContent(Map.of("type", "array", "items", ref(meta.entityName() + "UpdateInput")))),
                    "responses", Map.of("200", Map.of("description", "Bulk result"))
            ));
        }
        if (meta.isOperationAllowed(CrudOperation.DELETE)) {
            ops.put("delete", Map.of(
                    "summary", "Bulk delete " + meta.entityName(),
                    "operationId", "bulkDelete" + meta.entityName(),
                    "tags", List.of(meta.entityName()),
                    "requestBody", Map.of("required", true,
                            "content", jsonContent(Map.of("type", "array", "items", Map.of("type", mapIdType(meta.idType()))))),
                    "responses", Map.of("200", Map.of("description", "Bulk result"))
            ));
        }
        return ops;
    }

    private Map<String, Object> buildRestoreOperation(EntityMetadata meta) {
        return Map.of("post", Map.of(
                "summary", "Restore " + meta.entityName(),
                "operationId", "restore" + meta.entityName(),
                "tags", List.of(meta.entityName()),
                "parameters", List.of(idPathParam(meta)),
                "responses", Map.of(
                        "204", Map.of("description", "Restored"),
                        "404", Map.of("description", "Not found")
                )
        ));
    }

    private Map<String, Object> buildHistoryOperation(EntityMetadata meta) {
        return Map.of("get", Map.of(
                "summary", "Audit history for " + meta.entityName(),
                "operationId", "history" + meta.entityName(),
                "tags", List.of(meta.entityName()),
                "parameters", List.of(idPathParam(meta)),
                "responses", Map.of("200", Map.of("description", "Audit entries"))
        ));
    }

    private List<Map<String, Object>> buildListParameters(EntityMetadata meta) {
        List<Map<String, Object>> params = new ArrayList<>();
        params.add(Map.of("name", "page", "in", "query", "required", false,
                "schema", Map.of("type", "integer", "default", 0)));
        params.add(Map.of("name", "size", "in", "query", "required", false,
                "schema", Map.of("type", "integer", "default", properties.getDefaultPageSize())));
        params.add(Map.of("name", "sort", "in", "query", "required", false,
                "schema", Map.of("type", "string"), "example", "name,asc"));
        params.add(Map.of("name", "search", "in", "query", "required", false,
                "schema", Map.of("type", "string"), "description", "Full-text search across String fields"));
        params.add(expandParam());
        return params;
    }

    private Map<String, Object> buildSchemas() {
        Map<String, Object> schemas = new LinkedHashMap<>();
        for (EntityMetadata meta : entities) {
            schemas.put(meta.entityName() + "Response", buildResponseSchema(meta));
            schemas.put(meta.entityName() + "ListResponse", buildListResponseSchema(meta));
            schemas.put(meta.entityName() + "CreateInput", buildInputSchema(meta, true));
            schemas.put(meta.entityName() + "UpdateInput", buildInputSchema(meta, false));
        }
        return schemas;
    }

    private Map<String, Object> buildResponseSchema(EntityMetadata meta) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "data", buildEntitySchema(meta)
        ));
        return schema;
    }

    private Map<String, Object> buildListResponseSchema(EntityMetadata meta) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "data", Map.of("type", "array", "items", buildEntitySchema(meta)),
                "meta", Map.of("type", "object", "properties", Map.of(
                        "page", Map.of("type", "integer"),
                        "size", Map.of("type", "integer"),
                        "totalElements", Map.of("type", "integer"),
                        "totalPages", Map.of("type", "integer")
                ))
        ));
        return schema;
    }

    private Map<String, Object> buildEntitySchema(EntityMetadata meta) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        for (FieldMetadata field : meta.visibleFields()) {
            props.put(field.name(), fieldSchema(field));
        }
        schema.put("properties", props);
        return schema;
    }

    private Map<String, Object> buildInputSchema(EntityMetadata meta, boolean forCreate) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        List<FieldMetadata> fields = forCreate ? meta.creatableFields() : meta.updatableFields();
        for (FieldMetadata field : fields) {
            props.put(field.name(), fieldSchema(field));
            if (!field.nullable() && forCreate) {
                required.add(field.name());
            }
        }
        schema.put("properties", props);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private Map<String, Object> fieldSchema(FieldMetadata field) {
        Map<String, Object> schema = new LinkedHashMap<>();
        String type = mapJavaType(field.type());
        String format = mapJavaFormat(field.type());
        schema.put("type", type);
        if (format != null) {
            schema.put("format", format);
        }
        if (field.type().isEnum()) {
            List<String> values = new ArrayList<>();
            for (Object constant : field.type().getEnumConstants()) {
                values.add(((Enum<?>) constant).name());
            }
            schema.put("enum", values);
        }
        if (field.maxLength() != null) {
            schema.put("maxLength", field.maxLength());
        }
        if (field.nullable()) {
            schema.put("nullable", true);
        }
        return schema;
    }

    private String mapJavaType(Class<?> type) {
        if (type == String.class) return "string";
        if (type == Integer.class || type == int.class) return "integer";
        if (type == Long.class || type == long.class) return "integer";
        if (type == Double.class || type == double.class) return "number";
        if (type == Float.class || type == float.class) return "number";
        if (type == BigDecimal.class) return "number";
        if (type == Boolean.class || type == boolean.class) return "boolean";
        if (type == UUID.class) return "string";
        if (type == LocalDate.class || type == LocalDateTime.class || type == OffsetDateTime.class) return "string";
        if (type.isEnum()) return "string";
        return "object";
    }

    private String mapJavaFormat(Class<?> type) {
        if (type == Long.class || type == long.class) return "int64";
        if (type == Integer.class || type == int.class) return "int32";
        if (type == Float.class || type == float.class) return "float";
        if (type == Double.class || type == double.class) return "double";
        if (type == UUID.class) return "uuid";
        if (type == LocalDate.class) return "date";
        if (type == LocalDateTime.class || type == OffsetDateTime.class) return "date-time";
        return null;
    }

    private String mapIdType(Class<?> idType) {
        if (idType == Long.class || idType == long.class) return "integer";
        if (idType == Integer.class || idType == int.class) return "integer";
        return "string";
    }

    private Map<String, Object> idPathParam(EntityMetadata meta) {
        String paramName = meta.hasCustomLookupField() ? meta.lookupFieldName() : "id";
        Class<?> type = meta.hasCustomLookupField() ? meta.lookupFieldType() : meta.idType();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", mapIdType(type));
        String format = mapJavaFormat(type);
        if (format != null) schema.put("format", format);
        return Map.of(
                "name", paramName,
                "in", "path",
                "required", true,
                "schema", schema
        );
    }

    private Map<String, Object> expandParam() {
        return Map.of(
                "name", "expand",
                "in", "query",
                "required", false,
                "schema", Map.of("type", "string"),
                "description", "Comma-separated relations to expand"
        );
    }

    private Map<String, Object> ref(String schemaName) {
        return Map.of("$ref", "#/components/schemas/" + schemaName);
    }

    private Map<String, Object> jsonContent(Map<String, Object> schema) {
        return Map.of("application/json", Map.of("schema", schema));
    }

    private String normalizePath(String path) {
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        if (!path.startsWith("/")) path = "/" + path;
        return path;
    }
}
