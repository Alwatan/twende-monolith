package tz.co.twende.location.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LocationUpdateMessage {
    private String type;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private int bearing;
    private int speedKmh;
    private Instant timestamp;
}
