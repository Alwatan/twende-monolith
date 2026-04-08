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
@Table(name = "driver_stats_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class DriverStatsSnapshot extends BaseEntity {

    @Column(name = "driver_id", nullable = false, unique = true)
    private UUID driverId;

    @Column(name = "offered_count", nullable = false)
    private int offeredCount;

    @Column(name = "accepted_count", nullable = false)
    private int acceptedCount;

    @Column(name = "rejection_count", nullable = false)
    private int rejectionCount;

    @Column(name = "acceptance_rate", nullable = false, precision = 5, scale = 3)
    private BigDecimal acceptanceRate;

    @Column(name = "snapshot_at", nullable = false)
    private Instant snapshotAt;
}
