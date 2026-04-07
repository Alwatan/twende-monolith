package tz.co.twende.common.util;

import java.util.Map;
import tz.co.twende.common.exception.BadRequestException;

public final class PhoneUtil {

    private static final Map<String, String> COUNTRY_PREFIXES =
            Map.of(
                    "TZ", "+255",
                    "KE", "+254",
                    "UG", "+256");

    private PhoneUtil() {}

    /**
     * Normalise a phone number to E.164 format.
     *
     * @param phoneNumber raw input (may start with 0, country code without +, or already E.164)
     * @param countryCode ISO 3166-1 alpha-2 (e.g. "TZ", "KE", "UG")
     * @return E.164 formatted number (e.g. "+255712345678")
     * @throws BadRequestException if the number cannot be normalised
     */
    public static String normalise(String phoneNumber, String countryCode) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BadRequestException("Phone number is required");
        }
        if (countryCode == null || !COUNTRY_PREFIXES.containsKey(countryCode.toUpperCase())) {
            throw new BadRequestException("Unsupported country code: " + countryCode);
        }

        String prefix = COUNTRY_PREFIXES.get(countryCode.toUpperCase());
        String digits = phoneNumber.replaceAll("[\\s\\-()]+", "");

        if (digits.startsWith("+")) {
            if (!digits.startsWith(prefix)) {
                throw new BadRequestException(
                        "Phone number prefix does not match country " + countryCode);
            }
        } else if (digits.startsWith("0")) {
            digits = prefix + digits.substring(1);
        } else if (digits.startsWith(prefix.substring(1))) {
            digits = "+" + digits;
        } else {
            throw new BadRequestException("Invalid phone number format: " + phoneNumber);
        }

        // Validate length: prefix (4 chars including +) + 9 digits = 13
        if (digits.length() != 13) {
            throw new BadRequestException(
                    "Phone number must be 13 characters in E.164 format, got " + digits.length());
        }

        return digits;
    }
}
