package tz.co.twende.location.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OperatingCityDto {
    private UUID id;
    private String countryCode;
    private UUID cityId;
    private String name;
    private String timezone;
    private String status;
    private BigDecimal centerLat;
    private BigDecimal centerLng;
    private BigDecimal radiusKm;
    private String geocodingProvider;
    private String routingProvider;
    private String autocompleteProvider;
}
