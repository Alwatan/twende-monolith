package tz.co.twende.countryconfig.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(
        name = "operating_cities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"country_code", "city_id"}))
@Getter
@Setter
@NoArgsConstructor
public class OperatingCity extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String cityId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String timezone;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private double centerLat;

    @Column(nullable = false)
    private double centerLng;

    @Column(nullable = false)
    private Integer radiusKm;

    // Per-city provider switching
    @Column(nullable = false, length = 30)
    private String geocodingProvider;

    @Column(nullable = false, length = 30)
    private String routingProvider;

    @Column(nullable = false, length = 30)
    private String autocompleteProvider;
}
