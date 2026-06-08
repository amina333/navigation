package com.air.navigation.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI navigationOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .openapi("3.0.1")
                .info(new Info()
                        .title("Navigation Aérienne API")
                        .description("Plateforme Intégrée d'Assistance à la Navigation Aérienne — API REST")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Équipe Navigation")
                                .email("navigation@air.com")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
