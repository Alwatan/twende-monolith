package tz.co.twende.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String channel;

    @Column(name = "template_key", length = 100)
    private String templateKey;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 30)
    private String provider;

    @Column(name = "provider_ref", length = 200)
    private String providerRef;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}
