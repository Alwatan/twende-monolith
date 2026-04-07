package tz.co.twende.driver.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;
import tz.co.twende.common.enums.VehicleType;

@Entity
@Table(
        name = "driver_vehicles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"driver_id", "plate_number"}))
@Getter
@Setter
@NoArgsConstructor
public class DriverVehicle extends BaseEntity {

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 30)
    private VehicleType vehicleType;

    @Column(length = 50)
    private String make;

    @Column(length = 50)
    private String model;

    private Integer year;

    @Column(name = "plate_number", nullable = false, length = 20)
    private String plateNumber;

    @Column(length = 30)
    private String color;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
