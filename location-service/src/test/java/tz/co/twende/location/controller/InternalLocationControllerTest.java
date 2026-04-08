package tz.co.twende.location.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.location.dto.NearbyDriverResponse;
import tz.co.twende.location.entity.TripTrace;
import tz.co.twende.location.service.LocationService;
import tz.co.twende.location.service.TripTraceService;

@ExtendWith(MockitoExtension.class)
class InternalLocationControllerTest {

    @Mock private LocationService locationService;
    @Mock private TripTraceService tripTraceService;

    @InjectMocks private InternalLocationController controller;

    @Test
    void givenNearbyDriversExist_whenQueryNearby_thenReturnDriverList() {
        BigDecimal lat = new BigDecimal("-6.792");
        BigDecimal lng = new BigDecimal("39.208");
        BigDecimal radius = new BigDecimal("5");
        NearbyDriverResponse driver =
                NearbyDriverResponse.builder()
                        .driverId(UUID.randomUUID())
                        .latitude(lat)
                        .longitude(lng)
                        .distanceKm(new BigDecimal("1.5"))
                        .build();

        when(locationService.findNearbyDrivers("TZ", "BAJAJ", lat, lng, radius))
                .thenReturn(List.of(driver));

        ResponseEntity<ApiResponse<List<NearbyDriverResponse>>> response =
                controller.nearbyDrivers("TZ", "BAJAJ", lat, lng, radius);

        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getDriverId())
                .isEqualTo(driver.getDriverId());
    }

    @Test
    void givenDriverExists_whenGetDriverLocation_thenReturnLocation() {
        UUID driverId = UUID.randomUUID();
        NearbyDriverResponse location =
                NearbyDriverResponse.builder()
                        .driverId(driverId)
                        .latitude(new BigDecimal("-6.792"))
                        .longitude(new BigDecimal("39.208"))
                        .build();
        when(locationService.getDriverLocation(driverId)).thenReturn(location);

        ResponseEntity<ApiResponse<NearbyDriverResponse>> response =
                controller.driverLocation(driverId);

        assertThat(response.getBody().getData().getDriverId()).isEqualTo(driverId);
    }

    @Test
    void givenTraceExists_whenGetTripTrace_thenReturnTrace() {
        UUID rideId = UUID.randomUUID();
        TripTrace trace = new TripTrace();
        trace.setRideId(rideId);
        trace.setTrace("[{\"lat\":-6.792,\"lng\":39.208,\"ts\":\"2025-01-01T00:00:00Z\"}]");

        when(tripTraceService.getTrace(rideId)).thenReturn(trace);

        ResponseEntity<ApiResponse<TripTrace>> response = controller.tripTrace(rideId);

        assertThat(response.getBody().getData().getRideId()).isEqualTo(rideId);
    }
}
