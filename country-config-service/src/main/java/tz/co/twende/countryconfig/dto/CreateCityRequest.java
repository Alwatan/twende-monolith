package tz.co.twende.countryconfig.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCityRequest {

    @NotBlank private String cityId;

    @NotBlank private String name;

    @NotBlank private String timezone;

    @NotNull private Double centerLat;

    @NotNull private Double centerLng;

    @NotNull private Integer radiusKm;
}
