package io.github.hackermanme.flashapi.health;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Marks the application as ready after the context is fully initialized.
 */
@Component
public class ReadinessListener implements ApplicationListener<ApplicationReadyEvent> {

    private final HealthCheckController healthCheckController;

    public ReadinessListener(HealthCheckController healthCheckController) {
        this.healthCheckController = healthCheckController;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        healthCheckController.markReady();
    }
}
