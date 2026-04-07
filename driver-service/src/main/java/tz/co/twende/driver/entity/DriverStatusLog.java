package tz.co.twende.driver.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;
import tz.co.twende.common.enums.DriverStatus;

@Entity
@Table(name = "driver_status_log")
@Getter
@Setter
@NoArgsConstructor
public class DriverStatusLog extends BaseEntity {

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private DriverStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private DriverStatus toStatus;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();
}
