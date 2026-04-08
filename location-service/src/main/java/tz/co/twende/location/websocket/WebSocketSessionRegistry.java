package tz.co.twende.location.websocket;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(UUID userId, WebSocketSession session) {
        sessions.put(userId, session);
    }

    public void unregister(UUID userId) {
        sessions.remove(userId);
    }

    public WebSocketSession getSession(UUID userId) {
        return sessions.get(userId);
    }

    public boolean isConnected(UUID userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }

    public int getActiveCount() {
        return sessions.size();
    }
}
