package tz.co.twende.compliance.exception;

public class ComplianceException extends RuntimeException {

    public ComplianceException(String message) {
        super(message);
    }

    public ComplianceException(String message, Throwable cause) {
        super(message, cause);
    }
}
