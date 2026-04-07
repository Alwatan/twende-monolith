package com.twende.common.exception;

public class TwendeException extends RuntimeException {

    public TwendeException(String message) {
        super(message);
    }

    public TwendeException(String message, Throwable cause) {
        super(message, cause);
    }
}
