package tz.co.twende.common.entity;

import com.github.f4b6a3.ulid.UlidCreator;
import java.util.EnumSet;
import java.util.UUID;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

/**
 * Custom Hibernate ID generator that produces monotonically increasing ULIDs stored as UUIDs.
 *
 * <p>Implements {@link BeforeExecutionGenerator} (the Hibernate 6.5+ / 7+ replacement for the
 * deprecated {@code IdentifierGenerator} + {@code @GenericGenerator} approach).
 *
 * <p>If the entity already has a non-null ID (e.g. set from an external event like
 * UserRegisteredEvent), that ID is preserved and no new ULID is generated. This supports the
 * pattern where entity IDs are assigned externally (e.g. auth-service assigns the user ID, and
 * driver-service receives it via Kafka and must use the same ID).
 */
public class UlidGenerator implements BeforeExecutionGenerator {

    @Override
    public Object generate(
            SharedSessionContractImplementor session,
            Object owner,
            Object currentValue,
            EventType eventType) {
        if (currentValue != null) {
            return currentValue;
        }
        if (owner instanceof BaseEntity entity) {
            UUID existingId = entity.getId();
            if (existingId != null) {
                return existingId;
            }
        }
        return UlidCreator.getMonotonicUlid().toUuid();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }
}
