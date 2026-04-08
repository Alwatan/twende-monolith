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
public class Route {
    private int distanceMetres;
    private int durationSeconds;
    private String polyline;
    private BigDecimal startLat;
    private BigDecimal startLng;
    private BigDecimal endLat;
    private BigDecimal endLng;
}
