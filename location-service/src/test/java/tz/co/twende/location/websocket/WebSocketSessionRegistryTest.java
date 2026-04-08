package tz.co.twende.location.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionRegistryTest {

    @Mock private WebSocketSession session;
    @Mock private WebSocketSession session2;

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
    }

    @Test
    void givenNewRegistry_whenGetActiveCount_thenReturnZero() {
        assertThat(registry.getActiveCount()).isEqualTo(0);
    }

    @Test
    void givenRegisteredSession_whenGetSession_thenReturnSession() {
        UUID userId = UUID.randomUUID();
        registry.register(userId, session);

        assertThat(registry.getSession(userId)).isEqualTo(session);
        assertThat(registry.getActiveCount()).isEqualTo(1);
    }

    @Test
    void givenRegisteredSession_whenUnregister_thenSessionRemoved() {
        UUID userId = UUID.randomUUID();
        registry.register(userId, session);
        registry.unregister(userId);

        assertThat(registry.getSession(userId)).isNull();
        assertThat(registry.getActiveCount()).isEqualTo(0);
    }

    @Test
    void givenOpenSession_whenIsConnected_thenReturnTrue() {
        UUID userId = UUID.randomUUID();
        when(session.isOpen()).thenReturn(true);
        registry.register(userId, session);

        assertThat(registry.isConnected(userId)).isTrue();
    }

    @Test
    void givenClosedSession_whenIsConnected_thenReturnFalse() {
        UUID userId = UUID.randomUUID();
        when(session.isOpen()).thenReturn(false);
        registry.register(userId, session);

        assertThat(registry.isConnected(userId)).isFalse();
    }

    @Test
    void givenNoSession_whenIsConnected_thenReturnFalse() {
        UUID userId = UUID.randomUUID();

        assertThat(registry.isConnected(userId)).isFalse();
    }

    @Test
    void givenMultipleUsers_whenRegister_thenAllTracked() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        registry.register(user1, session);
        registry.register(user2, session2);

        assertThat(registry.getActiveCount()).isEqualTo(2);
        assertThat(registry.getSession(user1)).isEqualTo(session);
        assertThat(registry.getSession(user2)).isEqualTo(session2);
    }
}
