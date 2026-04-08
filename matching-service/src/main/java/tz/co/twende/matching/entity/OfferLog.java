package tz.co.twende.matching.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "offer_logs")
@Getter
@Setter
@NoArgsConstructor
public class OfferLog extends BaseEntity {

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "batch_number", nullable = false)
    private int batchNumber;

    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "score", precision = 5, scale = 3)
    private BigDecimal score;

    @Column(name = "offered_at", nullable = false)
    private Instant offeredAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "response", length = 20)
    private String response;
}
