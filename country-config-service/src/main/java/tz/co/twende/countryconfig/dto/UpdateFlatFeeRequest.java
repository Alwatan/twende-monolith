package tz.co.twende.countryconfig.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateFlatFeeRequest {

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    @DecimalMax(value = "99.99", inclusive = true)
    private BigDecimal percentage;
}
