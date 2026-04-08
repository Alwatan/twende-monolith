package tz.co.twende.common.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.generator.EventType;
import org.junit.jupiter.api.Test;

class UlidGeneratorTest {

    private final UlidGenerator generator = new UlidGenerator();

    @Test
    void givenGenerator_whenGenerate_thenReturnsUuid() {
        Object result = generator.generate(null, new Object(), null, EventType.INSERT);
        assertNotNull(result);
        assertInstanceOf(UUID.class, result);
    }

    @Test
    void givenMultipleCalls_whenGenerate_thenReturnsDifferentUuids() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add((UUID) generator.generate(null, new Object(), null, EventType.INSERT));
        }
        assertEquals(100, ids.size(), "All 100 generated UUIDs should be unique");
    }

    @Test
    void givenMonotonicUlids_whenGenerate_thenUuidsAreIncreasing() {
        UUID first = (UUID) generator.generate(null, new Object(), null, EventType.INSERT);
        UUID second = (UUID) generator.generate(null, new Object(), null, EventType.INSERT);
        UUID third = (UUID) generator.generate(null, new Object(), null, EventType.INSERT);

        assertTrue(first.compareTo(second) < 0, "First UUID should be less than second");
        assertTrue(second.compareTo(third) < 0, "Second UUID should be less than third");
    }

    @Test
    void givenGenerator_whenGenerateManyRapidly_thenAllAreMonotonicallyIncreasing() {
        UUID previous = (UUID) generator.generate(null, new Object(), null, EventType.INSERT);
        for (int i = 0; i < 50; i++) {
            UUID current = (UUID) generator.generate(null, new Object(), null, EventType.INSERT);
            assertTrue(
                    previous.compareTo(current) < 0,
                    "UUID at index " + i + " should be greater than previous");
            previous = current;
        }
    }

    @Test
    void givenEntityWithPresetId_whenGenerate_thenPresetIdPreserved() {
        UUID presetId = UUID.randomUUID();
        BaseEntity entity = new BaseEntity() {
                    // anonymous subclass for testing
                };
        entity.setId(presetId);

        Object result = generator.generate(null, entity, null, EventType.INSERT);
        assertEquals(presetId, result, "Pre-set ID should be preserved");
    }

    @Test
    void givenCurrentValueNotNull_whenGenerate_thenCurrentValueReturned() {
        UUID currentValue = UUID.randomUUID();
        Object result = generator.generate(null, new Object(), currentValue, EventType.INSERT);
        assertEquals(currentValue, result, "Non-null currentValue should be returned as-is");
    }

    @Test
    void givenInsertOnly_whenGetEventTypes_thenContainsOnlyInsert() {
        var eventTypes = generator.getEventTypes();
        assertTrue(eventTypes.contains(EventType.INSERT));
        assertFalse(eventTypes.contains(EventType.UPDATE));
    }
}
