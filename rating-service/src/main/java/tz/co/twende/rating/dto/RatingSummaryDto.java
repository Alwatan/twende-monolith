package tz.co.twende.rating.dto;

import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingSummaryDto {
    private UUID userId;
    private double averageScore;
    private long totalRatings;
    private Map<Short, Long> distribution;
}
