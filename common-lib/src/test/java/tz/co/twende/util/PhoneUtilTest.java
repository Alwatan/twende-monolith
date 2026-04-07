package tz.co.twende.common.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tz.co.twende.common.exception.BadRequestException;

class PhoneUtilTest {

    // --- TZ numbers ---

    @Test
    void givenTzNumberStartingWith0_whenNormalise_thenReturnsE164WithPlus255() {
        String result = PhoneUtil.normalise("0712345678", "TZ");
        assertEquals("+255712345678", result);
    }

    @Test
    void givenTzNumberStartingWith255NoPlus_whenNormalise_thenPrependsPlusSign() {
        String result = PhoneUtil.normalise("255712345678", "TZ");
        assertEquals("+255712345678", result);
    }

    @Test
    void givenTzNumberAlreadyE164_whenNormalise_thenReturnsUnchanged() {
        String result = PhoneUtil.normalise("+255712345678", "TZ");
        assertEquals("+255712345678", result);
    }

    // --- KE numbers ---

    @Test
    void givenKeNumberStartingWith0_whenNormalise_thenReturnsE164WithPlus254() {
        String result = PhoneUtil.normalise("0712345678", "KE");
        assertEquals("+254712345678", result);
    }

    @Test
    void givenKeNumberStartingWith254NoPlus_whenNormalise_thenPrependsPlusSign() {
        String result = PhoneUtil.normalise("254712345678", "KE");
        assertEquals("+254712345678", result);
    }

    @Test
    void givenKeNumberAlreadyE164_whenNormalise_thenReturnsUnchanged() {
        String result = PhoneUtil.normalise("+254712345678", "KE");
        assertEquals("+254712345678", result);
    }

    // --- UG numbers ---

    @Test
    void givenUgNumberStartingWith0_whenNormalise_thenReturnsE164WithPlus256() {
        String result = PhoneUtil.normalise("0712345678", "UG");
        assertEquals("+256712345678", result);
    }

    @Test
    void givenUgNumberStartingWith256NoPlus_whenNormalise_thenPrependsPlusSign() {
        String result = PhoneUtil.normalise("256712345678", "UG");
        assertEquals("+256712345678", result);
    }

    // --- Whitespace and special characters ---

    @Test
    void givenNumberWithSpaces_whenNormalise_thenStripsSpacesAndNormalises() {
        String result = PhoneUtil.normalise("071 234 5678", "TZ");
        assertEquals("+255712345678", result);
    }

    @Test
    void givenNumberWithDashes_whenNormalise_thenStripsDashesAndNormalises() {
        String result = PhoneUtil.normalise("071-234-5678", "TZ");
        assertEquals("+255712345678", result);
    }

    @Test
    void givenNumberWithParentheses_whenNormalise_thenStripsParensAndNormalises() {
        String result = PhoneUtil.normalise("(071) 2345678", "TZ");
        assertEquals("+255712345678", result);
    }

    @Test
    void givenNumberWithMixedFormatting_whenNormalise_thenStripsAllAndNormalises() {
        String result = PhoneUtil.normalise("+255 (71) 234-5678", "TZ");
        assertEquals("+255712345678", result);
    }

    // --- Invalid country code ---

    @Test
    void givenNullCountryCode_whenNormalise_thenThrowsBadRequestException() {
        BadRequestException ex =
                assertThrows(
                        BadRequestException.class, () -> PhoneUtil.normalise("0712345678", null));
        assertTrue(ex.getMessage().contains("Unsupported country code"));
    }

    @Test
    void givenInvalidCountryCode_whenNormalise_thenThrowsBadRequestException() {
        BadRequestException ex =
                assertThrows(
                        BadRequestException.class, () -> PhoneUtil.normalise("0712345678", "ZZ"));
        assertTrue(ex.getMessage().contains("Unsupported country code"));
    }

    // --- Null/blank phone ---

    @Test
    void givenNullPhone_whenNormalise_thenThrowsBadRequestException() {
        BadRequestException ex =
                assertThrows(BadRequestException.class, () -> PhoneUtil.normalise(null, "TZ"));
        assertTrue(ex.getMessage().contains("Phone number is required"));
    }

    @Test
    void givenBlankPhone_whenNormalise_thenThrowsBadRequestException() {
        BadRequestException ex =
                assertThrows(BadRequestException.class, () -> PhoneUtil.normalise("   ", "TZ"));
        assertTrue(ex.getMessage().contains("Phone number is required"));
    }

    @Test
    void givenEmptyPhone_whenNormalise_thenThrowsBadRequestException() {
        BadRequestException ex =
                assertThrows(BadRequestException.class, () -> PhoneUtil.normalise("", "TZ"));
        assertTrue(ex.getMessage().contains("Phone number is required"));
    }

    // --- Wrong prefix for country ---

    @Test
    void givenKeNumberWithTzCountry_whenNormalise_thenThrowsBadRequestException() {
        BadRequestException ex =
                assertThrows(
                        BadRequestException.class,
                        () -> PhoneUtil.normalise("+254712345678", "TZ"));
        assertTrue(ex.getMessage().contains("prefix does not match"));
    }

    @Test
    void givenTzNumberWithKeCountry_whenNormalise_thenThrowsBadRequestException() {
        BadRequestException ex =
                assertThrows(
                        BadRequestException.class,
                        () -> PhoneUtil.normalise("+255712345678", "KE"));
        assertTrue(ex.getMessage().contains("prefix does not match"));
    }

    // --- Wrong length ---

    @Test
    void givenTooShortNumber_whenNormalise_thenThrowsBadRequestException() {
        BadRequestException ex =
                assertThrows(
                        BadRequestException.class, () -> PhoneUtil.normalise("07123456", "TZ"));
        assertTrue(ex.getMessage().contains("13 characters"));
    }

    @Test
    void givenTooLongNumber_whenNormalise_thenThrowsBadRequestException() {
        BadRequestException ex =
                assertThrows(
                        BadRequestException.class,
                        () -> PhoneUtil.normalise("07123456789999", "TZ"));
        assertTrue(ex.getMessage().contains("13 characters"));
    }

    // --- Invalid format ---

    @Test
    void givenNumberNotStartingWith0OrPlusOrPrefix_whenNormalise_thenThrowsBadRequestException() {
        BadRequestException ex =
                assertThrows(
                        BadRequestException.class, () -> PhoneUtil.normalise("712345678", "TZ"));
        assertTrue(ex.getMessage().contains("Invalid phone number format"));
    }

    // --- Case insensitive country code ---

    @Test
    void givenLowercaseCountryCode_whenNormalise_thenWorksCorrectly() {
        String result = PhoneUtil.normalise("0712345678", "tz");
        assertEquals("+255712345678", result);
    }
}
