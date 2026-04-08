package tz.co.twende.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"tz.co.twende.matching", "tz.co.twende.common"})
@EnableJpaAuditing
@EnableScheduling
public class MatchingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MatchingServiceApplication.class, args);
    }
}
