package tz.co.twende.notification.provider.sms;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.notification.provider.SmsProvider;

@Slf4j
@Component
public class AfricasTalkingSmsProvider implements SmsProvider {

    private static final Set<String> SUPPORTED_COUNTRIES = Set.of("TZ", "KE", "UG");

    private final RestClient restClient;
    private final String username;
    private final String senderId;

    public AfricasTalkingSmsProvider(
            @Value("${twende.africastalking.base-url}") String baseUrl,
            @Value("${twende.africastalking.api-key}") String apiKey,
            @Value("${twende.africastalking.username}") String username,
            @Value("${twende.africastalking.sender-id}") String senderId) {
        this.username = username;
        this.senderId = senderId;
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("apiKey", apiKey)
                        .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .build();
    }

    @Override
    public String getId() {
        return "africastalking";
    }

    @Override
    public void sendSms(String phoneNumber, String message) {
        String formBody =
                "username="
                        + URLEncoder.encode(username, StandardCharsets.UTF_8)
                        + "&to="
                        + URLEncoder.encode(phoneNumber, StandardCharsets.UTF_8)
                        + "&message="
                        + URLEncoder.encode(message, StandardCharsets.UTF_8)
                        + "&from="
                        + URLEncoder.encode(senderId, StandardCharsets.UTF_8);

        try {
            restClient
                    .post()
                    .uri("/messaging")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formBody)
                    .retrieve()
                    .toBodilessEntity();
            log.info("SMS sent to {} via Africa's Talking", phoneNumber);
        } catch (Exception e) {
            log.error(
                    "Failed to send SMS to {} via Africa's Talking: {}",
                    phoneNumber,
                    e.getMessage());
            throw new RuntimeException("SMS delivery failed", e);
        }
    }

    @Override
    public boolean supportsCountry(String countryCode) {
        return SUPPORTED_COUNTRIES.contains(countryCode);
    }
}
