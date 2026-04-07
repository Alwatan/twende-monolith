package tz.co.twende.user.dto;

import java.math.BigDecimal;
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
public class RideHistoryResponse {
    private UUID rideId;
    private String pickupAddress;
    private String dropoffAddress;
    private BigDecimal fare;
    private String currencyCode;
    private String status;
    private Instant completedAt;
}
