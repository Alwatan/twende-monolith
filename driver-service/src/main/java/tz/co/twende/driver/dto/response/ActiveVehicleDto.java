package tz.co.twende.driver.dto.response;

import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.VehicleType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiveVehicleDto {
    private UUID vehicleId;
    private UUID driverId;
    private VehicleType vehicleType;
    private String make;
    private String model;
    private String plateNumber;
    private String color;
}
