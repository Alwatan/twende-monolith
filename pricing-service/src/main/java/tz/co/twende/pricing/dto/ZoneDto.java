package tz.co.twende.pricing.dto;

import java.math.BigDecimal;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneDto {
    private String type;
    private Map<String, String> config;

    public BigDecimal getSurcharge() {
        if (config != null && config.containsKey("surcharge")) {
            return new BigDecimal(config.get("surcharge"));
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getMultiplier() {
        if (config != null && config.containsKey("multiplier")) {
            return new BigDecimal(config.get("multiplier"));
        }
        return BigDecimal.ONE;
    }

    public String getReason() {
        if (config != null && config.containsKey("reason")) {
            return config.get("reason");
        }
        return "This area is restricted";
    }
}
