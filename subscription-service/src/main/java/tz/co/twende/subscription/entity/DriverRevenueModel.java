package tz.co.twende.subscription.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "driver_revenue_models")
@Getter
@Setter
@NoArgsConstructor
public class DriverRevenueModel extends BaseEntity {

    @Column(name = "driver_id", nullable = false, unique = true)
    private UUID driverId;

    @Column(name = "revenue_model", nullable = false, length = 20)
    private String revenueModel;

    @Column(name = "service_category", nullable = false, length = 20)
    private String serviceCategory;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;
}
