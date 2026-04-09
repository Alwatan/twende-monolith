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
public class AdminOverviewDto {
    private long totalRides;
    private long totalDrivers;
    private long totalRiders;
    private long activeSubscriptions;
    private BigDecimal revenueFromSubscriptions;
    private String currencyCode;
    private String periodStart;
    private String periodEnd;
}
