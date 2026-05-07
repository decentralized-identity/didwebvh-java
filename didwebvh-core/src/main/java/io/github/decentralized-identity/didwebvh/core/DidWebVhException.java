package io.github.decentralizedidentity.didwebvh.core;

/** Base runtime exception for all did:webvh errors. */
public class DidWebVhException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DidWebVhException(String message) {
        super(message);
    }

    public DidWebVhException(String message, Throwable cause) {
        super(message, cause);
    }
}
