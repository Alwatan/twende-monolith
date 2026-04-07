package com.twende.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "twende.auth")
public class AuthProperties {

    private Otp otp = new Otp();
    private Jwt jwt = new Jwt();

    @Data
    public static class Otp {
        private int length = 6;
        private int expiryMinutes = 5;
        private int maxAttempts = 3;
        private int maxRequestsPerWindow = 3;
        private int windowMinutes = 10;
        private boolean devMode = true;
    }

    @Data
    public static class Jwt {
        private long accessTokenTtlSeconds = 3600;
        private int refreshTokenTtlDays = 30;
        private String keystorePath;
        private String keystorePassword;
    }
}
