package tz.co.twende.analytics.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(
        name = "driver_daily_summaries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"driver_id", "date"}))
@Getter
@Setter
@NoArgsConstructor
public class DriverDailySummary extends BaseEntity {

    @Column(nullable = false)
    private UUID driverId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int tripCount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalEarned;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal onlineHours;
}
