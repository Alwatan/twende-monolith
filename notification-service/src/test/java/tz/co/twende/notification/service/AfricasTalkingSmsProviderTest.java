package tz.co.twende.notification.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tz.co.twende.notification.provider.sms.AfricasTalkingSmsProvider;

class AfricasTalkingSmsProviderTest {

    private final AfricasTalkingSmsProvider provider =
            new AfricasTalkingSmsProvider("http://localhost:9999", "test-key", "sandbox", "TWENDE");

    @Test
    void givenProvider_whenGetId_thenReturnAfricasTalking() {
        assertThat(provider.getId()).isEqualTo("africastalking");
    }

    @Test
    void givenTanzania_whenSupportsCountry_thenTrue() {
        assertThat(provider.supportsCountry("TZ")).isTrue();
    }

    @Test
    void givenKenya_whenSupportsCountry_thenTrue() {
        assertThat(provider.supportsCountry("KE")).isTrue();
    }

    @Test
    void givenUganda_whenSupportsCountry_thenTrue() {
        assertThat(provider.supportsCountry("UG")).isTrue();
    }

    @Test
    void givenUnsupportedCountry_whenSupportsCountry_thenFalse() {
        assertThat(provider.supportsCountry("US")).isFalse();
    }

    @Test
    void givenNoServer_whenSendSms_thenThrowsException() {
        try {
            provider.sendSms("+255712345678", "Hello World");
        } catch (RuntimeException e) {
            assertThat(e.getMessage()).contains("SMS delivery failed");
        }
    }
}
