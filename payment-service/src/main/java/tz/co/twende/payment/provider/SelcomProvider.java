package tz.co.twende.payment.provider;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class SelcomProvider implements PaymentProvider {

    private final RestClient restClient;

    public SelcomProvider(
            @Value("${twende.selcom.base-url}") String baseUrl,
            @Value("${twende.selcom.api-key}") String apiKey) {
        this.restClient =
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("Authorization", "Bearer " + apiKey)
                        .build();
    }

    @Override
    public String getId() {
        return "selcom";
    }

    @Override
    public PaymentResult charge(ChargeRequest request) {
        log.info(
                "Selcom charge: txId={}, amount={}, mobile={}",
                request.getTransactionId(),
                request.getAmount(),
                request.getMobileNumber());
        // Stub: In production, call Selcom push-pay API
        // POST /checkout/create-order-minimal
        // For now, simulate success
        String providerRef = "SELCOM-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Selcom charge successful: providerRef={}", providerRef);
        return PaymentResult.success(providerRef);
    }

    @Override
    public PaymentResult disburse(DisburseRequest request) {
        log.info(
                "Selcom disburse: txId={}, amount={}, mobile={}",
                request.getTransactionId(),
                request.getAmount(),
                request.getMobileNumber());
        // Stub: In production, call Selcom disburse API
        // POST /checkout/wallet-payment
        String providerRef = "SELCOM-D-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Selcom disburse successful: providerRef={}", providerRef);
        return PaymentResult.success(providerRef);
    }
}
