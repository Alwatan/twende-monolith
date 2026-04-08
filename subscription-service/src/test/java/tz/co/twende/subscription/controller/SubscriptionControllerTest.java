package tz.co.twende.subscription.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.response.ApiResponse;
import tz.co.twende.subscription.dto.PurchaseRequest;
import tz.co.twende.subscription.dto.SubscriptionDto;
import tz.co.twende.subscription.dto.SubscriptionPlanDto;
import tz.co.twende.subscription.service.SubscriptionService;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock private SubscriptionService subscriptionService;

    @InjectMocks private SubscriptionController controller;

    @Test
    void givenValidCountry_whenGetPlans_thenReturn200() {
        SubscriptionPlanDto dto = new SubscriptionPlanDto();
        dto.setVehicleType("BAJAJ");
        dto.setPrice(BigDecimal.valueOf(2000));

        when(subscriptionService.getPlans("TZ", "BAJAJ")).thenReturn(List.of(dto));

        ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> response =
                controller.getPlans("TZ", "BAJAJ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getVehicleType()).isEqualTo("BAJAJ");
    }

    @Test
    void givenValidPurchaseRequest_whenPurchase_thenReturn201() {
        UUID driverId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        PurchaseRequest request = new PurchaseRequest(planId, "MOBILE_MONEY");

        SubscriptionDto dto = new SubscriptionDto();
        dto.setStatus("ACTIVE");

        when(subscriptionService.purchase(driverId, "TZ", planId, "MOBILE_MONEY")).thenReturn(dto);

        ResponseEntity<ApiResponse<SubscriptionDto>> response =
                controller.purchase(driverId, "TZ", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void givenDriverId_whenGetCurrentSubscription_thenReturn200() {
        UUID driverId = UUID.randomUUID();
        SubscriptionDto dto = new SubscriptionDto();
        dto.setStatus("ACTIVE");

        when(subscriptionService.getCurrentSubscription(driverId)).thenReturn(dto);

        ResponseEntity<ApiResponse<SubscriptionDto>> response =
                controller.getCurrentSubscription(driverId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void givenDriverId_whenGetHistory_thenReturnPaginatedResults() {
        UUID driverId = UUID.randomUUID();
        SubscriptionDto dto = new SubscriptionDto();
        dto.setDriverId(driverId);
        Page<SubscriptionDto> page = new PageImpl<>(List.of(dto));

        when(subscriptionService.getHistory(eq(driverId), any())).thenReturn(page);

        ResponseEntity<ApiResponse<Page<SubscriptionDto>>> response =
                controller.getHistory(driverId, 0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getContent()).hasSize(1);
    }
}
