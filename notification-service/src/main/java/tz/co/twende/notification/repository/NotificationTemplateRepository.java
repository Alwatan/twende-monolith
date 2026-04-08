package tz.co.twende.notification.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tz.co.twende.notification.entity.NotificationTemplate;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    @Query(
            "SELECT t FROM NotificationTemplate t WHERE t.templateKey = :key"
                    + " AND t.locale = :locale AND t.channel = :channel")
    Optional<NotificationTemplate> findByKeyAndLocaleAndChannel(
            String key, String locale, String channel);

    @Query(
            "SELECT t FROM NotificationTemplate t WHERE t.templateKey = :key"
                    + " AND t.locale = :locale")
    Optional<NotificationTemplate> findByKeyAndLocale(String key, String locale);
}
