package tz.co.twende.location.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LatLng {
    private BigDecimal latitude;
    private BigDecimal longitude;
}
