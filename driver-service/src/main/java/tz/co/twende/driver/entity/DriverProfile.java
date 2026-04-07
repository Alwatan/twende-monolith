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
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
public class DriverProfile extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(length = 255)
    private String email;

    @Column(length = 500)
    private String profilePhotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DriverStatus status = DriverStatus.PENDING_APPROVAL;

    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    private Instant approvedAt;

    private UUID approvedBy;

    @Column(nullable = false)
    private int tripCount = 0;

    private Instant lastTripAt;
}
