package tz.co.twende.payment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;
import tz.co.twende.common.enums.PaymentMethod;
import tz.co.twende.common.enums.PaymentStatus;

@Entity
@Table(
        name = "transactions",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_transactions_reference",
                        columnNames = {"referenceId", "referenceType"}))
@Getter
@Setter
@NoArgsConstructor
public class Transaction extends BaseEntity {

    @Column(nullable = false)
    private UUID referenceId;

    @Column(nullable = false, length = 20)
    private String referenceType;

    @Column(nullable = false)
    private UUID payerId;

    private UUID payeeId;

    @Column(nullable = false, length = 20)
    private String paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Column(length = 30)
    private String provider;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(length = 200)
    private String providerRef;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false)
    private Instant initiatedAt;

    private Instant completedAt;
}
