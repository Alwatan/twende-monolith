package com.twende.common.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OtpUtilTest {

    @Test
    void givenGenerate4Digit_whenCalled_thenReturns4CharacterString() {
        String otp = OtpUtil.generate4Digit();
        assertEquals(4, otp.length());
    }

    @Test
    void givenGenerate4Digit_whenCalled_thenReturnsOnlyDigits() {
        String otp = OtpUtil.generate4Digit();
        assertTrue(otp.matches("\\d{4}"), "OTP should contain only digits: " + otp);
    }

    @Test
    void givenGenerate6Digit_whenCalled_thenReturns6CharacterString() {
        String otp = OtpUtil.generate6Digit();
        assertEquals(6, otp.length());
    }

    @Test
    void givenGenerate6Digit_whenCalled_thenReturnsOnlyDigits() {
        String otp = OtpUtil.generate6Digit();
        assertTrue(otp.matches("\\d{6}"), "OTP should contain only digits: " + otp);
    }

    @Test
    void givenMultipleCallsToGenerate4Digit_whenCalled_thenReturnsDifferentValues() {
        Set<String> otps = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            otps.add(OtpUtil.generate4Digit());
        }
        // With 10,000 possible values, 50 calls should produce at least 2 unique values
        assertTrue(
                otps.size() > 1, "Expected multiple unique OTPs from 50 calls, got " + otps.size());
    }

    @Test
    void givenMultipleCallsToGenerate6Digit_whenCalled_thenReturnsDifferentValues() {
        Set<String> otps = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            otps.add(OtpUtil.generate6Digit());
        }
        assertTrue(
                otps.size() > 1, "Expected multiple unique OTPs from 50 calls, got " + otps.size());
    }

    @Test
    void givenGenerate4Digit_whenValueIsSmall_thenIsZeroPadded() {
        // Run multiple times to increase chance of hitting a small value
        // At minimum, verify format is always 4 digits (zero-padding tested implicitly)
        for (int i = 0; i < 100; i++) {
            String otp = OtpUtil.generate4Digit();
            assertEquals(4, otp.length(), "OTP must always be 4 chars, got: " + otp);
            assertTrue(otp.matches("\\d{4}"), "OTP must be 4 digits, got: " + otp);
        }
    }

    @Test
    void givenGenerate6Digit_whenValueIsSmall_thenIsZeroPadded() {
        for (int i = 0; i < 100; i++) {
            String otp = OtpUtil.generate6Digit();
            assertEquals(6, otp.length(), "OTP must always be 6 chars, got: " + otp);
            assertTrue(otp.matches("\\d{6}"), "OTP must be 6 digits, got: " + otp);
        }
    }
}
