package tz.co.twende.payment.client;

import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.payment.dto.response.FlatFeeConfigDto;

@Component
@Slf4j
public class ConfigClient {

    private final RestClient restClient;

    public ConfigClient(@Value("${twende.services.country-config-service.url}") String baseUrl) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Accept", "application/json")
                        .build();
    }

    public BigDecimal getFlatFeePercentage(String countryCode, String serviceCategory) {
        try {
            ApiResponse<FlatFeeConfigDto> response =
                    restClient
                            .get()
                            .uri(
                                    "/internal/config/{countryCode}/flat-fee/{serviceCategory}",
                                    countryCode,
                                    serviceCategory)
                            .retrieve()
                            .body(
                                    new ParameterizedTypeReference<
                                            ApiResponse<FlatFeeConfigDto>>() {});
            if (response != null && response.getData() != null) {
                return response.getData().getPercentage();
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to get flat fee config for {}/{}: {}",
                    countryCode,
                    serviceCategory,
                    e.getMessage());
        }
        // Default fallback percentage
        return new BigDecimal("15.00");
    }
}
