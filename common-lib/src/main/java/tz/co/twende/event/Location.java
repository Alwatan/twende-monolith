package tz.co.twende.common.event;

import java.math.BigDecimal;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Location {
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;
}
