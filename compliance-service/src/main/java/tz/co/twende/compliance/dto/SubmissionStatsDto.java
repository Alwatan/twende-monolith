package tz.co.twende.compliance.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionStatsDto {
    private String countryCode;
    private long totalReports;
    private long submitted;
    private long pending;
    private long failed;
    private Instant lastSubmissionAt;
}
