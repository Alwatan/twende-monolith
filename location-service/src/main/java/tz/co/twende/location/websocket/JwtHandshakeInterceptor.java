package tz.co.twende.location.websocket;

import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token =
                UriComponentsBuilder.fromUri(request.getURI())
                        .build()
                        .getQueryParams()
                        .getFirst("token");
        if (token == null || token.isBlank()) {
            log.warn("WebSocket handshake rejected: no token provided");
            return false;
        }

        try {
            // Decode JWT payload (base64) to extract claims
            // In production, validate signature against JWKS
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                log.warn("WebSocket handshake rejected: malformed token");
                return false;
            }
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

            // Extract userId (sub claim)
            String sub = extractClaim(payload, "sub");
            if (sub == null) {
                log.warn("WebSocket handshake rejected: no sub claim");
                return false;
            }

            attributes.put("userId", UUID.fromString(sub));
            attributes.put("token", token);

            // Extract optional claims
            String role = extractClaim(payload, "role");
            if (role != null) {
                attributes.put("role", role);
            }
            String countryCode = extractClaim(payload, "countryCode");
            if (countryCode != null) {
                attributes.put("countryCode", countryCode);
            }

            log.debug("WebSocket handshake accepted for user {}", sub);
            return true;
        } catch (Exception e) {
            log.warn("WebSocket handshake rejected: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }

    private String extractClaim(String jsonPayload, String claim) {
        String search = "\"" + claim + "\":\"";
        int idx = jsonPayload.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        int end = jsonPayload.indexOf("\"", start);
        if (end < 0) return null;
        return jsonPayload.substring(start, end);
    }
}
