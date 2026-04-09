package tz.co.twende.analytics.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.analytics.entity.AnalyticsEvent;
import tz.co.twende.analytics.repository.AnalyticsEventRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class SummaryRefreshService {

    private final AnalyticsEventRepository eventRepository;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void refreshDailySummaries() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        refreshForDate(yesterday);
    }

    @Transactional
    public void refreshForDate(LocalDate date) {
        log.info("Refreshing daily summaries for date={}", date);
        Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<AnalyticsEvent> completedEvents =
                eventRepository.findByEventTypeAndOccurredAtBetween("RIDE_COMPLETED", from, to);

        if (completedEvents.isEmpty()) {
            log.info("No RIDE_COMPLETED events found for date={}", date);
            return;
        }

        // Group by actorId (driverId)
        var grouped =
                completedEvents.stream()
                        .filter(e -> e.getActorId() != null)
                        .collect(
                                java.util.stream.Collectors.groupingBy(AnalyticsEvent::getActorId));

        for (var entry : grouped.entrySet()) {
            UUID driverId = entry.getKey();
            List<AnalyticsEvent> driverEvents = entry.getValue();

            int tripCount = driverEvents.size();
            BigDecimal totalEarned =
                    driverEvents.stream()
                            .map(this::extractFinalFare)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

            String countryCode = driverEvents.getFirst().getCountryCode();

            upsertSummary(driverId, countryCode, date, tripCount, totalEarned);
        }

        log.info("Refreshed daily summaries for date={}, drivers={}", date, grouped.size());
    }

    BigDecimal extractFinalFare(AnalyticsEvent event) {
        try {
            String payload = event.getPayload();
            if (payload == null) {
                return BigDecimal.ZERO;
            }
            // Simple JSON field extraction without full deserialization
            int idx = payload.indexOf("\"finalFare\"");
            if (idx < 0) {
                return BigDecimal.ZERO;
            }
            String afterKey = payload.substring(idx + "\"finalFare\"".length());
            int colonIdx = afterKey.indexOf(':');
            if (colonIdx < 0) {
                return BigDecimal.ZERO;
            }
            String afterColon = afterKey.substring(colonIdx + 1).trim();
            StringBuilder numStr = new StringBuilder();
            for (char c : afterColon.toCharArray()) {
                if (Character.isDigit(c) || c == '.' || c == '-') {
                    numStr.append(c);
                } else if (!numStr.isEmpty()) {
                    break;
                }
            }
            return numStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(numStr.toString());
        } catch (Exception e) {
            log.warn("Failed to extract finalFare from event payload", e);
            return BigDecimal.ZERO;
        }
    }

    private void upsertSummary(
            UUID driverId,
            String countryCode,
            LocalDate date,
            int tripCount,
            BigDecimal totalEarned) {
        jdbcTemplate.update(
                "INSERT INTO driver_daily_summaries "
                        + "(id, driver_id, country_code, date, trip_count, total_earned,"
                        + " online_hours, created_at, updated_at) "
                        + "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, 0, now(), now()) "
                        + "ON CONFLICT (driver_id, date) DO UPDATE SET "
                        + "trip_count = EXCLUDED.trip_count, "
                        + "total_earned = EXCLUDED.total_earned, "
                        + "updated_at = now()",
                driverId,
                countryCode,
                date,
                tripCount,
                totalEarned);
    }
}
