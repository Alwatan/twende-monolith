package tz.co.twende.driver.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.*;
import tz.co.twende.common.enums.DriverStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverProfileDto {
    private UUID id;
    private String countryCode;
    private String fullName;
    private String email;
    private String profilePhotoUrl;
    private DriverStatus status;
    private String rejectionReason;
    private Instant approvedAt;
    private int tripCount;
    private Instant lastTripAt;
    private Instant createdAt;
    private Instant updatedAt;
}
