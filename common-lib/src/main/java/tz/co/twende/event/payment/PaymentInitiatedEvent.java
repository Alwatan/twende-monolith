package tz.co.twende.common.event.payment;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiatedEvent extends KafkaEvent {
    private UUID transactionId;
    private UUID userId;
    private BigDecimal amount;
    private String currencyCode;
    private String paymentMethod;
}
