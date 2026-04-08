package tz.co.twende.loyalty.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.OfferStatus;
import tz.co.twende.common.event.loyalty.FreeRideOfferEarnedEvent;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.loyalty.dto.request.UpdateLoyaltyRuleRequest;
import tz.co.twende.loyalty.entity.FreeRideOffer;
import tz.co.twende.loyalty.entity.LoyaltyRule;
import tz.co.twende.loyalty.entity.RiderProgress;
import tz.co.twende.loyalty.kafka.LoyaltyEventPublisher;
import tz.co.twende.loyalty.repository.FreeRideOfferRepository;
import tz.co.twende.loyalty.repository.LoyaltyRuleRepository;
import tz.co.twende.loyalty.repository.RiderProgressRepository;

@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {

    @Mock private LoyaltyRuleRepository ruleRepository;
    @Mock private RiderProgressRepository progressRepository;
    @Mock private FreeRideOfferRepository offerRepository;
    @Mock private LoyaltyEventPublisher eventPublisher;

    @InjectMocks private LoyaltyService loyaltyService;

    @Test
    void givenFreeRide_whenOnRideCompleted_thenProgressNotIncremented() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setFreeRide(true);
        event.setRideId(UUID.randomUUID());

        loyaltyService.onRideCompleted(event);

        verify(progressRepository, never()).save(any());
    }

    @Test
    void givenNullCountryCode_whenOnRideCompleted_thenSkipped() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setFreeRide(false);
        event.setRideId(UUID.randomUUID());
        event.setCountryCode(null);
        event.setVehicleType("BAJAJ");

        loyaltyService.onRideCompleted(event);

        verify(progressRepository, never()).save(any());
    }

    @Test
    void givenNullVehicleType_whenOnRideCompleted_thenSkipped() {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setFreeRide(false);
        event.setRideId(UUID.randomUUID());
        event.setCountryCode("TZ");
        event.setVehicleType(null);

        loyaltyService.onRideCompleted(event);

        verify(progressRepository, never()).save(any());
    }

    @Test
    void givenNewRider_whenFirstRideCompleted_thenProgressCreatedWithCount1() {
        UUID riderId = UUID.randomUUID();
        RideCompletedEvent event = createEvent(riderId, "BAJAJ", 5000, false);

        when(progressRepository.findByRiderIdAndCountryCodeAndVehicleType(riderId, "TZ", "BAJAJ"))
                .thenReturn(Optional.empty());
        when(progressRepository.save(any(RiderProgress.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ruleRepository.findByCountryCodeAndVehicleTypeAndIsActiveTrue("TZ", "BAJAJ"))
                .thenReturn(Optional.empty());

        loyaltyService.onRideCompleted(event);

        ArgumentCaptor<RiderProgress> captor = ArgumentCaptor.forClass(RiderProgress.class);
        verify(progressRepository).save(captor.capture());
        RiderProgress saved = captor.getValue();
        assertEquals(1, saved.getRideCount());
        assertEquals(new BigDecimal("5.00"), saved.getTotalDistanceKm());
    }

    @Test
    void givenExistingProgress_whenRideCompleted_thenCountAndDistanceIncremented() {
        UUID riderId = UUID.randomUUID();
        RiderProgress existing = new RiderProgress(riderId, "TZ", "BAJAJ");
        existing.setRideCount(5);
        existing.setTotalDistanceKm(new BigDecimal("30.00"));

        RideCompletedEvent event = createEvent(riderId, "BAJAJ", 3000, false);

        when(progressRepository.findByRiderIdAndCountryCodeAndVehicleType(riderId, "TZ", "BAJAJ"))
                .thenReturn(Optional.of(existing));
        when(progressRepository.save(any(RiderProgress.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ruleRepository.findByCountryCodeAndVehicleTypeAndIsActiveTrue("TZ", "BAJAJ"))
                .thenReturn(Optional.empty());

        loyaltyService.onRideCompleted(event);

        assertEquals(6, existing.getRideCount());
        assertEquals(new BigDecimal("33.00"), existing.getTotalDistanceKm());
    }

    @Test
    void givenRiderWith19Rides_whenRideCompleted_thenProgressIncrementedButNoOffer() {
        UUID riderId = UUID.randomUUID();
        RiderProgress progress = new RiderProgress(riderId, "TZ", "BAJAJ");
        progress.setRideCount(18);
        progress.setTotalDistanceKm(new BigDecimal("95.00"));

        RideCompletedEvent event = createEvent(riderId, "BAJAJ", 5000, false);

        when(progressRepository.findByRiderIdAndCountryCodeAndVehicleType(riderId, "TZ", "BAJAJ"))
                .thenReturn(Optional.of(progress));
        when(progressRepository.save(any(RiderProgress.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LoyaltyRule rule = createRule("TZ", "BAJAJ", 20, new BigDecimal("100.00"));
        when(ruleRepository.findByCountryCodeAndVehicleTypeAndIsActiveTrue("TZ", "BAJAJ"))
                .thenReturn(Optional.of(rule));

        loyaltyService.onRideCompleted(event);

        assertEquals(19, progress.getRideCount());
        verify(offerRepository, never()).save(any(FreeRideOffer.class));
    }

    @Test
    void givenRiderWith20BajajRidesAnd100Km_whenRideCompleted_thenFreeRideOfferAwarded() {
        UUID riderId = UUID.randomUUID();
        RiderProgress progress = new RiderProgress(riderId, "TZ", "BAJAJ");
        progress.setRideCount(19);
        progress.setTotalDistanceKm(new BigDecimal("97.00"));

        RideCompletedEvent event = createEvent(riderId, "BAJAJ", 4000, false);

        when(progressRepository.findByRiderIdAndCountryCodeAndVehicleType(riderId, "TZ", "BAJAJ"))
                .thenReturn(Optional.of(progress));
        when(progressRepository.save(any(RiderProgress.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LoyaltyRule rule = createRule("TZ", "BAJAJ", 20, new BigDecimal("100.00"));
        when(ruleRepository.findByCountryCodeAndVehicleTypeAndIsActiveTrue("TZ", "BAJAJ"))
                .thenReturn(Optional.of(rule));
        when(offerRepository.save(any(FreeRideOffer.class))).thenAnswer(inv -> inv.getArgument(0));

        loyaltyService.onRideCompleted(event);

        // Progress should be reset
        assertEquals(0, progress.getRideCount());
        assertEquals(BigDecimal.ZERO, progress.getTotalDistanceKm());
        assertNotNull(progress.getLastResetAt());

        // Offer should be created
        ArgumentCaptor<FreeRideOffer> offerCaptor = ArgumentCaptor.forClass(FreeRideOffer.class);
        verify(offerRepository).save(offerCaptor.capture());
        FreeRideOffer offer = offerCaptor.getValue();
        assertEquals(riderId, offer.getRiderId());
        assertEquals("BAJAJ", offer.getVehicleType());
        assertEquals(OfferStatus.AVAILABLE.name(), offer.getStatus());
        assertEquals(new BigDecimal("5.00"), offer.getMaxDistanceKm());

        // Event should be published
        verify(eventPublisher).publishFreeRideEarned(any(FreeRideOfferEarnedEvent.class));
    }

    @Test
    void givenEnoughRidesButNotEnoughDistance_whenCheckThreshold_thenNoOffer() {
        RiderProgress progress = new RiderProgress(UUID.randomUUID(), "TZ", "BAJAJ");
        progress.setRideCount(25);
        progress.setTotalDistanceKm(new BigDecimal("80.00"));

        LoyaltyRule rule = createRule("TZ", "BAJAJ", 20, new BigDecimal("100.00"));
        when(ruleRepository.findByCountryCodeAndVehicleTypeAndIsActiveTrue("TZ", "BAJAJ"))
                .thenReturn(Optional.of(rule));

        loyaltyService.checkAndAwardOffer(progress);

        verify(offerRepository, never()).save(any(FreeRideOffer.class));
    }

    @Test
    void givenNoActiveRule_whenCheckThreshold_thenNoOffer() {
        RiderProgress progress = new RiderProgress(UUID.randomUUID(), "TZ", "BAJAJ");
        progress.setRideCount(50);
        progress.setTotalDistanceKm(new BigDecimal("200.00"));

        when(ruleRepository.findByCountryCodeAndVehicleTypeAndIsActiveTrue("TZ", "BAJAJ"))
                .thenReturn(Optional.empty());

        loyaltyService.checkAndAwardOffer(progress);

        verify(offerRepository, never()).save(any(FreeRideOffer.class));
    }

    @Test
    void givenMatchingOffer_whenFindApplicable_thenOfferReturned() {
        UUID riderId = UUID.randomUUID();
        FreeRideOffer offer = new FreeRideOffer();
        offer.setRiderId(riderId);
        offer.setMaxDistanceKm(new BigDecimal("5.00"));

        when(offerRepository
                        .findFirstByRiderIdAndCountryCodeAndVehicleTypeAndStatusAndExpiresAtAfterOrderByEarnedAtAsc(
                                eq(riderId),
                                eq("TZ"),
                                eq("BAJAJ"),
                                eq(OfferStatus.AVAILABLE.name()),
                                any(Instant.class)))
                .thenReturn(Optional.of(offer));

        FreeRideOffer result =
                loyaltyService.findApplicableOffer(riderId, "TZ", "BAJAJ", new BigDecimal("4.00"));

        assertNotNull(result);
        assertEquals(riderId, result.getRiderId());
    }

    @Test
    void givenOfferWith5KmMax_whenRideIs8Km_thenOfferNotApplicable() {
        UUID riderId = UUID.randomUUID();
        FreeRideOffer offer = new FreeRideOffer();
        offer.setRiderId(riderId);
        offer.setMaxDistanceKm(new BigDecimal("5.00"));

        when(offerRepository
                        .findFirstByRiderIdAndCountryCodeAndVehicleTypeAndStatusAndExpiresAtAfterOrderByEarnedAtAsc(
                                eq(riderId),
                                eq("TZ"),
                                eq("BAJAJ"),
                                eq(OfferStatus.AVAILABLE.name()),
                                any(Instant.class)))
                .thenReturn(Optional.of(offer));

        FreeRideOffer result =
                loyaltyService.findApplicableOffer(riderId, "TZ", "BAJAJ", new BigDecimal("8.00"));

        assertNull(result);
    }

    @Test
    void givenBajajOffer_whenEconomyCarRideRequested_thenOfferNotApplicable() {
        UUID riderId = UUID.randomUUID();

        when(offerRepository
                        .findFirstByRiderIdAndCountryCodeAndVehicleTypeAndStatusAndExpiresAtAfterOrderByEarnedAtAsc(
                                eq(riderId),
                                eq("TZ"),
                                eq("CAR_ECONOMY"),
                                eq(OfferStatus.AVAILABLE.name()),
                                any(Instant.class)))
                .thenReturn(Optional.empty());

        FreeRideOffer result =
                loyaltyService.findApplicableOffer(
                        riderId, "TZ", "CAR_ECONOMY", new BigDecimal("3.00"));

        assertNull(result);
    }

    @Test
    void givenExistingProgress_whenGetProgress_thenListReturned() {
        UUID riderId = UUID.randomUUID();
        List<RiderProgress> progressList = List.of(new RiderProgress(riderId, "TZ", "BAJAJ"));

        when(progressRepository.findByRiderId(riderId)).thenReturn(progressList);

        List<RiderProgress> result = loyaltyService.getProgress(riderId);

        assertEquals(1, result.size());
    }

    @Test
    void givenAvailableOffers_whenGetOffers_thenListReturned() {
        UUID riderId = UUID.randomUUID();
        FreeRideOffer offer = new FreeRideOffer();
        offer.setRiderId(riderId);
        offer.setStatus(OfferStatus.AVAILABLE.name());

        when(offerRepository.findByRiderIdAndStatus(riderId, OfferStatus.AVAILABLE.name()))
                .thenReturn(List.of(offer));

        List<FreeRideOffer> result = loyaltyService.getAvailableOffers(riderId);

        assertEquals(1, result.size());
    }

    @Test
    void givenRules_whenGetRules_thenListReturned() {
        LoyaltyRule rule = createRule("TZ", "BAJAJ", 20, new BigDecimal("100.00"));
        when(ruleRepository.findByCountryCode("TZ")).thenReturn(List.of(rule));

        List<LoyaltyRule> result = loyaltyService.getRules("TZ");

        assertEquals(1, result.size());
    }

    @Test
    void givenExistingRule_whenUpdateRule_thenFieldsUpdated() {
        UUID ruleId = UUID.randomUUID();
        LoyaltyRule rule = createRule("TZ", "BAJAJ", 20, new BigDecimal("100.00"));
        rule.setId(ruleId);

        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(ruleRepository.save(any(LoyaltyRule.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateLoyaltyRuleRequest request = new UpdateLoyaltyRuleRequest();
        request.setRequiredRides(25);
        request.setFreeRideMaxDistanceKm(new BigDecimal("8.00"));

        LoyaltyRule updated = loyaltyService.updateRule(ruleId, request);

        assertEquals(25, updated.getRequiredRides());
        assertEquals(new BigDecimal("8.00"), updated.getFreeRideMaxDistanceKm());
    }

    @Test
    void givenNonExistentRule_whenUpdateRule_thenThrowsNotFound() {
        UUID ruleId = UUID.randomUUID();
        when(ruleRepository.findById(ruleId)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> loyaltyService.updateRule(ruleId, new UpdateLoyaltyRuleRequest()));
    }

    @Test
    void givenNullDistanceMetres_whenOnRideCompleted_thenOnlyRideCountIncremented() {
        UUID riderId = UUID.randomUUID();
        RiderProgress progress = new RiderProgress(riderId, "TZ", "BAJAJ");
        progress.setRideCount(0);
        progress.setTotalDistanceKm(BigDecimal.ZERO);

        RideCompletedEvent event = new RideCompletedEvent();
        event.setFreeRide(false);
        event.setRideId(UUID.randomUUID());
        event.setRiderId(riderId);
        event.setCountryCode("TZ");
        event.setVehicleType("BAJAJ");
        event.setDistanceMetres(null);

        when(progressRepository.findByRiderIdAndCountryCodeAndVehicleType(riderId, "TZ", "BAJAJ"))
                .thenReturn(Optional.of(progress));
        when(progressRepository.save(any(RiderProgress.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(ruleRepository.findByCountryCodeAndVehicleTypeAndIsActiveTrue("TZ", "BAJAJ"))
                .thenReturn(Optional.empty());

        loyaltyService.onRideCompleted(event);

        assertEquals(1, progress.getRideCount());
        assertEquals(BigDecimal.ZERO, progress.getTotalDistanceKm());
    }

    private RideCompletedEvent createEvent(
            UUID riderId, String vehicleType, int distanceMetres, boolean freeRide) {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(UUID.randomUUID());
        event.setRiderId(riderId);
        event.setDriverId(UUID.randomUUID());
        event.setVehicleType(vehicleType);
        event.setDistanceMetres(distanceMetres);
        event.setFreeRide(freeRide);
        event.setCountryCode("TZ");
        event.setFinalFare(new BigDecimal("5000"));
        return event;
    }

    private LoyaltyRule createRule(
            String countryCode,
            String vehicleType,
            int requiredRides,
            BigDecimal requiredDistanceKm) {
        LoyaltyRule rule = new LoyaltyRule();
        rule.setCountryCode(countryCode);
        rule.setVehicleType(vehicleType);
        rule.setRequiredRides(requiredRides);
        rule.setRequiredDistanceKm(requiredDistanceKm);
        rule.setFreeRideMaxDistanceKm(new BigDecimal("5.00"));
        rule.setOfferValidityDays(7);
        rule.setActive(true);
        return rule;
    }
}
