package tz.co.twende.ride.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "ride_status_events")
@Getter
@Setter
@NoArgsConstructor
public class RideStatusEvent extends BaseEntity {

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "from_status", length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role", length = 10)
    private String actorRole;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
