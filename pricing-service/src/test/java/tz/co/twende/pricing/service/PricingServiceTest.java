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
}
