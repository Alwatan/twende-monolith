package tz.co.twende.location.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.driver.DriverStatusUpdatedEvent;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.location.service.TripTraceService;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocationKafkaConsumer {

    private final TripTraceService tripTraceService;

    @KafkaListener(topics = "twende.rides.completed", groupId = "location-service")
    public void onRideCompleted(RideCompletedEvent event) {
        log.info("Ride completed event received: rideId={}", event.getRideId());
        try {
            tripTraceService.flushTrace(
                    event.getRideId(),
                    event.getDriverId(),
                    event.getCountryCode(),
                    event.getStartedAt(),
                    event.getCompletedAt());
        } catch (Exception e) {
            log.error("Failed to flush trace for ride {}", event.getRideId(), e);
        }
    }

    @KafkaListener(topics = "twende.drivers.status-updated", groupId = "location-service")
    public void onDriverStatusUpdated(DriverStatusUpdatedEvent event) {
        log.debug(
                "Driver status updated: driverId={}, newStatus={}",
                event.getDriverId(),
                event.getNewStatus());
        // GEO index updates happen via WebSocket location updates.
        // When a driver goes OFFLINE, their location hash TTL (90s) will expire,
        // effectively removing them from the system.
        // Explicit removal from GEO set could be done here if vehicleType is available.
    }
}
