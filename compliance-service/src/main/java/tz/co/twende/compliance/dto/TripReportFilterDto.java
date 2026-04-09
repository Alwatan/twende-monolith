package tz.co.twende.compliance.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TripReportFilterDto {
    private String countryCode;
    private Boolean submitted;
    private Instant from;
    private Instant to;
}
