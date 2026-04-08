package tz.co.twende.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RouteRequest {
    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal originLat;

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal originLng;

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal destinationLat;

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal destinationLng;

    @NotNull private UUID cityId;
}
