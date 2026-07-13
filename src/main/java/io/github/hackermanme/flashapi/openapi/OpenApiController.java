package io.github.hackermanme.flashapi.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Map;

public final class OpenApiController {

    private final Map<String, Object> spec;
    private volatile String cachedJson;

    public OpenApiController(Map<String, Object> spec) {
        this.spec = spec;
    }

    public void serveSpec(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (cachedJson == null) {
            cachedJson = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(spec);
        }
        response.getWriter().write(cachedJson);
    }

    public void serveUi(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        response.setCharacterEncoding("UTF-8");

        String basePath = request.getRequestURI().replace("/index.html", "").replaceAll("/+$", "");
        String specUrl = basePath + "/openapi.json";

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>%s — API Documentation</title>
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
                """.formatted(spec.get("info") instanceof Map<?,?> info ? info.get("title") : "FlashAPI", specUrl);

        response.getWriter().write(html);
    }
}
