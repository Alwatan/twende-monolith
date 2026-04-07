package tz.co.twende.driver.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.common.event.driver.DriverStatusUpdatedEvent;
import tz.co.twende.driver.config.KafkaConfig;
import tz.co.twende.driver.entity.DriverProfile;

@Component
@RequiredArgsConstructor
@Slf4j
public class DriverEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishStatusUpdated(DriverProfile driver, DriverStatus previousStatus) {
        DriverStatusUpdatedEvent event = new DriverStatusUpdatedEvent();
        event.setDriverId(driver.getId());
        event.setPreviousStatus(previousStatus);
        event.setNewStatus(driver.getStatus());
        event.setCountryCode(driver.getCountryCode());
        event.setEventType("DRIVER_STATUS_UPDATED");

        kafkaTemplate.send(
                KafkaConfig.TOPIC_DRIVERS_STATUS_UPDATED, driver.getId().toString(), event);
        log.info(
                "Published driver status update for {}: {} -> {}",
                driver.getId(),
                previousStatus,
                driver.getStatus());
    }

    public void publishDriverApproved(DriverProfile driver) {
        DriverStatusUpdatedEvent event = new DriverStatusUpdatedEvent();
        event.setDriverId(driver.getId());
        event.setPreviousStatus(DriverStatus.PENDING_APPROVAL);
        event.setNewStatus(DriverStatus.APPROVED);
        event.setCountryCode(driver.getCountryCode());
        event.setEventType("DRIVER_APPROVED");

        kafkaTemplate.send(KafkaConfig.TOPIC_DRIVERS_APPROVED, driver.getId().toString(), event);
        log.info("Published driver approved event for {}", driver.getId());
    }
}
