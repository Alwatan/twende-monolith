package tz.co.twende.location.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

@ExtendWith(MockitoExtension.class)
class JwtHandshakeInterceptorTest {

    @Mock private ServerHttpRequest request;
    @Mock private ServerHttpResponse response;
    @Mock private WebSocketHandler wsHandler;

    private JwtHandshakeInterceptor interceptor;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        interceptor = new JwtHandshakeInterceptor();
        attributes = new HashMap<>();
    }

    @Test
    void givenNoToken_whenBeforeHandshake_thenRejectHandshake() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/location"));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
    }

    @Test
    void givenBlankToken_whenBeforeHandshake_thenRejectHandshake() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/location?token="));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
    }

    @Test
    void givenMalformedToken_whenBeforeHandshake_thenRejectHandshake() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/location?token=notajwt"));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
    }

    @Test
    void givenValidJwt_whenBeforeHandshake_thenAcceptAndSetAttributes() throws Exception {
        UUID userId = UUID.randomUUID();
        // Build a fake JWT with base64-encoded payload
        String payloadJson =
                "{\"sub\":\"" + userId + "\",\"role\":\"DRIVER\",\"countryCode\":\"TZ\"}";
        String base64Payload =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payloadJson.getBytes());
        String fakeJwt = "header." + base64Payload + ".signature";

        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/location?token=" + fakeJwt));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(userId);
        assertThat(attributes.get("token")).isEqualTo(fakeJwt);
        assertThat(attributes.get("role")).isEqualTo("DRIVER");
        assertThat(attributes.get("countryCode")).isEqualTo("TZ");
    }

    @Test
    void givenJwtWithoutSubClaim_whenBeforeHandshake_thenReject() throws Exception {
        String payloadJson = "{\"role\":\"DRIVER\"}";
        String base64Payload =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payloadJson.getBytes());
        String fakeJwt = "header." + base64Payload + ".signature";

        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/location?token=" + fakeJwt));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
    }

    @Test
    void givenJwtWithoutOptionalClaims_whenBeforeHandshake_thenAcceptWithoutOptionals()
            throws Exception {
        UUID userId = UUID.randomUUID();
        String payloadJson = "{\"sub\":\"" + userId + "\"}";
        String base64Payload =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payloadJson.getBytes());
        String fakeJwt = "header." + base64Payload + ".signature";

        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/location?token=" + fakeJwt));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(userId);
        assertThat(attributes).doesNotContainKey("role");
        assertThat(attributes).doesNotContainKey("countryCode");
    }

    @Test
    void givenInvalidUuidInSub_whenBeforeHandshake_thenReject() throws Exception {
        String payloadJson = "{\"sub\":\"not-a-uuid\"}";
        String base64Payload =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(payloadJson.getBytes());
        String fakeJwt = "header." + base64Payload + ".signature";

        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/location?token=" + fakeJwt));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
    }

    @Test
    void givenAfterHandshake_whenCalled_thenNoOp() {
        // Should not throw
        interceptor.afterHandshake(request, response, wsHandler, null);
    }
}
