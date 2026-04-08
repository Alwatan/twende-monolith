package tz.co.twende.pricing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.pricing.client.CountryConfigClient;
import tz.co.twende.pricing.client.LocationServiceClient;
import tz.co.twende.pricing.dto.*;

@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);
    private static final BigDecimal METRES_TO_KM = new BigDecimal("1000");
    private static final BigDecimal SECONDS_TO_MINUTES = new BigDecimal("60");
    private static final int TZS_SCALE = 0;

    private final CountryConfigClient countryConfigClient;
    private final LocationServiceClient locationServiceClient;
    private final SurgeService surgeService;

    public PricingService(
            CountryConfigClient countryConfigClient,
            LocationServiceClient locationServiceClient,
            SurgeService surgeService) {
        this.countryConfigClient = countryConfigClient;
        this.locationServiceClient = locationServiceClient;
        this.surgeService = surgeService;
    }

    public EstimateResponse calculateEstimate(EstimateRequest request) {
        // 1. Check pickup zone for RESTRICTED
        checkRestrictedZone(request.getPickupLat(), request.getPickupLng(), request.getCityId());

        // 2. Get route from location-service
        RouteDto route =
                locationServiceClient.getRoute(
                        request.getPickupLat(),
                        request.getPickupLng(),
                        request.getDropoffLat(),
                        request.getDropoffLng(),
                        request.getCityId());
        if (route == null) {
            throw new BadRequestException("Unable to calculate route for the given coordinates");
        }

        // 3. Get vehicle config from country-config-service
        VehicleTypeConfigDto vehicleConfig =
                countryConfigClient.getVehicleTypeConfig(
                        request.getCountryCode(), request.getVehicleType());
        if (vehicleConfig == null) {
            throw new ResourceNotFoundException(
                    "Vehicle type config not found: "
                            + request.getCountryCode()
                            + "/"
                            + request.getVehicleType());
        }

        // Check if this is a charter request
        boolean isCharter = "CHARTER".equals(request.getServiceCategory());

        if (isCharter) {
            return calculateCharterEstimate(
                    request, route, vehicleConfig, request.getCountryCode());
        }

        // 4. Get surge from Redis
        BigDecimal demandSurge =
                surgeService.getSurge(request.getCountryCode(), request.getVehicleType());

        // 5. Check zones for adjustments
        ZoneAdjustments adjustments =
                getZoneAdjustments(
                        request.getPickupLat(),
                        request.getPickupLng(),
                        request.getDropoffLat(),
                        request.getDropoffLng(),
                        request.getCityId(),
                        vehicleConfig.getSurgeMultiplierCap());

        // 6. Calculate fare
        BigDecimal effectiveSurge =
                calculateEffectiveSurge(
                        demandSurge, adjustments.zoneSurge, vehicleConfig.getSurgeMultiplierCap());

        FareResult fareResult =
                calculateFare(
                        vehicleConfig,
                        route.getDistanceMetres(),
                        route.getDurationSeconds(),
                        effectiveSurge,
                        adjustments.airportSurcharge);

        // 7. Build response
        String currency = getCurrency(request.getCountryCode());
        return EstimateResponse.builder()
                .estimatedFare(fareResult.totalFare)
                .currency(currency)
                .displayFare(formatFare(fareResult.totalFare, currency))
                .distanceMetres(route.getDistanceMetres())
                .durationSeconds(route.getDurationSeconds())
                .surgeMultiplier(effectiveSurge)
                .fareBreakdown(fareResult.breakdown)
                .build();
    }

    EstimateResponse calculateCharterEstimate(
            EstimateRequest request,
            RouteDto route,
            VehicleTypeConfigDto vehicleConfig,
            String countryCode) {
        BigDecimal distanceKm =
                BigDecimal.valueOf(route.getDistanceMetres())
                        .divide(METRES_TO_KM, 4, RoundingMode.HALF_UP);

        BigDecimal baseFare = vehicleConfig.getBaseFare();
        BigDecimal distanceFare = distanceKm.multiply(vehicleConfig.getPerKm());

        // Hourly fare for charter
        BigDecimal estimatedHours =
                request.getEstimatedHours() != null ? request.getEstimatedHours() : BigDecimal.ONE;
        BigDecimal perHour =
                vehicleConfig.getPerHour() != null ? vehicleConfig.getPerHour() : BigDecimal.ZERO;
        BigDecimal hourlyFare = estimatedHours.multiply(perHour);

        // Quality tier surcharge
        BigDecimal qualityTierSurcharge =
                vehicleConfig.getQualityTierSurcharge() != null
                        ? vehicleConfig.getQualityTierSurcharge()
                        : BigDecimal.ZERO;

        BigDecimal totalFare = baseFare.add(distanceFare).add(hourlyFare).add(qualityTierSurcharge);

        // Round trip: 2x distance with 10% discount on return leg
        if ("ROUND_TRIP".equals(request.getTripDirection())) {
            BigDecimal returnDistanceFare =
                    distanceFare.multiply(new BigDecimal("0.90")); // 10% discount
            totalFare = totalFare.add(returnDistanceFare);
        }

        // Round to TZS
        totalFare = totalFare.setScale(TZS_SCALE, RoundingMode.HALF_UP);
        baseFare = baseFare.setScale(TZS_SCALE, RoundingMode.HALF_UP);
        distanceFare = distanceFare.setScale(TZS_SCALE, RoundingMode.HALF_UP);
        hourlyFare = hourlyFare.setScale(TZS_SCALE, RoundingMode.HALF_UP);
        qualityTierSurcharge = qualityTierSurcharge.setScale(TZS_SCALE, RoundingMode.HALF_UP);

        FareBreakdown breakdown =
                FareBreakdown.builder()
                        .baseFare(baseFare)
                        .distanceFare(distanceFare)
                        .timeFare(BigDecimal.ZERO)
                        .surgeFare(BigDecimal.ZERO)
                        .airportSurcharge(BigDecimal.ZERO)
                        .minimumFareApplied(false)
                        .charterHourlyFare(hourlyFare)
                        .qualityTierSurcharge(qualityTierSurcharge)
                        .build();

        String currency = getCurrency(countryCode);
        return EstimateResponse.builder()
                .estimatedFare(totalFare)
                .currency(currency)
                .displayFare(formatFare(totalFare, currency))
                .distanceMetres(route.getDistanceMetres())
                .durationSeconds(route.getDurationSeconds())
                .surgeMultiplier(BigDecimal.ONE)
                .fareBreakdown(breakdown)
                .build();
    }

    public CalculateResponse calculateFinal(CalculateRequest request) {
        // 1. Check pickup zone for RESTRICTED
        checkRestrictedZone(request.getPickupLat(), request.getPickupLng(), request.getCityId());

        // 2. Get vehicle config
        VehicleTypeConfigDto vehicleConfig =
                countryConfigClient.getVehicleTypeConfig(
                        request.getCountryCode(), request.getVehicleType());
        if (vehicleConfig == null) {
            throw new ResourceNotFoundException(
                    "Vehicle type config not found: "
                            + request.getCountryCode()
                            + "/"
                            + request.getVehicleType());
        }

        // 3. Get surge
        BigDecimal demandSurge =
                surgeService.getSurge(request.getCountryCode(), request.getVehicleType());

        // 4. Check zones for adjustments
        ZoneAdjustments adjustments =
                getZoneAdjustments(
                        request.getPickupLat(),
                        request.getPickupLng(),
                        request.getDropoffLat(),
                        request.getDropoffLng(),
                        request.getCityId(),
                        vehicleConfig.getSurgeMultiplierCap());

        BigDecimal effectiveSurge =
                calculateEffectiveSurge(
                        demandSurge, adjustments.zoneSurge, vehicleConfig.getSurgeMultiplierCap());

        // 5. Calculate fare using actual distance/duration
        FareResult fareResult =
                calculateFare(
                        vehicleConfig,
                        request.getActualDistanceMetres(),
                        request.getActualDurationSeconds(),
                        effectiveSurge,
                        adjustments.airportSurcharge);

        String currency = getCurrency(request.getCountryCode());
        return CalculateResponse.builder()
                .finalFare(fareResult.totalFare)
                .currency(currency)
                .fareBreakdown(fareResult.breakdown)
                .build();
    }

    FareResult calculateFare(
            VehicleTypeConfigDto config,
            int distanceMetres,
            int durationSeconds,
            BigDecimal surgeMultiplier,
            BigDecimal airportSurcharge) {

        BigDecimal distanceKm =
                BigDecimal.valueOf(distanceMetres).divide(METRES_TO_KM, 4, RoundingMode.HALF_UP);
        BigDecimal durationMinutes =
                BigDecimal.valueOf(durationSeconds)
                        .divide(SECONDS_TO_MINUTES, 4, RoundingMode.HALF_UP);

        BigDecimal baseFare = config.getBaseFare();
        BigDecimal distanceFare = distanceKm.multiply(config.getPerKm());
        BigDecimal timeFare = durationMinutes.multiply(config.getPerMinute());

        BigDecimal rawFare = baseFare.add(distanceFare).add(timeFare);

        // Apply minimum fare
        boolean minimumApplied = false;
        if (rawFare.compareTo(config.getMinimumFare()) < 0) {
            rawFare = config.getMinimumFare();
            minimumApplied = true;
        }

        // Calculate surge fare (fare * (surge - 1))
        BigDecimal fareBeforeSurge = rawFare;
        BigDecimal surgeFare = BigDecimal.ZERO;
        if (surgeMultiplier.compareTo(BigDecimal.ONE) > 0) {
            surgeFare = fareBeforeSurge.multiply(surgeMultiplier.subtract(BigDecimal.ONE));
            rawFare = fareBeforeSurge.multiply(surgeMultiplier);
        }

        // Add airport surcharge
        rawFare = rawFare.add(airportSurcharge);

        // Round to whole TZS
        BigDecimal totalFare = rawFare.setScale(TZS_SCALE, RoundingMode.HALF_UP);
        surgeFare = surgeFare.setScale(TZS_SCALE, RoundingMode.HALF_UP);
        distanceFare = distanceFare.setScale(TZS_SCALE, RoundingMode.HALF_UP);
        timeFare = timeFare.setScale(TZS_SCALE, RoundingMode.HALF_UP);
        baseFare = baseFare.setScale(TZS_SCALE, RoundingMode.HALF_UP);
        airportSurcharge = airportSurcharge.setScale(TZS_SCALE, RoundingMode.HALF_UP);

        FareBreakdown breakdown =
                FareBreakdown.builder()
                        .baseFare(baseFare)
                        .distanceFare(distanceFare)
                        .timeFare(timeFare)
                        .surgeFare(surgeFare)
                        .airportSurcharge(airportSurcharge)
                        .minimumFareApplied(minimumApplied)
                        .build();

        return new FareResult(totalFare, breakdown);
    }

    private void checkRestrictedZone(BigDecimal lat, BigDecimal lng, java.util.UUID cityId) {
        try {
            ZoneCheckDto zoneCheck = locationServiceClient.checkZones(lat, lng, cityId);
            if (zoneCheck != null && zoneCheck.isRestricted()) {
                String reason = "Rides cannot start in this area";
                if (zoneCheck.getZones() != null) {
                    reason =
                            zoneCheck.getZones().stream()
                                    .filter(z -> "RESTRICTED".equals(z.getType()))
                                    .findFirst()
                                    .map(ZoneDto::getReason)
                                    .orElse(reason);
                }
                throw new BadRequestException(reason);
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Zone check failed, proceeding without zone adjustments: {}", e.getMessage());
        }
    }

    private ZoneAdjustments getZoneAdjustments(
            BigDecimal pickupLat,
            BigDecimal pickupLng,
            BigDecimal dropoffLat,
            BigDecimal dropoffLng,
            java.util.UUID cityId,
            BigDecimal surgeMultiplierCap) {

        BigDecimal airportSurcharge = BigDecimal.ZERO;
        BigDecimal zoneSurge = BigDecimal.ONE;

        try {
            ZoneCheckDto pickupZones =
                    locationServiceClient.checkZones(pickupLat, pickupLng, cityId);
            ZoneCheckDto dropoffZones =
                    locationServiceClient.checkZones(dropoffLat, dropoffLng, cityId);

            airportSurcharge = extractAirportSurcharge(pickupZones, dropoffZones);
            zoneSurge = extractZoneSurge(pickupZones, dropoffZones);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Zone check failed, proceeding without zone adjustments: {}", e.getMessage());
        }

        return new ZoneAdjustments(airportSurcharge, zoneSurge);
    }

    private BigDecimal extractAirportSurcharge(ZoneCheckDto pickup, ZoneCheckDto dropoff) {
        BigDecimal surcharge = BigDecimal.ZERO;
        surcharge = surcharge.add(extractSurchargeFromZones(pickup));
        surcharge = surcharge.add(extractSurchargeFromZones(dropoff));
        return surcharge;
    }

    private BigDecimal extractSurchargeFromZones(ZoneCheckDto zoneCheck) {
        if (zoneCheck == null || zoneCheck.getZones() == null) {
            return BigDecimal.ZERO;
        }
        return zoneCheck.getZones().stream()
                .filter(z -> "AIRPORT".equals(z.getType()))
                .map(ZoneDto::getSurcharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal extractZoneSurge(ZoneCheckDto pickup, ZoneCheckDto dropoff) {
        BigDecimal maxSurge = BigDecimal.ONE;
        maxSurge = maxSurge.max(extractSurgeFromZones(pickup));
        maxSurge = maxSurge.max(extractSurgeFromZones(dropoff));
        return maxSurge;
    }

    private BigDecimal extractSurgeFromZones(ZoneCheckDto zoneCheck) {
        if (zoneCheck == null || zoneCheck.getZones() == null) {
            return BigDecimal.ONE;
        }
        return zoneCheck.getZones().stream()
                .filter(z -> "SURGE".equals(z.getType()))
                .map(ZoneDto::getMultiplier)
                .reduce(BigDecimal.ONE, BigDecimal::max);
    }

    BigDecimal calculateEffectiveSurge(
            BigDecimal demandSurge, BigDecimal zoneSurge, BigDecimal cap) {
        BigDecimal combined = demandSurge.multiply(zoneSurge);
        if (cap != null && combined.compareTo(cap) > 0) {
            return cap;
        }
        return combined;
    }

    private String getCurrency(String countryCode) {
        // TZ -> TZS; in future, fetch from country-config-service
        return switch (countryCode) {
            case "TZ" -> "TZS";
            case "KE" -> "KES";
            case "UG" -> "UGX";
            default -> "TZS";
        };
    }

    private String formatFare(BigDecimal fare, String currency) {
        return switch (currency) {
            case "TZS" -> "TSh " + String.format("%,.0f", fare);
            case "KES" -> "KSh " + String.format("%,.2f", fare);
            case "UGX" -> "USh " + String.format("%,.0f", fare);
            default -> currency + " " + String.format("%,.0f", fare);
        };
    }

    record FareResult(BigDecimal totalFare, FareBreakdown breakdown) {}

    private record ZoneAdjustments(BigDecimal airportSurcharge, BigDecimal zoneSurge) {}

    static List<String> getSupportedZoneTypes() {
        return List.of("AIRPORT", "SURGE", "RESTRICTED");
    }
}
