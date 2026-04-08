package tz.co.twende.matching.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI matchingServiceOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Matching Service API")
                                .version("1.0")
                                .description(
                                        "Broadcast-and-accept driver matching for ride"
                                                + " requests"));
    }
}
