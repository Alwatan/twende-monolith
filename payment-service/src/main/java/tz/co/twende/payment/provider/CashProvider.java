package tz.co.twende.payment.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CashProvider implements PaymentProvider {

    @Override
    public String getId() {
        return "cash";
    }

    @Override
    public PaymentResult charge(ChargeRequest request) {
        log.info(
                "Cash payment recorded: txId={}, amount={}",
                request.getTransactionId(),
                request.getAmount());
        return PaymentResult.success("CASH-" + request.getTransactionId());
    }

    @Override
    public PaymentResult disburse(DisburseRequest request) {
        log.warn("Disburse not supported for cash provider");
        return PaymentResult.failure("Cash disburse not supported");
    }
}
