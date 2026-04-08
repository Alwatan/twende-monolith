package tz.co.twende.driver.dto.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueModelResponseDto {

    private UUID driverId;
    private String revenueModel;
    private String serviceCategory;
    private boolean hasActiveSubscription;
    private Instant registeredAt;
}
