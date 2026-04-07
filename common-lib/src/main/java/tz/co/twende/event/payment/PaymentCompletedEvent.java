package tz.co.twende.common.event.payment;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.PaymentStatus;
import tz.co.twende.common.event.KafkaEvent;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent extends KafkaEvent {
    private UUID transactionId;
    private UUID userId;
    private BigDecimal amount;
    private PaymentStatus status;
}
