package io.github.decentralizedidentity.didwebvh.core;

/** Thrown when a did:webvh URL cannot be parsed. */
public class UrlParseException extends DidWebVhException {

    private static final long serialVersionUID = 1L;

    public UrlParseException(String message) {
        super(message);
    }

    public UrlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
