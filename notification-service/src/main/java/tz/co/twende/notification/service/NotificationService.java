package tz.co.twende.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tz.co.twende.notification.client.CountryConfigClient;
import tz.co.twende.notification.entity.NotificationLog;
import tz.co.twende.notification.provider.PushProvider;
import tz.co.twende.notification.provider.SmsProvider;
import tz.co.twende.notification.repository.NotificationLogRepository;

@Slf4j
@Service
public class NotificationService {

    private final Map<String, SmsProvider> smsProviders;
    private final Map<String, PushProvider> pushProviders;
    private final CountryConfigClient countryConfigClient;
    private final NotificationLogRepository logRepository;
    private final boolean smsDevMode;
    private final boolean pushDevMode;

    public NotificationService(
            List<SmsProvider> smsProviderList,
            List<PushProvider> pushProviderList,
            CountryConfigClient countryConfigClient,
            NotificationLogRepository logRepository,
            @Value("${twende.sms.dev-mode:true}") boolean smsDevMode,
            @Value("${twende.push.dev-mode:false}") boolean pushDevMode) {
        this.smsProviders =
                smsProviderList.stream()
                        .collect(Collectors.toMap(SmsProvider::getId, Function.identity()));
        this.pushProviders =
                pushProviderList.stream()
                        .collect(Collectors.toMap(PushProvider::getId, Function.identity()));
        this.countryConfigClient = countryConfigClient;
        this.logRepository = logRepository;
        this.smsDevMode = smsDevMode;
        this.pushDevMode = pushDevMode;
    }

    public void sendSms(
            String countryCode,
            UUID userId,
            String phoneNumber,
            String message,
            String templateKey) {
        if (smsDevMode) {
            log.info("DEV SMS to {}: {}", phoneNumber, message);
            logNotification(
                    userId,
                    countryCode,
                    "SMS",
                    templateKey,
                    null,
                    message,
                    "SENT",
                    "dev",
                    null,
                    null);
            return;
        }

        CountryConfigClient.CountryConfigDto config = countryConfigClient.getConfig(countryCode);
        String providerName = config.getSmsProvider().toLowerCase();
        SmsProvider provider = smsProviders.get(providerName);

        if (provider == null) {
            log.error("No SMS provider found for: {}", providerName);
            logNotification(
                    userId,
                    countryCode,
                    "SMS",
                    templateKey,
                    null,
                    message,
                    "FAILED",
                    providerName,
                    null,
                    "Provider not found");
            return;
        }

        try {
            provider.sendSms(phoneNumber, message);
            logNotification(
                    userId,
                    countryCode,
                    "SMS",
                    templateKey,
                    null,
                    message,
                    "SENT",
                    providerName,
                    null,
                    null);
        } catch (Exception e) {
            log.error("SMS delivery failed: {}", e.getMessage());
            logNotification(
                    userId,
                    countryCode,
                    "SMS",
                    templateKey,
                    null,
                    message,
                    "FAILED",
                    providerName,
                    null,
                    e.getMessage());
        }
    }

    public void sendPush(
            String countryCode,
            UUID userId,
            String title,
            String body,
            Map<String, String> data,
            String templateKey) {
        if (pushDevMode) {
            log.info("DEV PUSH to {}: {} - {}", userId, title, body);
            logNotification(
                    userId,
                    countryCode,
                    "PUSH",
                    templateKey,
                    title,
                    body,
                    "SENT",
                    "dev",
                    null,
                    null);
            return;
        }

        CountryConfigClient.CountryConfigDto config = countryConfigClient.getConfig(countryCode);
        String providerName = config.getPushProvider().toLowerCase();
        PushProvider provider = pushProviders.get(providerName);

        if (provider == null) {
            log.error("No push provider found for: {}", providerName);
            logNotification(
                    userId,
                    countryCode,
                    "PUSH",
                    templateKey,
                    title,
                    body,
                    "FAILED",
                    providerName,
                    null,
                    "Provider not found");
            return;
        }

        try {
            provider.sendNotification(userId, title, body, data);
            logNotification(
                    userId,
                    countryCode,
                    "PUSH",
                    templateKey,
                    title,
                    body,
                    "SENT",
                    providerName,
                    null,
                    null);
        } catch (Exception e) {
            log.error("Push delivery failed: {}", e.getMessage());
            logNotification(
                    userId,
                    countryCode,
                    "PUSH",
                    templateKey,
                    title,
                    body,
                    "FAILED",
                    providerName,
                    null,
                    e.getMessage());
        }
    }

    public void sendPushData(
            String countryCode, UUID userId, Map<String, String> data, String templateKey) {
        if (pushDevMode) {
            log.info("DEV PUSH DATA to {}: {}", userId, data);
            logNotification(
                    userId,
                    countryCode,
                    "PUSH",
                    templateKey,
                    null,
                    data.toString(),
                    "SENT",
                    "dev",
                    null,
                    null);
            return;
        }

        CountryConfigClient.CountryConfigDto config = countryConfigClient.getConfig(countryCode);
        String providerName = config.getPushProvider().toLowerCase();
        PushProvider provider = pushProviders.get(providerName);

        if (provider == null) {
            log.error("No push provider found for: {}", providerName);
            logNotification(
                    userId,
                    countryCode,
                    "PUSH",
                    templateKey,
                    null,
                    data.toString(),
                    "FAILED",
                    providerName,
                    null,
                    "Provider not found");
            return;
        }

        try {
            provider.sendData(userId, data);
            logNotification(
                    userId,
                    countryCode,
                    "PUSH",
                    templateKey,
                    null,
                    data.toString(),
                    "SENT",
                    providerName,
                    null,
                    null);
        } catch (Exception e) {
            log.error("Push data delivery failed: {}", e.getMessage());
            logNotification(
                    userId,
                    countryCode,
                    "PUSH",
                    templateKey,
                    null,
                    data.toString(),
                    "FAILED",
                    providerName,
                    null,
                    e.getMessage());
        }
    }

    private void logNotification(
            UUID userId,
            String countryCode,
            String channel,
            String templateKey,
            String title,
            String body,
            String status,
            String provider,
            String providerRef,
            String error) {
        try {
            NotificationLog logEntry = new NotificationLog();
            logEntry.setUserId(userId);
            logEntry.setCountryCode(countryCode);
            logEntry.setChannel(channel);
            logEntry.setTemplateKey(templateKey);
            logEntry.setTitle(title);
            logEntry.setBody(body);
            logEntry.setStatus(status);
            logEntry.setProvider(provider);
            logEntry.setProviderRef(providerRef);
            logEntry.setError(error);
            logEntry.setSentAt(Instant.now());
            logRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to log notification: {}", e.getMessage());
        }
    }
}
