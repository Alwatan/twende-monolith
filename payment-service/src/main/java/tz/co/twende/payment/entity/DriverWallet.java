package tz.co.twende.payment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "driver_wallets")
@Getter
@Setter
@NoArgsConstructor
public class DriverWallet {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID driverId;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public DriverWallet(UUID driverId, String countryCode, String currency) {
        this.driverId = driverId;
        this.countryCode = countryCode;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
        this.updatedAt = Instant.now();
    }
}
