package tz.co.twende.location.websocket;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import tz.co.twende.location.dto.LocationUpdateMessage;
import tz.co.twende.location.service.LocationService;

@ExtendWith(MockitoExtension.class)
class LocationWebSocketHandlerTest {

    @Mock private LocationService locationService;
    @Mock private WebSocketSessionRegistry sessionRegistry;
    @Mock private ObjectMapper objectMapper;
    @Mock private WebSocketSession session;

    @InjectMocks private LocationWebSocketHandler handler;

    private UUID userId;
    private Map<String, Object> sessionAttributes;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionAttributes = new HashMap<>();
        sessionAttributes.put("userId", userId);
        sessionAttributes.put("countryCode", "TZ");
        sessionAttributes.put("vehicleType", "BAJAJ");
    }

    @Test
    void givenValidSession_whenConnectionEstablished_thenRegisterSession() {
        when(session.getAttributes()).thenReturn(sessionAttributes);

        handler.afterConnectionEstablished(session);

        verify(sessionRegistry).register(userId, session);
    }

    @Test
    void givenNoUserId_whenConnectionEstablished_thenDoNotRegister() {
        Map<String, Object> emptyAttributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(emptyAttributes);

        handler.afterConnectionEstablished(session);

        verify(sessionRegistry, never()).register(any(), any());
    }

    @Test
    void givenPingMessage_whenHandleTextMessage_thenRespondWithPong() throws Exception {
        TextMessage pingMessage = new TextMessage("{\"type\":\"PING\"}");
        handler.handleTextMessage(session, pingMessage);

        verify(session).sendMessage(argThat(msg -> msg.getPayload().equals("{\"type\":\"PONG\"}")));
    }

    @Test
    void givenLocationUpdate_whenHandleTextMessage_thenUpdateDriverLocation() throws Exception {
        when(session.getAttributes()).thenReturn(sessionAttributes);

        String payload =
                "{\"type\":\"LOCATION_UPDATE\",\"latitude\":-6.792,\"longitude\":39.208,\"bearing\":45,\"speedKmh\":32}";
        LocationUpdateMessage update = new LocationUpdateMessage();
        update.setType("LOCATION_UPDATE");
        update.setLatitude(new BigDecimal("-6.792"));
        update.setLongitude(new BigDecimal("39.208"));
        update.setBearing(45);
        update.setSpeedKmh(32);

        when(objectMapper.readValue(eq(payload), eq(LocationUpdateMessage.class)))
                .thenReturn(update);

        TextMessage message = new TextMessage(payload);
        handler.handleTextMessage(session, message);

        verify(locationService)
                .updateDriverLocation(
                        eq(userId),
                        eq("TZ"),
                        eq("BAJAJ"),
                        eq(new BigDecimal("-6.792")),
                        eq(new BigDecimal("39.208")),
                        eq(45),
                        eq(32));
    }

    @Test
    void givenMissingSessionAttributes_whenLocationUpdate_thenDoNotUpdate() throws Exception {
        Map<String, Object> partialAttributes = new HashMap<>();
        // no userId
        when(session.getAttributes()).thenReturn(partialAttributes);

        String payload = "{\"type\":\"LOCATION_UPDATE\",\"latitude\":-6.792,\"longitude\":39.208}";
        LocationUpdateMessage update = new LocationUpdateMessage();
        update.setLatitude(new BigDecimal("-6.792"));
        update.setLongitude(new BigDecimal("39.208"));
        when(objectMapper.readValue(eq(payload), eq(LocationUpdateMessage.class)))
                .thenReturn(update);

        TextMessage message = new TextMessage(payload);
        handler.handleTextMessage(session, message);

        verify(locationService, never())
                .updateDriverLocation(any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void givenValidSession_whenConnectionClosed_thenUnregisterSession() {
        when(session.getAttributes()).thenReturn(sessionAttributes);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionRegistry).unregister(userId);
    }

    @Test
    void givenNoUserId_whenConnectionClosed_thenDoNotUnregister() {
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionRegistry, never()).unregister(any());
    }

    @Test
    void givenException_whenHandleTextMessage_thenLogErrorWithoutThrowing() throws Exception {
        String payload = "{\"type\":\"LOCATION_UPDATE\",\"bad\":true}";

        when(objectMapper.readValue(eq(payload), eq(LocationUpdateMessage.class)))
                .thenThrow(new RuntimeException("parse error"));

        TextMessage message = new TextMessage(payload);
        // Should not throw - error is caught and logged
        handler.handleTextMessage(session, message);
    }

    @Test
    void givenTransportError_whenHandleTransportError_thenLogError() {
        when(session.getAttributes()).thenReturn(sessionAttributes);

        handler.handleTransportError(session, new RuntimeException("transport failure"));

        // Verify it does not throw; just logs
    }

    @Test
    void givenDefaultCountryCode_whenNoCountryCodeInSession_thenUseTZ() throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        // no countryCode, no vehicleType
        when(session.getAttributes()).thenReturn(attrs);

        String payload = "{\"type\":\"LOCATION_UPDATE\",\"latitude\":-6.0,\"longitude\":39.0}";
        LocationUpdateMessage update = new LocationUpdateMessage();
        update.setLatitude(new BigDecimal("-6.0"));
        update.setLongitude(new BigDecimal("39.0"));
        when(objectMapper.readValue(eq(payload), eq(LocationUpdateMessage.class)))
                .thenReturn(update);

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(locationService)
                .updateDriverLocation(
                        eq(userId), eq("TZ"), eq("BAJAJ"), any(), any(), eq(0), eq(0));
    }
}
