package tz.co.twende.analytics.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverTripStatsDto {
    private int totalTrips;
    private BigDecimal totalEarned;
    private BigDecimal onlineHours;
    private String periodStart;
    private String periodEnd;
}
