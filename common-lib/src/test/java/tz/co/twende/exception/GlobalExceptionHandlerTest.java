package tz.co.twende.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tz.co.twende.common.response.ApiResponse;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void givenResourceNotFoundException_whenHandleNotFound_thenReturns404WithErrorMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Ride not found");

        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Ride not found", response.getBody().getMessage());
    }

    @Test
    void givenConflictException_whenHandleConflict_thenReturns409() {
        ConflictException ex = new ConflictException("Phone number already registered");

        ResponseEntity<ApiResponse<Void>> response = handler.handleConflict(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Phone number already registered", response.getBody().getMessage());
    }

    @Test
    void givenUnauthorizedException_whenHandleUnauthorized_thenReturns401() {
        UnauthorizedException ex = new UnauthorizedException("Invalid token");

        ResponseEntity<ApiResponse<Void>> response = handler.handleUnauthorized(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid token", response.getBody().getMessage());
    }

    @Test
    void givenBadRequestException_whenHandleBadRequest_thenReturns400() {
        BadRequestException ex = new BadRequestException("Phone number is required");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBadRequest(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Phone number is required", response.getBody().getMessage());
    }

    @Test
    void givenGenericException_whenHandleGeneral_thenReturns500WithGenericMessage() {
        Exception ex = new RuntimeException("Something unexpected");

        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("An unexpected error occurred", response.getBody().getMessage());
    }

    @Test
    void givenResourceNotFoundException_whenHandled_thenDataIsNull() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Not found");

        ResponseEntity<ApiResponse<Void>> response = handler.handleNotFound(ex);

        assertNull(response.getBody().getData());
    }
}
