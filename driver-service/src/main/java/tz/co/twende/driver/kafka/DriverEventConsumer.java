package tz.co.twende.driver.kafka;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.common.enums.UserRole;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.event.subscription.SubscriptionActivatedEvent;
import tz.co.twende.common.event.subscription.SubscriptionExpiredEvent;
import tz.co.twende.common.event.user.UserRegisteredEvent;
import tz.co.twende.driver.entity.DriverProfile;
import tz.co.twende.driver.repository.DriverProfileRepository;
import tz.co.twende.driver.service.DriverService;

@Component
@RequiredArgsConstructor
@Slf4j
public class DriverEventConsumer {

    private final DriverProfileRepository driverProfileRepository;
    private final EntityManager entityManager;
    private final DriverService driverService;
    private final DriverEventPublisher driverEventPublisher;

    @KafkaListener(
            topics = "twende.users.registered",
            groupId = "driver-service-group")
    @Transactional
    public void onUserRegistered(UserRegisteredEvent event) {
        if (event.getRole() != UserRole.DRIVER) {
            log.debug(
                    "Ignoring non-DRIVER registration for user {}",
                    event.getUserId());
            return;
        }

        if (driverProfileRepository.existsById(event.getUserId())) {
            log.debug(
                    "Driver profile already exists for {}, skipping",
                    event.getUserId());
            return;
        }

        DriverProfile driver = new DriverProfile();
        driver.setId(event.getUserId());
        driver.setFullName(event.getFullName());
        driver.setCountryCode(event.getCountryCode());
        driver.setStatus(DriverStatus.PENDING_APPROVAL);

        entityManager.merge(driver);
        log.info("Created driver profile for {}", event.getUserId());
    }

    @KafkaListener(
            topics = "twende.rides.completed",
            groupId = "driver-service-group")
    @Transactional
    public void onRideCompleted(RideCompletedEvent event) {
        driverProfileRepository
                .findById(event.getDriverId())
                .ifPresent(
                        driver -> {
                            driver.setTripCount(driver.getTripCount() + 1);
                            driver.setLastTripAt(
                                    event.getCompletedAt() != null
                                            ? event.getCompletedAt()
                                            : Instant.now());
                            driverProfileRepository.save(driver);
                            log.info(
                                    "Updated trip count for driver {}: {}",
                                    driver.getId(),
                                    driver.getTripCount());
                        });
    }

    @KafkaListener(
            topics = "twende.subscriptions.activated",
            groupId = "driver-service-group")
    public void onSubscriptionActivated(SubscriptionActivatedEvent event) {
        log.info(
                "Subscription activated for driver {}, plan: {}",
                event.getDriverId(),
                event.getPlan());
    }

    @KafkaListener(
            topics = "twende.subscriptions.expired",
            groupId = "driver-service-group")
    @Transactional
    public void onSubscriptionExpired(SubscriptionExpiredEvent event) {
        driverProfileRepository
                .findById(event.getDriverId())
                .ifPresent(
                        driver -> {
                            if (driver.getStatus() == DriverStatus.ONLINE_AVAILABLE) {
                                DriverStatus oldStatus = driver.getStatus();
                                driver.setStatus(DriverStatus.OFFLINE);
                                driverProfileRepository.save(driver);
                                driverService.logStatusChange(
                                        driver.getId(),
                                        driver.getCountryCode(),
                                        oldStatus,
                                        DriverStatus.OFFLINE,
                                        "Subscription expired");
                                driverEventPublisher.publishStatusUpdated(
                                        driver, oldStatus);
                                log.info(
                                        "Forced driver {} offline due to"
                                                + " subscription expiry",
                                        driver.getId());
                            }
                        });
    }
}
