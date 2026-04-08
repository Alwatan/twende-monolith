package tz.co.twende.ride.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.NotificationType;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.common.event.notification.SendNotificationEvent;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.exception.ResourceNotFoundException;
import tz.co.twende.common.exception.UnauthorizedException;
import tz.co.twende.ride.client.LocationClient;
import tz.co.twende.ride.client.LoyaltyClient;
import tz.co.twende.ride.client.PricingClient;
import tz.co.twende.ride.config.KafkaConfig;
import tz.co.twende.ride.dto.request.CreateRideRequest;
import tz.co.twende.ride.dto.response.EstimateDto;
import tz.co.twende.ride.entity.Ride;
import tz.co.twende.ride.entity.RideDriverRejection;
import tz.co.twende.ride.entity.RideStatusEvent;
import tz.co.twende.ride.kafka.RideEventPublisher;
import tz.co.twende.ride.repository.RideDriverRejectionRepository;
import tz.co.twende.ride.repository.RideRepository;
import tz.co.twende.ride.repository.RideStatusEventRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class RideService {

    private static final int MATCHING_TIMEOUT_MINUTES = 3;
    private static final int REJECTION_NUDGE_THRESHOLD = 3;

    private final RideRepository rideRepository;
    private final RideStatusEventRepository statusEventRepository;
    private final RideDriverRejectionRepository rejectionRepository;
    private final PricingClient pricingClient;
    private final LocationClient locationClient;
    private final LoyaltyClient loyaltyClient;
    private final RideEventPublisher eventPublisher;
    private final TripOtpService tripOtpService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ---- Create Ride ----

    @Transactional
    public Ride createRide(UUID riderId, String countryCode, CreateRideRequest request) {
        // 1. Check restricted zone
        if (locationClient.isInRestrictedZone(request.getPickupLat(), request.getPickupLng())) {
            throw new BadRequestException("Rides cannot start in restricted zones");
        }

        // 2. Get fare estimate from pricing-service
        EstimateDto estimate =
                pricingClient.getFareEstimate(
                        countryCode,
                        request.getVehicleType(),
                        request.getPickupLat(),
                        request.getPickupLng(),
                        request.getDropoffLat(),
                        request.getDropoffLng());

        BigDecimal estimatedFare = BigDecimal.ZERO;
        String currencyCode = "TZS";
        Integer distanceMetres = null;
        Integer durationSeconds = null;

        if (estimate != null) {
            estimatedFare = estimate.getEstimatedFare();
            currencyCode = estimate.getCurrency() != null ? estimate.getCurrency() : currencyCode;
            distanceMetres = estimate.getDistanceMetres();
            durationSeconds = estimate.getDurationSeconds();
        }

        // 3. Check for free ride offer from loyalty-service
        boolean freeRide = false;
        UUID freeRideOfferId = null;
        BigDecimal distanceKm =
                distanceMetres != null
                        ? BigDecimal.valueOf(distanceMetres)
                                .divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

        Map<String, Object> offer =
                loyaltyClient.findApplicableOffer(riderId, request.getVehicleType(), distanceKm);
        if (offer != null && offer.containsKey("id")) {
            freeRideOfferId = UUID.fromString(String.valueOf(offer.get("id")));
            boolean redeemed = loyaltyClient.redeemOffer(freeRideOfferId, null);
            if (redeemed) {
                freeRide = true;
                estimatedFare = BigDecimal.ZERO;
            } else {
                freeRideOfferId = null;
            }
        }

        // 4. Create Ride entity
        Ride ride = new Ride();
        ride.setRiderId(riderId);
        ride.setCountryCode(countryCode);
        ride.setStatus(RideStatus.REQUESTED);
        ride.setVehicleType(request.getVehicleType());
        ride.setCityId(request.getCityId());

        ride.setPickupLat(request.getPickupLat());
        ride.setPickupLng(request.getPickupLng());
        ride.setPickupAddress(request.getPickupAddress());

        ride.setDropoffLat(request.getDropoffLat());
        ride.setDropoffLng(request.getDropoffLng());
        ride.setDropoffAddress(request.getDropoffAddress());

        ride.setEstimatedFare(estimatedFare);
        ride.setFareBoostAmount(BigDecimal.ZERO);
        ride.setCurrencyCode(currencyCode);

        ride.setFreeRide(freeRide);
        ride.setFreeRideOfferId(freeRideOfferId);

        ride.setDriverRejectionCount(0);
        ride.setTripStartOtpAttempts(0);

        ride.setDistanceMetres(distanceMetres);
        ride.setDurationSeconds(durationSeconds);

        Instant now = Instant.now();
        ride.setRequestedAt(now);
        ride.setMatchingTimeoutAt(now.plus(MATCHING_TIMEOUT_MINUTES, ChronoUnit.MINUTES));

        ride = rideRepository.save(ride);

        // Update free ride offer with rideId
        if (freeRide && freeRideOfferId != null) {
            loyaltyClient.redeemOffer(freeRideOfferId, ride.getId());
        }

        // 5. Log status event
        logStatusEvent(ride, null, RideStatus.REQUESTED, riderId, "RIDER");

        // 6. Publish RideRequestedEvent
        eventPublisher.publishRideRequested(ride);

        log.info(
                "Created ride {} for rider {} with estimated fare {}",
                ride.getId(),
                riderId,
                estimatedFare);
        return ride;
    }

    // ---- Assign Driver (from matching-service) ----

    @Transactional
    public Ride assignDriver(UUID rideId, UUID driverId, int estimatedArrivalSeconds) {
        Ride ride =
                rideRepository
                        .findById(rideId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Ride not found: " + rideId));

        // Idempotency: if already assigned to same driver, no-op
        if (ride.getStatus() == RideStatus.DRIVER_ASSIGNED && driverId.equals(ride.getDriverId())) {
            return ride;
        }

        validateTransition(ride.getStatus(), RideStatus.DRIVER_ASSIGNED);

        RideStatus previousStatus = ride.getStatus();
        ride.setStatus(RideStatus.DRIVER_ASSIGNED);
        ride.setDriverId(driverId);
        ride.setAssignedAt(Instant.now());
        ride = rideRepository.save(ride);

        logStatusEvent(ride, previousStatus, RideStatus.DRIVER_ASSIGNED, driverId, "DRIVER");
        eventPublisher.publishStatusUpdated(ride, previousStatus, RideStatus.DRIVER_ASSIGNED);

        log.info("Assigned driver {} to ride {}", driverId, rideId);
        return ride;
    }

    // ---- Driver Arrived ----

    @Transactional
    public Ride driverArrived(UUID rideId, UUID driverId) {
        Ride ride =
                rideRepository
                        .findByIdAndDriverId(rideId, driverId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Ride not found or not assigned to this driver"));

        validateTransition(ride.getStatus(), RideStatus.DRIVER_ARRIVED);

        RideStatus previousStatus = ride.getStatus();
        ride.setStatus(RideStatus.DRIVER_ARRIVED);
        ride.setArrivedAt(Instant.now());
        ride = rideRepository.save(ride);

        // Generate and send OTP to rider
        tripOtpService.generateAndSendOtp(ride);

        logStatusEvent(ride, previousStatus, RideStatus.DRIVER_ARRIVED, driverId, "DRIVER");
        eventPublisher.publishStatusUpdated(ride, previousStatus, RideStatus.DRIVER_ARRIVED);

        log.info("Driver {} arrived for ride {}", driverId, rideId);
        return ride;
    }

    // ---- Start Trip (OTP verification) ----

    @Transactional
    public Ride startTrip(UUID rideId, UUID driverId, String otp) {
        Ride ride =
                rideRepository
                        .findByIdAndDriverId(rideId, driverId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Ride not found or not assigned to this driver"));

        if (ride.getStatus() != RideStatus.DRIVER_ARRIVED) {
            throw new BadRequestException("Driver must be marked as arrived first");
        }

        // Verify OTP -- throws BadRequestException on failure
        tripOtpService.verifyOtp(ride, otp);

        RideStatus previousStatus = ride.getStatus();
        ride.setStatus(RideStatus.IN_PROGRESS);
        ride.setStartedAt(Instant.now());
        ride = rideRepository.save(ride);

        logStatusEvent(ride, previousStatus, RideStatus.IN_PROGRESS, driverId, "DRIVER");
        eventPublisher.publishStatusUpdated(ride, previousStatus, RideStatus.IN_PROGRESS);

        log.info("Trip started for ride {} by driver {}", rideId, driverId);
        return ride;
    }

    // ---- Complete Trip ----

    @Transactional
    public Ride completeTrip(UUID rideId, UUID driverId) {
        Ride ride =
                rideRepository
                        .findByIdAndDriverId(rideId, driverId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Ride not found or not assigned to this driver"));

        validateTransition(ride.getStatus(), RideStatus.COMPLETED);

        // Calculate final fare via pricing-service
        EstimateDto finalCalc =
                pricingClient.calculateFinalFare(
                        ride.getCountryCode(),
                        ride.getVehicleType(),
                        ride.getDistanceMetres(),
                        ride.getDurationSeconds());

        BigDecimal finalFare = ride.getEstimatedFare();
        if (finalCalc != null && finalCalc.getEstimatedFare() != null) {
            finalFare = finalCalc.getEstimatedFare();
            if (finalCalc.getDistanceMetres() != null) {
                ride.setDistanceMetres(finalCalc.getDistanceMetres());
            }
            if (finalCalc.getDurationSeconds() != null) {
                ride.setDurationSeconds(finalCalc.getDurationSeconds());
            }
        }

        // For free rides, final fare still calculated but rider pays nothing
        if (ride.isFreeRide()) {
            // Store the actual calculated fare (for driver payment) but mark free
            ride.setFinalFare(finalFare);
        } else {
            ride.setFinalFare(finalFare);
        }

        RideStatus previousStatus = ride.getStatus();
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(Instant.now());
        ride = rideRepository.save(ride);

        logStatusEvent(ride, previousStatus, RideStatus.COMPLETED, driverId, "DRIVER");
        eventPublisher.publishRideCompleted(ride);
        eventPublisher.publishStatusUpdated(ride, previousStatus, RideStatus.COMPLETED);

        log.info("Ride {} completed with final fare {}", rideId, finalFare);
        return ride;
    }

    // ---- Cancel Ride ----

    @Transactional
    public Ride cancelRide(UUID rideId, UUID actorId, String role, String reason) {
        Ride ride =
                rideRepository
                        .findById(rideId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Ride not found: " + rideId));

        // Validate the actor has permission
        if ("RIDER".equals(role) && !ride.getRiderId().equals(actorId)) {
            throw new UnauthorizedException("You can only cancel your own ride");
        }
        if ("DRIVER".equals(role)
                && (ride.getDriverId() == null || !ride.getDriverId().equals(actorId))) {
            throw new UnauthorizedException("You can only cancel rides assigned to you");
        }

        // Validate cancellable statuses
        RideStatus current = ride.getStatus();
        if (current != RideStatus.REQUESTED
                && current != RideStatus.DRIVER_ASSIGNED
                && current != RideStatus.DRIVER_ARRIVED) {
            throw new BadRequestException("Ride cannot be cancelled in status: " + current);
        }

        RideStatus previousStatus = ride.getStatus();
        ride.setStatus(RideStatus.CANCELLED);
        ride.setCancelledAt(Instant.now());
        ride.setCancelReason(reason);
        ride.setCancelledBy(role);
        ride = rideRepository.save(ride);

        logStatusEvent(ride, previousStatus, RideStatus.CANCELLED, actorId, role);
        eventPublisher.publishRideCancelled(ride);
        eventPublisher.publishStatusUpdated(ride, previousStatus, RideStatus.CANCELLED);

        log.info("Ride {} cancelled by {} ({})", rideId, actorId, role);
        return ride;
    }

    // ---- Driver Rejection Handling ----

    @Transactional
    public void handleDriverRejection(UUID rideId, UUID driverId) {
        Ride ride =
                rideRepository
                        .findById(rideId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Ride not found: " + rideId));

        // Idempotency check
        if (rejectionRepository.existsByRideIdAndDriverId(rideId, driverId)) {
            log.info("Duplicate rejection for ride {} from driver {}", rideId, driverId);
            return;
        }

        // Record rejection
        RideDriverRejection rejection = new RideDriverRejection();
        rejection.setRideId(rideId);
        rejection.setDriverId(driverId);
        rejection.setRejectedAt(Instant.now());
        rejection.setCountryCode(ride.getCountryCode());
        rejectionRepository.save(rejection);

        // Increment counter
        ride.setDriverRejectionCount(ride.getDriverRejectionCount() + 1);
        rideRepository.save(ride);

        // Send rejection count update to rider
        SendNotificationEvent countUpdate = new SendNotificationEvent();
        countUpdate.setRecipientUserId(ride.getRiderId());
        countUpdate.setType(NotificationType.IN_APP);
        countUpdate.setData(
                Map.of(
                        "type",
                        "REJECTION_COUNT_UPDATE",
                        "rideId",
                        ride.getId().toString(),
                        "count",
                        String.valueOf(ride.getDriverRejectionCount())));
        countUpdate.setEventType("SEND_NOTIFICATION");
        countUpdate.setCountryCode(ride.getCountryCode());
        kafkaTemplate.send(KafkaConfig.TOPIC_NOTIFICATIONS_SEND, countUpdate);

        // At threshold: nudge rider to boost fare
        if (ride.getDriverRejectionCount() == REJECTION_NUDGE_THRESHOLD) {
            SendNotificationEvent nudge = new SendNotificationEvent();
            nudge.setRecipientUserId(ride.getRiderId());
            nudge.setType(NotificationType.PUSH);
            nudge.setTitleKey("notification.ride.boost-nudge.title");
            nudge.setBodyKey("notification.ride.boost-nudge.body");
            nudge.setData(Map.of("type", "FARE_BOOST_NUDGE", "rideId", ride.getId().toString()));
            nudge.setEventType("SEND_NOTIFICATION");
            nudge.setCountryCode(ride.getCountryCode());
            kafkaTemplate.send(KafkaConfig.TOPIC_NOTIFICATIONS_SEND, nudge);
        }

        log.info(
                "Driver {} rejected ride {}. Total rejections: {}",
                driverId,
                rideId,
                ride.getDriverRejectionCount());
    }

    // ---- Resend OTP ----

    @Transactional
    public Ride resendOtp(UUID rideId, UUID riderId) {
        Ride ride =
                rideRepository
                        .findByIdAndRiderId(rideId, riderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Ride not found"));

        if (ride.getStatus() != RideStatus.DRIVER_ARRIVED) {
            throw new BadRequestException("OTP can only be resent when driver has arrived");
        }

        tripOtpService.resendOtp(ride);
        return ride;
    }

    // ---- Query Methods ----

    public Ride getRide(UUID rideId) {
        return rideRepository
                .findById(rideId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found: " + rideId));
    }

    public Ride getRideForRider(UUID rideId, UUID riderId) {
        return rideRepository
                .findByIdAndRiderId(rideId, riderId)
                .orElseThrow(() -> new ResourceNotFoundException("Ride not found"));
    }

    public List<Ride> getActiveRidesForRider(UUID riderId) {
        return rideRepository.findByRiderIdAndStatusIn(
                riderId,
                List.of(
                        RideStatus.REQUESTED,
                        RideStatus.DRIVER_ASSIGNED,
                        RideStatus.DRIVER_ARRIVED,
                        RideStatus.IN_PROGRESS));
    }

    public List<Ride> getActiveRidesForDriver(UUID driverId) {
        return rideRepository.findByDriverIdAndStatusIn(
                driverId,
                List.of(
                        RideStatus.DRIVER_ASSIGNED,
                        RideStatus.DRIVER_ARRIVED,
                        RideStatus.IN_PROGRESS));
    }

    public Page<Ride> getRideHistory(UUID riderId, Pageable pageable) {
        return rideRepository.findByRiderIdOrderByCreatedAtDesc(riderId, pageable);
    }

    public Page<Ride> getRideHistoryByCity(UUID riderId, UUID cityId, int limit) {
        return rideRepository.findByRiderIdAndCityIdAndStatusOrderByCompletedAtDesc(
                riderId, cityId, RideStatus.COMPLETED, PageRequest.of(0, Math.min(limit, 10)));
    }

    // ---- Matching Timeout Scheduler ----

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void checkMatchingTimeouts() {
        List<Ride> timedOut =
                rideRepository.findByStatusAndMatchingTimeoutAtBefore(
                        RideStatus.REQUESTED, Instant.now());
        for (Ride ride : timedOut) {
            RideStatus previousStatus = ride.getStatus();
            ride.setStatus(RideStatus.NO_DRIVER_FOUND);
            ride.setCancelledBy("SYSTEM");
            ride.setCancelReason("No driver found within matching window");
            ride.setCancelledAt(Instant.now());
            rideRepository.save(ride);

            logStatusEvent(ride, previousStatus, RideStatus.NO_DRIVER_FOUND, null, "SYSTEM");
            eventPublisher.publishStatusUpdated(ride, previousStatus, RideStatus.NO_DRIVER_FOUND);

            log.info("Ride {} timed out -- no driver found", ride.getId());
        }
    }

    // ---- Status transition validation ----

    private void validateTransition(RideStatus from, RideStatus to) {
        boolean valid =
                switch (to) {
                    case DRIVER_ASSIGNED -> from == RideStatus.REQUESTED;
                    case DRIVER_ARRIVED -> from == RideStatus.DRIVER_ASSIGNED;
                    case IN_PROGRESS -> from == RideStatus.DRIVER_ARRIVED;
                    case COMPLETED -> from == RideStatus.IN_PROGRESS;
                    case CANCELLED ->
                            from == RideStatus.REQUESTED
                                    || from == RideStatus.DRIVER_ASSIGNED
                                    || from == RideStatus.DRIVER_ARRIVED;
                    case NO_DRIVER_FOUND -> from == RideStatus.REQUESTED;
                    default -> false;
                };
        if (!valid) {
            throw new BadRequestException("Invalid status transition: " + from + " -> " + to);
        }
    }

    private void logStatusEvent(
            Ride ride, RideStatus fromStatus, RideStatus toStatus, UUID actorId, String actorRole) {
        RideStatusEvent event = new RideStatusEvent();
        event.setRideId(ride.getId());
        event.setFromStatus(fromStatus != null ? fromStatus.name() : null);
        event.setToStatus(toStatus.name());
        event.setActorId(actorId);
        event.setActorRole(actorRole);
        event.setOccurredAt(Instant.now());
        event.setCountryCode(ride.getCountryCode());
        statusEventRepository.save(event);
    }
}
