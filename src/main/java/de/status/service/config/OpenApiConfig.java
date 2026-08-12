package de.status.service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                // Relative server URL so Swagger UI's "Try it out" targets the
                // page origin, not the http:// URL springdoc infers behind the proxy.
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("Server Status API")
                        .description("Live status of the self-hosted game servers")
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact().name("Fynn Koch")));
    }
}
