package tz.co.twende.analytics.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import tz.co.twende.analytics.entity.AnalyticsEvent;
import tz.co.twende.analytics.repository.AnalyticsEventRepository;

@ExtendWith(MockitoExtension.class)
class SummaryRefreshServiceTest {

    @Mock private AnalyticsEventRepository eventRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks private SummaryRefreshService summaryRefreshService;

    @Test
    void givenRideCompletedEvents_whenRefreshForDate_thenUpsertsForEachDriver() {
        LocalDate date = LocalDate.of(2026, 4, 8);
        UUID driver1 = UUID.randomUUID();
        UUID driver2 = UUID.randomUUID();

        AnalyticsEvent e1 =
                createEvent(
                        driver1, "TZ", "{\"finalFare\": 5000, \"driverId\": \"" + driver1 + "\"}");
        AnalyticsEvent e2 =
                createEvent(
                        driver1, "TZ", "{\"finalFare\": 3000, \"driverId\": \"" + driver1 + "\"}");
        AnalyticsEvent e3 =
                createEvent(
                        driver2, "TZ", "{\"finalFare\": 7000, \"driverId\": \"" + driver2 + "\"}");

        when(eventRepository.findByEventTypeAndOccurredAtBetween(
                        eq("RIDE_COMPLETED"), any(), any()))
                .thenReturn(List.of(e1, e2, e3));

        summaryRefreshService.refreshForDate(date);

        verify(jdbcTemplate, times(2)).update(anyString(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void givenNoEvents_whenRefreshForDate_thenNoUpsertsPerformed() {
        LocalDate date = LocalDate.of(2026, 4, 8);

        when(eventRepository.findByEventTypeAndOccurredAtBetween(
                        eq("RIDE_COMPLETED"), any(), any()))
                .thenReturn(Collections.emptyList());

        summaryRefreshService.refreshForDate(date);

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void givenPayloadWithFinalFare_whenExtractFinalFare_thenReturnsCorrectValue() {
        AnalyticsEvent event = new AnalyticsEvent();
        event.setPayload("{\"finalFare\": 5000.50}");

        BigDecimal result = summaryRefreshService.extractFinalFare(event);

        assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(5000.50));
    }

    @Test
    void givenPayloadWithoutFinalFare_whenExtractFinalFare_thenReturnsZero() {
        AnalyticsEvent event = new AnalyticsEvent();
        event.setPayload("{\"otherField\": 100}");

        BigDecimal result = summaryRefreshService.extractFinalFare(event);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void givenNullPayload_whenExtractFinalFare_thenReturnsZero() {
        AnalyticsEvent event = new AnalyticsEvent();
        event.setPayload(null);

        BigDecimal result = summaryRefreshService.extractFinalFare(event);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private AnalyticsEvent createEvent(UUID actorId, String countryCode, String payload) {
        AnalyticsEvent event = new AnalyticsEvent();
        event.setActorId(actorId);
        event.setCountryCode(countryCode);
        event.setEventType("RIDE_COMPLETED");
        event.setPayload(payload);
        event.setOccurredAt(Instant.now());
        return event;
    }
}
