package tz.co.twende.countryconfig.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(
        name = "flat_fee_configs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"country_code", "service_category"}))
@Getter
@Setter
@NoArgsConstructor
public class FlatFeeConfig extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String serviceCategory;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(nullable = false)
    private boolean active = true;
}
