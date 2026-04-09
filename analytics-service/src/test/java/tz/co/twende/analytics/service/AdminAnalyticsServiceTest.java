package tz.co.twende.analytics.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.analytics.dto.AdminOverviewDto;
import tz.co.twende.analytics.dto.CountryMetricsDto;
import tz.co.twende.analytics.repository.AnalyticsEventRepository;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

    @Mock private AnalyticsEventRepository eventRepository;

    @InjectMocks private AdminAnalyticsService adminAnalyticsService;

    @Test
    void givenEventsExist_whenGetOverview_thenReturnsCorrectKPIs() {
        when(eventRepository.countByEventTypeAndOccurredAtBetween(
                        eq("RIDE_COMPLETED"), any(), any()))
                .thenReturn(100L);
        when(eventRepository.countDistinctActorByEventType(eq("DRIVER_APPROVED"), any(), any()))
                .thenReturn(25L);
        when(eventRepository.countDistinctActorByEventType(eq("USER_REGISTERED"), any(), any()))
                .thenReturn(500L);
        when(eventRepository.countByEventTypeAndOccurredAtBetween(
                        eq("SUBSCRIPTION_ACTIVATED"), any(), any()))
                .thenReturn(20L);
        when(eventRepository.sumPayloadAmountByEventType(
                        eq("SUBSCRIPTION_ACTIVATED"), any(), any()))
                .thenReturn(BigDecimal.valueOf(400000));

        AdminOverviewDto result = adminAnalyticsService.getOverview();

        assertThat(result.getTotalRides()).isEqualTo(100);
        assertThat(result.getTotalDrivers()).isEqualTo(25);
        assertThat(result.getTotalRiders()).isEqualTo(500);
        assertThat(result.getActiveSubscriptions()).isEqualTo(20);
        assertThat(result.getRevenueFromSubscriptions())
                .isEqualByComparingTo(BigDecimal.valueOf(400000));
        assertThat(result.getCurrencyCode()).isEqualTo("TZS");
    }

    @Test
    void givenNullRevenue_whenGetOverview_thenDefaultsToZero() {
        when(eventRepository.countByEventTypeAndOccurredAtBetween(anyString(), any(), any()))
                .thenReturn(0L);
        when(eventRepository.countDistinctActorByEventType(anyString(), any(), any()))
                .thenReturn(0L);
        when(eventRepository.sumPayloadAmountByEventType(anyString(), any(), any()))
                .thenReturn(null);

        AdminOverviewDto result = adminAnalyticsService.getOverview();

        assertThat(result.getRevenueFromSubscriptions()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void givenCountryCode_whenGetCountryMetrics_thenReturnsCorrectMetrics() {
        when(eventRepository.countByEventTypeAndCountryCodeAndOccurredAtBetween(
                        eq("RIDE_COMPLETED"), eq("TZ"), any(), any()))
                .thenReturn(80L);
        when(eventRepository.countByEventTypeAndCountryCodeAndOccurredAtBetween(
                        eq("RIDE_CANCELLED"), eq("TZ"), any(), any()))
                .thenReturn(5L);
        when(eventRepository.avgPayloadFinalFareByCountry(
                        eq("RIDE_COMPLETED"), eq("TZ"), any(), any()))
                .thenReturn(BigDecimal.valueOf(3500));
        when(eventRepository.sumPayloadFinalFareByCountry(
                        eq("RIDE_COMPLETED"), eq("TZ"), any(), any()))
                .thenReturn(BigDecimal.valueOf(280000));
        when(eventRepository.countByEventTypeAndCountryCodeAndOccurredAtBetween(
                        eq("DRIVER_APPROVED"), eq("TZ"), any(), any()))
                .thenReturn(10L);
        when(eventRepository.countByEventTypeAndCountryCodeAndOccurredAtBetween(
                        eq("USER_REGISTERED"), eq("TZ"), any(), any()))
                .thenReturn(200L);
        when(eventRepository.findTopVehicleTypeByCountry(
                        eq("RIDE_COMPLETED"), eq("TZ"), any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {"BAJAJ", 50L}));

        CountryMetricsDto result = adminAnalyticsService.getCountryMetrics("TZ");

        assertThat(result.getCountryCode()).isEqualTo("TZ");
        assertThat(result.getTotalRides()).isEqualTo(85);
        assertThat(result.getCompletedRides()).isEqualTo(80);
        assertThat(result.getCancelledRides()).isEqualTo(5);
        assertThat(result.getAverageFare()).isEqualByComparingTo(BigDecimal.valueOf(3500));
        assertThat(result.getNewDrivers()).isEqualTo(10);
        assertThat(result.getTopVehicleType()).isEqualTo("BAJAJ");
    }

    @Test
    void givenNoVehicleData_whenGetCountryMetrics_thenTopVehicleTypeIsUnknown() {
        when(eventRepository.countByEventTypeAndCountryCodeAndOccurredAtBetween(
                        anyString(), eq("KE"), any(), any()))
                .thenReturn(0L);
        when(eventRepository.avgPayloadFinalFareByCountry(anyString(), eq("KE"), any(), any()))
                .thenReturn(null);
        when(eventRepository.sumPayloadFinalFareByCountry(anyString(), eq("KE"), any(), any()))
                .thenReturn(null);
        when(eventRepository.findTopVehicleTypeByCountry(anyString(), eq("KE"), any(), any()))
                .thenReturn(Collections.emptyList());

        CountryMetricsDto result = adminAnalyticsService.getCountryMetrics("KE");

        assertThat(result.getTopVehicleType()).isEqualTo("UNKNOWN");
        assertThat(result.getAverageFare()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
