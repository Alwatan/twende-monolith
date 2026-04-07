package com.twende.common.util;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CurrencyUtilTest {

    @Test
    void givenTzsAmount_whenFormat_thenReturnsNoDecimalsWithTShPrefix() {
        String result = CurrencyUtil.format(new BigDecimal("2000"), "TZS");
        assertEquals("TSh 2,000", result);
    }

    @Test
    void givenTzsAmountWithDecimals_whenFormat_thenRoundsToWholeNumber() {
        String result = CurrencyUtil.format(new BigDecimal("2000.75"), "TZS");
        assertEquals("TSh 2,001", result);
    }

    @Test
    void givenKesAmount_whenFormat_thenReturnsTwoDecimalsWithKShPrefix() {
        String result = CurrencyUtil.format(new BigDecimal("150"), "KES");
        assertEquals("KSh 150.00", result);
    }

    @Test
    void givenKesAmountWithDecimals_whenFormat_thenKeepsTwoDecimalPlaces() {
        String result = CurrencyUtil.format(new BigDecimal("150.50"), "KES");
        assertEquals("KSh 150.50", result);
    }

    @Test
    void givenUgxAmount_whenFormat_thenReturnsNoDecimalsWithUShPrefix() {
        String result = CurrencyUtil.format(new BigDecimal("5000"), "UGX");
        assertEquals("USh 5,000", result);
    }

    @Test
    void givenLargeAmount_whenFormat_thenIncludesGroupingSeparators() {
        String result = CurrencyUtil.format(new BigDecimal("1000000"), "TZS");
        assertEquals("TSh 1,000,000", result);
    }

    @Test
    void givenNullAmount_whenFormat_thenReturnsEmptyString() {
        String result = CurrencyUtil.format(null, "TZS");
        assertEquals("", result);
    }

    @Test
    void givenNullCurrencyCode_whenFormat_thenReturnsEmptyString() {
        String result = CurrencyUtil.format(new BigDecimal("100"), null);
        assertEquals("", result);
    }

    @Test
    void givenBothNull_whenFormat_thenReturnsEmptyString() {
        String result = CurrencyUtil.format(null, null);
        assertEquals("", result);
    }

    @Test
    void givenUnknownCurrencyCode_whenFormat_thenUsesCurrencyCodeAsSymbolAndTwoDecimals() {
        String result = CurrencyUtil.format(new BigDecimal("100"), "USD");
        assertEquals("USD 100.00", result);
    }

    @Test
    void givenZeroAmount_whenFormat_thenFormatsCorrectly() {
        String result = CurrencyUtil.format(BigDecimal.ZERO, "TZS");
        assertEquals("TSh 0", result);
    }
}
