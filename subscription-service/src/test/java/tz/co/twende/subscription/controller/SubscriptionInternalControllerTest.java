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
import tz.co.twende.subscription.service.SubscriptionService;

@ExtendWith(MockitoExtension.class)
class SubscriptionInternalControllerTest {

    @Mock private SubscriptionService subscriptionService;

    @InjectMocks private SubscriptionInternalController controller;

    @Test
    void givenActiveSubscription_whenCheckActive_thenReturnsTrue() {
        UUID driverId = UUID.randomUUID();
        when(subscriptionService.hasActiveSubscription(driverId)).thenReturn(true);

        ResponseEntity<ApiResponse<Boolean>> response = controller.hasActiveSubscription(driverId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isTrue();
    }

    @Test
    void givenNoActiveSubscription_whenCheckActive_thenReturnsFalse() {
        UUID driverId = UUID.randomUUID();
        when(subscriptionService.hasActiveSubscription(driverId)).thenReturn(false);

        ResponseEntity<ApiResponse<Boolean>> response = controller.hasActiveSubscription(driverId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isFalse();
    }
}
