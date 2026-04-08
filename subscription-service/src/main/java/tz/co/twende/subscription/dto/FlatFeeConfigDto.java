package tz.co.twende.subscription.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlatFeeConfigDto {

    private String countryCode;
    private String serviceCategory;
    private BigDecimal percentage;
}
