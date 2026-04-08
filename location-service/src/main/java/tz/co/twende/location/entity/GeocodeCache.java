package tz.co.twende.location.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "geocode_cache")
@Getter
@Setter
@NoArgsConstructor
public class GeocodeCache extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String queryHash;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 30)
    private String provider;

    @Column(nullable = false)
    private int hitCount = 1;

    private Instant expiresAt;
}
