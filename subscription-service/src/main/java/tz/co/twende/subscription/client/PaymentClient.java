package tz.co.twende.subscription.client;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tz.co.twende.subscription.dto.PaymentRequest;
import tz.co.twende.subscription.dto.PaymentResponse;

@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(@Value("${twende.services.payment.base-url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public PaymentResponse initiateSubscriptionPayment(
            UUID driverId,
            UUID subscriptionId,
            BigDecimal amount,
            String currencyCode,
            String paymentMethod,
            String countryCode) {
        PaymentRequest request = new PaymentRequest();
        request.setDriverId(driverId);
        request.setSubscriptionId(subscriptionId);
        request.setAmount(amount);
        request.setCurrencyCode(currencyCode);
        request.setPaymentMethod(paymentMethod);
        request.setCountryCode(countryCode);

        return restClient
                .post()
                .uri("/internal/payments/subscription")
                .body(request)
                .retrieve()
                .body(PaymentResponse.class);
    }
}
