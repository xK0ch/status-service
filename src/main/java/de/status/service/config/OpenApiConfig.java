package de.status.service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Server Status API")
                        .description("Live status of the self-hosted game servers")
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact().name("Fynn Koch")));
    }
}
