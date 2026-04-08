package tz.co.twende.pricing.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneCheckDto {
    private boolean inServiceArea;
    private boolean restricted;
    private List<ZoneDto> zones;
}
