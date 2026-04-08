package tz.co.twende.matching.kafka;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tz.co.twende.common.event.driver.DriverOfferNotificationEvent;
import tz.co.twende.common.event.driver.DriverRejectedRideEvent;
import tz.co.twende.common.event.driver.RideOfferAcceptedEvent;
import tz.co.twende.matching.config.KafkaConfig;
import tz.co.twende.matching.dto.OfferPayload;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOfferNotification(OfferPayload payload, String countryCode, UUID riderId) {
        DriverOfferNotificationEvent event = new DriverOfferNotificationEvent();
        event.setEventType("DRIVER_OFFER_NOTIFICATION");
        event.setCountryCode(countryCode);
        event.setRideId(payload.getRideId());
        event.setDriverId(payload.getDriverId());
        event.setPickupDistanceKm(
                payload.getPickupDistanceKm() != null
                        ? payload.getPickupDistanceKm().doubleValue()
                        : 0.0);
        event.setPickupAreaName(payload.getPickupAreaName());
        event.setEstimatedTripDistanceKm(
                payload.getEstimatedTripDistanceKm() != null
                        ? payload.getEstimatedTripDistanceKm().doubleValue()
                        : 0.0);
        event.setEstimatedTripMinutes(payload.getEstimatedTripMinutes());
        event.setTotalFare(payload.getTotalFare());
        event.setBoostAmount(
                payload.getBoostAmount() != null ? payload.getBoostAmount() : BigDecimal.ZERO);
        event.setCurrencyCode(payload.getCurrencyCode());
        event.setOfferWindowSeconds(payload.getOfferWindowSeconds());

        String key = countryCode + ":" + payload.getRideId();
        kafkaTemplate.send(KafkaConfig.TOPIC_DRIVERS_OFFER_NOTIFICATION, key, event);
        log.debug(
                "Published offer notification for ride {} to driver {}",
                payload.getRideId(),
                payload.getDriverId());
    }

    public void publishOfferAccepted(
            UUID rideId, UUID driverId, String countryCode, int estimatedArrivalSeconds) {
        RideOfferAcceptedEvent event = new RideOfferAcceptedEvent();
        event.setEventType("RIDE_OFFER_ACCEPTED");
        event.setCountryCode(countryCode);
        event.setRideId(rideId);
        event.setDriverId(driverId);
        event.setEstimatedArrivalSeconds(estimatedArrivalSeconds);

        String key = countryCode + ":" + rideId;
        kafkaTemplate.send(KafkaConfig.TOPIC_RIDES_OFFER_ACCEPTED, key, event);
        log.info("Published offer accepted for ride {} by driver {}", rideId, driverId);
    }

    public void publishDriverRejected(
            UUID rideId, UUID driverId, String countryCode, int newRejectionCount) {
        DriverRejectedRideEvent event = new DriverRejectedRideEvent();
        event.setEventType("DRIVER_REJECTED_RIDE");
        event.setCountryCode(countryCode);
        event.setRideId(rideId);
        event.setDriverId(driverId);
        event.setNewRejectionCount(newRejectionCount);

        String key = countryCode + ":" + rideId;
        kafkaTemplate.send(KafkaConfig.TOPIC_DRIVERS_REJECTED_RIDE, key, event);
        log.info("Published driver rejected for ride {} by driver {}", rideId, driverId);
    }
}
