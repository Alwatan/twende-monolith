package tz.co.twende.pricing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.pricing.client.CountryConfigClient;
import tz.co.twende.pricing.client.LocationServiceClient;
import tz.co.twende.pricing.dto.*;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock private CountryConfigClient countryConfigClient;
    @Mock private LocationServiceClient locationServiceClient;
    @Mock private SurgeService surgeService;

    @InjectMocks private PricingService pricingService;

    private VehicleTypeConfigDto bajajConfig() {
        return VehicleTypeConfigDto.builder()
                .vehicleType("BAJAJ")
                .baseFare(new BigDecimal("500"))
                .perKm(new BigDecimal("200"))
                .perMinute(new BigDecimal("20"))
                .minimumFare(new BigDecimal("1000"))
                .cancellationFee(new BigDecimal("200"))
                .surgeMultiplierCap(new BigDecimal("2.5"))
                .build();
    }

    private EstimateRequest defaultRequest() {
        return EstimateRequest.builder()
                .vehicleType("BAJAJ")
                .countryCode("TZ")
                .pickupLat(new BigDecimal("-6.7728"))
                .pickupLng(new BigDecimal("39.2310"))
                .dropoffLat(new BigDecimal("-6.8160"))
                .dropoffLng(new BigDecimal("39.2803"))
                .cityId(UUID.randomUUID())
                .build();
    }

    private ZoneCheckDto noZones() {
        return ZoneCheckDto.builder()
                .inServiceArea(true)
                .restricted(false)
                .zones(Collections.emptyList())
                .build();
    }

    @Test
    void givenNormalBajajTrip_whenEstimate_thenCorrectFareCalculated() {
        EstimateRequest request = defaultRequest();
        RouteDto route = RouteDto.builder().distanceMetres(8200).durationSeconds(900).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "BAJAJ")).thenReturn(bajajConfig());
        when(surgeService.getSurge("TZ", "BAJAJ")).thenReturn(BigDecimal.ONE);

        EstimateResponse response = pricingService.calculateEstimate(request);

        // base=500, distance=8.2*200=1640, time=15*20=300 => 2440 (> 1000 min)
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("2440"));
        assertThat(response.getCurrency()).isEqualTo("TZS");
        assertThat(response.getSurgeMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.getFareBreakdown().isMinimumFareApplied()).isFalse();
    }

    @Test
    void givenFareBelowMinimum_whenEstimate_thenMinimumFareApplied() {
        EstimateRequest request = defaultRequest();
        // Very short trip: 200m, 30s
        RouteDto route = RouteDto.builder().distanceMetres(200).durationSeconds(30).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "BAJAJ")).thenReturn(bajajConfig());
        when(surgeService.getSurge("TZ", "BAJAJ")).thenReturn(BigDecimal.ONE);

        EstimateResponse response = pricingService.calculateEstimate(request);

        // base=500, distance=0.2*200=40, time=0.5*20=10 => 550 < 1000 min
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(response.getFareBreakdown().isMinimumFareApplied()).isTrue();
    }

    @Test
    void givenSurgeMultiplier_whenEstimate_thenSurgeApplied() {
        EstimateRequest request = defaultRequest();
        RouteDto route = RouteDto.builder().distanceMetres(5000).durationSeconds(600).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "BAJAJ")).thenReturn(bajajConfig());
        when(surgeService.getSurge("TZ", "BAJAJ")).thenReturn(new BigDecimal("1.5"));

        EstimateResponse response = pricingService.calculateEstimate(request);

        // base=500, distance=5*200=1000, time=10*20=200 => 1700 * 1.5 = 2550
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("2550"));
        assertThat(response.getSurgeMultiplier()).isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(response.getFareBreakdown().getSurgeFare())
                .isEqualByComparingTo(new BigDecimal("850"));
    }

    @Test
    void givenSurgeAboveCap_whenCalculateEffectiveSurge_thenCapped() {
        BigDecimal result =
                pricingService.calculateEffectiveSurge(
                        new BigDecimal("2.0"), new BigDecimal("2.0"), new BigDecimal("2.5"));
        assertThat(result).isEqualByComparingTo(new BigDecimal("2.5"));
    }

    @Test
    void givenAirportPickup_whenEstimate_thenSurchargeAdded() {
        EstimateRequest request = defaultRequest();
        RouteDto route = RouteDto.builder().distanceMetres(5000).durationSeconds(600).build();

        ZoneCheckDto airportZone =
                ZoneCheckDto.builder()
                        .inServiceArea(true)
                        .restricted(false)
                        .zones(
                                List.of(
                                        ZoneDto.builder()
                                                .type("AIRPORT")
                                                .config(Map.of("surcharge", "2000"))
                                                .build()))
                        .build();

        // First call is restricted check, second and third are for zone adjustments
        when(locationServiceClient.checkZones(
                        eq(request.getPickupLat()), eq(request.getPickupLng()), any()))
                .thenReturn(airportZone);
        when(locationServiceClient.checkZones(
                        eq(request.getDropoffLat()), eq(request.getDropoffLng()), any()))
                .thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "BAJAJ")).thenReturn(bajajConfig());
        when(surgeService.getSurge("TZ", "BAJAJ")).thenReturn(BigDecimal.ONE);

        EstimateResponse response = pricingService.calculateEstimate(request);

        // base=500, distance=5*200=1000, time=10*20=200 => 1700 + 2000 airport = 3700
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("3700"));
        assertThat(response.getFareBreakdown().getAirportSurcharge())
                .isEqualByComparingTo(new BigDecimal("2000"));
    }

    @Test
    void givenRestrictedZone_whenEstimate_thenRequestRejected() {
        EstimateRequest request = defaultRequest();

        ZoneCheckDto restrictedZone =
                ZoneCheckDto.builder()
                        .inServiceArea(true)
                        .restricted(true)
                        .zones(
                                List.of(
                                        ZoneDto.builder()
                                                .type("RESTRICTED")
                                                .config(
                                                        Map.of(
                                                                "reason",
                                                                "Military zone - no rides allowed"))
                                                .build()))
                        .build();

        when(locationServiceClient.checkZones(
                        eq(request.getPickupLat()), eq(request.getPickupLng()), any()))
                .thenReturn(restrictedZone);

        assertThatThrownBy(() -> pricingService.calculateEstimate(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Military zone");
    }

    @Test
    void givenZeroDistanceRide_whenEstimate_thenMinimumFareApplied() {
        EstimateRequest request = defaultRequest();
        RouteDto route = RouteDto.builder().distanceMetres(0).durationSeconds(0).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "BAJAJ")).thenReturn(bajajConfig());
        when(surgeService.getSurge("TZ", "BAJAJ")).thenReturn(BigDecimal.ONE);

        EstimateResponse response = pricingService.calculateEstimate(request);

        // base=500, distance=0, time=0 => 500 < 1000 min
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(response.getFareBreakdown().isMinimumFareApplied()).isTrue();
    }

    @Test
    void givenFare_whenRounding_thenRoundedToWholeTzs() {
        // Test the internal fare calculation with values that produce fractional results
        VehicleTypeConfigDto config =
                VehicleTypeConfigDto.builder()
                        .vehicleType("BAJAJ")
                        .baseFare(new BigDecimal("500"))
                        .perKm(new BigDecimal("200"))
                        .perMinute(new BigDecimal("20"))
                        .minimumFare(new BigDecimal("100"))
                        .cancellationFee(BigDecimal.ZERO)
                        .surgeMultiplierCap(new BigDecimal("2.5"))
                        .build();

        // 3333m = 3.333km, 421s = 7.0167min
        // base=500, distance=3.333*200=666.6, time=7.0167*20=140.333
        // total = 1306.933 => rounded to 1307
        PricingService.FareResult result =
                pricingService.calculateFare(config, 3333, 421, BigDecimal.ONE, BigDecimal.ZERO);

        assertThat(result.totalFare().scale()).isEqualTo(0);
        assertThat(result.totalFare()).isEqualByComparingTo(new BigDecimal("1307"));
    }

    @Test
    void givenCalculateRequest_whenCalculateFinal_thenUsesActualDistanceDuration() {
        CalculateRequest request =
                CalculateRequest.builder()
                        .rideId(UUID.randomUUID())
                        .vehicleType("BAJAJ")
                        .countryCode("TZ")
                        .actualDistanceMetres(10000)
                        .actualDurationSeconds(1200)
                        .pickupLat(new BigDecimal("-6.7728"))
                        .pickupLng(new BigDecimal("39.2310"))
                        .dropoffLat(new BigDecimal("-6.8160"))
                        .dropoffLng(new BigDecimal("39.2803"))
                        .cityId(UUID.randomUUID())
                        .build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(countryConfigClient.getVehicleTypeConfig("TZ", "BAJAJ")).thenReturn(bajajConfig());
        when(surgeService.getSurge("TZ", "BAJAJ")).thenReturn(BigDecimal.ONE);

        CalculateResponse response = pricingService.calculateFinal(request);

        // base=500, distance=10*200=2000, time=20*20=400 => 2900
        assertThat(response.getFinalFare()).isEqualByComparingTo(new BigDecimal("2900"));
        assertThat(response.getCurrency()).isEqualTo("TZS");
    }

    @Test
    void givenZoneSurgeAndDemandSurge_whenEstimate_thenStackedButCapped() {
        BigDecimal result =
                pricingService.calculateEffectiveSurge(
                        new BigDecimal("1.5"), new BigDecimal("1.3"), new BigDecimal("2.5"));
        // 1.5 * 1.3 = 1.95 < 2.5 cap
        assertThat(result).isEqualByComparingTo(new BigDecimal("1.95"));
    }

    // ---- Charter pricing tests ----

    private VehicleTypeConfigDto minibusStandardConfig() {
        return VehicleTypeConfigDto.builder()
                .vehicleType("MINIBUS_STANDARD")
                .baseFare(new BigDecimal("50000"))
                .perKm(new BigDecimal("1500"))
                .perMinute(BigDecimal.ZERO)
                .minimumFare(new BigDecimal("50000"))
                .cancellationFee(new BigDecimal("10000"))
                .surgeMultiplierCap(new BigDecimal("2.0"))
                .perHour(new BigDecimal("15000"))
                .qualityTier("STANDARD")
                .qualityTierSurcharge(BigDecimal.ZERO)
                .build();
    }

    private VehicleTypeConfigDto minibusLuxuryConfig() {
        return VehicleTypeConfigDto.builder()
                .vehicleType("MINIBUS_LUXURY")
                .baseFare(new BigDecimal("80000"))
                .perKm(new BigDecimal("2000"))
                .perMinute(BigDecimal.ZERO)
                .minimumFare(new BigDecimal("80000"))
                .cancellationFee(new BigDecimal("15000"))
                .surgeMultiplierCap(new BigDecimal("2.0"))
                .perHour(new BigDecimal("20000"))
                .qualityTier("LUXURY")
                .qualityTierSurcharge(new BigDecimal("30000"))
                .build();
    }

    @Test
    void givenCharterOneWay_whenEstimate_thenCorrectFareCalculated() {
        EstimateRequest request =
                EstimateRequest.builder()
                        .vehicleType("MINIBUS_STANDARD")
                        .countryCode("TZ")
                        .pickupLat(new BigDecimal("-6.7728"))
                        .pickupLng(new BigDecimal("39.2310"))
                        .dropoffLat(new BigDecimal("-6.8160"))
                        .dropoffLng(new BigDecimal("39.2803"))
                        .cityId(UUID.randomUUID())
                        .serviceCategory("CHARTER")
                        .qualityTier("STANDARD")
                        .tripDirection("ONE_WAY")
                        .estimatedHours(new BigDecimal("3"))
                        .build();

        RouteDto route = RouteDto.builder().distanceMetres(50000).durationSeconds(3600).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "MINIBUS_STANDARD"))
                .thenReturn(minibusStandardConfig());

        EstimateResponse response = pricingService.calculateEstimate(request);

        // baseFare=50000 + distance=50*1500=75000 + hourly=3*15000=45000 + surcharge=0
        // total = 170000
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("170000"));
        assertThat(response.getSurgeMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(response.getFareBreakdown().getCharterHourlyFare())
                .isEqualByComparingTo(new BigDecimal("45000"));
        assertThat(response.getFareBreakdown().getQualityTierSurcharge())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void givenCharterRoundTrip_whenEstimate_thenReturnLegDiscounted() {
        EstimateRequest request =
                EstimateRequest.builder()
                        .vehicleType("MINIBUS_STANDARD")
                        .countryCode("TZ")
                        .pickupLat(new BigDecimal("-6.7728"))
                        .pickupLng(new BigDecimal("39.2310"))
                        .dropoffLat(new BigDecimal("-6.8160"))
                        .dropoffLng(new BigDecimal("39.2803"))
                        .cityId(UUID.randomUUID())
                        .serviceCategory("CHARTER")
                        .qualityTier("STANDARD")
                        .tripDirection("ROUND_TRIP")
                        .estimatedHours(new BigDecimal("2"))
                        .build();

        RouteDto route = RouteDto.builder().distanceMetres(30000).durationSeconds(2400).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "MINIBUS_STANDARD"))
                .thenReturn(minibusStandardConfig());

        EstimateResponse response = pricingService.calculateEstimate(request);

        // baseFare=50000, distance=30*1500=45000, hourly=2*15000=30000, surcharge=0
        // oneWay=125000, return distance=45000*0.9=40500, total=165500
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("165500"));
    }

    @Test
    void givenCharterLuxury_whenEstimate_thenQualityTierSurchargeAdded() {
        EstimateRequest request =
                EstimateRequest.builder()
                        .vehicleType("MINIBUS_LUXURY")
                        .countryCode("TZ")
                        .pickupLat(new BigDecimal("-6.7728"))
                        .pickupLng(new BigDecimal("39.2310"))
                        .dropoffLat(new BigDecimal("-6.8160"))
                        .dropoffLng(new BigDecimal("39.2803"))
                        .cityId(UUID.randomUUID())
                        .serviceCategory("CHARTER")
                        .qualityTier("LUXURY")
                        .tripDirection("ONE_WAY")
                        .estimatedHours(new BigDecimal("2"))
                        .build();

        RouteDto route = RouteDto.builder().distanceMetres(20000).durationSeconds(1800).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "MINIBUS_LUXURY"))
                .thenReturn(minibusLuxuryConfig());

        EstimateResponse response = pricingService.calculateEstimate(request);

        // baseFare=80000 + distance=20*2000=40000 + hourly=2*20000=40000 + surcharge=30000
        // total = 190000
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("190000"));
        assertThat(response.getFareBreakdown().getQualityTierSurcharge())
                .isEqualByComparingTo(new BigDecimal("30000"));
    }

    // ---- Cargo pricing tests ----

    private VehicleTypeConfigDto cargoTuktukConfig() {
        return VehicleTypeConfigDto.builder()
                .vehicleType("CARGO_TUKTUK")
                .baseFare(new BigDecimal("5000"))
                .perKm(new BigDecimal("800"))
                .perMinute(BigDecimal.ZERO)
                .minimumFare(new BigDecimal("5000"))
                .cancellationFee(new BigDecimal("2000"))
                .surgeMultiplierCap(new BigDecimal("1.0"))
                .weightTierSurcharges("{\"LIGHT\": 0, \"MEDIUM\": 2000, \"FULL\": 5000}")
                .build();
    }

    private VehicleTypeConfigDto truckHeavyConfig() {
        return VehicleTypeConfigDto.builder()
                .vehicleType("TRUCK_HEAVY")
                .baseFare(new BigDecimal("50000"))
                .perKm(new BigDecimal("3500"))
                .perMinute(BigDecimal.ZERO)
                .minimumFare(new BigDecimal("50000"))
                .cancellationFee(new BigDecimal("15000"))
                .surgeMultiplierCap(new BigDecimal("1.0"))
                .weightTierSurcharges("{\"LIGHT\": 0, \"MEDIUM\": 15000, \"FULL\": 30000}")
                .build();
    }

    @Test
    void givenCargoLightTier_whenEstimate_thenNoWeightSurcharge() {
        EstimateRequest request =
                EstimateRequest.builder()
                        .vehicleType("CARGO_TUKTUK")
                        .countryCode("TZ")
                        .pickupLat(new BigDecimal("-6.7728"))
                        .pickupLng(new BigDecimal("39.2310"))
                        .dropoffLat(new BigDecimal("-6.8160"))
                        .dropoffLng(new BigDecimal("39.2803"))
                        .cityId(UUID.randomUUID())
                        .serviceCategory("CARGO")
                        .weightTier("LIGHT")
                        .build();

        RouteDto route = RouteDto.builder().distanceMetres(10000).durationSeconds(1200).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "CARGO_TUKTUK"))
                .thenReturn(cargoTuktukConfig());

        EstimateResponse response = pricingService.calculateEstimate(request);

        // baseFare=5000 + distance=10*800=8000 + weightSurcharge=0 = 13000
        // NO time component
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("13000"));
        assertThat(response.getFareBreakdown().getTimeFare()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getFareBreakdown().getWeightTierSurcharge())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getSurgeMultiplier()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void givenCargoFullTier_whenEstimate_thenWeightSurchargeApplied() {
        EstimateRequest request =
                EstimateRequest.builder()
                        .vehicleType("CARGO_TUKTUK")
                        .countryCode("TZ")
                        .pickupLat(new BigDecimal("-6.7728"))
                        .pickupLng(new BigDecimal("39.2310"))
                        .dropoffLat(new BigDecimal("-6.8160"))
                        .dropoffLng(new BigDecimal("39.2803"))
                        .cityId(UUID.randomUUID())
                        .serviceCategory("CARGO")
                        .weightTier("FULL")
                        .build();

        RouteDto route = RouteDto.builder().distanceMetres(10000).durationSeconds(1200).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "CARGO_TUKTUK"))
                .thenReturn(cargoTuktukConfig());

        EstimateResponse response = pricingService.calculateEstimate(request);

        // baseFare=5000 + distance=10*800=8000 + weightSurcharge=5000 = 18000
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("18000"));
        assertThat(response.getFareBreakdown().getWeightTierSurcharge())
                .isEqualByComparingTo(new BigDecimal("5000"));
    }

    @Test
    void givenCargoHeavyTruck_whenEstimate_thenCorrectFareWithMediumWeight() {
        EstimateRequest request =
                EstimateRequest.builder()
                        .vehicleType("TRUCK_HEAVY")
                        .countryCode("TZ")
                        .pickupLat(new BigDecimal("-6.7728"))
                        .pickupLng(new BigDecimal("39.2310"))
                        .dropoffLat(new BigDecimal("-6.8160"))
                        .dropoffLng(new BigDecimal("39.2803"))
                        .cityId(UUID.randomUUID())
                        .serviceCategory("CARGO")
                        .weightTier("MEDIUM")
                        .build();

        RouteDto route = RouteDto.builder().distanceMetres(50000).durationSeconds(7200).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "TRUCK_HEAVY"))
                .thenReturn(truckHeavyConfig());

        EstimateResponse response = pricingService.calculateEstimate(request);

        // baseFare=50000 + distance=50*3500=175000 + weightSurcharge=15000 = 240000
        // No time component, no surge for cargo
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("240000"));
        assertThat(response.getFareBreakdown().getTimeFare()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getFareBreakdown().getSurgeFare())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getFareBreakdown().getWeightTierSurcharge())
                .isEqualByComparingTo(new BigDecimal("15000"));
    }

    @Test
    void givenCargoNoTimeComponent_whenEstimate_thenTimeFareIsZero() {
        // Even with long duration, cargo has no time component
        EstimateRequest request =
                EstimateRequest.builder()
                        .vehicleType("CARGO_TUKTUK")
                        .countryCode("TZ")
                        .pickupLat(new BigDecimal("-6.7728"))
                        .pickupLng(new BigDecimal("39.2310"))
                        .dropoffLat(new BigDecimal("-6.8160"))
                        .dropoffLng(new BigDecimal("39.2803"))
                        .cityId(UUID.randomUUID())
                        .serviceCategory("CARGO")
                        .weightTier("MEDIUM")
                        .build();

        // 3 hours of route duration — should NOT affect cargo price
        RouteDto route = RouteDto.builder().distanceMetres(5000).durationSeconds(10800).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "CARGO_TUKTUK"))
                .thenReturn(cargoTuktukConfig());

        EstimateResponse response = pricingService.calculateEstimate(request);

        // baseFare=5000 + distance=5*800=4000 + weightSurcharge=2000 = 11000
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("11000"));
        assertThat(response.getFareBreakdown().getTimeFare()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void givenWeightTierSurchargesJson_whenParse_thenCorrectValue() {
        String json = "{\"LIGHT\": 0, \"MEDIUM\": 2000, \"FULL\": 5000}";
        assertThat(pricingService.parseWeightTierSurcharge(json, "LIGHT"))
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(pricingService.parseWeightTierSurcharge(json, "MEDIUM"))
                .isEqualByComparingTo(new BigDecimal("2000"));
        assertThat(pricingService.parseWeightTierSurcharge(json, "FULL"))
                .isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(pricingService.parseWeightTierSurcharge(json, "UNKNOWN"))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void givenCharterNoEstimatedHours_whenEstimate_thenDefaultsToOneHour() {
        EstimateRequest request =
                EstimateRequest.builder()
                        .vehicleType("MINIBUS_STANDARD")
                        .countryCode("TZ")
                        .pickupLat(new BigDecimal("-6.7728"))
                        .pickupLng(new BigDecimal("39.2310"))
                        .dropoffLat(new BigDecimal("-6.8160"))
                        .dropoffLng(new BigDecimal("39.2803"))
                        .cityId(UUID.randomUUID())
                        .serviceCategory("CHARTER")
                        .qualityTier("STANDARD")
                        .tripDirection("ONE_WAY")
                        // no estimatedHours
                        .build();

        RouteDto route = RouteDto.builder().distanceMetres(10000).durationSeconds(900).build();

        when(locationServiceClient.checkZones(any(), any(), any())).thenReturn(noZones());
        when(locationServiceClient.getRoute(any(), any(), any(), any(), any())).thenReturn(route);
        when(countryConfigClient.getVehicleTypeConfig("TZ", "MINIBUS_STANDARD"))
                .thenReturn(minibusStandardConfig());

        EstimateResponse response = pricingService.calculateEstimate(request);

        // baseFare=50000 + distance=10*1500=15000 + hourly=1*15000=15000 + surcharge=0
        // total = 80000
        assertThat(response.getEstimatedFare()).isEqualByComparingTo(new BigDecimal("80000"));
    }
}
