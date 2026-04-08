package tz.co.twende.location.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NearbyDriverRequest {
    @NotNull private String countryCode;
    @NotNull private String vehicleType;

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal latitude;

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal longitude;

    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("50")
    private BigDecimal radiusKm;
}
