package tz.co.twende.ride.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.event.ride.RideFareBoostedEvent;
import tz.co.twende.common.event.ride.RideRequestedEvent;
import tz.co.twende.common.event.ride.RideStatusUpdatedEvent;
import tz.co.twende.ride.config.KafkaConfig;
import tz.co.twende.ride.entity.Ride;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRideRequested(Ride ride) {
        RideRequestedEvent event = new RideRequestedEvent();
        event.setRideId(ride.getId());
        event.setRiderId(ride.getRiderId());
        event.setEstimatedFare(ride.getEstimatedFare());
        event.setEventType("RIDE_REQUESTED");
        event.setCountryCode(ride.getCountryCode());

        tz.co.twende.common.event.Location pickup = new tz.co.twende.common.event.Location();
        pickup.setLatitude(ride.getPickupLat());
        pickup.setLongitude(ride.getPickupLng());
        pickup.setAddress(ride.getPickupAddress());
        event.setPickupLocation(pickup);

        tz.co.twende.common.event.Location dropoff = new tz.co.twende.common.event.Location();
        dropoff.setLatitude(ride.getDropoffLat());
        dropoff.setLongitude(ride.getDropoffLng());
        dropoff.setAddress(ride.getDropoffAddress());
        event.setDropoffLocation(dropoff);

        String key = ride.getCountryCode() + ":" + ride.getId();
        kafkaTemplate.send(KafkaConfig.TOPIC_RIDES_REQUESTED, key, event);
        log.info("Published RideRequestedEvent for ride {}", ride.getId());
    }

    public void publishStatusUpdated(Ride ride, RideStatus previousStatus, RideStatus newStatus) {
        RideStatusUpdatedEvent event = new RideStatusUpdatedEvent();
        event.setRideId(ride.getId());
        event.setPreviousStatus(previousStatus);
        event.setNewStatus(newStatus);
        event.setEventType("RIDE_STATUS_UPDATED");
        event.setCountryCode(ride.getCountryCode());

        String key = ride.getCountryCode() + ":" + ride.getId();
        kafkaTemplate.send(KafkaConfig.TOPIC_RIDES_STATUS_UPDATED, key, event);
        log.info(
                "Published RideStatusUpdatedEvent for ride {}: {} -> {}",
                ride.getId(),
                previousStatus,
                newStatus);
    }

    public void publishRideCompleted(Ride ride) {
        RideCompletedEvent event = new RideCompletedEvent();
        event.setRideId(ride.getId());
        event.setRiderId(ride.getRiderId());
        event.setDriverId(ride.getDriverId());
        event.setFinalFare(ride.getFinalFare());
        event.setDistanceMetres(ride.getDistanceMetres());
        event.setDurationSeconds(ride.getDurationSeconds());
        event.setStartedAt(ride.getStartedAt());
        event.setCompletedAt(ride.getCompletedAt());
        event.setFreeRide(ride.isFreeRide());
        event.setFreeRideOfferId(ride.getFreeRideOfferId());
        event.setEventType("RIDE_COMPLETED");
        event.setCountryCode(ride.getCountryCode());

        String key = ride.getCountryCode() + ":" + ride.getId();
        kafkaTemplate.send(KafkaConfig.TOPIC_RIDES_COMPLETED, key, event);
        log.info("Published RideCompletedEvent for ride {}", ride.getId());
    }

    public void publishRideCancelled(Ride ride) {
        RideStatusUpdatedEvent event = new RideStatusUpdatedEvent();
        event.setRideId(ride.getId());
        event.setNewStatus(RideStatus.CANCELLED);
        event.setEventType("RIDE_CANCELLED");
        event.setCountryCode(ride.getCountryCode());

        String key = ride.getCountryCode() + ":" + ride.getId();
        kafkaTemplate.send(KafkaConfig.TOPIC_RIDES_CANCELLED, key, event);
        log.info("Published RideCancelledEvent for ride {}", ride.getId());
    }

    public void publishFareBoosted(
            Ride ride, java.math.BigDecimal previousFare, java.math.BigDecimal boostAmount) {
        RideFareBoostedEvent event = new RideFareBoostedEvent();
        event.setRideId(ride.getId());
        event.setRiderId(ride.getRiderId());
        event.setPreviousFare(previousFare);
        event.setNewFare(ride.getEstimatedFare());
        event.setBoostAmount(boostAmount);
        event.setEventType("RIDE_FARE_BOOSTED");
        event.setCountryCode(ride.getCountryCode());

        String key = ride.getCountryCode() + ":" + ride.getId();
        kafkaTemplate.send(KafkaConfig.TOPIC_RIDES_FARE_BOOSTED, key, event);
        log.info("Published RideFareBoostedEvent for ride {}", ride.getId());
    }
}
