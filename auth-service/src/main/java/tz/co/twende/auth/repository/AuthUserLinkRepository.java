package tz.co.twende.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.auth.entity.AuthUserLink;

@Repository
public interface AuthUserLinkRepository extends JpaRepository<AuthUserLink, UUID> {

    Optional<AuthUserLink> findByProviderAndProviderUserId(String provider, String providerUserId);

    List<AuthUserLink> findByUserId(UUID userId);

    boolean existsByUserIdAndProvider(UUID userId, String provider);
}
