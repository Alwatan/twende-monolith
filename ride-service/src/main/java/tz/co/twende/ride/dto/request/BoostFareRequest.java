package tz.co.twende.ride.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BoostFareRequest {

    @NotNull
    @DecimalMin(value = "1", message = "Boost amount must be at least 1")
    private BigDecimal boostAmount;
}
