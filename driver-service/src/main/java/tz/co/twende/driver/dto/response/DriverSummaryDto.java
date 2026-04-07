package tz.co.twende.driver.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.DriverStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverSummaryDto {
    private UUID id;
    private String fullName;
    private DriverStatus status;
    private int tripCount;
    private Instant lastTripAt;
}
