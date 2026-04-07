package tz.co.twende.countryconfig.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCountryConfigRequest {

    @Size(max = 100)
    private String name;

    private String status;

    @Size(max = 10)
    private String defaultLocale;

    private String[] supportedLocales;

    @Size(max = 20)
    private String dateFormat;

    @Size(max = 5)
    private String distanceUnit;

    @Size(max = 5)
    private String timeFormat;

    @Size(max = 3)
    private String currencyCode;

    @Size(max = 5)
    private String currencySymbol;

    private Integer minorUnits;

    @Size(max = 20)
    private String displayFormat;

    @Size(max = 5)
    private String phonePrefix;

    private Boolean cashEnabled;

    @Size(max = 50)
    private String subscriptionAggregator;

    @Size(max = 30)
    private String smsProvider;

    @Size(max = 30)
    private String pushProvider;

    @Size(max = 100)
    private String regulatoryAuthority;

    private Boolean tripReportingRequired;

    private Integer dataRetentionDays;

    private String features;
}
