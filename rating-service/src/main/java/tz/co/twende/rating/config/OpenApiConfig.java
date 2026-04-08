package tz.co.twende.rating.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ratingServiceOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Rating Service API")
                                .version("1.0")
                                .description(
                                        "Mutual rider and driver ratings for completed rides"));
    }
}
