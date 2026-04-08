package tz.co.twende.location.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateZoneRequest {
    @Size(max = 100)
    private String name;

    private String boundary;

    @Size(max = 20)
    private String type;

    private String config;
    private Boolean active;
}
