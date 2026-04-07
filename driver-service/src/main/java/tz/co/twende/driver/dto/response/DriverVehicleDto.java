package tz.co.twende.driver.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.VehicleType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverVehicleDto {
    private UUID id;
    private UUID driverId;
    private VehicleType vehicleType;
    private String make;
    private String model;
    private Integer year;
    private String plateNumber;
    private String color;
    private boolean active;
    private Instant createdAt;
}
