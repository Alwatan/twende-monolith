package tz.co.twende.ride.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import tz.co.twende.common.enums.NotificationType;
import tz.co.twende.common.event.notification.SendNotificationEvent;
import tz.co.twende.common.exception.BadRequestException;
import tz.co.twende.common.util.OtpUtil;
import tz.co.twende.ride.config.KafkaConfig;
import tz.co.twende.ride.entity.Ride;
import tz.co.twende.ride.repository.RideRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class TripOtpService {

    private static final int MAX_OTP_ATTEMPTS = 3;
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int BCRYPT_STRENGTH = 4;

    private final RideRepository rideRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder(BCRYPT_STRENGTH);

    /** Generate a new 4-digit OTP, hash it, and send notification to rider. */
    public void generateAndSendOtp(Ride ride) {
        String otp = OtpUtil.generate4Digit();
        ride.setTripStartOtpHash(passwordEncoder.encode(otp));
        ride.setTripStartOtpExpiresAt(Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES));
        ride.setTripStartOtpAttempts(0);
        rideRepository.save(ride);

        sendOtpNotification(ride, otp);
        log.info("Generated trip start OTP for ride {}", ride.getId());
    }

    /**
     * Verify OTP submitted by driver.
     *
     * @return true if OTP is valid and trip can start
     * @throws BadRequestException if OTP is expired, wrong, or max attempts exceeded
     */
    public boolean verifyOtp(Ride ride, String plainOtp) {
        // Check expiry
        if (ride.getTripStartOtpExpiresAt() != null
                && ride.getTripStartOtpExpiresAt().isBefore(Instant.now())) {
            generateAndSendOtp(ride);
            throw new BadRequestException("Code expired. A new code has been sent to the rider.");
        }

        // Check OTP hash is present
        if (ride.getTripStartOtpHash() == null) {
            throw new BadRequestException("No OTP has been generated for this ride.");
        }

        // Check hash match
        if (!passwordEncoder.matches(plainOtp, ride.getTripStartOtpHash())) {
            ride.setTripStartOtpAttempts(ride.getTripStartOtpAttempts() + 1);

            if (ride.getTripStartOtpAttempts() >= MAX_OTP_ATTEMPTS) {
                generateAndSendOtp(ride);
                throw new BadRequestException(
                        "Too many incorrect attempts. A new code has been sent to the rider.");
            }

            int remaining = MAX_OTP_ATTEMPTS - ride.getTripStartOtpAttempts();
            rideRepository.save(ride);
            throw new BadRequestException(
                    "Incorrect code. " + remaining + " attempt(s) remaining.");
        }

        // Valid OTP -- single-use, null out immediately
        ride.setTripStartOtpHash(null);
        ride.setTripStartOtpExpiresAt(null);
        ride.setTripStartOtpAttempts(0);
        return true;
    }

    /** Resend a new OTP to the rider. */
    public void resendOtp(Ride ride) {
        generateAndSendOtp(ride);
    }

    private void sendOtpNotification(Ride ride, String otp) {
        SendNotificationEvent notification = new SendNotificationEvent();
        notification.setRecipientUserId(ride.getRiderId());
        notification.setType(NotificationType.PUSH);
        notification.setTitleKey("notification.trip.otp.title");
        notification.setBodyKey("notification.trip.otp.body");
        notification.setTemplateParams(Map.of("otp", otp));
        notification.setData(
                Map.of("otp", otp, "rideId", ride.getId().toString(), "type", "TRIP_OTP"));
        notification.setEventType("SEND_NOTIFICATION");
        notification.setCountryCode(ride.getCountryCode());

        kafkaTemplate.send(KafkaConfig.TOPIC_NOTIFICATIONS_SEND, notification);
    }
}
