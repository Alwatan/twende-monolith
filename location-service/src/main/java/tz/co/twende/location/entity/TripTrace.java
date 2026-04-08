package tz.co.twende.location.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "trip_traces")
@Getter
@Setter
@NoArgsConstructor
public class TripTrace extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID rideId;

    @Column(nullable = false)
    private UUID driverId;

    @Column(nullable = false, columnDefinition = "JSONB")
    private String trace;

    private Integer distanceMetres;

    private Instant startedAt;

    private Instant completedAt;
}
