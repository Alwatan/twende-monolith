package tz.co.twende.common.exception;

public class ConflictException extends TwendeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
