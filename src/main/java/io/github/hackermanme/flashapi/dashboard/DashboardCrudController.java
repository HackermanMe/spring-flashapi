package io.github.hackermanme.flashapi.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@ConditionalOnProperty(name = "flashapi.dashboard.enabled", havingValue = "true", matchIfMissing = true)
public class DashboardCrudController {

    private final String basePath;
    private final ObjectMapper objectMapper;

    public DashboardCrudController(ObjectMapper objectMapper) {
        this.basePath = "/api";
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/api/dashboard/crud", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> dashboardHtml() throws IOException {
        // Load HTML template from resources
        String html = loadTemplate();

        // Empty entity list for now (will be populated via API discovery)
        List<String> entityNames = List.of();

        // Replace placeholders
        String entitiesJson = objectMapper.writeValueAsString(entityNames);
        html = html.replace("ENTITIES_PLACEHOLDER", entitiesJson);
        html = html.replace("'BASE_PATH_PLACEHOLDER'", "'" + basePath + "'");

        return ResponseEntity.ok(html);
    }

    @GetMapping("/api/dashboard/counts")
    public ResponseEntity<Map<String, Long>> getCounts() {
        Map<String, Long> counts = new HashMap<>();
        // Empty for now - will be populated via repository discovery
        return ResponseEntity.ok(counts);
    }

    private String loadTemplate() throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/templates/dashboard-crud.html")) {
            if (is == null) {
                throw new IOException("Dashboard template not found");
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
