package tz.co.twende.payment.provider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tz.co.twende.common.exception.BadRequestException;

@Component
public class PaymentGateway {

    private final Map<String, PaymentProvider> providers;

    public PaymentGateway(List<PaymentProvider> providerList) {
        this.providers =
                providerList.stream()
                        .collect(Collectors.toMap(PaymentProvider::getId, Function.identity()));
    }

    public PaymentResult charge(String providerId, ChargeRequest request) {
        return getProvider(providerId).charge(request);
    }

    public PaymentResult disburse(String providerId, DisburseRequest request) {
        return getProvider(providerId).disburse(request);
    }

    public PaymentProvider getProvider(String providerId) {
        PaymentProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new BadRequestException("Unknown payment provider: " + providerId);
        }
        return provider;
    }

    public boolean hasProvider(String providerId) {
        return providers.containsKey(providerId);
    }
}
