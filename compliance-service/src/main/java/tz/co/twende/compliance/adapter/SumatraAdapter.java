package tz.co.twende.compliance.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.compliance.entity.TripReport;
import tz.co.twende.compliance.exception.ComplianceException;

@Component
@Slf4j
public class SumatraAdapter implements ComplianceAdapter {

    private final RestClient restClient;
    private final boolean enabled;

    public SumatraAdapter(
            @Value("${twende.compliance.sumatra.base-url}") String baseUrl,
            @Value("${twende.compliance.sumatra.api-key}") String apiKey,
            @Value("${twende.compliance.sumatra.enabled}") boolean enabled) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Authorization", "Bearer " + apiKey)
                        .defaultHeader("Content-Type", "application/json")
                        .build();
        this.enabled = enabled;
    }

    @Override
    public String getCountryCode() {
        return "TZ";
    }

    @Override
    public boolean isTripReportingRequired() {
        return true;
    }

    @Override
    public void submitTripReport(TripReport report) throws ComplianceException {
        if (!enabled) {
            log.info(
                    "SUMATRA integration disabled. Simulating submission for ride {}",
                    report.getRideId());
            report.setSubmissionRef("SIMULATED-" + report.getRideId());
            return;
        }

        try {
            restClient
                    .post()
                    .uri("/trips")
                    .body(buildPayload(report))
                    .retrieve()
                    .toBodilessEntity();
            report.setSubmissionRef("SUMATRA-" + report.getRideId());
        } catch (Exception e) {
            throw new ComplianceException("SUMATRA submission failed: " + e.getMessage(), e);
        }
    }

    private Object buildPayload(TripReport report) {
        return java.util.Map.of(
                "rideId", report.getRideId().toString(),
                "driverId", report.getDriverId().toString(),
                "vehicleType", report.getVehicleType(),
                "pickupLat", report.getPickupLat(),
                "pickupLng", report.getPickupLng(),
                "dropoffLat", report.getDropoffLat(),
                "dropoffLng", report.getDropoffLng(),
                "distanceMetres",
                        report.getDistanceMetres() != null ? report.getDistanceMetres() : 0,
                "fare", report.getFare() != null ? report.getFare() : java.math.BigDecimal.ZERO);
    }
}
