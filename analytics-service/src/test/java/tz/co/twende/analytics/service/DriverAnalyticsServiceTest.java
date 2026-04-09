package tz.co.twende.analytics.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.analytics.dto.DriverEarningsDto;
import tz.co.twende.analytics.dto.DriverTripStatsDto;
import tz.co.twende.analytics.entity.DriverDailySummary;
import tz.co.twende.analytics.repository.DriverDailySummaryRepository;

@ExtendWith(MockitoExtension.class)
class DriverAnalyticsServiceTest {

    @Mock private DriverDailySummaryRepository summaryRepository;

    @InjectMocks private DriverAnalyticsService driverAnalyticsService;

    @Test
    void givenWeeklyPeriod_whenGetEarnings_thenAggregatesCorrectly() {
        UUID driverId = UUID.randomUUID();

        DriverDailySummary s1 = new DriverDailySummary();
        s1.setDriverId(driverId);
        s1.setDate(LocalDate.now().minusDays(2));
        s1.setTripCount(5);
        s1.setTotalEarned(BigDecimal.valueOf(10000));
        s1.setOnlineHours(BigDecimal.valueOf(4));

        DriverDailySummary s2 = new DriverDailySummary();
        s2.setDriverId(driverId);
        s2.setDate(LocalDate.now().minusDays(1));
        s2.setTripCount(3);
        s2.setTotalEarned(BigDecimal.valueOf(6000));
        s2.setOnlineHours(BigDecimal.valueOf(3));

        when(summaryRepository.findByDriverIdAndDateBetweenOrderByDateAsc(
                        eq(driverId), any(), any()))
                .thenReturn(List.of(s1, s2));

        DriverEarningsDto result = driverAnalyticsService.getEarnings(driverId, "WEEKLY");

        assertThat(result.getDriverId()).isEqualTo(driverId);
        assertThat(result.getPeriod()).isEqualTo("WEEKLY");
        assertThat(result.getTotalEarned()).isEqualByComparingTo(BigDecimal.valueOf(16000));
        assertThat(result.getTripCount()).isEqualTo(8);
        assertThat(result.getOnlineHours()).isEqualByComparingTo(BigDecimal.valueOf(7));
        assertThat(result.getDailyBreakdown()).hasSize(2);
    }

    @Test
    void givenNoSummaries_whenGetEarnings_thenReturnsZeros() {
        UUID driverId = UUID.randomUUID();

        when(summaryRepository.findByDriverIdAndDateBetweenOrderByDateAsc(
                        eq(driverId), any(), any()))
                .thenReturn(Collections.emptyList());

        DriverEarningsDto result = driverAnalyticsService.getEarnings(driverId, "DAILY");

        assertThat(result.getTotalEarned()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTripCount()).isZero();
        assertThat(result.getDailyBreakdown()).isEmpty();
    }

    @Test
    void givenMonthlyPeriod_whenResolveFrom_thenMinus30Days() {
        LocalDate now = LocalDate.of(2026, 4, 9);
        LocalDate result = driverAnalyticsService.resolveFrom(now, "MONTHLY");
        assertThat(result).isEqualTo(LocalDate.of(2026, 3, 10));
    }

    @Test
    void givenNullPeriod_whenResolveFrom_thenDefaultsToWeekly() {
        LocalDate now = LocalDate.of(2026, 4, 9);
        LocalDate result = driverAnalyticsService.resolveFrom(now, null);
        assertThat(result).isEqualTo(now.minusDays(7));
    }

    @Test
    void givenNullDateRange_whenGetTripStats_thenDefaultsToLast7Days() {
        UUID driverId = UUID.randomUUID();
        Object[] agg = new Object[] {BigDecimal.valueOf(5000), 10L, BigDecimal.valueOf(8)};

        when(summaryRepository.aggregateByDriverAndDateRange(eq(driverId), any(), any()))
                .thenReturn(agg);

        DriverTripStatsDto result = driverAnalyticsService.getTripStats(driverId, null, null);

        assertThat(result.getTotalTrips()).isEqualTo(10);
        assertThat(result.getTotalEarned()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    void givenDateRange_whenGetTripStats_thenUsesProvidedDates() {
        UUID driverId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 4, 1);
        LocalDate to = LocalDate.of(2026, 4, 7);
        Object[] agg = new Object[] {BigDecimal.valueOf(20000), 15L, BigDecimal.valueOf(12)};

        when(summaryRepository.aggregateByDriverAndDateRange(driverId, from, to)).thenReturn(agg);

        DriverTripStatsDto result = driverAnalyticsService.getTripStats(driverId, from, to);

        assertThat(result.getTotalTrips()).isEqualTo(15);
        assertThat(result.getPeriodStart()).isEqualTo("2026-04-01");
        assertThat(result.getPeriodEnd()).isEqualTo("2026-04-07");
    }
}
