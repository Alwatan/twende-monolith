package tz.co.twende.location.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocationMessage {
    private String type;
    private UUID rideId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private int bearing;
    private int estimatedArrivalSeconds;
}
