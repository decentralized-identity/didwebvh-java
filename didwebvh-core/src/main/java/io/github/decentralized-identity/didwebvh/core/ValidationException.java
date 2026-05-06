package io.github.decentralizedidentity.didwebvh.core;

/** Thrown when input data fails validation. */
public class ValidationException extends DidWebVhException {

    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
