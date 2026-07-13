package io.github.hackermanme.flashapi.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

public final class FlashBulkEndpointHandler {

    private final FlashController controller;
    private final String operation;

    public FlashBulkEndpointHandler(FlashController controller, String operation) {
        this.controller = controller;
        this.operation = operation;
    }

    public ResponseEntity<?> handle(
            HttpServletRequest request,
            @RequestBody(required = false) Object body) {

        return switch (operation) {
            case "bulkCreate" -> controller.bulkCreate(body);
            case "bulkUpdate" -> controller.bulkUpdate(body);
            case "bulkDelete" -> controller.bulkDelete(body);
            default -> ResponseEntity.internalServerError().build();
        };
    }
}
