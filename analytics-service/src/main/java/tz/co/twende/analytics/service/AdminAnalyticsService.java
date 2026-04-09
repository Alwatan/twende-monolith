package tz.co.twende.analytics.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tz.co.twende.analytics.dto.AdminOverviewDto;
import tz.co.twende.analytics.dto.CountryMetricsDto;
import tz.co.twende.analytics.repository.AnalyticsEventRepository;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private final AnalyticsEventRepository eventRepository;

    public AdminOverviewDto getOverview() {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = now.minusDays(7);
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = now.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long totalRides =
                eventRepository.countByEventTypeAndOccurredAtBetween(
                        "RIDE_COMPLETED", fromInstant, toInstant);
        long totalDrivers =
                eventRepository.countDistinctActorByEventType(
                        "DRIVER_APPROVED", fromInstant, toInstant);
        long totalRiders =
                eventRepository.countDistinctActorByEventType(
                        "USER_REGISTERED", fromInstant, toInstant);
        long activeSubscriptions =
                eventRepository.countByEventTypeAndOccurredAtBetween(
                        "SUBSCRIPTION_ACTIVATED", fromInstant, toInstant);

        BigDecimal revenue =
                eventRepository.sumPayloadAmountByEventType(
                        "SUBSCRIPTION_ACTIVATED", fromInstant, toInstant);
        if (revenue == null) {
            revenue = BigDecimal.ZERO;
        }

        return AdminOverviewDto.builder()
                .totalRides(totalRides)
                .totalDrivers(totalDrivers)
                .totalRiders(totalRiders)
                .activeSubscriptions(activeSubscriptions)
                .revenueFromSubscriptions(revenue)
                .currencyCode("TZS")
                .periodStart(from.toString())
                .periodEnd(now.toString())
                .build();
    }

    public CountryMetricsDto getCountryMetrics(String countryCode) {
        LocalDate now = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = now.minusDays(7);
        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = now.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        long completedRides =
                eventRepository.countByEventTypeAndCountryCodeAndOccurredAtBetween(
                        "RIDE_COMPLETED", countryCode, fromInstant, toInstant);
        long cancelledRides =
                eventRepository.countByEventTypeAndCountryCodeAndOccurredAtBetween(
                        "RIDE_CANCELLED", countryCode, fromInstant, toInstant);
        long totalRides = completedRides + cancelledRides;

        BigDecimal avgFare =
                eventRepository.avgPayloadFinalFareByCountry(
                        "RIDE_COMPLETED", countryCode, fromInstant, toInstant);
        if (avgFare == null) {
            avgFare = BigDecimal.ZERO;
        }

        BigDecimal totalFareVolume =
                eventRepository.sumPayloadFinalFareByCountry(
                        "RIDE_COMPLETED", countryCode, fromInstant, toInstant);
        if (totalFareVolume == null) {
            totalFareVolume = BigDecimal.ZERO;
        }

        long newDrivers =
                eventRepository.countByEventTypeAndCountryCodeAndOccurredAtBetween(
                        "DRIVER_APPROVED", countryCode, fromInstant, toInstant);
        long newRiders =
                eventRepository.countByEventTypeAndCountryCodeAndOccurredAtBetween(
                        "USER_REGISTERED", countryCode, fromInstant, toInstant);

        String topVehicleType = resolveTopVehicleType(countryCode, fromInstant, toInstant);

        return CountryMetricsDto.builder()
                .countryCode(countryCode)
                .totalRides(totalRides)
                .completedRides(completedRides)
                .cancelledRides(cancelledRides)
                .averageFare(avgFare)
                .totalFareVolume(totalFareVolume)
                .newDrivers(newDrivers)
                .newRiders(newRiders)
                .topVehicleType(topVehicleType)
                .periodStart(from.toString())
                .periodEnd(now.toString())
                .build();
    }

    private String resolveTopVehicleType(String countryCode, Instant from, Instant to) {
        List<Object[]> results =
                eventRepository.findTopVehicleTypeByCountry(
                        "RIDE_COMPLETED", countryCode, from, to);
        if (results != null && !results.isEmpty()) {
            Object[] row = results.getFirst();
            return row[0] != null ? row[0].toString() : "UNKNOWN";
        }
        return "UNKNOWN";
    }
}
