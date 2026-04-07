package tz.co.twende.countryconfig.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "country_configs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class CountryConfig {

    @Id
    @Column(length = 2, updatable = false, nullable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CountryStatus status;

    // Locale
    @Column(nullable = false, length = 10)
    private String defaultLocale;

    @Column(nullable = false, columnDefinition = "TEXT[]")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.ARRAY)
    private String[] supportedLocales;

    @Column(nullable = false, length = 20)
    private String dateFormat;

    @Column(nullable = false, length = 5)
    private String distanceUnit;

    @Column(nullable = false, length = 5)
    private String timeFormat;

    // Currency
    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 5)
    private String currencySymbol;

    @Column(nullable = false)
    private Integer minorUnits;

    @Column(nullable = false, length = 20)
    private String displayFormat;

    // Phone
    @Column(nullable = false, length = 5)
    private String phonePrefix;

    // Payment
    @Column(nullable = false)
    private Boolean cashEnabled;

    @Column(length = 50)
    private String subscriptionAggregator;

    // Notification providers
    @Column(nullable = false, length = 30)
    private String smsProvider;

    @Column(nullable = false, length = 30)
    private String pushProvider;

    // Regulatory
    @Column(length = 100)
    private String regulatoryAuthority;

    @Column(nullable = false)
    private Boolean tripReportingRequired;

    @Column(nullable = false)
    private Integer dataRetentionDays;

    // Feature flags (JSONB)
    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String features;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public enum CountryStatus {
        ACTIVE,
        COMING_SOON,
        INACTIVE
    }
}
