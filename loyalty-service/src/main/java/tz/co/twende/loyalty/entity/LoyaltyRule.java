package tz.co.twende.loyalty.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "loyalty_rules")
@Getter
@Setter
@NoArgsConstructor
public class LoyaltyRule extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String vehicleType;

    @Column(nullable = false)
    private int requiredRides;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal requiredDistanceKm;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal freeRideMaxDistanceKm;

    @Column(nullable = false)
    private int offerValidityDays;

    @Column(nullable = false)
    private boolean isActive = true;
}
