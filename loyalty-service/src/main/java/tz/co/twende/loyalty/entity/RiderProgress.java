package tz.co.twende.loyalty.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "rider_progress")
@Getter
@Setter
@NoArgsConstructor
public class RiderProgress extends BaseEntity {

    @Column(nullable = false)
    private UUID riderId;

    @Column(nullable = false, length = 30)
    private String vehicleType;

    @Column(nullable = false)
    private int rideCount = 0;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalDistanceKm = BigDecimal.ZERO;

    private Instant lastResetAt;

    public RiderProgress(UUID riderId, String countryCode, String vehicleType) {
        this.riderId = riderId;
        setCountryCode(countryCode);
        this.vehicleType = vehicleType;
    }
}
