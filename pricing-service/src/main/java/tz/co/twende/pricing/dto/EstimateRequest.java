package tz.co.twende.pricing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EstimateRequest {

    @NotBlank private String vehicleType;

    @NotBlank private String countryCode;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private BigDecimal pickupLat;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private BigDecimal pickupLng;

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private BigDecimal dropoffLat;

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private BigDecimal dropoffLng;

    @NotNull private UUID cityId;
}
