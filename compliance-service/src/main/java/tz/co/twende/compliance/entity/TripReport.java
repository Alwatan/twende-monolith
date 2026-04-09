package tz.co.twende.compliance.entity;

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
@Table(name = "trip_reports")
@Getter
@Setter
@NoArgsConstructor
public class TripReport extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID rideId;

    @Column(nullable = false)
    private UUID driverId;

    @Column(nullable = false)
    private UUID riderId;

    @Column(nullable = false, length = 30)
    private String vehicleType;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLat;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLng;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal dropoffLat;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal dropoffLng;

    private Integer distanceMetres;

    private Integer durationSeconds;

    @Column(precision = 12, scale = 2)
    private BigDecimal fare;

    @Column(length = 3)
    private String currency;

    private Instant startedAt;

    private Instant completedAt;

    @Column(nullable = false)
    private boolean submitted = false;

    private Instant submittedAt;

    @Column(length = 200)
    private String submissionRef;

    @Column(columnDefinition = "TEXT")
    private String submissionError;
}
