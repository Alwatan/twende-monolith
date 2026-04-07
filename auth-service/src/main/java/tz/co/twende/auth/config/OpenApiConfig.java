package tz.co.twende.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI authServiceOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Twende Auth Service API")
                                .version("1.0")
                                .description(
                                        "OAuth2 Authorization Server with phone OTP authentication"));
    }
}
