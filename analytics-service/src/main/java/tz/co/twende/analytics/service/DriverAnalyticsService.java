package tz.co.twende.analytics.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tz.co.twende.analytics.dto.DailyBreakdownDto;
import tz.co.twende.analytics.dto.DriverEarningsDto;
import tz.co.twende.analytics.dto.DriverTripStatsDto;
import tz.co.twende.analytics.entity.DriverDailySummary;
import tz.co.twende.analytics.repository.DriverDailySummaryRepository;
import tz.co.twende.common.util.CurrencyUtil;

@Service
@RequiredArgsConstructor
public class DriverAnalyticsService {

    private final DriverDailySummaryRepository summaryRepository;

    public DriverEarningsDto getEarnings(UUID driverId, String period) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = resolveFrom(now, period);

        List<DriverDailySummary> summaries =
                summaryRepository.findByDriverIdAndDateBetweenOrderByDateAsc(driverId, from, now);

        BigDecimal totalEarned = BigDecimal.ZERO;
        int tripCount = 0;
        BigDecimal onlineHours = BigDecimal.ZERO;

        List<DailyBreakdownDto> breakdown = new java.util.ArrayList<>();
        for (DriverDailySummary s : summaries) {
            totalEarned = totalEarned.add(s.getTotalEarned());
            tripCount += s.getTripCount();
            onlineHours = onlineHours.add(s.getOnlineHours());
            breakdown.add(
                    DailyBreakdownDto.builder()
                            .date(s.getDate())
                            .earned(s.getTotalEarned())
                            .trips(s.getTripCount())
                            .onlineHours(s.getOnlineHours())
                            .build());
        }

        return DriverEarningsDto.builder()
                .driverId(driverId)
                .period(period)
                .totalEarned(totalEarned)
                .currencyCode("TZS")
                .displayTotal(CurrencyUtil.format(totalEarned, "TZS"))
                .tripCount(tripCount)
                .onlineHours(onlineHours)
                .dailyBreakdown(breakdown)
                .build();
    }

    public DriverTripStatsDto getTripStats(UUID driverId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            LocalDate now = LocalDate.now(ZoneOffset.UTC);
            to = now;
            from = now.minusDays(7);
        }

        Object[] agg = summaryRepository.aggregateByDriverAndDateRange(driverId, from, to);

        BigDecimal totalEarned = BigDecimal.ZERO;
        int totalTrips = 0;
        BigDecimal onlineHrs = BigDecimal.ZERO;

        if (agg != null && agg.length > 0 && agg[0] instanceof Object[] row) {
            totalEarned = row[0] != null ? (BigDecimal) row[0] : BigDecimal.ZERO;
            totalTrips = row[1] != null ? ((Number) row[1]).intValue() : 0;
            onlineHrs = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;
        } else if (agg != null && agg.length >= 3) {
            totalEarned = agg[0] != null ? (BigDecimal) agg[0] : BigDecimal.ZERO;
            totalTrips = agg[1] != null ? ((Number) agg[1]).intValue() : 0;
            onlineHrs = agg[2] != null ? (BigDecimal) agg[2] : BigDecimal.ZERO;
        }

        return DriverTripStatsDto.builder()
                .totalTrips(totalTrips)
                .totalEarned(totalEarned)
                .onlineHours(onlineHrs)
                .periodStart(from.toString())
                .periodEnd(to.toString())
                .build();
    }

    LocalDate resolveFrom(LocalDate now, String period) {
        return switch (period != null ? period.toUpperCase() : "WEEKLY") {
            case "DAILY" -> now;
            case "MONTHLY" -> now.minusDays(30);
            default -> now.minusDays(7);
        };
    }
}
