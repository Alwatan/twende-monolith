package tz.co.twende.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;
import tz.co.twende.common.enums.UserRole;

@Entity
@Table(name = "auth_users")
@Getter
@Setter
@NoArgsConstructor
public class AuthUser extends BaseEntity {

    @Column(name = "phone_number", length = 20, unique = true)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private UserRole role;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified = false;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "auth_provider", length = 10, nullable = false)
    private String authProvider = "PHONE";

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "profile_photo_url", length = 500)
    private String profilePhotoUrl;
}
