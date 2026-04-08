package tz.co.twende.loyalty.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.OfferStatus;
import tz.co.twende.loyalty.entity.FreeRideOffer;
import tz.co.twende.loyalty.repository.FreeRideOfferRepository;

@ExtendWith(MockitoExtension.class)
class OfferExpirySchedulerTest {

    @Mock private FreeRideOfferRepository offerRepository;

    @InjectMocks private OfferExpiryScheduler offerExpiryScheduler;

    @Test
    void givenExpiredOffers_whenSchedulerRuns_thenOffersMarkedExpired() {
        FreeRideOffer offer1 = createOffer(Instant.now().minus(1, ChronoUnit.DAYS));
        FreeRideOffer offer2 = createOffer(Instant.now().minus(2, ChronoUnit.HOURS));

        when(offerRepository.findByStatusAndExpiresAtBefore(
                        eq(OfferStatus.AVAILABLE.name()), any(Instant.class)))
                .thenReturn(List.of(offer1, offer2));

        offerExpiryScheduler.expireOffers();

        assertEquals(OfferStatus.EXPIRED.name(), offer1.getStatus());
        assertEquals(OfferStatus.EXPIRED.name(), offer2.getStatus());
        verify(offerRepository).saveAll(List.of(offer1, offer2));
    }

    @Test
    void givenNoExpiredOffers_whenSchedulerRuns_thenNothingSaved() {
        when(offerRepository.findByStatusAndExpiresAtBefore(
                        eq(OfferStatus.AVAILABLE.name()), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        offerExpiryScheduler.expireOffers();

        verify(offerRepository, never()).saveAll(anyList());
    }

    private FreeRideOffer createOffer(Instant expiresAt) {
        FreeRideOffer offer = new FreeRideOffer();
        offer.setId(UUID.randomUUID());
        offer.setRiderId(UUID.randomUUID());
        offer.setCountryCode("TZ");
        offer.setVehicleType("BAJAJ");
        offer.setMaxDistanceKm(new BigDecimal("5.00"));
        offer.setStatus(OfferStatus.AVAILABLE.name());
        offer.setEarnedAt(Instant.now().minus(7, ChronoUnit.DAYS));
        offer.setExpiresAt(expiresAt);
        return offer;
    }
}
