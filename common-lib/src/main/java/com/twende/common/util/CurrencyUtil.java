package com.twende.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

public final class CurrencyUtil {

    private static final Map<String, String> CURRENCY_SYMBOLS =
            Map.of(
                    "TZS", "TSh",
                    "KES", "KSh",
                    "UGX", "USh");

    private static final Map<String, Integer> MINOR_UNITS =
            Map.of(
                    "TZS", 0,
                    "KES", 2,
                    "UGX", 0);

    private CurrencyUtil() {}

    /**
     * Format a monetary amount for display.
     *
     * <p>Tanzania (TZS): no decimal places, e.g. "TSh 2,000"
     *
     * <p>Kenya (KES): 2 decimal places, e.g. "KSh 150.00"
     *
     * <p>Uganda (UGX): no decimal places, e.g. "USh 5,000"
     *
     * @param amount the monetary amount (must not be null)
     * @param currencyCode ISO 4217 currency code (e.g. "TZS", "KES", "UGX")
     * @return formatted string with currency symbol and grouping separators
     */
    public static String format(BigDecimal amount, String currencyCode) {
        if (amount == null || currencyCode == null) {
            return "";
        }
        int scale = MINOR_UNITS.getOrDefault(currencyCode, 2);
        BigDecimal rounded = amount.setScale(scale, RoundingMode.HALF_UP);
        String symbol = CURRENCY_SYMBOLS.getOrDefault(currencyCode, currencyCode);

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(scale);
        nf.setMaximumFractionDigits(scale);

        return symbol + " " + nf.format(rounded);
    }
}
