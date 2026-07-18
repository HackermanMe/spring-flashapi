package io.github.hackermanme.flashapi.openapi;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Collection;
import java.util.Map;

public final class OpenApiController {

    private final Map<String, Object> spec;
    private volatile String cachedJson;

    public OpenApiController(Map<String, Object> spec) {
        this.spec = spec;
    }

    public ResponseEntity<String> serveSpec(HttpServletRequest request, HttpServletResponse response) {
        if (cachedJson == null) {
            cachedJson = toJson(spec);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Access-Control-Allow-Origin", "*")
                .body(cachedJson);
    }

    public void serveUi(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.setCharacterEncoding("UTF-8");

        String basePath = request.getRequestURI().replace("/index.html", "").replaceAll("/+$", "");
        String specUrl = basePath + "/openapi.json";

        String title = "FlashAPI";
        if (spec.get("info") instanceof Map<?,?> info && info.get("title") instanceof String t) {
            title = t;
        }

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>%s — API Documentation</title>
                    <link rel="icon" type="image/svg+xml" href="https://raw.githubusercontent.com/HackermanMe/spring-flashapi/main/docs/assets/favicon.svg">
                    <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
                    <style>
                        body { margin: 0; padding: 0; }
                        #swagger-ui { max-width: 1200px; margin: 0 auto; }
                    </style>
                </head>
                <body>
                    <div id="swagger-ui"></div>
                    <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
                    <script>
                        SwaggerUIBundle({
                            url: '%s',
                            dom_id: '#swagger-ui',
                            presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],
                            layout: 'BaseLayout'
                        });
                    </script>
                </body>
                </html>
                """.formatted(title, specUrl);

        response.getWriter().write(html);
        response.flushBuffer();
    }

    @SuppressWarnings("unchecked")
    private static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof String s) return "\"" + escape(s) + "\"";
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(entry.getKey().toString())).append("\":");
                sb.append(toJson(entry.getValue()));
                first = false;
            }
            return sb.append("}").toString();
        }
        if (value instanceof Collection<?> list) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            return sb.append("]").toString();
        }
        if (value.getClass().isArray()) {
            if (value instanceof Object[] arr) {
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) sb.append(",");
                    sb.append(toJson(arr[i]));
                }
                return sb.append("]").toString();
            }
            return "[]";
        }
        return "\"" + escape(value.toString()) + "\"";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
