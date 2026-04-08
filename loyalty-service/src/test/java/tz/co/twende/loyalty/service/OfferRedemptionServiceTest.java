package tz.co.twende.loyalty.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.OfferStatus;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.loyalty.entity.FreeRideOffer;
import tz.co.twende.loyalty.repository.FreeRideOfferRepository;

@ExtendWith(MockitoExtension.class)
class OfferRedemptionServiceTest {

    @Mock private FreeRideOfferRepository offerRepository;

    @InjectMocks private OfferRedemptionService offerRedemptionService;

    @Test
    void givenAvailableOffer_whenRedeemed_thenStatusIsRedeemedAndRideIdSet() {
        UUID offerId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        FreeRideOffer offer = createOffer(offerId, OfferStatus.AVAILABLE.name());
        offer.setExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS));

        when(offerRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(offerRepository.save(any(FreeRideOffer.class))).thenAnswer(inv -> inv.getArgument(0));

        offerRedemptionService.redeemOffer(offerId, rideId);

        assertEquals(OfferStatus.REDEEMED.name(), offer.getStatus());
        assertEquals(rideId, offer.getRideId());
        assertNotNull(offer.getRedeemedAt());
    }

    @Test
    void givenAlreadyRedeemedOffer_whenRedeemed_thenConflictThrown() {
        UUID offerId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        FreeRideOffer offer = createOffer(offerId, OfferStatus.REDEEMED.name());
        when(offerRepository.findById(offerId)).thenReturn(Optional.of(offer));

        assertThrows(
                ConflictException.class, () -> offerRedemptionService.redeemOffer(offerId, rideId));
    }

    @Test
    void givenExpiredOffer_whenRedeemed_thenBadRequestThrown() {
        UUID offerId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        FreeRideOffer offer = createOffer(offerId, OfferStatus.AVAILABLE.name());
        offer.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(offerRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(offerRepository.save(any(FreeRideOffer.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(
                BadRequestException.class,
                () -> offerRedemptionService.redeemOffer(offerId, rideId));

        assertEquals(OfferStatus.EXPIRED.name(), offer.getStatus());
    }

    @Test
    void givenNonExistentOffer_whenRedeemed_thenNotFoundThrown() {
        UUID offerId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        when(offerRepository.findById(offerId)).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> offerRedemptionService.redeemOffer(offerId, rideId));
    }

    @Test
    void givenExpiredStatusOffer_whenRedeemed_thenConflictThrown() {
        UUID offerId = UUID.randomUUID();
        UUID rideId = UUID.randomUUID();

        FreeRideOffer offer = createOffer(offerId, OfferStatus.EXPIRED.name());
        when(offerRepository.findById(offerId)).thenReturn(Optional.of(offer));

        assertThrows(
                ConflictException.class, () -> offerRedemptionService.redeemOffer(offerId, rideId));
    }

    private FreeRideOffer createOffer(UUID offerId, String status) {
        FreeRideOffer offer = new FreeRideOffer();
        offer.setId(offerId);
        offer.setRiderId(UUID.randomUUID());
        offer.setCountryCode("TZ");
        offer.setVehicleType("BAJAJ");
        offer.setMaxDistanceKm(new BigDecimal("5.00"));
        offer.setStatus(status);
        offer.setEarnedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        offer.setExpiresAt(Instant.now().plus(5, ChronoUnit.DAYS));
        return offer;
    }
}
