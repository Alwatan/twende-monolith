package tz.co.twende.subscription.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.subscription.entity.Subscription;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    boolean existsByDriverIdAndStatusAndExpiresAtAfter(UUID driverId, String status, Instant now);

    List<Subscription> findByStatusAndExpiresAtBefore(String status, Instant now);

    Page<Subscription> findByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable pageable);

    Optional<Subscription> findFirstByDriverIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
            UUID driverId, String status, Instant now);
}
