package tz.co.twende.location.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodingResult {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String formattedAddress;
    private String placeId;
}
