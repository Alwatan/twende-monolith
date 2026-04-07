package tz.co.twende.countryconfig.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(
        name = "vehicle_type_configs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"country_code", "vehicle_type"}))
@Getter
@Setter
@NoArgsConstructor
public class VehicleTypeConfig extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String vehicleType;

    @Column(nullable = false, length = 50)
    private String displayName;

    @Column(nullable = false)
    private Integer maxPassengers;

    @Column(length = 50)
    private String iconKey;

    @Column(nullable = false)
    private Boolean isActive;

    // Pricing (all monetary values as BigDecimal, never double/float)
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal baseFare;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal perKm;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal perMinute;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumFare;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cancellationFee;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal surgeMultiplierCap;

    @Column(nullable = false, columnDefinition = "TEXT[]")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    private String[] requiredDocs;
}
