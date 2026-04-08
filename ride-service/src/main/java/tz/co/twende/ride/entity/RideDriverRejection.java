package tz.co.twende.ride.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "ride_driver_rejections")
@Getter
@Setter
@NoArgsConstructor
public class RideDriverRejection extends BaseEntity {

    @Column(name = "ride_id", nullable = false)
    private UUID rideId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "rejected_at", nullable = false)
    private Instant rejectedAt;
}
