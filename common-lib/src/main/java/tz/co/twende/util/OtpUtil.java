package tz.co.twende.common.util;

import java.security.SecureRandom;

public final class OtpUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OtpUtil() {}

    /**
     * Generate a 4-digit OTP for trip start verification. Driver enters this code (shared verbally
     * by the rider) to begin the trip.
     *
     * @return a zero-padded 4-digit string (e.g. "0042", "1234")
     */
    public static String generate4Digit() {
        return String.format("%04d", SECURE_RANDOM.nextInt(10000));
    }

    /**
     * Generate a 6-digit OTP for phone authentication. Sent via SMS to the user's phone number
     * during login.
     *
     * @return a zero-padded 6-digit string (e.g. "004217", "123456")
     */
    public static String generate6Digit() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1000000));
    }
}
