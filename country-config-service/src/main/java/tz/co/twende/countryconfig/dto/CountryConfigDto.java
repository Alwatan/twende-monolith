package tz.co.twende.countryconfig.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryConfigDto implements Serializable {

    private String code;
    private String name;
    private String status;

    // Locale
    private String defaultLocale;
    private String[] supportedLocales;
    private String dateFormat;
    private String distanceUnit;
    private String timeFormat;

    // Currency
    private String currencyCode;
    private String currencySymbol;
    private Integer minorUnits;
    private String displayFormat;

    // Phone
    private String phonePrefix;

    // Payment
    private Boolean cashEnabled;
    private String subscriptionAggregator;

    // Notification providers
    private String smsProvider;
    private String pushProvider;

    // Regulatory
    private String regulatoryAuthority;
    private Boolean tripReportingRequired;
    private Integer dataRetentionDays;

    // Feature flags
    private String features;

    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;

    // Nested collections
    private List<VehicleTypeConfigDto> vehicleTypes;
    private List<OperatingCityDto> cities;
    private List<PaymentMethodConfigDto> paymentMethods;
    private List<RequiredDriverDocumentDto> requiredDocuments;
    private List<FlatFeeConfigDto> flatFeeConfigs;
}
