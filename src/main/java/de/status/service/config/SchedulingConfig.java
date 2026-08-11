package de.status.service.config;

import de.status.service.status.StatusService;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Registers the poll loop programmatically so the interval can come straight
 * from the typed {@link StatusProperties#getRefreshInterval()} Duration. A fixed
 * delay task runs once immediately on startup and then repeats.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    private final StatusProperties properties;
    private final StatusService statusService;

    public SchedulingConfig(StatusProperties properties, StatusService statusService) {
        this.properties = properties;
        this.statusService = statusService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(statusService::refresh, properties.getRefreshInterval());
    }
}
