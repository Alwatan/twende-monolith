package tz.co.twende.driver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI driverServiceOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Driver Service API")
                                .version("1.0")
                                .description(
                                        "Driver profile, vehicle, document management"
                                                + " and status control"));
    }
}
