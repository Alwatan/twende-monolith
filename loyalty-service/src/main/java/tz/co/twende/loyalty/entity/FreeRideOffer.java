package tz.co.twende.loyalty.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "free_ride_offers")
@Getter
@Setter
@NoArgsConstructor
public class FreeRideOffer extends BaseEntity {

    @Column(nullable = false)
    private UUID riderId;

    @Column(nullable = false, length = 30)
    private String vehicleType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal maxDistanceKm;

    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE";

    @Column(nullable = false)
    private Instant earnedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant redeemedAt;

    private UUID rideId;
}
