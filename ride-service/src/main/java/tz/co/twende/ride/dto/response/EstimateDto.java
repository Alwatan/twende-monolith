package tz.co.twende.ride.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstimateDto {

    private BigDecimal estimatedFare;
    private String currency;
    private Integer distanceMetres;
    private Integer durationSeconds;
}
