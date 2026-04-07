package tz.co.twende.auth.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.response.ApiResponse;

class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    void givenTooManyRequests_whenHandle_thenReturns429() {
        var ex = new TooManyRequestsException("Rate limit exceeded");

        ResponseEntity<ApiResponse<Void>> response = handler.handleTooManyRequests(ex);

        assertEquals(429, response.getStatusCode().value());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Rate limit exceeded", response.getBody().getMessage());
    }
}
