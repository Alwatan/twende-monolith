package tz.co.twende.analytics.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tz.co.twende.common.entity.BaseEntity;

@Entity
@Table(name = "analytics_events")
@Getter
@Setter
@NoArgsConstructor
public class AnalyticsEvent extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String eventType;

    private UUID actorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;
}
