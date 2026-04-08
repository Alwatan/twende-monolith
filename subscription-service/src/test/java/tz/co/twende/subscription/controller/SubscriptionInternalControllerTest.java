package tz.co.twende.subscription.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.subscription.dto.RevenueModelDto;
import tz.co.twende.subscription.service.RevenueModelService;
import tz.co.twende.subscription.service.SubscriptionService;

@ExtendWith(MockitoExtension.class)
class SubscriptionInternalControllerTest {

    @Mock private SubscriptionService subscriptionService;
    @Mock private RevenueModelService revenueModelService;

    @InjectMocks private SubscriptionInternalController controller;

    @Test
    void givenActiveRevenueModel_whenCheckActive_thenReturnsTrue() {
        UUID driverId = UUID.randomUUID();
        when(revenueModelService.hasActiveRevenueModel(driverId)).thenReturn(true);

        ResponseEntity<ApiResponse<Boolean>> response = controller.hasActiveSubscription(driverId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isTrue();
    }

    @Test
    void givenNoActiveRevenueModel_whenCheckActive_thenReturnsFalse() {
        UUID driverId = UUID.randomUUID();
        when(revenueModelService.hasActiveRevenueModel(driverId)).thenReturn(false);

        ResponseEntity<ApiResponse<Boolean>> response = controller.hasActiveSubscription(driverId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isFalse();
    }

    @Test
    void givenFlatFeeDriver_whenGetRevenueModel_thenReturnsDto() {
        UUID driverId = UUID.randomUUID();
        RevenueModelDto dto =
                RevenueModelDto.builder()
                        .driverId(driverId)
                        .revenueModel("FLAT_FEE")
                        .serviceCategory("RIDE")
                        .hasActiveSubscription(false)
                        .build();
        when(revenueModelService.getRevenueModel(driverId)).thenReturn(dto);

        ResponseEntity<ApiResponse<RevenueModelDto>> response =
                controller.getRevenueModel(driverId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getRevenueModel()).isEqualTo("FLAT_FEE");
    }
}
