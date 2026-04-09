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
public class CountryMetricsDto {
    private String countryCode;
    private long totalRides;
    private long completedRides;
    private long cancelledRides;
    private BigDecimal averageFare;
    private BigDecimal totalFareVolume;
    private long newDrivers;
    private long newRiders;
    private String topVehicleType;
    private String periodStart;
    private String periodEnd;
}
