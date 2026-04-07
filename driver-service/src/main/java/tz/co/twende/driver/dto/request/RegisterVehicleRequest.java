package tz.co.twende.driver.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import tz.co.twende.common.enums.VehicleType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterVehicleRequest {

    @NotNull private VehicleType vehicleType;

    @Size(max = 50)
    private String make;

    @Size(max = 50)
    private String model;

    private Integer year;

    @NotBlank
    @Size(max = 20)
    private String plateNumber;

    @Size(max = 30)
    private String color;
}
