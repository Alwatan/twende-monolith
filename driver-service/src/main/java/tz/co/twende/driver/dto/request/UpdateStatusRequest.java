package tz.co.twende.driver.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import tz.co.twende.common.enums.DriverStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest {

    @NotNull private DriverStatus status;
}
