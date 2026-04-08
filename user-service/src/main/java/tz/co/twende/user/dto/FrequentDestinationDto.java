package tz.co.twende.user.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrequentDestinationDto {
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private int visitCount;
    private Instant lastVisitedAt;
}
