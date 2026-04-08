package tz.co.twende.ride.kafka;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tz.co.twende.common.enums.RideStatus;
import tz.co.twende.ride.entity.Ride;

@ExtendWith(MockitoExtension.class)
class RideEventPublisherTest {

    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private RideEventPublisher publisher;

    @Test
    void givenRide_whenPublishRideRequested_thenKafkaSendCalled() {
        Ride ride = createTestRide();

        publisher.publishRideRequested(ride);

        verify(kafkaTemplate).send(eq("twende.rides.requested"), anyString(), any());
    }

    @Test
    void givenRide_whenPublishStatusUpdated_thenKafkaSendCalled() {
        Ride ride = createTestRide();

        publisher.publishStatusUpdated(ride, RideStatus.REQUESTED, RideStatus.DRIVER_ASSIGNED);

        verify(kafkaTemplate).send(eq("twende.rides.status-updated"), anyString(), any());
    }

    @Test
    void givenCompletedRide_whenPublishRideCompleted_thenKafkaSendCalled() {
        Ride ride = createTestRide();
        ride.setDriverId(UUID.randomUUID());
        ride.setFinalFare(BigDecimal.valueOf(3800));
        ride.setStartedAt(Instant.now());
        ride.setCompletedAt(Instant.now());

        publisher.publishRideCompleted(ride);

        verify(kafkaTemplate).send(eq("twende.rides.completed"), anyString(), any());
    }

    @Test
    void givenCancelledRide_whenPublishRideCancelled_thenKafkaSendCalled() {
        Ride ride = createTestRide();

        publisher.publishRideCancelled(ride);

        verify(kafkaTemplate).send(eq("twende.rides.cancelled"), anyString(), any());
    }

    @Test
    void givenBoostedRide_whenPublishFareBoosted_thenKafkaSendCalled() {
        Ride ride = createTestRide();

        publisher.publishFareBoosted(ride, BigDecimal.valueOf(3500), BigDecimal.valueOf(1000));

        verify(kafkaTemplate).send(eq("twende.rides.fare-boosted"), anyString(), any());
    }

    private Ride createTestRide() {
        Ride ride = new Ride();
        ride.setId(UUID.randomUUID());
        ride.setRiderId(UUID.randomUUID());
        ride.setCountryCode("TZ");
        ride.setVehicleType("BAJAJ");
        ride.setStatus(RideStatus.REQUESTED);
        ride.setPickupLat(BigDecimal.valueOf(-6.77));
        ride.setPickupLng(BigDecimal.valueOf(39.23));
        ride.setPickupAddress("Test Pickup");
        ride.setDropoffLat(BigDecimal.valueOf(-6.81));
        ride.setDropoffLng(BigDecimal.valueOf(39.28));
        ride.setDropoffAddress("Test Dropoff");
        ride.setEstimatedFare(BigDecimal.valueOf(3500));
        ride.setFareBoostAmount(BigDecimal.ZERO);
        ride.setCurrencyCode("TZS");
        ride.setRequestedAt(Instant.now());
        return ride;
    }
}
