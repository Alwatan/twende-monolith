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
public class PlaceResult {
    private String placeId;
    private String description;
    private String mainText;
    private String secondaryText;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
