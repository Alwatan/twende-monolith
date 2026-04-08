package tz.co.twende.payment.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tz.co.twende.common.enums.PaymentMethod;
import tz.co.twende.common.enums.PaymentStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDto {

    private UUID id;
    private UUID referenceId;
    private String referenceType;
    private UUID payerId;
    private UUID payeeId;
    private String paymentType;
    private PaymentMethod paymentMethod;
    private String provider;
    private BigDecimal amount;
    private String currencyCode;
    private PaymentStatus status;
    private String providerRef;
    private String failureReason;
    private Instant initiatedAt;
    private Instant completedAt;
}
