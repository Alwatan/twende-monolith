package tz.co.twende.countryconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"tz.co.twende.countryconfig", "tz.co.twende.common"})
@EnableJpaAuditing
@EnableScheduling
@EnableCaching
public class CountryConfigServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CountryConfigServiceApplication.class, args);
    }
}
