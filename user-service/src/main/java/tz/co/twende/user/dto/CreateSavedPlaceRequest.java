package tz.co.twende.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSavedPlaceRequest {

    @NotBlank
    @Size(max = 50)
    private String label;

    @NotBlank
    @Size(max = 300)
    private String address;

    @NotNull private Double latitude;

    @NotNull private Double longitude;
}
