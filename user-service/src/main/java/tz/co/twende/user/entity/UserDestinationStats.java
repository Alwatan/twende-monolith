package tz.co.twende.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(
        name = "user_destination_stats",
        uniqueConstraints =
                @UniqueConstraint(
                        columnNames = {"user_id", "city_id", "destination_lat", "destination_lng"}))
@Getter
@Setter
@NoArgsConstructor
public class UserDestinationStats extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID cityId;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal destinationLat;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal destinationLng;

    @Column(columnDefinition = "TEXT")
    private String destinationAddress;

    @Column(nullable = false)
    private int tripCount = 1;

    @Column(nullable = false)
    private Instant lastTripAt;
}
