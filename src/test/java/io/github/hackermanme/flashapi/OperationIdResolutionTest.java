package io.github.hackermanme.flashapi;

import io.github.hackermanme.flashapi.autoconfigure.FlashProperties;
import io.github.hackermanme.flashapi.openapi.ControllerEndpoint;
import io.github.hackermanme.flashapi.openapi.OpenApiGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OperationIdResolutionTest {

    @Test
    void duplicateOperationIdThrowsAtGeneration() {
        FlashProperties props = new FlashProperties();

        ControllerEndpoint ep1 = new ControllerEndpoint(
                "/api/widgets", "post", "Widget", "createWidget", "Create Widget",
                List.of(), null, null);
        ControllerEndpoint ep2 = new ControllerEndpoint(
                "/api/gadgets", "post", "Gadget", "createWidget", "Create Gadget",
                List.of(), null, null);

        OpenApiGenerator generator = new OpenApiGenerator(props, List.of(), List.of(ep1, ep2));

        IllegalStateException ex = assertThrows(IllegalStateException.class, generator::generate);
        assertTrue(ex.getMessage().contains("duplicate operationId"));
        assertTrue(ex.getMessage().contains("createWidget"));
    }

    @Test
    void uniqueOperationIdsPassValidation() {
        FlashProperties props = new FlashProperties();

        ControllerEndpoint ep1 = new ControllerEndpoint(
                "/api/widgets", "post", "Widget", "createWidget", "Create Widget",
                List.of(), null, null);
        ControllerEndpoint ep2 = new ControllerEndpoint(
                "/api/gadgets", "post", "Gadget", "createGadget", "Create Gadget",
                List.of(), null, null);

        OpenApiGenerator generator = new OpenApiGenerator(props, List.of(), List.of(ep1, ep2));

        Map<String, Object> spec = generator.generate();
        assertNotNull(spec);
        assertEquals("3.0.3", spec.get("openapi"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void controllerEndpointsHaveCorrectOperationIdInSpec() {
        FlashProperties props = new FlashProperties();

        ControllerEndpoint ep = new ControllerEndpoint(
                "/api/auth/login", "post", "Auth", "loginAuth", "Login",
                List.of(), null, null);

        OpenApiGenerator generator = new OpenApiGenerator(props, List.of(), List.of(ep));
        Map<String, Object> spec = generator.generate();

        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
        Map<String, Object> pathItem = (Map<String, Object>) paths.get("/api/auth/login");
        Map<String, Object> operation = (Map<String, Object>) pathItem.get("post");
        assertEquals("loginAuth", operation.get("operationId"));
    }
}
