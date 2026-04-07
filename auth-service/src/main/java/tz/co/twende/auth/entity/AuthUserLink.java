package tz.co.twende.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(
        name = "auth_user_links",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_auth_user_links_provider_user",
                        columnNames = {"provider", "provider_user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AuthUserLink extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private java.util.UUID userId;

    @Column(name = "provider", length = 10, nullable = false)
    private String provider;

    @Column(name = "provider_user_id", length = 255, nullable = false)
    private String providerUserId;

    @Column(name = "email", length = 255)
    private String email;
}
