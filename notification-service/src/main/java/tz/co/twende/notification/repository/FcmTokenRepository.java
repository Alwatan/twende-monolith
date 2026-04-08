package tz.co.twende.notification.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.notification.entity.FcmToken;

@Repository
public interface FcmTokenRepository extends JpaRepository<FcmToken, UUID> {

    Optional<FcmToken> findFirstByUserIdAndActiveTrueOrderByCreatedAtDesc(UUID userId);

    Optional<FcmToken> findByUserIdAndToken(UUID userId, String token);
}
