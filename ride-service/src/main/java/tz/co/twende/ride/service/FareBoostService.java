package tz.co.twende.ride.service;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.ride.client.ConfigClient;
import tz.co.twende.ride.entity.Ride;
import tz.co.twende.ride.kafka.RideEventPublisher;
import tz.co.twende.ride.repository.RideRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class FareBoostService {

    private final RideRepository rideRepository;
    private final ConfigClient configClient;
    private final RideEventPublisher eventPublisher;

    @Transactional
    public Ride boostFare(UUID rideId, UUID riderId, BigDecimal boostAmount) {
        Ride ride =
                rideRepository
                        .findById(rideId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Ride not found: " + rideId));

        // Validate rider owns this ride
        if (!ride.getRiderId().equals(riderId)) {
            throw new UnauthorizedException("You can only boost fare on your own ride");
        }

        // Validate status
        if (ride.getStatus() != RideStatus.REQUESTED) {
            throw new BadRequestException("Can only boost fare before a driver is assigned");
        }

        // Validate boost amount is positive
        if (boostAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Boost amount must be positive");
        }

        // Fetch max fare cap from country config
        BigDecimal maxFareCap =
                configClient.getMaxFareCap(ride.getCountryCode(), ride.getVehicleType());
        BigDecimal newFare = ride.getEstimatedFare().add(boostAmount);

        if (maxFareCap != null && newFare.compareTo(maxFareCap) > 0) {
            throw new BadRequestException("Fare cannot exceed maximum of " + maxFareCap);
        }

        BigDecimal previousFare = ride.getEstimatedFare();
        ride.setFareBoostAmount(ride.getFareBoostAmount().add(boostAmount));
        ride.setEstimatedFare(newFare);
        rideRepository.save(ride);

        // Publish event for matching-service to re-broadcast
        eventPublisher.publishFareBoosted(ride, previousFare, boostAmount);

        log.info(
                "Fare boosted for ride {}: {} -> {} (boost: {})",
                rideId,
                previousFare,
                newFare,
                boostAmount);
        return ride;
    }
}
