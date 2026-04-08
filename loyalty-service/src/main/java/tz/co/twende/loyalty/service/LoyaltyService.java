package tz.co.twende.loyalty.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.OfferStatus;
import tz.co.twende.common.enums.VehicleType;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyService {

    private final LoyaltyRuleRepository ruleRepository;
    private final RiderProgressRepository progressRepository;
    private final FreeRideOfferRepository offerRepository;
    private final LoyaltyEventPublisher eventPublisher;

    @Transactional
    public void onRideCompleted(RideCompletedEvent event) {
        if (event.isFreeRide()) {
            log.debug("Skipping free ride {} for loyalty progress", event.getRideId());
            return;
        }

        String countryCode = event.getCountryCode();
        String vehicleType = event.getVehicleType();

        if (countryCode == null || vehicleType == null) {
            log.warn(
                    "RideCompletedEvent for ride {} missing countryCode or vehicleType, skipping",
                    event.getRideId());
            return;
        }

        RiderProgress progress =
                progressRepository
                        .findByRiderIdAndCountryCodeAndVehicleType(
                                event.getRiderId(), countryCode, vehicleType)
                        .orElseGet(
                                () ->
                                        new RiderProgress(
                                                event.getRiderId(), countryCode, vehicleType));

        progress.setRideCount(progress.getRideCount() + 1);

        if (event.getDistanceMetres() != null && event.getDistanceMetres() > 0) {
            BigDecimal tripKm =
                    new BigDecimal(event.getDistanceMetres())
                            .divide(new BigDecimal("1000"), 2, RoundingMode.HALF_UP);
            progress.setTotalDistanceKm(progress.getTotalDistanceKm().add(tripKm));
        }

        progressRepository.save(progress);
        log.debug(
                "Updated progress for rider {} on {}: rides={}, km={}",
                event.getRiderId(),
                vehicleType,
                progress.getRideCount(),
                progress.getTotalDistanceKm());

        checkAndAwardOffer(progress);
    }

    void checkAndAwardOffer(RiderProgress progress) {
        LoyaltyRule rule =
                ruleRepository
                        .findByCountryCodeAndVehicleTypeAndIsActiveTrue(
                                progress.getCountryCode(), progress.getVehicleType())
                        .orElse(null);

        if (rule == null) {
            return;
        }

        if (progress.getRideCount() >= rule.getRequiredRides()
                && progress.getTotalDistanceKm().compareTo(rule.getRequiredDistanceKm()) >= 0) {
            awardFreeRide(progress, rule);
        }
    }

    private void awardFreeRide(RiderProgress progress, LoyaltyRule rule) {
        FreeRideOffer offer = new FreeRideOffer();
        offer.setRiderId(progress.getRiderId());
        offer.setCountryCode(progress.getCountryCode());
        offer.setVehicleType(progress.getVehicleType());
        offer.setMaxDistanceKm(rule.getFreeRideMaxDistanceKm());
        offer.setStatus(OfferStatus.AVAILABLE.name());
        offer.setEarnedAt(Instant.now());
        offer.setExpiresAt(Instant.now().plus(rule.getOfferValidityDays(), ChronoUnit.DAYS));
        offerRepository.save(offer);

        progress.setRideCount(0);
        progress.setTotalDistanceKm(BigDecimal.ZERO);
        progress.setLastResetAt(Instant.now());
        progressRepository.save(progress);

        log.info(
                "Awarded free ride offer {} to rider {} for vehicle type {}",
                offer.getId(),
                progress.getRiderId(),
                progress.getVehicleType());

        FreeRideOfferEarnedEvent event = new FreeRideOfferEarnedEvent();
        event.setOfferId(offer.getId());
        event.setRiderId(offer.getRiderId());
        event.setVehicleType(VehicleType.valueOf(offer.getVehicleType()));
        event.setMaxDistanceKm(offer.getMaxDistanceKm());
        event.setExpiresAt(offer.getExpiresAt());
        event.setCountryCode(offer.getCountryCode());
        eventPublisher.publishFreeRideEarned(event);
    }

    public FreeRideOffer findApplicableOffer(
            UUID riderId, String countryCode, String vehicleType, BigDecimal estimatedDistanceKm) {
        return offerRepository
                .findFirstByRiderIdAndCountryCodeAndVehicleTypeAndStatusAndExpiresAtAfterOrderByEarnedAtAsc(
                        riderId,
                        countryCode,
                        vehicleType,
                        OfferStatus.AVAILABLE.name(),
                        Instant.now())
                .filter(offer -> estimatedDistanceKm.compareTo(offer.getMaxDistanceKm()) <= 0)
                .orElse(null);
    }

    public List<RiderProgress> getProgress(UUID riderId) {
        return progressRepository.findByRiderId(riderId);
    }

    public List<FreeRideOffer> getAvailableOffers(UUID riderId) {
        return offerRepository.findByRiderIdAndStatus(riderId, OfferStatus.AVAILABLE.name());
    }

    public List<LoyaltyRule> getRules(String countryCode) {
        return ruleRepository.findByCountryCode(countryCode);
    }

    @Transactional
    public LoyaltyRule updateRule(UUID ruleId, UpdateLoyaltyRuleRequest request) {
        LoyaltyRule rule =
                ruleRepository
                        .findById(ruleId)
                        .orElseThrow(() -> new ResourceNotFoundException("Loyalty rule not found"));

        if (request.getRequiredRides() != null) {
            rule.setRequiredRides(request.getRequiredRides());
        }
        if (request.getRequiredDistanceKm() != null) {
            rule.setRequiredDistanceKm(request.getRequiredDistanceKm());
        }
        if (request.getFreeRideMaxDistanceKm() != null) {
            rule.setFreeRideMaxDistanceKm(request.getFreeRideMaxDistanceKm());
        }
        if (request.getOfferValidityDays() != null) {
            rule.setOfferValidityDays(request.getOfferValidityDays());
        }
        if (request.getIsActive() != null) {
            rule.setActive(request.getIsActive());
        }

        return ruleRepository.save(rule);
    }
}
