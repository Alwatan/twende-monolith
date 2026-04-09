package tz.co.twende.analytics.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverEarningsDto {
    private UUID driverId;
    private String period;
    private BigDecimal totalEarned;
    private String currencyCode;
    private String displayTotal;
    private int tripCount;
    private BigDecimal onlineHours;
    private List<DailyBreakdownDto> dailyBreakdown;
}
