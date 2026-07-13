package io.github.hackermanme.flashapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.Map;

/**
 * Bridge between Spring MVC's handler infrastructure and FlashController.
 * Each instance is bound to a single operation on a single entity.
 * Spring MVC resolves method arguments via annotations on handle().
 */
public final class FlashEndpointHandler {

    private final FlashController controller;
    private final String operation;

    public FlashEndpointHandler(FlashController controller, String operation) {
        this.controller = controller;
        this.operation = operation;
    }

    public ResponseEntity<?> handle(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(required = false) Map<String, String> params,
            @RequestBody(required = false) Map<String, Object> body) throws IOException {

        if ("export".equals(operation)) {
            controller.export(params != null ? params : Map.of(), response);
            return null;
        }

        return switch (operation) {
            case "list" -> controller.list(params != null ? params : Map.of());
            case "getById" -> controller.getById(extractId(request), params);
            case "create" -> controller.create(body != null ? body : Map.of());
            case "update" -> controller.update(extractId(request), body != null ? body : Map.of());
            case "delete" -> controller.delete(extractId(request));
            case "restore" -> controller.restore(extractIdBeforeSegment(request, "restore"));
            case "history" -> controller.history(extractIdBeforeSegment(request, "history"));
            default -> ResponseEntity.internalServerError().build();
        };
    }

    private Object extractId(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String idStr = uri.substring(uri.lastIndexOf('/') + 1);
        return convertId(idStr);
    }

    private Object extractIdBeforeSegment(HttpServletRequest request, String segment) {
        String uri = request.getRequestURI();
        int segmentIdx = uri.lastIndexOf("/" + segment);
        String beforeSegment = uri.substring(0, segmentIdx);
        String idStr = beforeSegment.substring(beforeSegment.lastIndexOf('/') + 1);
        return convertId(idStr);
    }

    private Object convertId(String raw) {
        Class<?> idType = controller.getMetadata().idType();
        if (idType == Long.class || idType == long.class) return Long.parseLong(raw);
        if (idType == Integer.class || idType == int.class) return Integer.parseInt(raw);
        if (idType == java.util.UUID.class) return java.util.UUID.fromString(raw);
        return raw;
    }
}
