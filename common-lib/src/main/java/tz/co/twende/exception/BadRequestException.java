package tz.co.twende.common.exception;

public class BadRequestException extends TwendeException {

    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
