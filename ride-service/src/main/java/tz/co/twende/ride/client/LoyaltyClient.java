package tz.co.twende.ride.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class LoyaltyClient {

    private final RestClient restClient;

    public LoyaltyClient(@Value("${twende.services.loyalty-service.url}") String baseUrl) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Accept", "application/json")
                        .build();
    }

    /**
     * Check for an applicable free ride offer. Returns offer details or null if no offer available.
     */
    public Map<String, Object> findApplicableOffer(
            UUID riderId, String vehicleType, BigDecimal distanceKm) {
        try {
            return restClient
                    .get()
                    .uri(
                            "/internal/loyalty/offers/applicable?riderId={riderId}"
                                    + "&vehicleType={vt}&distanceKm={dist}",
                            riderId,
                            vehicleType,
                            distanceKm)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("No free ride offer available for rider {}: {}", riderId, e.getMessage());
            return null;
        }
    }

    /** Redeem a free ride offer. */
    public boolean redeemOffer(UUID offerId, UUID rideId) {
        try {
            restClient
                    .post()
                    .uri("/internal/loyalty/offers/{offerId}/redeem", offerId)
                    .body(Map.of("rideId", rideId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("Failed to redeem loyalty offer {}: {}", offerId, e.getMessage());
            return false;
        }
    }
}
