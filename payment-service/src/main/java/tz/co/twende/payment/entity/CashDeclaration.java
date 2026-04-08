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
@Table(name = "cash_declarations")
@Getter
@Setter
@NoArgsConstructor
public class CashDeclaration {

    @Id
    @UlidId
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID rideId;

    @Column(nullable = false)
    private UUID driverId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant declaredAt = Instant.now();

    @Column(nullable = false)
    private boolean verified = false;
}
