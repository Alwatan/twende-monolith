package tz.co.twende.payment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.UlidId;

@Entity
@Table(name = "wallet_entries")
@Getter
@Setter
@NoArgsConstructor
public class WalletEntry {

    @Id
    @UlidId
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID driverId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceAfter;

    private UUID referenceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public WalletEntry(
            UUID driverId,
            String type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            UUID referenceId,
            String description) {
        this.driverId = driverId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.referenceId = referenceId;
        this.description = description;
        this.createdAt = Instant.now();
    }
}
