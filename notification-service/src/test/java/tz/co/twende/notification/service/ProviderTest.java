package tz.co.twende.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tz.co.twende.notification.provider.push.OneSignalPushProvider;
import tz.co.twende.notification.provider.sms.TwilioSmsProvider;

class ProviderTest {

    @Test
    void givenTwilioProvider_whenGetId_thenReturnTwilio() {
        TwilioSmsProvider provider = new TwilioSmsProvider();
        assertThat(provider.getId()).isEqualTo("twilio");
    }

    @Test
    void givenTwilioProvider_whenSendSms_thenThrowsUnsupported() {
        TwilioSmsProvider provider = new TwilioSmsProvider();
        assertThrows(
                UnsupportedOperationException.class,
                () -> provider.sendSms("+254712345678", "Hello"));
    }

    @Test
    void givenTwilioProvider_whenSupportsKenya_thenTrue() {
        TwilioSmsProvider provider = new TwilioSmsProvider();
        assertThat(provider.supportsCountry("KE")).isTrue();
        assertThat(provider.supportsCountry("TZ")).isFalse();
    }

    @Test
    void givenOneSignalProvider_whenGetId_thenReturnOneSignal() {
        OneSignalPushProvider provider = new OneSignalPushProvider();
        assertThat(provider.getId()).isEqualTo("onesignal");
    }

    @Test
    void givenOneSignalProvider_whenSendNotification_thenThrowsUnsupported() {
        OneSignalPushProvider provider = new OneSignalPushProvider();
        assertThrows(
                UnsupportedOperationException.class,
                () -> provider.sendNotification(UUID.randomUUID(), "Title", "Body", Map.of()));
    }

    @Test
    void givenOneSignalProvider_whenSendData_thenThrowsUnsupported() {
        OneSignalPushProvider provider = new OneSignalPushProvider();
        assertThrows(
                UnsupportedOperationException.class,
                () -> provider.sendData(UUID.randomUUID(), Map.of("key", "value")));
    }
}
