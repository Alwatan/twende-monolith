package tz.co.twende.notification.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tz.co.twende.notification.entity.NotificationLog;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByUserIdOrderBySentAtDesc(UUID userId, Pageable pageable);

    boolean existsByTemplateKeyAndProviderRef(String templateKey, String providerRef);
}
