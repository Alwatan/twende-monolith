package tz.co.twende.common.entity;

import com.github.f4b6a3.ulid.UlidCreator;
import java.util.UUID;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

/**
 * Custom Hibernate IdentifierGenerator that produces monotonically increasing ULIDs stored as
 * UUIDs. If the entity already has a non-null ID (e.g. set from an external event like
 * UserRegisteredEvent), that ID is preserved and no new ULID is generated.
 */
public class UlidGenerator implements IdentifierGenerator {
    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        if (object instanceof BaseEntity entity) {
            UUID existingId = entity.getId();
            if (existingId != null) {
                return existingId;
            }
        }
        return UlidCreator.getMonotonicUlid().toUuid();
    }
}
