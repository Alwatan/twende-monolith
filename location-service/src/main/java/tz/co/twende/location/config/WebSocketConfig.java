package tz.co.twende.location.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tz.co.twende.location.websocket.JwtHandshakeInterceptor;
import tz.co.twende.location.websocket.LocationWebSocketHandler;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final LocationWebSocketHandler locationWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(locationWebSocketHandler, "/ws/location")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
