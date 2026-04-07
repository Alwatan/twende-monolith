package com.twende.common.event.payment;

import com.twende.common.event.KafkaEvent;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

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
