package tz.co.twende.ride;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"tz.co.twende.ride", "tz.co.twende.common"})
@EnableJpaAuditing
@EnableScheduling
public class RideServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RideServiceApplication.class, args);
    }
}
