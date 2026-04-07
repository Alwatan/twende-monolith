package tz.co.twende.countryconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"tz.co.twende.countryconfig", "tz.co.twende.common"})
public class CountryConfigServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CountryConfigServiceApplication.class, args);
    }
}
