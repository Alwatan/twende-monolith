package tz.co.twende.analytics.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tz.co.twende.analytics.dto.AdminOverviewDto;
import tz.co.twende.analytics.dto.CountryMetricsDto;
import tz.co.twende.analytics.service.AdminAnalyticsService;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.common.response.ApiResponse;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsControllerTest {

    @Mock private AdminAnalyticsService adminAnalyticsService;

    @InjectMocks private AdminAnalyticsController controller;

    @Test
    void givenAdminRole_whenGetOverview_thenReturnsOk() {
        AdminOverviewDto dto =
                AdminOverviewDto.builder()
                        .totalRides(100)
                        .totalDrivers(25)
                        .revenueFromSubscriptions(BigDecimal.valueOf(500000))
                        .build();

        when(adminAnalyticsService.getOverview()).thenReturn(dto);

        ResponseEntity<ApiResponse<AdminOverviewDto>> result = controller.getOverview("ADMIN");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().getData().getTotalRides()).isEqualTo(100);
    }

    @Test
    void givenNonAdminRole_whenGetOverview_thenThrowsUnauthorized() {
        assertThatThrownBy(() -> controller.getOverview("DRIVER"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Admin access required");
    }

    @Test
    void givenAdminRole_whenGetCountryMetrics_thenReturnsOk() {
        CountryMetricsDto dto =
                CountryMetricsDto.builder()
                        .countryCode("TZ")
                        .totalRides(80)
                        .completedRides(75)
                        .cancelledRides(5)
                        .build();

        when(adminAnalyticsService.getCountryMetrics("TZ")).thenReturn(dto);

        ResponseEntity<ApiResponse<CountryMetricsDto>> result =
                controller.getCountryMetrics("ADMIN", "TZ");

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().getData().getCountryCode()).isEqualTo("TZ");
    }

    @Test
    void givenRiderRole_whenGetCountryMetrics_thenThrowsUnauthorized() {
        assertThatThrownBy(() -> controller.getCountryMetrics("RIDER", "TZ"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
