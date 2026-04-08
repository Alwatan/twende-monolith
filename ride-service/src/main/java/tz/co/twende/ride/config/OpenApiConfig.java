package tz.co.twende.ride.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rideServiceOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Ride Service API")
                                .version("1.0")
                                .description(
                                        "Ride lifecycle orchestration: request, matching,"
                                                + " OTP verification, completion, and"
                                                + " cancellation"));
    }
}
