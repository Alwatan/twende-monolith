package com.twende.common.response;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void givenData_whenOk_thenReturnsSuccessTrueWithDataAndNullMessage() {
        ApiResponse<String> response = ApiResponse.ok("hello");

        assertTrue(response.isSuccess());
        assertEquals("hello", response.getData());
        assertNull(response.getMessage());
    }

    @Test
    void givenDataAndMessage_whenOk_thenReturnsSuccessTrueWithDataAndMessage() {
        ApiResponse<String> response = ApiResponse.ok("hello", "Created successfully");

        assertTrue(response.isSuccess());
        assertEquals("hello", response.getData());
        assertEquals("Created successfully", response.getMessage());
    }

    @Test
    void givenMessage_whenError_thenReturnsSuccessFalseWithMessageAndNullData() {
        ApiResponse<String> response = ApiResponse.error("Something went wrong");

        assertFalse(response.isSuccess());
        assertEquals("Something went wrong", response.getMessage());
        assertNull(response.getData());
    }

    @Test
    void givenAnyFactoryMethod_whenCalled_thenTimestampIsSetAutomatically() {
        ApiResponse<String> ok = ApiResponse.ok("data");
        ApiResponse<String> error = ApiResponse.error("error");

        assertNotNull(ok.getTimestamp());
        assertNotNull(error.getTimestamp());
    }

    @Test
    void givenNullData_whenOk_thenReturnsSuccessTrueWithNullData() {
        ApiResponse<Object> response = ApiResponse.ok(null);

        assertTrue(response.isSuccess());
        assertNull(response.getData());
    }

    @Test
    void givenIntegerData_whenOk_thenGenericTypeIsPreserved() {
        ApiResponse<Integer> response = ApiResponse.ok(42);

        assertTrue(response.isSuccess());
        assertEquals(42, response.getData());
    }
}
