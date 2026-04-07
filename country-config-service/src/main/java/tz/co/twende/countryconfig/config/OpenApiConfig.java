package tz.co.twende.countryconfig.config;

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
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Twende Country Config Service API")
                                .description(
                                        "Country and city configuration for the Twende"
                                                + " ride-hailing platform. Public GET endpoints for"
                                                + " mobile apps, admin write endpoints for"
                                                + " configuration management.")
                                .version("1.0.0")
                                .contact(
                                        new Contact()
                                                .name("Twende Engineering")
                                                .email("engineering@twende.co.tz")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"))
                .schemaRequirement(
                        "bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(
                                        "JWT token required for admin write endpoints only."
                                                + " GET endpoints are public."));
    }
}
