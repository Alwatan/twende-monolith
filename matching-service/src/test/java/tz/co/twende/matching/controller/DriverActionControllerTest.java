package tz.co.twende.matching.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.matching.dto.AcceptRejectResponse;
import tz.co.twende.matching.service.AcceptanceService;

@ExtendWith(MockitoExtension.class)
class DriverActionControllerTest {

    @Mock private AcceptanceService acceptanceService;

    @InjectMocks private DriverActionController controller;

    @Test
    void givenAcceptRequest_whenAcceptRide_thenReturnsApiResponse() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        AcceptRejectResponse expected =
                AcceptRejectResponse.builder()
                        .rideId(rideId)
                        .driverId(driverId)
                        .action("ACCEPT")
                        .success(true)
                        .message("Ride accepted successfully")
                        .build();

        when(acceptanceService.acceptOffer(rideId, driverId, "TZ")).thenReturn(expected);

        ResponseEntity<ApiResponse<AcceptRejectResponse>> response =
                controller.acceptRide(rideId, driverId, "TZ");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().isSuccess()).isTrue();
    }

    @Test
    void givenRejectRequest_whenRejectRide_thenReturnsApiResponse() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        AcceptRejectResponse expected =
                AcceptRejectResponse.builder()
                        .rideId(rideId)
                        .driverId(driverId)
                        .action("REJECT")
                        .success(true)
                        .message("Offer rejected")
                        .build();

        when(acceptanceService.rejectOffer(rideId, driverId, "TZ")).thenReturn(expected);

        ResponseEntity<ApiResponse<AcceptRejectResponse>> response =
                controller.rejectRide(rideId, driverId, "TZ");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getAction()).isEqualTo("REJECT");
    }

    @Test
    void givenFailedAcceptance_whenAcceptRide_thenReturnsFailureData() {
        UUID rideId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        AcceptRejectResponse expected =
                AcceptRejectResponse.builder()
                        .rideId(rideId)
                        .driverId(driverId)
                        .action("ACCEPT")
                        .success(false)
                        .message("Ride already accepted by another driver")
                        .build();

        when(acceptanceService.acceptOffer(rideId, driverId, "KE")).thenReturn(expected);

        ResponseEntity<ApiResponse<AcceptRejectResponse>> response =
                controller.acceptRide(rideId, driverId, "KE");

        assertThat(response.getBody().getData().isSuccess()).isFalse();
        assertThat(response.getBody().getData().getMessage())
                .isEqualTo("Ride already accepted by another driver");
    }
}
