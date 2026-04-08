package tz.co.twende.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Payment Service API")
                                .version("1.0")
                                .description(
                                        "Wallet management, payment processing, cash"
                                                + " declarations, and earnings"));
    }
}
