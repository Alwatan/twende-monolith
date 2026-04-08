package tz.co.twende.location.websocket;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import tz.co.twende.location.dto.LocationUpdateMessage;
import tz.co.twende.location.service.LocationService;

@Component
@RequiredArgsConstructor
@Slf4j
public class LocationWebSocketHandler extends TextWebSocketHandler {

    private final LocationService locationService;
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = getUserId(session);
        if (userId != null) {
            sessionRegistry.register(userId, session);
            log.info("WebSocket connected: user={}", userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();

            // Handle PING
            if (payload.contains("\"type\":\"PING\"") || payload.contains("\"type\": \"PING\"")) {
                session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
                return;
            }

            // Handle LOCATION_UPDATE
            if (payload.contains("\"LOCATION_UPDATE\"")) {
                LocationUpdateMessage update =
                        objectMapper.readValue(payload, LocationUpdateMessage.class);
                UUID userId = getUserId(session);
                String countryCode = getCountryCode(session);
                String vehicleType = getVehicleType(session);

                if (userId != null && countryCode != null && vehicleType != null) {
                    locationService.updateDriverLocation(
                            userId,
                            countryCode,
                            vehicleType,
                            update.getLatitude(),
                            update.getLongitude(),
                            update.getBearing(),
                            update.getSpeedKmh());
                }
            }
        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = getUserId(session);
        if (userId != null) {
            sessionRegistry.unregister(userId);
            log.info("WebSocket disconnected: user={}, status={}", userId, status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        UUID userId = getUserId(session);
        log.error("WebSocket transport error for user {}", userId, exception);
    }

    private UUID getUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        return userId instanceof UUID ? (UUID) userId : null;
    }

    private String getCountryCode(WebSocketSession session) {
        Object cc = session.getAttributes().get("countryCode");
        return cc != null ? cc.toString() : "TZ";
    }

    private String getVehicleType(WebSocketSession session) {
        Object vt = session.getAttributes().get("vehicleType");
        return vt != null ? vt.toString() : "BAJAJ";
    }
}
