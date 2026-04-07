package tz.co.twende.common.exception;

public class UnauthorizedException extends TwendeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
