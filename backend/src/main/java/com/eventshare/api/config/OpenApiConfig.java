package com.eventshare.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI eventShareOpenApi() {
        final String bearer = "ClerkBearer";
        return new OpenAPI()
                .info(new Info()
                        .title("EventShare API")
                        .version("0.1.0")
                        .description("Event media sharing: events, signed uploads, gallery, moderation.")
                        .license(new License().name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList(bearer))
                .components(new Components().addSecuritySchemes(bearer,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Clerk-issued JWT. Send as: Authorization: Bearer <token>")));
    }
}
