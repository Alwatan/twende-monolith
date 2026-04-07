package tz.co.twende.gateway.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class FallbackControllerTest {

    private FallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new FallbackController();
    }

    @Test
    void givenFallbackCalled_whenGet_thenReturns503() {
        ResponseEntity<Map<String, Object>> response = controller.fallbackGet();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("success"));
        assertEquals(
                "Service temporarily unavailable. Please try again shortly.",
                response.getBody().get("message"));
        assertNull(response.getBody().get("data"));
    }

    @Test
    void givenFallbackCalled_whenPost_thenReturns503() {
        ResponseEntity<Map<String, Object>> response = controller.fallbackPost();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse((Boolean) response.getBody().get("success"));
        assertEquals(
                "Service temporarily unavailable. Please try again shortly.",
                response.getBody().get("message"));
        assertNull(response.getBody().get("data"));
    }
}
