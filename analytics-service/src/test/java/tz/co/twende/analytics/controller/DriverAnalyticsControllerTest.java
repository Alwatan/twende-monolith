package tz.co.twende.analytics.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.analytics.dto.DriverEarningsDto;
import tz.co.twende.analytics.dto.DriverTripStatsDto;
import tz.co.twende.analytics.service.DriverAnalyticsService;
import tz.co.twende.common.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
class DriverAnalyticsControllerTest {

    @Mock private DriverAnalyticsService driverAnalyticsService;

    @InjectMocks private DriverAnalyticsController controller;

    @Test
    void givenValidDriverId_whenGetEarnings_thenReturnsOk() {
        UUID driverId = UUID.randomUUID();
        DriverEarningsDto dto =
                DriverEarningsDto.builder()
                        .driverId(driverId)
                        .period("WEEKLY")
                        .totalEarned(BigDecimal.valueOf(10000))
                        .tripCount(5)
                        .dailyBreakdown(Collections.emptyList())
                        .build();

        when(driverAnalyticsService.getEarnings(driverId, "WEEKLY")).thenReturn(dto);

        ResponseEntity<ApiResponse<DriverEarningsDto>> result =
                controller.getEarnings(driverId, "WEEKLY");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getData().getTripCount()).isEqualTo(5);
    }

    @Test
    void givenValidDriverId_whenGetTripStats_thenReturnsOk() {
        UUID driverId = UUID.randomUUID();
        DriverTripStatsDto dto =
                DriverTripStatsDto.builder()
                        .totalTrips(10)
                        .totalEarned(BigDecimal.valueOf(20000))
                        .build();

        when(driverAnalyticsService.getTripStats(eq(driverId), any(), any())).thenReturn(dto);

        ResponseEntity<ApiResponse<DriverTripStatsDto>> result =
                controller.getTripStats(driverId, null, null);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().getData().getTotalTrips()).isEqualTo(10);
    }
}
