package tz.co.twende.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String fullName;

    @Column(length = 255)
    private String email;

    @Column(length = 500)
    private String profilePhotoUrl;

    @Column(length = 10)
    private String preferredLocale;

    @Column(length = 30)
    private String preferredPaymentMethod;

    @Column(nullable = false)
    private Boolean isActive = true;
}
