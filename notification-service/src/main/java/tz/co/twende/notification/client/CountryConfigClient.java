package tz.co.twende.notification.client;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class CountryConfigClient {

    private final RestClient restClient;

    public CountryConfigClient(
            @Value("${twende.services.country-config-service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public CountryConfigDto getConfig(String countryCode) {
        try {
            return restClient
                    .get()
                    .uri("/internal/config/countries/{code}", countryCode)
                    .retrieve()
                    .body(CountryConfigDto.class);
        } catch (Exception e) {
            log.warn(
                    "Failed to fetch country config for {}, using defaults: {}",
                    countryCode,
                    e.getMessage());
            return defaultConfig(countryCode);
        }
    }

    private CountryConfigDto defaultConfig(String countryCode) {
        CountryConfigDto config = new CountryConfigDto();
        config.setCountryCode(countryCode);
        config.setSmsProvider("africastalking");
        config.setPushProvider("fcm");
        config.setCurrencyCode("TZS");
        config.setDefaultLocale("sw-TZ");
        return config;
    }

    @Data
    public static class CountryConfigDto {
        private String countryCode;
        private String smsProvider;
        private String pushProvider;
        private String currencyCode;
        private String defaultLocale;
    }
}
