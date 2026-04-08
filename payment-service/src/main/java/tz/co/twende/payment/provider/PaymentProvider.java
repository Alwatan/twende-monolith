package tz.co.twende.payment.provider;

public interface PaymentProvider {

    String getId();

    PaymentResult charge(ChargeRequest request);

    PaymentResult disburse(DisburseRequest request);
}
