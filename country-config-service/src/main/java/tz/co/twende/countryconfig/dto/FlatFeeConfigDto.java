package tz.co.twende.countryconfig.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlatFeeConfigDto implements Serializable {

    private UUID id;
    private String countryCode;
    private String serviceCategory;
    private BigDecimal percentage;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
