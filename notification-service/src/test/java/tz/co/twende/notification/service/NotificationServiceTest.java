package tz.co.twende.notification.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tz.co.twende.notification.client.CountryConfigClient;
import tz.co.twende.notification.provider.PushProvider;
import tz.co.twende.notification.provider.SmsProvider;
import tz.co.twende.notification.repository.NotificationLogRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private CountryConfigClient countryConfigClient;
    @Mock private NotificationLogRepository logRepository;
    @Mock private SmsProvider africasTalkingProvider;
    @Mock private PushProvider fcmProvider;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        lenient().when(africasTalkingProvider.getId()).thenReturn("africastalking");
        lenient().when(fcmProvider.getId()).thenReturn("fcm");
    }

    @Test
    void givenDevModeEnabled_whenSendSms_thenLoggedNotSent() {
        notificationService =
                new NotificationService(
                        List.of(africasTalkingProvider),
                        List.of(fcmProvider),
                        countryConfigClient,
                        logRepository,
                        true,
                        false);

        UUID userId = UUID.randomUUID();
        notificationService.sendSms("TZ", userId, "+255712345678", "Hello", "test.key");

        verify(africasTalkingProvider, never()).sendSms(any(), any());
        verify(logRepository).save(any());
    }

    @Test
    void givenDevModeDisabled_whenSendSms_thenProviderCalled() {
        notificationService =
                new NotificationService(
                        List.of(africasTalkingProvider),
                        List.of(fcmProvider),
                        countryConfigClient,
                        logRepository,
                        false,
                        false);

        CountryConfigClient.CountryConfigDto config = new CountryConfigClient.CountryConfigDto();
        config.setSmsProvider("africastalking");
        when(countryConfigClient.getConfig("TZ")).thenReturn(config);

        UUID userId = UUID.randomUUID();
        notificationService.sendSms("TZ", userId, "+255712345678", "Hello", "test.key");

        verify(africasTalkingProvider).sendSms("+255712345678", "Hello");
        verify(logRepository).save(any());
    }

    @Test
    void givenTanzaniaCountryCode_whenSendSms_thenAfricasTalkingProviderUsed() {
        notificationService =
                new NotificationService(
                        List.of(africasTalkingProvider),
                        List.of(fcmProvider),
                        countryConfigClient,
                        logRepository,
                        false,
                        false);

        CountryConfigClient.CountryConfigDto config = new CountryConfigClient.CountryConfigDto();
        config.setSmsProvider("africastalking");
        when(countryConfigClient.getConfig("TZ")).thenReturn(config);

        UUID userId = UUID.randomUUID();
        notificationService.sendSms("TZ", userId, "+255712345678", "Test", "test.key");

        verify(africasTalkingProvider).sendSms("+255712345678", "Test");
    }

    @Test
    void givenDevModeEnabled_whenSendPush_thenLoggedNotSent() {
        notificationService =
                new NotificationService(
                        List.of(africasTalkingProvider),
                        List.of(fcmProvider),
                        countryConfigClient,
                        logRepository,
                        false,
                        true);

        UUID userId = UUID.randomUUID();
        notificationService.sendPush("TZ", userId, "Title", "Body", Map.of(), "test.key");

        verify(fcmProvider, never()).sendNotification(any(), any(), any(), any());
        verify(logRepository).save(any());
    }

    @Test
    void givenDevModeDisabled_whenSendPush_thenFcmProviderCalled() {
        notificationService =
                new NotificationService(
                        List.of(africasTalkingProvider),
                        List.of(fcmProvider),
                        countryConfigClient,
                        logRepository,
                        false,
                        false);

        CountryConfigClient.CountryConfigDto config = new CountryConfigClient.CountryConfigDto();
        config.setPushProvider("fcm");
        when(countryConfigClient.getConfig("TZ")).thenReturn(config);

        UUID userId = UUID.randomUUID();
        Map<String, String> data = Map.of("type", "TEST");
        notificationService.sendPush("TZ", userId, "Title", "Body", data, "test.key");

        verify(fcmProvider).sendNotification(userId, "Title", "Body", data);
        verify(logRepository).save(any());
    }

    @Test
    void givenProviderThrows_whenSendSms_thenFailureLogged() {
        notificationService =
                new NotificationService(
                        List.of(africasTalkingProvider),
                        List.of(fcmProvider),
                        countryConfigClient,
                        logRepository,
                        false,
                        false);

        CountryConfigClient.CountryConfigDto config = new CountryConfigClient.CountryConfigDto();
        config.setSmsProvider("africastalking");
        when(countryConfigClient.getConfig("TZ")).thenReturn(config);
        doThrow(new RuntimeException("Network error"))
                .when(africasTalkingProvider)
                .sendSms(any(), any());

        UUID userId = UUID.randomUUID();
        notificationService.sendSms("TZ", userId, "+255712345678", "Hello", "test.key");

        verify(logRepository).save(any());
    }

    @Test
    void givenUnknownProvider_whenSendSms_thenFailureLogged() {
        notificationService =
                new NotificationService(
                        List.of(africasTalkingProvider),
                        List.of(fcmProvider),
                        countryConfigClient,
                        logRepository,
                        false,
                        false);

        CountryConfigClient.CountryConfigDto config = new CountryConfigClient.CountryConfigDto();
        config.setSmsProvider("unknown_provider");
        when(countryConfigClient.getConfig("TZ")).thenReturn(config);

        UUID userId = UUID.randomUUID();
        notificationService.sendSms("TZ", userId, "+255712345678", "Hello", "test.key");

        verify(africasTalkingProvider, never()).sendSms(any(), any());
        verify(logRepository).save(any());
    }

    @Test
    void givenDevModeEnabled_whenSendPushData_thenLoggedNotSent() {
        notificationService =
                new NotificationService(
                        List.of(africasTalkingProvider),
                        List.of(fcmProvider),
                        countryConfigClient,
                        logRepository,
                        false,
                        true);

        UUID userId = UUID.randomUUID();
        notificationService.sendPushData(
                "TZ", userId, Map.of("type", "OTP", "otp", "1234"), "trip.otp");

        verify(fcmProvider, never()).sendData(any(), any());
        verify(logRepository).save(any());
    }

    @Test
    void givenDevModeDisabled_whenSendPushData_thenProviderCalled() {
        notificationService =
                new NotificationService(
                        List.of(africasTalkingProvider),
                        List.of(fcmProvider),
                        countryConfigClient,
                        logRepository,
                        false,
                        false);

        CountryConfigClient.CountryConfigDto config = new CountryConfigClient.CountryConfigDto();
        config.setPushProvider("fcm");
        when(countryConfigClient.getConfig("TZ")).thenReturn(config);

        UUID userId = UUID.randomUUID();
        Map<String, String> data = Map.of("type", "OTP", "otp", "1234");
        notificationService.sendPushData("TZ", userId, data, "trip.otp");

        verify(fcmProvider).sendData(userId, data);
        verify(logRepository).save(any());
    }
}
