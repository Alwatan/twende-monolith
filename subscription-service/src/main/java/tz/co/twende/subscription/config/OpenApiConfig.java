package tz.co.twende.subscription.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI subscriptionServiceOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Subscription Service API")
                                .version("1.0")
                                .description("Driver subscription bundle management and billing"));
    }
}
