package tz.co.twende.loyalty.service;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.OfferStatus;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ConflictException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.loyalty.entity.FreeRideOffer;
import tz.co.twende.loyalty.repository.FreeRideOfferRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfferRedemptionService {

    private final FreeRideOfferRepository offerRepository;

    @Transactional
    public void redeemOffer(UUID offerId, UUID rideId) {
        FreeRideOffer offer =
                offerRepository
                        .findById(offerId)
                        .orElseThrow(() -> new ResourceNotFoundException("Offer not found"));

        if (!OfferStatus.AVAILABLE.name().equals(offer.getStatus())) {
            throw new ConflictException("Offer is not available for redemption");
        }

        if (offer.getExpiresAt().isBefore(Instant.now())) {
            offer.setStatus(OfferStatus.EXPIRED.name());
            offerRepository.save(offer);
            throw new BadRequestException("Offer has expired");
        }

        offer.setStatus(OfferStatus.REDEEMED.name());
        offer.setRedeemedAt(Instant.now());
        offer.setRideId(rideId);
        offerRepository.save(offer);

        log.info("Redeemed offer {} for ride {}", offerId, rideId);
    }
}
