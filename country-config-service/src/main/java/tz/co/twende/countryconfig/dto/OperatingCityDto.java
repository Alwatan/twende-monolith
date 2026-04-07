package tz.co.twende.countryconfig.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperatingCityDto implements Serializable {

    private UUID id;
    private String countryCode;
    private String cityId;
    private String name;
    private String timezone;
    private String status;
    private double centerLat;
    private double centerLng;
    private Integer radiusKm;

    // Per-city provider switching
    private String geocodingProvider;
    private String routingProvider;
    private String autocompleteProvider;

    private Instant createdAt;
}
