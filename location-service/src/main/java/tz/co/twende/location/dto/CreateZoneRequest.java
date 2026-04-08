package tz.co.twende.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateZoneRequest {
    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank private String boundary;

    @NotBlank
    @Size(max = 20)
    private String type;

    private String config;
}
