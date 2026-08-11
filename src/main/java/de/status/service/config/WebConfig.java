package de.status.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enables CORS for the API only when origins are configured. In production the
 * UI and API share an origin through nginx, so this stays empty; it exists for
 * local development where Vite runs on a different port.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StatusProperties properties;

    public WebConfig(StatusProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (properties.getCorsAllowedOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(properties.getCorsAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET");
    }
}
