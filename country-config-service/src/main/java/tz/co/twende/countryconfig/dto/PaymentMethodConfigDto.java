package tz.co.twende.countryconfig.dto;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodConfigDto implements Serializable {

    private UUID id;
    private String countryCode;
    private String methodId;
    private String provider;
    private Boolean isEnabled;
    private String displayName;
    private String iconKey;
}
