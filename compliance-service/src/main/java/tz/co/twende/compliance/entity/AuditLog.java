package tz.co.twende.compliance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tz.co.twende.common.entity.UlidId;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @UlidId
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 100)
    private String eventType;

    private UUID entityId;

    private UUID actorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;
}
