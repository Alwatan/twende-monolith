package tz.co.twende.ride.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.driver.DriverRejectedRideEvent;
import tz.co.twende.common.event.driver.RideOfferAcceptedEvent;
import tz.co.twende.ride.config.KafkaConfig;
import tz.co.twende.ride.service.RideService;

@Component
@RequiredArgsConstructor
@Slf4j
public class RideEventConsumer {

    private final RideService rideService;

    @KafkaListener(topics = KafkaConfig.TOPIC_OFFER_ACCEPTED, groupId = "ride-service")
    public void onOfferAccepted(RideOfferAcceptedEvent event) {
        log.info(
                "Received RideOfferAcceptedEvent: rideId={}, driverId={}",
                event.getRideId(),
                event.getDriverId());
        try {
            rideService.assignDriver(
                    event.getRideId(), event.getDriverId(), event.getEstimatedArrivalSeconds());
        } catch (Exception e) {
            log.warn(
                    "Failed to assign driver {} to ride {}: {}",
                    event.getDriverId(),
                    event.getRideId(),
                    e.getMessage());
        }
    }

    @KafkaListener(topics = KafkaConfig.TOPIC_DRIVER_REJECTED, groupId = "ride-service")
    public void onDriverRejected(DriverRejectedRideEvent event) {
        log.info(
                "Received DriverRejectedRideEvent: rideId={}, driverId={}",
                event.getRideId(),
                event.getDriverId());
        try {
            rideService.handleDriverRejection(event.getRideId(), event.getDriverId());
        } catch (Exception e) {
            log.warn(
                    "Failed to handle rejection for ride {}: {}",
                    event.getRideId(),
                    e.getMessage());
        }
    }
}
