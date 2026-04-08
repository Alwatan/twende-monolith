package tz.co.twende.pricing.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Pricing Service API")
                                .version("1.0")
                                .description(
                                        "Fare estimation, final fare calculation,"
                                                + " and surge pricing for the Twende"
                                                + " transport platform"));
    }
}
