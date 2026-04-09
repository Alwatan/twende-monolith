package tz.co.twende.compliance.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ComplianceExceptionTest {

    @Test
    void givenMessage_whenCreate_thenMessagePreserved() {
        ComplianceException ex = new ComplianceException("test error");
        assertEquals("test error", ex.getMessage());
    }

    @Test
    void givenMessageAndCause_whenCreate_thenBothPreserved() {
        RuntimeException cause = new RuntimeException("root cause");
        ComplianceException ex = new ComplianceException("test error", cause);
        assertEquals("test error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
