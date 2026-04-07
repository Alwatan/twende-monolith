package com.twende.common.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UlidGeneratorTest {

    private final UlidGenerator generator = new UlidGenerator();

    @Test
    void givenGenerator_whenGenerate_thenReturnsUuid() {
        Object result = generator.generate(null, null);
        assertNotNull(result);
        assertInstanceOf(UUID.class, result);
    }

    @Test
    void givenMultipleCalls_whenGenerate_thenReturnsDifferentUuids() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add((UUID) generator.generate(null, null));
        }
        assertEquals(100, ids.size(), "All 100 generated UUIDs should be unique");
    }

    @Test
    void givenMonotonicUlids_whenGenerate_thenUuidsAreIncreasing() {
        UUID first = (UUID) generator.generate(null, null);
        UUID second = (UUID) generator.generate(null, null);
        UUID third = (UUID) generator.generate(null, null);

        // ULID monotonic property: each successive ID is greater than the previous
        assertTrue(first.compareTo(second) < 0, "First UUID should be less than second");
        assertTrue(second.compareTo(third) < 0, "Second UUID should be less than third");
    }

    @Test
    void givenGenerator_whenGenerateManyRapidly_thenAllAreMonotonicallyIncreasing() {
        UUID previous = (UUID) generator.generate(null, null);
        for (int i = 0; i < 50; i++) {
            UUID current = (UUID) generator.generate(null, null);
            assertTrue(
                    previous.compareTo(current) < 0,
                    "UUID at index " + i + " should be greater than previous");
            previous = current;
        }
    }
}
