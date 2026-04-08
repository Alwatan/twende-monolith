package tz.co.twende.rating.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingCacheEntry {
    private double average;
    private long count;
    private Instant updatedAt;
}
