package tz.co.twende.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyBreakdownDto {
    private LocalDate date;
    private BigDecimal earned;
    private int trips;
    private BigDecimal onlineHours;
}
