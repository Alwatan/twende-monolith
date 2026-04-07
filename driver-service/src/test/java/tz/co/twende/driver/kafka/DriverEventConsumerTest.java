package tz.co.twende.driver.kafka;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.common.enums.DriverStatus;
import tz.co.twende.common.enums.UserRole;
import tz.co.twende.common.event.ride.RideCompletedEvent;
import tz.co.twende.common.event.subscription.SubscriptionExpiredEvent;
import tz.co.twende.common.event.user.UserRegisteredEvent;
import tz.co.twende.driver.entity.DriverProfile;
import tz.co.twende.driver.repository.DriverProfileRepository;
import tz.co.twende.driver.service.DriverService;

@ExtendWith(MockitoExtension.class)
class DriverEventConsumerTest {

    @Mock private DriverProfileRepository driverProfileRepository;
    @Mock private EntityManager entityManager;
    @Mock private DriverService driverService;
    @Mock private DriverEventPublisher driverEventPublisher;

    @InjectMocks private DriverEventConsumer consumer;

    @Test
    void givenDriverRegistration_whenConsumed_thenProfileCreated() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = createDriverEvent(userId);

        when(driverProfileRepository.existsById(userId)).thenReturn(false);

        consumer.onUserRegistered(event);

        ArgumentCaptor<DriverProfile> captor = ArgumentCaptor.forClass(DriverProfile.class);
        verify(entityManager).merge(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(userId);
        assertThat(captor.getValue().getFullName()).isEqualTo("Driver Name");
        assertThat(captor.getValue().getCountryCode()).isEqualTo("TZ");
        assertThat(captor.getValue().getStatus()).isEqualTo(DriverStatus.PENDING_APPROVAL);
    }

    @Test
    void givenRiderRegistration_whenConsumed_thenIgnored() {
        UserRegisteredEvent event = createRiderEvent(UUID.randomUUID());

        consumer.onUserRegistered(event);

        verify(entityManager, never()).merge(any());
    }

    @Test
    void givenDuplicateRegistration_whenConsumed_thenNoError() {
        UUID userId = UUID.randomUUID();
        UserRegisteredEvent event = createDriverEvent(userId);

        when(driverProfileRepository.existsById(userId)).thenReturn(true);

        consumer.onUserRegistered(event);

        verify(entityManager, never()).merge(any());
    }

    @Test
    void givenRideCompleted_whenConsumed_thenTripCountUpdated() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, 5);

        RideCompletedEvent event = new RideCompletedEvent();
        event.setDriverId(driverId);
        event.setCompletedAt(Instant.now());

        when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.save(any())).thenReturn(driver);

        consumer.onRideCompleted(event);

        ArgumentCaptor<DriverProfile> captor = ArgumentCaptor.forClass(DriverProfile.class);
        verify(driverProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getTripCount()).isEqualTo(6);
    }

    @Test
    void givenRideCompleted_whenDriverNotFound_thenIgnored() {
        UUID driverId = UUID.randomUUID();
        RideCompletedEvent event = new RideCompletedEvent();
        event.setDriverId(driverId);

        when(driverProfileRepository.findById(driverId)).thenReturn(Optional.empty());

        consumer.onRideCompleted(event);

        verify(driverProfileRepository, never()).save(any());
    }

    @Test
    void givenSubscriptionExpired_whenDriverIsOnline_thenForcedOffline() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, DriverStatus.ONLINE_AVAILABLE);

        SubscriptionExpiredEvent event = new SubscriptionExpiredEvent();
        event.setDriverId(driverId);

        when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(driverProfileRepository.save(any())).thenReturn(driver);

        consumer.onSubscriptionExpired(event);

        ArgumentCaptor<DriverProfile> captor = ArgumentCaptor.forClass(DriverProfile.class);
        verify(driverProfileRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DriverStatus.OFFLINE);
        verify(driverService)
                .logStatusChange(
                        eq(driverId),
                        any(),
                        eq(DriverStatus.ONLINE_AVAILABLE),
                        eq(DriverStatus.OFFLINE),
                        eq("Subscription expired"));
        verify(driverEventPublisher).publishStatusUpdated(any(), eq(DriverStatus.ONLINE_AVAILABLE));
    }

    @Test
    void givenSubscriptionExpired_whenDriverIsOffline_thenNoChange() {
        UUID driverId = UUID.randomUUID();
        DriverProfile driver = createDriver(driverId, DriverStatus.OFFLINE);

        SubscriptionExpiredEvent event = new SubscriptionExpiredEvent();
        event.setDriverId(driverId);

        when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(driver));

        consumer.onSubscriptionExpired(event);

        verify(driverProfileRepository, never()).save(any());
    }

    @Test
    void givenAdminRegistration_whenConsumed_thenIgnored() {
        UserRegisteredEvent event = createAdminEvent();

        consumer.onUserRegistered(event);

        verify(entityManager, never()).merge(any());
    }

    private UserRegisteredEvent createDriverEvent(UUID userId) {
        UserRegisteredEvent e = new UserRegisteredEvent();
        e.setUserId(userId);
        e.setFullName("Driver Name");
        e.setPhoneNumber("+255712345678");
        e.setRole(UserRole.DRIVER);
        e.setCountryCode("TZ");
        return e;
    }

    private UserRegisteredEvent createRiderEvent(UUID userId) {
        UserRegisteredEvent e = new UserRegisteredEvent();
        e.setUserId(userId);
        e.setFullName("Rider Name");
        e.setPhoneNumber("+255712345678");
        e.setRole(UserRole.RIDER);
        e.setCountryCode("TZ");
        return e;
    }

    private UserRegisteredEvent createAdminEvent() {
        UserRegisteredEvent e = new UserRegisteredEvent();
        e.setUserId(UUID.randomUUID());
        e.setFullName("Admin Name");
        e.setPhoneNumber("+255712345679");
        e.setRole(UserRole.ADMIN);
        e.setCountryCode("TZ");
        return e;
    }

    private DriverProfile createDriver(UUID id, int tripCount) {
        DriverProfile driver = new DriverProfile();
        driver.setId(id);
        driver.setFullName("John Driver");
        driver.setCountryCode("TZ");
        driver.setStatus(DriverStatus.ONLINE_AVAILABLE);
        driver.setTripCount(tripCount);
        driver.setCreatedAt(Instant.now());
        driver.setUpdatedAt(Instant.now());
        return driver;
    }

    private DriverProfile createDriver(UUID id, DriverStatus status) {
        DriverProfile driver = createDriver(id, 0);
        driver.setStatus(status);
        return driver;
    }
}
