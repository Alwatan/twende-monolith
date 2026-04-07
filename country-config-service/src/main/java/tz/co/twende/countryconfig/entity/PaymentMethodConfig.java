package tz.co.twende.countryconfig.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(
        name = "payment_method_configs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"country_code", "method_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PaymentMethodConfig extends BaseEntity {

    @Column(nullable = false, length = 30)
    private String methodId;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false)
    private Boolean isEnabled;

    @Column(nullable = false, length = 50)
    private String displayName;

    @Column(length = 50)
    private String iconKey;
}
