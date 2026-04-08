package tz.co.twende.payment.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResult {

    private boolean success;
    private String reference;
    private String errorMessage;

    public static PaymentResult success(String reference) {
        return PaymentResult.builder().success(true).reference(reference).build();
    }

    public static PaymentResult failure(String errorMessage) {
        return PaymentResult.builder().success(false).errorMessage(errorMessage).build();
    }
}
