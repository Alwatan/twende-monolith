package com.twende.countryconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.twende.countryconfig", "com.twende.common"})
public class CountryConfigServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CountryConfigServiceApplication.class, args);
    }
}
