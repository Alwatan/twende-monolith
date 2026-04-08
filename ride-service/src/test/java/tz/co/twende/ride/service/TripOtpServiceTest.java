package tz.co.twende.ride.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.ride.entity.Ride;
import tz.co.twende.ride.repository.RideRepository;

@ExtendWith(MockitoExtension.class)
class TripOtpServiceTest {

    @Mock private RideRepository rideRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private TripOtpService tripOtpService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void givenRide_whenGenerateOtp_thenHashSetAndNotificationSent() {
        Ride ride = createRide();
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        tripOtpService.generateAndSendOtp(ride);

        assertNotNull(ride.getTripStartOtpHash());
        assertNotNull(ride.getTripStartOtpExpiresAt());
        assertEquals(0, ride.getTripStartOtpAttempts());
        verify(rideRepository).save(ride);
        verify(kafkaTemplate).send(eq("twende.notifications.send"), any());
    }

    @Test
    void givenValidOtp_whenVerify_thenReturnTrueAndNullOutHash() {
        Ride ride = createRide();
        String otp = "1234";
        ride.setTripStartOtpHash(encoder.encode(otp));
        ride.setTripStartOtpExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        ride.setTripStartOtpAttempts(0);

        boolean result = tripOtpService.verifyOtp(ride, otp);

        assertTrue(result);
        assertNull(ride.getTripStartOtpHash());
        assertNull(ride.getTripStartOtpExpiresAt());
    }

    @Test
    void givenWrongOtp_whenVerify_thenThrowWithRemainingAttempts() {
        Ride ride = createRide();
        ride.setTripStartOtpHash(encoder.encode("1234"));
        ride.setTripStartOtpExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        ride.setTripStartOtpAttempts(0);
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BadRequestException ex =
                assertThrows(
                        BadRequestException.class, () -> tripOtpService.verifyOtp(ride, "9999"));

        assertTrue(ex.getMessage().contains("2 attempt(s) remaining"));
        assertEquals(1, ride.getTripStartOtpAttempts());
    }

    @Test
    void givenThreeWrongAttempts_whenVerify_thenRegenerateOtpAndThrow() {
        Ride ride = createRide();
        ride.setTripStartOtpHash(encoder.encode("1234"));
        ride.setTripStartOtpExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));
        ride.setTripStartOtpAttempts(2); // third attempt will exceed
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BadRequestException ex =
                assertThrows(
                        BadRequestException.class, () -> tripOtpService.verifyOtp(ride, "9999"));

        assertTrue(ex.getMessage().contains("Too many incorrect attempts"));
        // OTP should have been regenerated
        verify(rideRepository, atLeastOnce()).save(ride);
    }

    @Test
    void givenExpiredOtp_whenVerify_thenRegenerateAndThrow() {
        Ride ride = createRide();
        ride.setTripStartOtpHash(encoder.encode("1234"));
        ride.setTripStartOtpExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        ride.setTripStartOtpAttempts(0);
        when(rideRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BadRequestException ex =
                assertThrows(
                        BadRequestException.class, () -> tripOtpService.verifyOtp(ride, "1234"));

        assertTrue(ex.getMessage().contains("Code expired"));
    }

    @Test
    void givenNoOtpHash_whenVerify_thenThrowBadRequest() {
        Ride ride = createRide();
        ride.setTripStartOtpHash(null);
        ride.setTripStartOtpExpiresAt(Instant.now().plus(5, ChronoUnit.MINUTES));

        assertThrows(BadRequestException.class, () -> tripOtpService.verifyOtp(ride, "1234"));
    }

    private Ride createRide() {
        Ride ride = new Ride();
        ride.setId(UUID.randomUUID());
        ride.setRiderId(UUID.randomUUID());
        ride.setCountryCode("TZ");
        ride.setVehicleType("BAJAJ");
        ride.setPickupLat(BigDecimal.valueOf(-6.77));
        ride.setPickupLng(BigDecimal.valueOf(39.23));
        ride.setPickupAddress("Test");
        ride.setDropoffLat(BigDecimal.valueOf(-6.81));
        ride.setDropoffLng(BigDecimal.valueOf(39.28));
        ride.setDropoffAddress("Test");
        ride.setEstimatedFare(BigDecimal.valueOf(3500));
        ride.setFareBoostAmount(BigDecimal.ZERO);
        ride.setCurrencyCode("TZS");
        ride.setRequestedAt(Instant.now());
        return ride;
    }
}
